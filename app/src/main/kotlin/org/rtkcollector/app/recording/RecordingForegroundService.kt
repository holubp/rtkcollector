package org.rtkcollector.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.rtkcollector.app.diagnostics.DiagnosticCategory
import org.rtkcollector.app.diagnostics.DiagnosticsSettings
import org.rtkcollector.app.diagnostics.DiagnosticsStore
import org.rtkcollector.app.diagnostics.PerformanceDiagnosticSample
import org.rtkcollector.app.diagnostics.PerformanceDiagnostics
import org.rtkcollector.app.diagnostics.RuntimeDiagnosticRecord
import org.rtkcollector.app.diagnostics.RuntimeDiagnostics
import org.rtkcollector.app.mocklocation.AndroidMockLocationSink
import org.rtkcollector.app.mocklocation.MockLocationPublishResult
import org.rtkcollector.app.mocklocation.MockLocationPublisher
import org.rtkcollector.app.mocklocation.mockLocationSetupFailureMessage
import org.rtkcollector.app.profile.RecordingPolicyProfile
import org.rtkcollector.app.profile.validateWorkflowModeCommandsForStart
import org.rtkcollector.app.ui.MainActivity
import org.rtkcollector.app.receiver.isPlausibleUm980MaintenanceResponse
import org.rtkcollector.app.receiver.isUm980CommandOkResponse
import org.rtkcollector.app.receiver.um980VersionProbeBytes
import org.rtkcollector.app.ui.usb.UsbStartAccessDecision
import org.rtkcollector.app.usb.AndroidUsbSerialTransport
import org.rtkcollector.app.usb.UsbSerialOpenOptions
import org.rtkcollector.core.capture.AdvisoryConsumer
import org.rtkcollector.core.capture.AsyncAdvisoryFanout
import org.rtkcollector.core.capture.AdvisoryFanout
import org.rtkcollector.core.capture.CaptureEvent
import org.rtkcollector.core.capture.CaptureEventSink
import org.rtkcollector.core.capture.CaptureRuntime
import org.rtkcollector.core.capture.RawRecorder
import org.rtkcollector.core.correction.DefaultNtripRuntimeClient
import org.rtkcollector.core.correction.NtripClient
import org.rtkcollector.core.correction.NtripCasterUploadController
import org.rtkcollector.core.correction.NtripCasterUploadRequest
import org.rtkcollector.core.correction.NtripCasterUploadRuntimeConfig
import org.rtkcollector.core.correction.NtripCasterUploadSnapshot
import org.rtkcollector.core.correction.NtripCredentials
import org.rtkcollector.core.correction.NtripProtocolVersion
import org.rtkcollector.core.correction.NtripReconnectPolicy
import org.rtkcollector.core.correction.NtripRequest
import org.rtkcollector.core.correction.NtripRuntimeConfig
import org.rtkcollector.core.correction.NtripRuntimeController
import org.rtkcollector.core.correction.NtripRuntimeSnapshot
import org.rtkcollector.core.correction.NtripRuntimeState
import org.rtkcollector.core.correction.Rtcm3Extractor
import org.rtkcollector.core.correction.Rtcm3ReferenceStation
import org.rtkcollector.core.correction.Rtcm3ReferenceStationParser
import org.rtkcollector.core.rtklib.RtklibConfig
import org.rtkcollector.core.rtklib.RtklibNativeBridge
import org.rtkcollector.core.rtklib.RtklibOutputWriters
import org.rtkcollector.core.rtklib.RtklibPreset
import org.rtkcollector.core.rtklib.RtklibWorker
import org.rtkcollector.core.session.AntennaMetadata
import org.rtkcollector.core.session.NtripSessionMetadata
import org.rtkcollector.core.session.SerialParameters
import org.rtkcollector.core.session.SessionWriterCloseReport
import org.rtkcollector.core.session.SessionWriterIssue
import org.rtkcollector.core.session.SessionWriterIssueCategory
import org.rtkcollector.core.session.SessionWriterIssueSeverity
import org.rtkcollector.core.session.SessionMetadata
import org.rtkcollector.core.session.SessionMode
import org.rtkcollector.core.session.exportSessionMetadata
import org.rtkcollector.receiver.unicore.NmeaSentenceExtractor
import org.rtkcollector.receiver.unicore.NmeaGgaFix
import org.rtkcollector.receiver.unicore.NmeaGgaParser
import org.rtkcollector.receiver.unicore.NmeaGsaParser
import org.rtkcollector.receiver.unicore.NmeaGstParser
import org.rtkcollector.receiver.unicore.NmeaGsvInViewTracker
import org.rtkcollector.receiver.unicore.NmeaGsvParser
import org.rtkcollector.receiver.unicore.Um980AsciiSolution
import org.rtkcollector.receiver.unicore.Um980AsciiSolutionParser
import org.rtkcollector.receiver.unicore.Um980BinaryParser
import org.rtkcollector.receiver.unicore.Um980MessageFrequencyTracker
import org.rtkcollector.receiver.unicore.Um980MessageKind
import org.rtkcollector.receiver.unicore.Um980ModeParser
import org.rtkcollector.receiver.unicore.Um980NmeaExporter
import org.rtkcollector.receiver.unicore.Um980NmeaExportOptions
import org.rtkcollector.receiver.unicore.Um980PersistentBaudPlan
import org.rtkcollector.receiver.unicore.Um980PersistentBaudStep
import org.rtkcollector.receiver.unicore.Um980RuntimeCommandValidator
import org.rtkcollector.receiver.unicore.Um980StreamParser
import org.rtkcollector.receiver.unicore.Um980Telemetry
import org.rtkcollector.receiver.unicore.pppStatusLabel
import org.rtkcollector.receiver.unicore.toBestnavCandidate
import org.rtkcollector.receiver.unicore.toCandidate
import org.rtkcollector.receiver.unicore.toPppCandidate
import org.rtkcollector.receiver.unicore.um980PppStatusLabel
import org.rtkcollector.receiver.api.ReceiverCommand
import org.rtkcollector.receiver.ublox.UbloxMessageFrequencyTracker
import org.rtkcollector.receiver.ublox.UbloxMessageKind
import org.rtkcollector.receiver.ublox.UbloxNavPvtParser
import org.rtkcollector.receiver.ublox.UbloxNavSatParser
import org.rtkcollector.receiver.ublox.UbloxNmeaExporter
import org.rtkcollector.receiver.ublox.UbloxScriptCompiler
import org.rtkcollector.receiver.ublox.UbloxStreamParser
import org.rtkcollector.receiver.ublox.UbloxTelemetry
import org.rtkcollector.core.solution.BestSolutionSelector
import org.rtkcollector.core.solution.CoordinateAverageAddResult
import org.rtkcollector.core.solution.CoordinateAverageSummary
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionSourcePolicy
import org.rtkcollector.core.workflow.CorrectionFormat
import org.rtkcollector.core.workflow.RtklibCorrectionInputRoute
import org.rtkcollector.core.workflow.RtklibInputRoute
import org.rtkcollector.core.workflow.RtklibInputRouteKind
import org.rtkcollector.core.workflow.RtklibInputRoutePlan
import org.rtkcollector.core.workflow.RtklibRoverInputFormat
import org.rtkcollector.core.workflow.RtklibSolutionDirection
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RecordingForegroundService : Service() {
    private enum class CompilePhase { START, STOP }
    private val running = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private val shutdownSent = AtomicBoolean(false)
    private var captureThread: Thread? = null
    @Volatile private var runtime: CaptureRuntime? = null
    @Volatile private var transport: AndroidUsbSerialTransport? = null
    private var writers: RecordingSessionWriters? = null
    private var advisoryFanout: AsyncAdvisoryFanout? = null
    private var activeSessionMetadata: SessionMetadata? = null
    private var activeRecorder: SessionRawRecorder? = null
    private var activeEventSink: CaptureEventSink? = null
    private var activeProfileBaud: Int = 230400
    private var activeSerialBaud: Int = 230400
    private var activeTargetBaud: Int = 230400
    private var activeBaudPlan: Um980BaudTransitionPlan? = null
    private var activeUsbVid: Int? = null
    private var activeUsbPid: Int? = null
    private var ntripController: NtripRuntimeController? = null
    private var casterUploadController: NtripCasterUploadController? = null
    private var rtklibWorker: RtklibWorker? = null
    private var activeWorkflowUsesNtrip: Boolean = false
    private var activeNtripSendToReceiver: Boolean = true
    private var wakeLock: PowerManager.WakeLock? = null
    private var shutdownCommands: List<String> = emptyList()
    private var state = RecordingServiceState()
    private val diagnosticsSettings by lazy { DiagnosticsSettings(this) }
    private val diagnosticsStore by lazy { DiagnosticsStore(filesDir) }
    private val runtimeDiagnostics by lazy {
        RuntimeDiagnostics(diagnosticsStore) { diagnosticsSettings.runtimeLoggingEnabled }
    }
    private val performanceDiagnostics by lazy {
        PerformanceDiagnostics(diagnosticsStore) { diagnosticsSettings.performanceMonitoringEnabled }
    }
    private val persistentWriteInProgress = AtomicBoolean(false)
    private val runtimeLock = Any()
    private val txLock = Any()
    private var ntripRateWindowStartedAtMillis: Long = 0L
    private var ntripRateWindowCorrectionBytes: Long = 0L
    private var ntripRateWindowTxBytes: Long = 0L
    private var lastNotificationUpdateAtMillis: Long = 0L
    private var lastNotificationText: String = ""
    @Volatile
    private var activeReceiverFamily: String = ""
    private val bestSolutionExecutor: java.util.concurrent.ScheduledExecutorService =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "rtkcollector-best").apply { isDaemon = true }
        }
    private var bestSolutionTicker: java.util.concurrent.ScheduledFuture<*>? = null
    private var lastMockPublishedAt: Long? = null
    private var lastMockPublishedIdentity: String? = null
    private var lastMockPublishWallClockAtMillis: Long? = null
    private var previousMockResult: org.rtkcollector.app.mocklocation.MockLocationPublishResult? = null
    private var mockLocationRequested: Boolean = false
    private var mockLocationRateHz: Int = RecordingPolicyProfile.DEFAULT_MOCK_LOCATION_RATE_HZ
    private var screenSolutionPolicy: SolutionSourcePolicy = SolutionSourcePolicy.AUTO_BEST
    private var mockSolutionPolicy: SolutionSourcePolicy = SolutionSourcePolicy.AUTO_BEST
    private var ubloxStreamParser = UbloxStreamParser()
    private var ubloxFrequencyTracker = UbloxMessageFrequencyTracker()
    private var lastUbloxNavSatAtMillis: Long? = null
    private val routineStateBroadcastRateLimiter =
        StateBroadcastRateLimiter(ROUTINE_STATE_BROADCAST_INTERVAL_MILLIS)
    private var sharedNmeaGgaParser: NmeaGgaParser = NmeaGgaParser()
    private val solutionCandidates =
        java.util.concurrent.ConcurrentHashMap<String, org.rtkcollector.core.solution.SolutionCandidate>()
    private var mockLocationPublisher: MockLocationPublisher? = null
    private val coordinateAveragingController = CoordinateAveragingController()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording(sendShutdown = true)
            ACTION_QUERY -> {
                broadcastState()
                if (!running.get()) {
                    stopSelf(startId)
                }
            }
            ACTION_UPDATE_NTRIP -> updateNtrip(intent)
            ACTION_DISABLE_NTRIP -> disableNtrip()
            ACTION_UPDATE_MOCK_LOCATION -> updateMockLocation(intent)
            ACTION_WRITE_PERSISTENT_RECEIVER_CONFIG -> writePersistentReceiverConfig(intent)
            ACTION_START_COORDINATE_AVERAGING -> startCoordinateAveraging()
            ACTION_STOP_COORDINATE_AVERAGING -> stopCoordinateAveraging()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecording(sendShutdown = false)
        bestSolutionTicker?.cancel(false)
        bestSolutionTicker = null
        bestSolutionExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun startRecording(intent: Intent) {
        if (!running.compareAndSet(false, true)) {
            return
        }
        stopping.set(false)
        shutdownSent.set(false)
        activeWorkflowUsesNtrip = false
        routineStateBroadcastRateLimiter.reset()
        state = state.copy(
            lifecycle = RecordingLifecycleState.STARTING,
            errorCategory = RecordingErrorCategory.NONE,
            errorSeverity = RecordingErrorSeverity.NONE,
            lastError = null,
            receiverRxBytes = 0,
            txToReceiverBytes = 0,
            correctionInputBytes = 0,
            nmeaBytes = 0,
            sessionTotalBytes = 0,
            ntripTransferred = "0 B",
            ntripRates = "n/a",
            rawRecordingActive = false,
            correctionsActive = false,
            receiverRtkStatus = "n/a",
            rtkPositionType = null,
            rtkCalculateStatus = null,
            rtkCalculateStatusDescription = null,
            receiverRtkEvidenceAtMillis = null,
            correctionInputRtcmAtMillis = null,
            rtcmDecodedAtMillis = null,
            rtcmLastMessageId = null,
            rtcmLastBaseId = null,
            um980Frequency = DEFAULT_UM980_FREQUENCY_DISPLAY,
            um980Mode = "n/a",
            ubloxFrequency = DEFAULT_UBLOX_FREQUENCY_DISPLAY,
        ).clearBestSolutionFields(
            mockLocationState = "Disabled",
            ubloxFrequency = DEFAULT_UBLOX_FREQUENCY_DISPLAY,
        )
        broadcastState()

        lastNotificationText = recordingNotificationText(running = true, receiverRxBytes = 0, correctionInputBytes = 0)
        lastNotificationUpdateAtMillis = System.currentTimeMillis()
        startForeground(NOTIFICATION_ID, notification(lastNotificationText))
        acquireWakeLock()

        try {
            val usbDevice = intent.usbDevice() ?: error("No USB device supplied.")
            val usbManager = getSystemService(USB_SERVICE) as UsbManager
            if (!usbManager.hasPermission(usbDevice)) {
                error("USB permission has not been granted.")
            }

            val serialBaud = validateBaud(intent.getIntExtra(EXTRA_SERIAL_BAUD, 230400), "serial baud")
            val profileBaud = validateBaud(intent.getIntExtra(EXTRA_PROFILE_BAUD, serialBaud), "profile baud")
            val workflowUsesNtrip = intent.getBooleanExtra(EXTRA_NTRIP_ENABLED, false)
            activeReceiverFamily = recordingReceiverFamily(
                receiverProfileId = intent.getStringExtra(EXTRA_RECEIVER_PROFILE_ID),
                commandReceiverFamily = intent.getStringExtra(EXTRA_COMMAND_RECEIVER_FAMILY),
            )
            val receiverDriverId = sessionReceiverDriverId(
                receiverProfileId = intent.getStringExtra(EXTRA_RECEIVER_PROFILE_ID),
                commandReceiverFamily = intent.getStringExtra(EXTRA_COMMAND_RECEIVER_FAMILY),
            )
            val initCommands = intent.getStringArrayListExtra(EXTRA_INIT_COMMANDS).orEmpty().validatedCommands()
            val baudSwitchCommands = intent.getStringArrayListExtra(EXTRA_BAUD_SWITCH_COMMANDS).orEmpty().validatedCommands()
            val modeCommands = intent.getStringArrayListExtra(EXTRA_MODE_COMMANDS).orEmpty().validatedCommands()
            shutdownCommands = intent.getStringArrayListExtra(EXTRA_SHUTDOWN_COMMANDS).orEmpty().validatedCommands()
            validateWorkflowModeCommandsForStart(intent.getStringExtra(EXTRA_WORKFLOW_ID), initCommands + baudSwitchCommands + modeCommands)
            val preflight = RecordingStartPreflight.validate(
                RecordingStartPreflight.Input(
                    workflowUsesNtrip = workflowUsesNtrip,
                    usbProfileSelected = !intent.getStringExtra(EXTRA_USB_BAUD_PROFILE_ID).isNullOrBlank(),
                    usbDeviceConnected = true,
                    usbPermissionGranted = true,
                    serialDriverAvailable = AndroidUsbSerialTransport.hasUsableSerialEndpoint(usbDevice),
                    serialOpenSucceeded = true,
                    storageWritable = isStorageWritableForStart(intent),
                    ntripMountpointConfigured = !intent.getStringExtra(EXTRA_NTRIP_MOUNTPOINT).isNullOrBlank(),
                ),
            )
            if (!preflight.canStart) {
                error(preflight.message)
            }
            val usbTransport = AndroidUsbSerialTransport(
                usbManager = usbManager,
                device = usbDevice,
                options = UsbSerialOpenOptions(profileBaud),
            )
            runCatching { usbTransport.open() }
                .onFailure {
                    runCatching { usbTransport.close() }
                    error(UsbStartAccessDecision.openFailureMessage())
                }
            transport = usbTransport

            val openedSession = openSessionWriters(intent)
            val sessionWriters = openedSession.writers
            val startedAt = Instant.now().toString()
            val sessionUuid = UUID.randomUUID().toString()
            val metadata = SessionMetadata(
                appVersion = "0.1.0",
                androidDeviceModel = Build.MODEL ?: "unknown",
                androidVersion = Build.VERSION.RELEASE ?: "unknown",
                receiverDriverId = receiverDriverId,
                receiverIdentification = null,
                usbVid = usbDevice.vendorId,
                usbPid = usbDevice.productId,
                baudRate = serialBaud,
                serialParameters = SerialParameters(),
                mode = sessionModeFrom(intent.getStringExtra(EXTRA_RECEIVER_ROLE)),
                startedAt = startedAt,
                stoppedAt = null,
                ntrip = ntripSessionMetadata(intent),
                antenna = AntennaMetadata(),
                sessionUuid = sessionUuid,
                linkedBaseSessionUuid = null,
                workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID),
                workflowName = intent.getStringExtra(EXTRA_WORKFLOW_NAME),
                receiverRole = intent.getStringExtra(EXTRA_RECEIVER_ROLE),
                um980ProfileId = intent.getStringExtra(EXTRA_UM980_PROFILE_ID),
                commandProfileId = intent.getStringExtra(EXTRA_COMMAND_PROFILE_ID),
                usbBaudProfileId = intent.getStringExtra(EXTRA_USB_BAUD_PROFILE_ID),
                ntripCasterProfileId = intent.getStringExtra(EXTRA_NTRIP_CASTER_PROFILE_ID),
                ntripMountpointProfileId = intent.getStringExtra(EXTRA_NTRIP_MOUNTPOINT_PROFILE_ID),
                recordingPolicyId = intent.getStringExtra(EXTRA_RECORDING_POLICY_ID),
                rtklibProfileId = intent.getStringExtra(EXTRA_RTKLIB_PROFILE_ID),
                rtklibEnabled = intent.getBooleanExtra(EXTRA_RTKLIB_ENABLED, false),
                rtklibPreset = intent.getStringExtra(EXTRA_RTKLIB_PRESET),
                rtklibSnapshotId = intent.getStringExtra(EXTRA_RTKLIB_SNAPSHOT_ID),
                rtklibRoutePlan = intent.getStringExtra(EXTRA_RTKLIB_ROUTE_PLAN),
                rtklibValidationSummary = intent.getStringExtra(EXTRA_RTKLIB_VALIDATION_SUMMARY),
                rtklibOutputNmea = intent.getBooleanExtra(EXTRA_RTKLIB_OUTPUT_NMEA, false),
                rtklibOutputPos = intent.getBooleanExtra(EXTRA_RTKLIB_OUTPUT_POS, false),
                solutionPolicyProfileId = intent.getStringExtra(EXTRA_SOLUTION_POLICY_PROFILE_ID),
                solutionScreenPolicy = intent.getStringExtra(EXTRA_SOLUTION_SCREEN_POLICY),
                solutionMockPolicy = intent.getStringExtra(EXTRA_SOLUTION_MOCK_POLICY),
                storageProfileId = intent.getStringExtra(EXTRA_STORAGE_PROFILE_ID),
                storageKind = intent.getStringExtra(EXTRA_STORAGE_KIND),
                coordinateSource = intent.getStringExtra(EXTRA_COORDINATE_SOURCE),
                baseCoordinateId = intent.getStringExtra(EXTRA_BASE_COORDINATE_ID),
                baseCoordinateName = intent.getStringExtra(EXTRA_BASE_COORDINATE_NAME),
                baseCoordinateMethod = intent.getStringExtra(EXTRA_BASE_COORDINATE_METHOD),
                baseCasterUploadEnabled = intent.getBooleanExtra(EXTRA_BASE_CASTER_UPLOAD_ENABLED, false),
                baseCasterUploadHost = intent.getStringExtra(EXTRA_BASE_CASTER_UPLOAD_HOST),
                baseCasterUploadPort = if (intent.getBooleanExtra(EXTRA_BASE_CASTER_UPLOAD_ENABLED, false)) {
                    intent.getIntExtra(EXTRA_BASE_CASTER_UPLOAD_PORT, 2101)
                } else {
                    null
                },
                baseCasterUploadMountpoint = intent.getStringExtra(EXTRA_BASE_CASTER_UPLOAD_MOUNTPOINT),
                baseCasterUploadUsernamePresent =
                    intent.getBooleanExtra(EXTRA_BASE_CASTER_UPLOAD_USERNAME_PRESENT, false),
                baseCasterUploadSecretRef = intent.getStringExtra(EXTRA_BASE_CASTER_UPLOAD_SECRET_REF),
                baseCasterUploadFinalStatus = intent.getStringExtra(EXTRA_BASE_CASTER_UPLOAD_FINAL_STATUS),
                validationSummary = intent.getStringExtra(EXTRA_VALIDATION_SUMMARY),
                expectedArtifacts = intent.getStringArrayListExtra(EXTRA_EXPECTED_ARTIFACTS).orEmpty(),
            )
            sessionWriters.writeSessionJson(exportSessionMetadata(metadata))
            writers = sessionWriters
            activeSessionMetadata = metadata
            intent.getStringExtra(EXTRA_BASE_POSITION_JSON)
                ?.takeIf { it.isNotBlank() }
                ?.let(sessionWriters::writeBasePositionJson)
            val recorder = SessionRawRecorder(
                writers = sessionWriters,
                recordCorrectionInput = intent.getBooleanExtra(EXTRA_RECORD_NTRIP_CORRECTION_INPUT, true),
            )
            activeRecorder = recorder
            val eventSink = SessionEventSink(sessionWriters)
            activeEventSink = eventSink
            startRtklibWorkerIfEnabled(intent, sessionWriters)
            val casterUploadConfig = casterUploadRuntimeConfig(intent)
            val uploadController = casterUploadConfig?.let {
                NtripCasterUploadController().also { controller ->
                    casterUploadController = controller
                }
            }
            activeProfileBaud = profileBaud
            activeSerialBaud = profileBaud
            activeTargetBaud = serialBaud
            activeUsbVid = usbDevice.vendorId
            activeUsbPid = usbDevice.productId
            val asyncAdvisoryFanout = AsyncAdvisoryFanout(
                delegate = buildAdvisoryFanout(
                    sessionWriters = sessionWriters,
                    eventSink = eventSink,
                    exportNmea = intent.getBooleanExtra(EXTRA_EXPORT_NMEA, true),
                    nmeaExportOptions = Um980NmeaExportOptions(
                        pppGgaQuality = intent.getIntExtra(
                            EXTRA_PPP_NMEA_GGA_QUALITY,
                            Um980NmeaExportOptions.DEFAULT_PPP_GGA_QUALITY,
                        ),
                    ),
                    exportJsonSolution = intent.getBooleanExtra(EXTRA_EXPORT_JSON_SOLUTION, true),
                    casterUploadController = uploadController,
                ),
                eventSink = eventSink,
            )
            advisoryFanout = asyncAdvisoryFanout
            val captureRuntime = CaptureRuntime(
                transport = usbTransport,
                recorder = recorder,
                eventSink = eventSink,
                advisoryFanout = asyncAdvisoryFanout,
            )
            captureRuntime.open()
            runtime = captureRuntime

            solutionCandidates.clear()
            ubloxStreamParser = UbloxStreamParser()
            ubloxFrequencyTracker = UbloxMessageFrequencyTracker()
            sharedNmeaGgaParser = NmeaGgaParser()
            val enableMockLocation = intent.getBooleanExtra(EXTRA_ENABLE_MOCK_LOCATION, false)
            mockLocationRequested = enableMockLocation
            mockLocationRateHz = intent.mockLocationRateHz()
            screenSolutionPolicy = intent.solutionSourcePolicyExtra(EXTRA_SOLUTION_SCREEN_POLICY)
            mockSolutionPolicy = intent.solutionSourcePolicyExtra(EXTRA_SOLUTION_MOCK_POLICY)
            configureMockLocation(enableMockLocation)
            val configuredMode = Um980ModeParser.configuredMode(
                (initCommands + baudSwitchCommands + modeCommands).joinToString("\n"),
            )
            val baudPlan = Um980BaudTransitionPlan.build(
                profileBaud = profileBaud,
                serialBaud = serialBaud,
                initCommands = initCommands,
                baudSwitchCommands = baudSwitchCommands,
                modeCommands = modeCommands,
            )
            activeBaudPlan = baudPlan
            executeBaudTransition(baudPlan, captureRuntime, usbTransport)
            activeSerialBaud = serialBaud
            casterUploadConfig?.let { config ->
                uploadController?.start(config)
                eventSink.recordEvent(
                    CaptureEvent(
                        timestamp = Instant.now().toString(),
                        type = "base-caster-upload-started",
                        message = config.mountpointUrl,
                    ),
                )
                state = state.copy(
                    baseCasterUploadState = "CONNECTING",
                    baseCasterUploadUrl = config.mountpointUrl,
                )
            }

            val commandProfileLabel = intent.getStringExtra(EXTRA_SETTINGS_COMMAND_PROFILE_NAME)
                ?: intent.getStringExtra(EXTRA_COMMAND_PROFILE_ID)
                ?: intent.getStringExtra(EXTRA_RECEIVER_PROFILE_ID)
                ?: "n/a"
            state = state.copy(
                running = true,
                lifecycle = RecordingLifecycleState.RECORDING,
                workflowLabel = intent.getStringExtra(EXTRA_WORKFLOW_NAME) ?: intent.getStringExtra(EXTRA_WORKFLOW_ID) ?: "n/a",
                receiverLabel = commandProfileLabel,
                storageLabel = intent.getStringExtra(EXTRA_STORAGE_PROFILE_ID) ?: intent.getStringExtra(EXTRA_STORAGE_KIND) ?: "n/a",
                settingsSetLabel = intent.getStringExtra(EXTRA_SETTINGS_SET_NAME) ?: "n/a",
                settingsCommandProfileLabel = commandProfileLabel,
                settingsBaudProfileLabel = intent.getStringExtra(EXTRA_SETTINGS_USB_BAUD_PROFILE_NAME) ?: "n/a",
                settingsNtripCasterProfileLabel = intent.getStringExtra(EXTRA_SETTINGS_NTRIP_CASTER_PROFILE_NAME) ?: "n/a",
                settingsRecordingOutputProfileLabel = intent.getStringExtra(EXTRA_SETTINGS_RECORDING_OUTPUT_PROFILE_NAME) ?: "n/a",
                settingsStorageProfileLabel = intent.getStringExtra(EXTRA_SETTINGS_STORAGE_PROFILE_NAME) ?: "n/a",
                sessionPath = openedSession.displayPath,
                ntripState = if (intent.getBooleanExtra(EXTRA_NTRIP_ENABLED, false)) "Configured" else "Not configured",
                ntripUrl = ntripDisplayUrl(intent),
                ntripStationId = "",
                ntripBaseLatLon = latLonDisplay(
                    intent.optionalDoubleExtra(EXTRA_NTRIP_BASE_LAT),
                    intent.optionalDoubleExtra(EXTRA_NTRIP_BASE_LON),
                ),
                rawRecordingActive = true,
                correctionsActive = false,
                um980Mode = configuredMode?.let { "Commanded $it" } ?: "n/a",
                mockLocationRateHz = mockLocationRateHz,
            )
            ntripRateWindowStartedAtMillis = System.currentTimeMillis()
            ntripRateWindowCorrectionBytes = 0L
            ntripRateWindowTxBytes = 0L
            activeWorkflowUsesNtrip = workflowUsesNtrip
            activeNtripSendToReceiver = intent.getBooleanExtra(EXTRA_NTRIP_SEND_TO_RECEIVER, true)
            broadcastState()

            lastMockPublishedAt = null
            lastMockPublishedIdentity = null
            lastMockPublishWallClockAtMillis = null
            previousMockResult = null
            startBestSolutionTicker()

            captureThread = Thread({ captureLoop(recorder) }, "rtkcollector-capture").also { it.start() }
            maybeStartNtrip(intent, recorder)
        } catch (error: Throwable) {
            runCatching { casterUploadController?.stop() }
            casterUploadController = null
            state = state.copy(
                running = false,
                lifecycle = RecordingLifecycleState.FAILED,
                lastError = error.message,
                errorCategory = classifyStartError(error),
                errorSeverity = RecordingErrorSeverity.FATAL,
                rawRecordingActive = false,
                correctionsActive = false,
            )
            recordRuntimeDiagnostic(
                category = state.errorCategory.toDiagnosticCategory(),
                severity = state.errorSeverity.name,
                message = { "Recording start failed: ${error.message ?: error.javaClass.simpleName}" },
            )
            broadcastState()
            stopRecording(sendShutdown = false)
        }
    }

    private fun captureLoop(recorder: SessionRawRecorder) {
        while (running.get()) {
            runCatching {
                synchronized(runtimeLock) {
                    runtime?.readOnce(READ_BUFFER_BYTES) ?: 0
                }
                state = state.copy(
                    running = true,
                    receiverRxBytes = recorder.receiverRxBytes,
                    txToReceiverBytes = recorder.txToReceiverBytes,
                    correctionInputBytes = recorder.correctionInputBytes,
                    nmeaBytes = recorder.nmeaBytes,
                    sessionTotalBytes = writers?.totalBytesWritten ?: state.sessionTotalBytes,
                    ntripTransferred = bytesDisplay(recorder.correctionInputBytes),
                    rawRecordingActive = true,
                ).clearRecoverableUsbError()
                broadcastRoutineState()
                updateForegroundNotification()
            }.onFailure { error ->
                state = state.copy(
                    lastError = error.message,
                    errorCategory = RecordingErrorCategory.USB,
                    errorSeverity = RecordingErrorSeverity.DEGRADED,
                    rawRecordingActive = false,
                )
                recordRuntimeDiagnostic(
                    category = DiagnosticCategory.USB,
                    severity = RecordingErrorSeverity.DEGRADED.name,
                    message = { "USB capture read failed: ${error.message ?: error.javaClass.simpleName}" },
                )
                broadcastState()
                if (!tryReconnectUsb(recorder)) {
                    Thread.sleep(1_000)
                }
            }
        }
    }

    private fun tryReconnectUsb(recorder: SessionRawRecorder): Boolean {
        val vid = activeUsbVid ?: return false
        val pid = activeUsbPid ?: return false
        val plan = activeBaudPlan ?: return false
        val eventSink = activeEventSink ?: return false
        val fanout = advisoryFanout ?: return false
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.firstOrNull { candidate ->
            candidate.vendorId == vid && candidate.productId == pid
        } ?: return false
        if (!usbManager.hasPermission(device)) {
            state = state.copy(
                lastError = "USB receiver reconnected but permission is not granted.",
                errorCategory = RecordingErrorCategory.USB,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
                rawRecordingActive = false,
            )
            broadcastState()
            return false
        }
        return runCatching {
            synchronized(runtimeLock) {
                runCatching { transport?.close() }
                val usbTransport = AndroidUsbSerialTransport(
                    usbManager = usbManager,
                    device = device,
                    options = UsbSerialOpenOptions(activeProfileBaud),
                )
                val captureRuntime = CaptureRuntime(
                    transport = usbTransport,
                    recorder = recorder,
                    eventSink = eventSink,
                    advisoryFanout = fanout,
                )
                captureRuntime.open()
                executeBaudTransition(plan, captureRuntime, usbTransport)
                activeSerialBaud = activeTargetBaud
                transport = usbTransport
                runtime = captureRuntime
            }
            state = state.copy(
                lastError = null,
                errorCategory = RecordingErrorCategory.NONE,
                errorSeverity = RecordingErrorSeverity.NONE,
                rawRecordingActive = true,
            )
            broadcastState()
        }.isSuccess
    }

    private fun maybeStartNtrip(
        intent: Intent,
        recorder: SessionRawRecorder,
    ) {
        if (!intent.getBooleanExtra(EXTRA_NTRIP_ENABLED, false)) {
            state = state.copy(ntripState = "Disabled", correctionsActive = false)
            broadcastState()
            return
        }
        val config = ntripRuntimeConfig(intent)
        if (config == null) {
            return
        }
        startNtripController(
            config = config,
            recorder = recorder,
            sendCorrectionsToReceiver = intent.getBooleanExtra(EXTRA_NTRIP_SEND_TO_RECEIVER, true),
        )
    }

    private fun startNtripController(
        config: NtripRuntimeConfig,
        recorder: SessionRawRecorder,
        sendCorrectionsToReceiver: Boolean,
    ) {
        val ntripRtcmExtractor = Rtcm3Extractor(validateCrc = true)
        val controller = NtripRuntimeController(
            clientFactory = { runtimeConfig ->
                DefaultNtripRuntimeClient(
                    NtripClient(
                        request = runtimeConfig.request,
                        reconnectPolicy = NtripReconnectPolicy(maxAttempts = Int.MAX_VALUE, delayMillis = 5_000),
                    ),
                )
            },
            emit = ::handleNtripSnapshot,
            onRtcmBytes = { bytes ->
                if (running.get()) {
                    processNtripCorrectionBytes(bytes, recorder, ntripRtcmExtractor)
                    val txResult = if (sendCorrectionsToReceiver) {
                        runCatching {
                            synchronized(txLock) {
                                runtime?.sendToReceiver(bytes) ?: error("USB runtime is not available.")
                            }
                        }
                    } else {
                        Result.success(Unit)
                    }
                    ntripRateWindowCorrectionBytes += bytes.size
                    if (sendCorrectionsToReceiver && txResult.isSuccess) {
                        ntripRateWindowTxBytes += bytes.size
                    }
                    val updatedState = state.copy(
                        correctionInputBytes = recorder.correctionInputBytes,
                        nmeaBytes = recorder.nmeaBytes,
                        sessionTotalBytes = writers?.totalBytesWritten ?: state.sessionTotalBytes,
                        ntripTransferred = bytesDisplay(recorder.correctionInputBytes),
                        ntripRates = ntripRatesDisplay(),
                        txToReceiverBytes = recorder.txToReceiverBytes,
                        correctionsActive = txResult.isSuccess,
                        lastError = txResult.exceptionOrNull()?.message ?: state.lastError,
                        errorCategory = if (txResult.isFailure) RecordingErrorCategory.USB else state.errorCategory,
                        errorSeverity = if (txResult.isFailure) RecordingErrorSeverity.DEGRADED else state.errorSeverity,
                    )
                    state = if (txResult.isSuccess) {
                        updatedState.clearRecoverableUsbError()
                    } else {
                        updatedState
                    }
                    broadcastState()
                    updateForegroundNotification()
                }
            },
        )
        ntripController = controller
        controller.start(config)
    }

    private fun processNtripCorrectionBytes(
        bytes: ByteArray,
        recorder: SessionRawRecorder,
        rtcmExtractor: Rtcm3Extractor,
    ) {
        recorder.appendCorrectionInputBytes(bytes)
        rtklibWorker?.offerCorrectionBytes(
            bytes = bytes,
            timestampMillis = System.currentTimeMillis(),
            sessionOffsetBytes = recorder.correctionInputBytes,
        )
        val frames = rtcmExtractor.accept(bytes)
        if (frames.isNotEmpty()) {
            state = state.copy(correctionInputRtcmAtMillis = android.os.SystemClock.elapsedRealtime())
        }
        frames.forEach { frame ->
            runCatching {
                writers?.appendQualityLiveJson(
                    """{"type":"ntrip-rtcm3-frame","payloadLength":${frame.payloadLength},"messageType":${frame.messageType ?: "null"},"crcValid":${frame.crcValid ?: "null"}}""",
                )
            }
            val referenceStation = Rtcm3ReferenceStationParser.parse(frame)
            state = if (referenceStation != null) {
                state.withRtcmReferenceStation(referenceStation).copy(rtcmFrames = state.rtcmFrames + 1)
            } else {
                state.copy(rtcmFrames = state.rtcmFrames + 1)
            }
        }
    }

    private fun handleNtripSnapshot(snapshot: NtripRuntimeSnapshot) {
        val ntripProblem = snapshot.state == NtripRuntimeState.AUTH_ERROR || snapshot.state == NtripRuntimeState.NETWORK_ERROR
        state = state.copy(
            ntripState = snapshot.state.name,
            ntripTransferred = bytesDisplay(activeRecorder?.correctionInputBytes ?: state.correctionInputBytes),
            lastError = snapshot.message ?: state.lastError,
            errorCategory = if (ntripProblem) RecordingErrorCategory.NTRIP else state.errorCategory,
            errorSeverity = if (ntripProblem) RecordingErrorSeverity.DEGRADED else state.errorSeverity,
            correctionsActive = snapshot.correctionsActive,
        )
        if (ntripProblem) {
            recordRuntimeDiagnostic(
                category = DiagnosticCategory.NTRIP,
                severity = RecordingErrorSeverity.DEGRADED.name,
                message = { "NTRIP ${snapshot.state.name}: ${snapshot.message ?: "no detail"}" },
                attributes = { mapOf("url" to state.ntripUrl) },
            )
        }
        broadcastState()
    }

    private fun startRtklibWorkerIfEnabled(
        intent: Intent,
        sessionWriters: RecordingSessionWriters,
    ) {
        if (!intent.getBooleanExtra(EXTRA_RTKLIB_ENABLED, false)) {
            state = state.copy(rtklibState = "Disabled")
            return
        }

        val outputNmea = intent.getBooleanExtra(EXTRA_RTKLIB_OUTPUT_NMEA, false)
        val outputPos = intent.getBooleanExtra(EXTRA_RTKLIB_OUTPUT_POS, false)
        val routePlanText = intent.getStringExtra(EXTRA_RTKLIB_ROUTE_PLAN).orEmpty()
        val config = RtklibConfig(
            routePlan = rtklibRoutePlanFromText(routePlanText),
            preset = rtklibPresetFrom(intent.getStringExtra(EXTRA_RTKLIB_PRESET)),
            receiverProfileId = intent.getStringExtra(EXTRA_RECEIVER_PROFILE_ID).orEmpty(),
            baseContextSummary = ntripDisplayUrl(intent).takeUnless { it == "n/a" } ?: "NTRIP RTCM3",
            outputNmea = outputNmea,
            outputPos = outputPos,
            maxRoverQueueBytes = intent.getIntExtra(
                EXTRA_RTKLIB_MAX_ROVER_QUEUE_BYTES,
                RtklibConfig.DEFAULT_MAX_ROVER_QUEUE_BYTES,
            ),
            maxCorrectionQueueBytes = intent.getIntExtra(
                EXTRA_RTKLIB_MAX_CORRECTION_QUEUE_BYTES,
                RtklibConfig.DEFAULT_MAX_CORRECTION_QUEUE_BYTES,
            ),
        )
        val worker = RtklibWorker(
            backendFactory = RtklibNativeBridge(),
            outputWriters = RtklibOutputWriters.fromCallbacks(
                appendNmeaLine = if (outputNmea) sessionWriters::appendRtklibSolutionNmea else null,
                appendPosLine = if (outputPos) sessionWriters::appendRtklibSolutionPos else null,
            ),
        )
        val result = worker.start(config)
        if (result.started) {
            rtklibWorker = worker
            state = state.copy(
                rtklibState = "RUNNING",
                rtklibRoutePlan = routePlanText,
                rtklibSnapshotId = intent.getStringExtra(EXTRA_RTKLIB_SNAPSHOT_ID) ?: "n/a",
                rtklibLastError = null,
            )
        } else {
            worker.stop()
            state = state.copy(
                rtklibState = "FAILED",
                rtklibRoutePlan = routePlanText,
                rtklibSnapshotId = intent.getStringExtra(EXTRA_RTKLIB_SNAPSHOT_ID) ?: "n/a",
                rtklibLastError = result.message,
            )
        }
    }

    private fun rtklibPresetFrom(value: String?): RtklibPreset =
        runCatching { RtklibPreset.valueOf(value.orEmpty()) }
            .getOrDefault(RtklibPreset.ROVER_KINEMATIC_RTK)

    private fun rtklibRoutePlanFromText(text: String): RtklibInputRoutePlan {
        val roverRoute = when {
            text.contains("input_ubx") -> RtklibInputRoute(
                kind = RtklibInputRouteKind.DIRECT_RTKLIB_DECODER,
                format = RtklibRoverInputFormat.UBX_RXM_RAWX_SFRBX,
                decoderId = "input_ubx",
                reason = "App start validation selected u-blox RAWX/SFRBX direct RTKLIB route.",
            )
            text.contains("input_unicore") -> RtklibInputRoute(
                kind = RtklibInputRouteKind.DIRECT_RTKLIB_DECODER,
                format = RtklibRoverInputFormat.UNICORE_OBSVMB,
                decoderId = "input_unicore",
                reason = "App start validation selected Unicore OBSVMB direct RTKLIB route.",
            )
            text.contains("rtkcollector-obsvmcmp-shim") -> RtklibInputRoute(
                kind = RtklibInputRouteKind.CONVERTER,
                format = RtklibRoverInputFormat.UNICORE_OBSVMCMPB,
                decoderId = "input_unicore",
                converterId = "rtkcollector-obsvmcmp-shim",
                reason = "App start validation selected RtkCollector OBSVMCMPB decoder shim.",
            )
            else -> RtklibInputRoute(
                kind = RtklibInputRouteKind.UNSUPPORTED,
                reason = text.ifBlank { "RTKLIB rover input route is missing." },
            )
        }
        return RtklibInputRoutePlan(
            roverInput = roverRoute,
            correctionInput = RtklibCorrectionInputRoute(
                kind = RtklibInputRouteKind.DIRECT_RTKLIB_DECODER,
                format = CorrectionFormat.RTCM3,
                decoderId = "input_rtcm3",
                reason = "NTRIP RTCM3 correction stream.",
            ),
            solutionDirection = RtklibSolutionDirection.FORWARD_ONLY,
        )
    }

    private fun ntripRuntimeConfig(intent: Intent): NtripRuntimeConfig? {
        val host = intent.getStringExtra(EXTRA_NTRIP_HOST).orEmpty()
        val mountpoint = intent.getStringExtra(EXTRA_NTRIP_MOUNTPOINT).orEmpty()
        if (host.isBlank() || mountpoint.isBlank()) {
            state = state.copy(
                ntripState = "CONFIG_ERROR",
                correctionsActive = false,
                lastError = "NTRIP host and mountpoint are required before connecting.",
                errorCategory = RecordingErrorCategory.NTRIP,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
            broadcastState()
            return null
        }
        val request = NtripRequest(
            host = host,
            port = validatePort(intent.getIntExtra(EXTRA_NTRIP_PORT, 2101)),
            mountpoint = mountpoint,
            credentials = intent.getStringExtra(EXTRA_NTRIP_USERNAME)?.takeIf { it.isNotBlank() }?.let { username ->
                NtripCredentials(username = username, password = intent.getStringExtra(EXTRA_NTRIP_PASSWORD).orEmpty())
            },
        )
        return NtripRuntimeConfig(
            request = request,
            ggaLines = listOfNotNull(intent.getStringExtra(EXTRA_NTRIP_GGA)?.takeIf { it.isNotBlank() }),
        )
    }

    private fun casterUploadRuntimeConfig(intent: Intent): NtripCasterUploadRuntimeConfig? {
        if (!intent.getBooleanExtra(EXTRA_BASE_CASTER_UPLOAD_ENABLED, false)) {
            return null
        }
        val host = intent.getStringExtra(EXTRA_BASE_CASTER_UPLOAD_HOST).orEmpty()
        val mountpoint = intent.getStringExtra(EXTRA_BASE_CASTER_UPLOAD_MOUNTPOINT).orEmpty()
        if (host.isBlank() || mountpoint.isBlank()) {
            state = state.copy(
                lastError = "NTRIP caster upload host and mountpoint are required before connecting.",
                errorCategory = RecordingErrorCategory.NTRIP,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
            broadcastState()
            return null
        }
        val username = intent.getStringExtra(EXTRA_BASE_CASTER_UPLOAD_USERNAME).orEmpty()
        val password = intent.getStringExtra(EXTRA_BASE_CASTER_UPLOAD_PASSWORD).orEmpty()
        val credentials = if (username.isNotBlank() || password.isNotEmpty()) {
            NtripCredentials(username = username, password = password)
        } else {
            null
        }
        return NtripCasterUploadRuntimeConfig(
            request = NtripCasterUploadRequest(
                host = host,
                port = validatePort(intent.getIntExtra(EXTRA_BASE_CASTER_UPLOAD_PORT, 2101)),
                mountpoint = mountpoint,
                credentials = credentials,
                protocolVersion = uploadProtocolVersion(
                    intent.getStringExtra(EXTRA_BASE_CASTER_UPLOAD_PROTOCOL_POLICY),
                ),
            ),
        )
    }

    private fun updateNtrip(intent: Intent) {
        val policy = NtripUpdatePolicy.validateUpdate(
            activeRecordingRunning = running.get(),
            activeWorkflowUsesNtrip = activeWorkflowUsesNtrip,
        )
        if (!policy.allowed) {
            state = state.copy(
                lastError = policy.message,
                errorCategory = policy.category,
                errorSeverity = policy.severity,
            )
            broadcastState()
            return
        }
        val config = runCatching { ntripRuntimeConfig(intent) }
            .getOrElse { error ->
                state = state.copy(
                    lastError = "Cannot update NTRIP: ${error.message ?: error.javaClass.simpleName}",
                    errorCategory = RecordingErrorCategory.NTRIP,
                    errorSeverity = RecordingErrorSeverity.DEGRADED,
                )
                broadcastState()
                return
            }
        if (config == null) {
            state = state.copy(
                lastError = "Cannot update NTRIP: host and mountpoint are required.",
                errorCategory = RecordingErrorCategory.NTRIP,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
            broadcastState()
            return
        }
        val recorder = activeRecorder ?: return
        val updatedUrl = ntripDisplayUrl(config)
        val mountpointChanged = updatedUrl != state.ntripUrl
        if (mountpointChanged) {
            state = state.copy(
                ntripUrl = updatedUrl,
                ntripStationId = "",
                ntripBaseLatLon = "n/a",
                ntripBaseLatDeg = null,
                ntripBaseLonDeg = null,
                baseline = "n/a",
            )
            broadcastState()
        }
        runCatching {
            writers?.appendEventJson(
                """{"type":"ntrip-config-updated","host":"${config.request.host.jsonEscape()}","mountpoint":"${config.request.mountpoint.jsonEscape()}","usernamePresent":${config.request.credentials != null}}""",
            )
        }
        if (ntripController == null) {
            startNtripController(
                config = config,
                recorder = recorder,
                sendCorrectionsToReceiver = activeNtripSendToReceiver,
            )
        } else {
            ntripController?.update(config)
        }
    }

    private fun disableNtrip() {
        ntripController?.disable("User disabled NTRIP during recording.")
        runCatching { writers?.appendEventJson("""{"type":"ntrip-disabled","reason":"user"}""") }
        state = state.copy(ntripState = "DISABLED", correctionsActive = false)
        broadcastState()
    }

    private fun updateMockLocation(intent: Intent) {
        if (!running.get()) {
            return
        }
        mockLocationRequested = intent.getBooleanExtra(EXTRA_ENABLE_MOCK_LOCATION, false)
        mockLocationRateHz = intent.mockLocationRateHz()
        lastMockPublishedAt = null
        lastMockPublishedIdentity = null
        lastMockPublishWallClockAtMillis = null
        previousMockResult = null
        state = state.copy(
            mockLocationRateHz = mockLocationRateHz,
            mockLocationLastIntervalMs = null,
            mockLocationSolutionAgeMs = null,
            mockLocationState = if (mockLocationRequested) "Enabled" else "Disabled",
        )
        configureMockLocation(mockLocationRequested)
        startBestSolutionTicker()
        broadcastState()
    }

    private fun writePersistentReceiverConfig(intent: Intent) {
        if (!running.get()) {
            state = state.copy(
                lastError = "Cannot write receiver configuration: recording service is not connected.",
                errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
            recordRuntimeDiagnostic(
                category = DiagnosticCategory.RECEIVER_COMMAND,
                severity = RecordingErrorSeverity.DEGRADED.name,
                message = { state.lastError ?: "Cannot write receiver configuration." },
            )
            broadcastState()
            return
        }
        val captureRuntime = runtime
        val usbTransport = transport
        if (captureRuntime == null || usbTransport == null) {
            state = state.copy(
                lastError = "Cannot write receiver configuration: active receiver connection is unavailable.",
                errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
            recordRuntimeDiagnostic(
                category = DiagnosticCategory.RECEIVER_COMMAND,
                severity = RecordingErrorSeverity.DEGRADED.name,
                message = { state.lastError ?: "Cannot write receiver configuration." },
            )
            broadcastState()
            return
        }
        val commands = intent.getStringArrayListExtra(EXTRA_PERSISTENT_COMMANDS)
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
        if (commands.isEmpty()) {
            state = state.copy(
                lastError = "Cannot write receiver configuration: no commands were provided.",
                errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
            recordRuntimeDiagnostic(
                category = DiagnosticCategory.RECEIVER_COMMAND,
                severity = RecordingErrorSeverity.DEGRADED.name,
                message = { state.lastError ?: "Cannot write receiver configuration." },
            )
            broadcastState()
            return
        }
        val label = intent.getStringExtra(EXTRA_PERSISTENT_WRITE_LABEL)?.takeIf(String::isNotBlank)
            ?: "persistent receiver configuration"
        val targetBaud = if (intent.hasExtra(EXTRA_PERSISTENT_TARGET_BAUD)) {
            validateBaud(intent.getIntExtra(EXTRA_PERSISTENT_TARGET_BAUD, activeSerialBaud), "persistent target baud")
        } else {
            null
        }
        if (!persistentWriteInProgress.compareAndSet(false, true)) {
            state = state.copy(
                lastError = "Persistent receiver configuration write is already in progress.",
                errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
            recordRuntimeDiagnostic(
                category = DiagnosticCategory.RECEIVER_COMMAND,
                severity = RecordingErrorSeverity.DEGRADED.name,
                message = { state.lastError ?: "Persistent receiver configuration write is already in progress." },
            )
            broadcastState()
            return
        }
        Thread({
            try {
                writePersistentReceiverConfigAsync(captureRuntime, usbTransport, commands, label, targetBaud)
            } finally {
                persistentWriteInProgress.set(false)
            }
        }, "rtkcollector-persistent-receiver-write").start()
    }

    private fun writePersistentReceiverConfigAsync(
        captureRuntime: CaptureRuntime,
        usbTransport: AndroidUsbSerialTransport,
        commands: List<String>,
        label: String,
        targetBaud: Int?,
    ) {
        runCatching {
            writers?.appendEventJson(
                """{"type":"persistent-receiver-write-started","label":"${label.jsonEscape()}","commandCount":${commands.size}}""",
            )
            synchronized(runtimeLock) {
                if (targetBaud == null) {
                    sendPersistentCommandLines(captureRuntime, commands)
                } else {
                    val plan = Um980PersistentBaudPlan.build(
                        currentHostBaud = activeSerialBaud,
                        targetBaud = targetBaud,
                    )
                    executePersistentBaudPlan(plan, captureRuntime, usbTransport)
                    activeSerialBaud = targetBaud
                    activeTargetBaud = targetBaud
                    activeProfileBaud = targetBaud
                }
            }
            writers?.appendEventJson(
                """{"type":"persistent-receiver-write-succeeded","label":"${label.jsonEscape()}"}""",
            )
            state = state.copy(
                lastError = null,
                errorCategory = RecordingErrorCategory.NONE,
                errorSeverity = RecordingErrorSeverity.NONE,
            )
            broadcastState()
        }.onFailure { error ->
            val message = error.message ?: error.javaClass.simpleName
            runCatching {
                writers?.appendEventJson(
                    """{"type":"persistent-receiver-write-failed","label":"${label.jsonEscape()}","message":"${message.jsonEscape()}"}""",
                )
            }
            state = state.copy(
                lastError = "Persistent receiver configuration failed: $message",
                errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
            recordRuntimeDiagnostic(
                category = DiagnosticCategory.RECEIVER_COMMAND,
                severity = RecordingErrorSeverity.DEGRADED.name,
                message = { state.lastError ?: "Persistent receiver configuration failed." },
                attributes = { mapOf("label" to label) },
            )
            broadcastState()
        }
    }

    private fun executePersistentBaudPlan(
        plan: Um980PersistentBaudPlan,
        captureRuntime: CaptureRuntime,
        usbTransport: AndroidUsbSerialTransport,
    ) {
        plan.steps.forEach { step ->
            when (step) {
                is Um980PersistentBaudStep.SendCommands -> sendCommandLines(captureRuntime, step.commands)
                Um980PersistentBaudStep.PauseAfterDeviceBaudCommands -> Thread.sleep(500)
                is Um980PersistentBaudStep.ReconfigureHostBaud -> {
                    usbTransport.reconfigureBaud(step.baud)
                    activeSerialBaud = step.baud
                }
                Um980PersistentBaudStep.VerifyReceiverAtTargetBaud -> verifyActiveReceiverConnection(captureRuntime)
                Um980PersistentBaudStep.ExpectSaveConfigOk -> waitForActiveCommandOk(captureRuntime, "SAVECONFIG")
            }
        }
    }

    private fun sendPersistentCommandLines(captureRuntime: CaptureRuntime, commands: List<String>) {
        commands.forEach { command ->
            sendCommandLines(captureRuntime, listOf(command))
            if (command.trim().equals("SAVECONFIG", ignoreCase = true)) {
                waitForActiveCommandOk(captureRuntime, "SAVECONFIG")
            }
        }
    }

    private fun verifyActiveReceiverConnection(captureRuntime: CaptureRuntime) {
        val initialBytes = collectActiveReceiverBytes(captureRuntime, 300)
        if (isPlausibleUm980MaintenanceResponse(initialBytes)) return
        captureRuntime.sendToReceiver(um980VersionProbeBytes())
        val response = collectActiveReceiverBytes(captureRuntime, 1_200)
        require(isPlausibleUm980MaintenanceResponse(response)) {
            "Receiver did not respond after host baud reconfiguration."
        }
    }

    private fun waitForActiveCommandOk(captureRuntime: CaptureRuntime, commandLabel: String) {
        val response = collectActiveCommandOkBytes(captureRuntime, SAVE_CONFIG_OK_TIMEOUT_MILLIS)
        require(isUm980CommandOkResponse(response)) {
            "$commandLabel was not acknowledged by receiver."
        }
    }

    private fun collectActiveCommandOkBytes(captureRuntime: CaptureRuntime, timeoutMillis: Long): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMillis
        val response = ByteArrayOutputStream()
        while (System.currentTimeMillis() < deadline) {
            val bytes = captureRuntime.readReceiverBytesOnce(READ_BUFFER_BYTES)
            if (bytes.isNotEmpty()) {
                response.write(bytes)
                val collected = response.toByteArray()
                if (isUm980CommandOkResponse(collected)) {
                    return collected
                }
            } else {
                Thread.sleep(50)
            }
        }
        return response.toByteArray()
    }

    private fun collectActiveReceiverBytes(captureRuntime: CaptureRuntime, timeoutMillis: Long): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMillis
        val response = ByteArrayOutputStream()
        while (System.currentTimeMillis() < deadline) {
            val bytes = captureRuntime.readReceiverBytesOnce(READ_BUFFER_BYTES)
            if (bytes.isNotEmpty()) {
                response.write(bytes)
                val collected = response.toByteArray()
                if (isPlausibleUm980MaintenanceResponse(collected) || isUm980CommandOkResponse(collected)) {
                    return collected
                }
            } else {
                Thread.sleep(50)
            }
        }
        return response.toByteArray()
    }

    private fun stopRecording(sendShutdown: Boolean) {
        if (!stopping.compareAndSet(false, true)) {
            broadcastState()
            return
        }
        if (!running.getAndSet(false) && runtime == null && writers == null && transport == null) {
            stopping.set(false)
            return
        }
        routineStateBroadcastRateLimiter.reset()
        state = state.copy(lifecycle = RecordingLifecycleState.STOPPING, running = false)
        broadcastState()
        if (sendShutdown && shutdownSent.compareAndSet(false, true)) {
            runCatching {
                runtime?.let { captureRuntime ->
                    synchronized(txLock) {
                        sendCommandLines(captureRuntime, shutdownCommands, CompilePhase.STOP)
                    }
                }
            }.onFailure { error ->
                state = state.copy(
                    lastError = error.message,
                    errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
                    errorSeverity = RecordingErrorSeverity.DEGRADED,
                )
                broadcastState()
            }
        }
        bestSolutionTicker?.cancel(false)
        bestSolutionTicker = null
        runCatching { ntripController?.stop() }
        runCatching { captureThread?.join(1500) }
        runCatching { advisoryFanout?.close() }
        runCatching { rtklibWorker?.stop() }
        val casterUploadSnapshot = casterUploadController?.snapshot()
        runCatching { casterUploadController?.stop() }
        casterUploadSnapshot?.let { snapshot ->
            runCatching {
                writers?.appendEventJson(
                    """{"type":"base-caster-upload-final","state":"${snapshot.state.jsonEscape()}","uploadedBytes":${snapshot.bytesUploaded},"droppedBytes":${snapshot.bytesDropped},"url":"${snapshot.mountpointUrl.jsonEscape()}","lastError":${snapshot.lastError.jsonStringOrNull()}}""",
                )
            }
        }
        synchronized(runtimeLock) {
            runCatching {
                activeSessionMetadata
                    ?.copy(
                        stoppedAt = Instant.now().toString(),
                        baseCasterUploadFinalStatus = casterUploadSnapshot?.state,
                    )
                    ?.let { metadata ->
                        writers?.writeSessionJson(exportSessionMetadata(metadata))
                        activeSessionMetadata = metadata
                    }
            }
            runCatching { runtime?.close() }
            val closeReport = runCatching { writers?.closeAll() }
                .getOrElse { error ->
                    SessionWriterCloseReport(
                        issues = listOf(
                            SessionWriterIssue(
                                artifact = "session-writers",
                                category = SessionWriterIssueCategory.RAW_RX,
                                severity = SessionWriterIssueSeverity.FATAL,
                                message = error.message ?: "Writer close failed.",
                            ),
                        ),
                    )
                }
            if (closeReport != null && closeReport.issues.isNotEmpty()) {
                state = state.copy(
                    lastError = closeReport.userMessage,
                    errorCategory = RecordingErrorCategory.STORAGE,
                    errorSeverity = if (closeReport.hasFatalIssue) {
                        RecordingErrorSeverity.FATAL
                    } else {
                        RecordingErrorSeverity.DEGRADED
                    },
                    rawRecordingActive = false,
                )
                recordRuntimeDiagnostic(
                    category = DiagnosticCategory.STORAGE,
                    severity = state.errorSeverity.name,
                    message = { closeReport.userMessage ?: "Session writer close failed." },
                )
            }
        }
        runtime = null
        transport = null
        writers = null
        advisoryFanout = null
        activeSessionMetadata = null
        activeRecorder = null
        activeEventSink = null
        activeBaudPlan = null
        activeUsbVid = null
        activeUsbPid = null
        activeSerialBaud = 230400
        activeTargetBaud = 230400
        ntripController = null
        casterUploadController = null
        rtklibWorker = null
        activeWorkflowUsesNtrip = false
        shutdownCommands = emptyList()
        teardownMockLocation()
        solutionCandidates.clear()
        lastMockPublishedAt = null
        lastMockPublishedIdentity = null
        lastMockPublishWallClockAtMillis = null
        previousMockResult = null
        mockLocationRequested = false
        mockLocationRateHz = RecordingPolicyProfile.DEFAULT_MOCK_LOCATION_RATE_HZ
        releaseWakeLock()
        state = state.copy(
            running = false,
            lifecycle = if (state.errorSeverity == RecordingErrorSeverity.FATAL) {
                RecordingLifecycleState.FAILED
            } else {
                RecordingLifecycleState.STOPPED
            },
            rawRecordingActive = false,
            correctionsActive = false,
            baseCasterUploadState = "Disabled",
            baseCasterUploadUrl = "n/a",
            baseCasterUploadBytes = 0,
            baseCasterUploadDroppedBytes = 0,
            baseCasterUploadLastError = null,
            rtklibState = "Disabled",
            rtklibRoutePlan = "n/a",
            rtklibSnapshotId = "n/a",
            rtklibLastError = null,
            rtklibFixClass = "n/a",
            rtklibSolutionAgeMs = null,
            rtklibRoverQueueBytes = 0,
            rtklibCorrectionQueueBytes = 0,
            rtklibDroppedRoverBytes = 0,
            rtklibDroppedCorrectionBytes = 0,
            rtklibDecodedRoverEpochs = 0,
            rtklibDecodedCorrectionMessages = 0,
            rtklibOutputNmeaLines = 0,
            rtklibOutputPosLines = 0,
            ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/GGA -/-/-/-/-/- Hz",
            mockLocationRateHz = mockLocationRateHz,
        ).clearBestSolutionFields(
            mockLocationState = "Disabled",
            ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/GGA -/-/-/-/-/- Hz",
        )
        stopping.set(false)
        broadcastState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendCommandLines(
        captureRuntime: CaptureRuntime,
        commands: List<String>,
        phase: CompilePhase = CompilePhase.START,
    ) {
        if (commands.isEmpty()) return
        val script = commands.joinToString("\n")
        val compiled = compileReceiverCommands(phase, activeReceiverFamily, script)
        compiled.forEach { command ->
            synchronized(txLock) {
                captureRuntime.sendToReceiver(command.payload)
                Thread.sleep(100)
            }
        }
    }

    private fun executeBaudTransition(
        plan: Um980BaudTransitionPlan,
        captureRuntime: CaptureRuntime,
        usbTransport: AndroidUsbSerialTransport,
    ) {
        plan.steps.forEach { step ->
            when (step) {
                is Um980BaudStep.OpenHostAtProfileBaud -> Unit
                is Um980BaudStep.SendCommands -> sendCommandLines(captureRuntime, step.commands)
                Um980BaudStep.PauseAfterDeviceBaudCommand -> Thread.sleep(500)
                is Um980BaudStep.ReconfigureHostBaud -> usbTransport.reconfigureBaud(step.baud)
                Um980BaudStep.DrainTransitionalRx -> drainAfterProfile(captureRuntime)
            }
        }
    }

    private fun List<String>.validatedCommands(): List<String> {
        val isUblox = activeReceiverFamily.startsWith("ublox", ignoreCase = true)
        return asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .onEach { command ->
                if (!isUblox) {
                    Um980RuntimeCommandValidator.validateRuntimeCommand(command)
                }
            }
            .toList()
    }

    private fun validateBaud(value: Int, label: String): Int {
        require(value in 9600..921600) { "$label must be between 9600 and 921600." }
        return value
    }

    private fun validatePort(value: Int): Int {
        require(value in 1..65535) { "NTRIP port must be between 1 and 65535." }
        return value
    }

    private fun uploadProtocolVersion(policy: String?): NtripProtocolVersion =
        when (policy) {
            "NTRIP_V1_ONLY" -> NtripProtocolVersion.NTRIP_V1
            else -> NtripProtocolVersion.NTRIP_V2
        }

    private fun ntripDisplayUrl(intent: Intent): String {
        val host = intent.getStringExtra(EXTRA_NTRIP_HOST).orEmpty()
        val mountpoint = intent.getStringExtra(EXTRA_NTRIP_MOUNTPOINT).orEmpty()
        if (host.isBlank() || mountpoint.isBlank()) return "n/a"
        val port = intent.getIntExtra(EXTRA_NTRIP_PORT, 2101)
        return "$host:$port/$mountpoint"
    }

    private fun ntripDisplayUrl(config: NtripRuntimeConfig): String =
        "${config.request.host}:${config.request.port}/${config.request.mountpoint}"

    private fun latLonDisplay(latDeg: Double?, lonDeg: Double?): String =
        if (latDeg == null || lonDeg == null) "n/a" else "%.9f, %.9f".format(java.util.Locale.US, latDeg, lonDeg)

    private fun metersDisplay(value: Double?): String =
        if (value == null) "n/a" else "%.3f m".format(java.util.Locale.US, value)

    private fun distanceDisplay(valueMeters: Double?): String =
        if (valueMeters == null) {
            "n/a"
        } else if (valueMeters >= 1000.0) {
            "%.3f km".format(java.util.Locale.US, valueMeters / 1000.0)
        } else {
            "%.3f m".format(java.util.Locale.US, valueMeters)
        }

    private fun satelliteDisplay(used: Int?, inView: Int?): String =
        when {
            used == null && inView == null -> "n/a"
            inView == null -> used.toString()
            used == null -> "n/a / $inView"
            else -> "$used / $inView"
        }

    private fun dopPairDisplay(hdop: Double?, vdop: Double?): String =
        when {
            hdop == null && vdop == null -> "n/a"
            vdop == null -> "%.1f / n/a".format(java.util.Locale.US, hdop)
            hdop == null -> "n/a / %.1f".format(java.util.Locale.US, vdop)
            else -> "%.1f / %.1f".format(java.util.Locale.US, hdop, vdop)
        }

    private fun bytesDisplay(bytes: Long): String =
        when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> "%.1f kB".format(java.util.Locale.US, bytes / 1000.0)
            bytes < 1_000_000_000 -> "%.1f MB".format(java.util.Locale.US, bytes / 1_000_000.0)
            else -> "%.1f GB".format(java.util.Locale.US, bytes / 1_000_000_000.0)
        }

    private fun ntripRatesDisplay(): String {
        val elapsedSeconds = ((System.currentTimeMillis() - ntripRateWindowStartedAtMillis).coerceAtLeast(1L)) / 1000.0
        return "${rateDisplay(ntripRateWindowCorrectionBytes / elapsedSeconds)} from NTRIP / " +
            "${rateDisplay(ntripRateWindowTxBytes / elapsedSeconds)} to rover"
    }

    private fun rateDisplay(bytesPerSecond: Double): String =
        when {
            bytesPerSecond < 1000.0 -> "%.0f B/s".format(java.util.Locale.US, bytesPerSecond)
            bytesPerSecond < 1_000_000.0 -> "%.1f kB/s".format(java.util.Locale.US, bytesPerSecond / 1000.0)
            else -> "%.1f MB/s".format(java.util.Locale.US, bytesPerSecond / 1_000_000.0)
        }

    private fun baselineDisplay(roverLatDeg: Double?, roverLonDeg: Double?, baseLatDeg: Double?, baseLonDeg: Double?): String? {
        if (roverLatDeg == null || roverLonDeg == null || baseLatDeg == null || baseLonDeg == null) {
            return null
        }
        return distanceDisplay(haversineMeters(roverLatDeg, roverLonDeg, baseLatDeg, baseLonDeg))
    }

    private fun haversineMeters(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Double {
        val radiusM = 6_371_000.0
        val lat1 = Math.toRadians(lat1Deg)
        val lat2 = Math.toRadians(lat2Deg)
        val dLat = Math.toRadians(lat2Deg - lat1Deg)
        val dLon = Math.toRadians(lon2Deg - lon1Deg)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        return radiusM * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun classifyStartError(error: Throwable): RecordingErrorCategory {
        val message = error.message.orEmpty()
        return when {
            message.contains("USB", ignoreCase = true) -> RecordingErrorCategory.USB
            message.contains("SAF", ignoreCase = true) -> RecordingErrorCategory.STORAGE
            message.contains("storage", ignoreCase = true) -> RecordingErrorCategory.STORAGE
            message.contains("NTRIP", ignoreCase = true) -> RecordingErrorCategory.NTRIP
            message.contains("command", ignoreCase = true) -> RecordingErrorCategory.RECEIVER_COMMAND
            else -> RecordingErrorCategory.SERVICE_LIFECYCLE
        }
    }

    private fun drainAfterProfile(captureRuntime: CaptureRuntime) {
        val deadline = System.currentTimeMillis() + PROFILE_DRAIN_MILLIS
        while (System.currentTimeMillis() < deadline) {
            captureRuntime.readOnce(READ_BUFFER_BYTES)
        }
    }

    private fun openSessionWriters(intent: Intent): OpenedRecordingSession {
        val sessionName = createSessionName()
        return if (intent.getStringExtra(EXTRA_STORAGE_KIND) == "SAF_TREE") {
            val treeUriText = intent.getStringExtra(EXTRA_STORAGE_TREE_URI)
                ?.takeIf { it.isNotBlank() }
                ?: error("SAF storage profile selected but no tree URI was supplied.")
            require(hasPersistedSafWritePermission(treeUriText)) {
                "SAF storage profile selected but Android has no persisted write permission for the tree."
            }
            val safWriters = SafRecordingSessionWriters.open(
                resolver = contentResolver,
                treeUri = Uri.parse(treeUriText),
                sessionName = sessionName,
            )
            OpenedRecordingSession(
                displayPath = safWriters.sessionUri.toString(),
                writers = safWriters,
            )
        } else {
            val directory = createSessionDirectory(sessionName)
            OpenedRecordingSession(
                displayPath = directory.toString(),
                writers = PathRecordingSessionWriters.open(directory),
            )
        }
    }

    private fun createSessionName(): String =
        "session-${Instant.now().toString().replace(':', '-')}-${UUID.randomUUID()}"

    private fun createSessionDirectory(sessionName: String): Path {
        val root = getExternalFilesDir("sessions") ?: filesDir.resolve("sessions")
        val directory = root.resolve(sessionName)
        return directory.toPath()
    }

    private fun isStorageWritableForStart(intent: Intent): Boolean =
        if (intent.getStringExtra(EXTRA_STORAGE_KIND) == "SAF_TREE") {
            val treeUriText = intent.getStringExtra(EXTRA_STORAGE_TREE_URI)?.takeIf { it.isNotBlank() }
            treeUriText != null && hasPersistedSafWritePermission(treeUriText)
        } else {
            runCatching {
                val root = getExternalFilesDir("sessions") ?: filesDir.resolve("sessions")
                root.exists() || root.mkdirs()
            }.getOrDefault(false)
        }

    private fun hasPersistedSafWritePermission(treeUri: String): Boolean =
        contentResolver.persistedUriPermissions.any { permission ->
            permission.uri.toString() == treeUri && permission.isWritePermission
        }

    private fun ntripSessionMetadata(intent: Intent): NtripSessionMetadata? {
        if (!intent.getBooleanExtra(EXTRA_NTRIP_ENABLED, false)) {
            return null
        }
        val host = intent.getStringExtra(EXTRA_NTRIP_HOST).orEmpty()
        val mountpoint = intent.getStringExtra(EXTRA_NTRIP_MOUNTPOINT).orEmpty()
        if (host.isBlank() || mountpoint.isBlank()) {
            return null
        }
        return NtripSessionMetadata(
            casterHost = host,
            casterPort = validatePort(intent.getIntExtra(EXTRA_NTRIP_PORT, 2101)),
            mountpoint = mountpoint,
            usernamePresent = !intent.getStringExtra(EXTRA_NTRIP_USERNAME).isNullOrBlank(),
            ggaUploadEnabled = !intent.getStringExtra(EXTRA_NTRIP_GGA).isNullOrBlank(),
            secretRef = intent.getStringExtra(EXTRA_NTRIP_SECRET_REF)?.takeIf { it.isNotBlank() },
            protocol = "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY",
            finalStatus = null,
        )
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RtkCollector:Recording").also {
            it.setReferenceCounted(false)
            it.acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "RtkCollector recording", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RtkCollector")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateForegroundNotification(force: Boolean = false) {
        if (!running.get() && !force) return
        val text = recordingNotificationText(state)
        val now = System.currentTimeMillis()
        val becameActive = lastNotificationText == "Starting recording" && text.startsWith("Recording in progress")
        if (!force && text == lastNotificationText) {
            return
        }
        if (!force && !becameActive && now - lastNotificationUpdateAtMillis < NOTIFICATION_UPDATE_INTERVAL_MILLIS) {
            return
        }
        lastNotificationText = text
        lastNotificationUpdateAtMillis = now
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification(text))
    }

    private fun recordRuntimeDiagnostic(
        category: DiagnosticCategory,
        severity: String,
        message: () -> String,
        attributes: () -> Map<String, String> = { emptyMap() },
    ) {
        if (!diagnosticsSettings.runtimeLoggingEnabled) return
        if (!runtimeDiagnostics.isEnabled) return
        runtimeDiagnostics.record(
            RuntimeDiagnosticRecord(
                timestampMillis = System.currentTimeMillis(),
                category = category,
                severity = severity,
                message = message(),
                attributes = attributes(),
            ),
        )
    }

    private fun recordPerformanceDiagnosticIfDue() {
        if (!diagnosticsSettings.performanceMonitoringEnabled) return
        if (!performanceDiagnostics.isEnabled) return
        val now = System.currentTimeMillis()
        performanceDiagnostics.recordIfDue(now) {
            val runtime = Runtime.getRuntime()
            PerformanceDiagnosticSample(
                timestampMillis = now,
                receiverRxBytes = state.receiverRxBytes,
                correctionInputBytes = state.correctionInputBytes,
                txToReceiverBytes = state.txToReceiverBytes,
                sessionTotalBytes = state.sessionTotalBytes,
                heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
                heapMaxBytes = runtime.maxMemory(),
                threadCount = Thread.activeCount(),
                mockLastIntervalMs = state.mockLocationLastIntervalMs,
            )
        }
    }

    private fun RecordingErrorCategory.toDiagnosticCategory(): DiagnosticCategory =
        when (this) {
            RecordingErrorCategory.NONE -> DiagnosticCategory.APP
            RecordingErrorCategory.USB -> DiagnosticCategory.USB
            RecordingErrorCategory.STORAGE -> DiagnosticCategory.STORAGE
            RecordingErrorCategory.NTRIP -> DiagnosticCategory.NTRIP
            RecordingErrorCategory.RECEIVER_COMMAND -> DiagnosticCategory.RECEIVER_COMMAND
            RecordingErrorCategory.PARSER_EXPORT -> DiagnosticCategory.SERVICE
            RecordingErrorCategory.SERVICE_LIFECYCLE -> DiagnosticCategory.SERVICE
        }

    private fun broadcastState() {
        casterUploadController?.snapshot()?.let { snapshot ->
            state = state.withCasterUploadSnapshot(snapshot)
        }
        rtklibWorker?.snapshot()?.let { snapshot ->
            state = state.copy(
                rtklibState = snapshot.state.name,
                rtklibLastError = snapshot.lastError ?: state.rtklibLastError,
                rtklibFixClass = snapshot.latestSolution?.fixClass?.name ?: state.rtklibFixClass,
                rtklibSolutionAgeMs = snapshot.latestSolution?.timestampMillis?.let { timestamp ->
                    (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
                } ?: state.rtklibSolutionAgeMs,
                rtklibRoverQueueBytes = snapshot.roverQueueBytes,
                rtklibCorrectionQueueBytes = snapshot.correctionQueueBytes,
                rtklibDroppedRoverBytes = snapshot.droppedRoverBytes,
                rtklibDroppedCorrectionBytes = snapshot.droppedCorrectionBytes,
                rtklibDecodedRoverEpochs = snapshot.decodedRoverEpochs,
                rtklibDecodedCorrectionMessages = snapshot.decodedCorrectionMessages,
                rtklibOutputNmeaLines = snapshot.outputNmeaLines,
                rtklibOutputPosLines = snapshot.outputPosLines,
            )
        }
        sendBroadcast(
            Intent(ACTION_STATE).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATE_RUNNING, state.running)
                putExtra(EXTRA_STATE_WORKFLOW_LABEL, state.workflowLabel)
                putExtra(EXTRA_STATE_RECEIVER_LABEL, state.receiverLabel)
                putExtra(EXTRA_STATE_RECEIVER_FAMILY, activeReceiverFamily)
                putExtra(EXTRA_STATE_STORAGE_LABEL, state.storageLabel)
                putExtra(EXTRA_STATE_SESSION_PATH, state.sessionPath)
                putExtra(EXTRA_STATE_RX_BYTES, state.receiverRxBytes)
                putExtra(EXTRA_STATE_TX_BYTES, state.txToReceiverBytes)
                putExtra(EXTRA_STATE_CORRECTION_BYTES, state.correctionInputBytes)
                putExtra(EXTRA_STATE_NMEA_BYTES, state.nmeaBytes)
                putExtra(EXTRA_STATE_SESSION_TOTAL_BYTES, state.sessionTotalBytes)
                putExtra(EXTRA_STATE_SETTINGS_SET_LABEL, state.settingsSetLabel)
                putExtra(EXTRA_STATE_SETTINGS_COMMAND_PROFILE_LABEL, state.settingsCommandProfileLabel)
                putExtra(EXTRA_STATE_SETTINGS_BAUD_PROFILE_LABEL, state.settingsBaudProfileLabel)
                putExtra(EXTRA_STATE_SETTINGS_NTRIP_CASTER_PROFILE_LABEL, state.settingsNtripCasterProfileLabel)
                putExtra(EXTRA_STATE_SETTINGS_RECORDING_OUTPUT_PROFILE_LABEL, state.settingsRecordingOutputProfileLabel)
                putExtra(EXTRA_STATE_SETTINGS_STORAGE_PROFILE_LABEL, state.settingsStorageProfileLabel)
                putExtra(EXTRA_STATE_NTRIP, state.ntripState)
                putExtra(EXTRA_STATE_NTRIP_URL, state.ntripUrl)
                putExtra(EXTRA_STATE_NTRIP_TRANSFERRED, state.ntripTransferred)
                putExtra(EXTRA_STATE_NTRIP_RATES, state.ntripRates)
                putExtra(EXTRA_STATE_NTRIP_STATION_ID, state.ntripStationId)
                putExtra(EXTRA_STATE_NTRIP_BASE_LAT_LON, state.ntripBaseLatLon)
                putExtra(EXTRA_STATE_BASE_CASTER_UPLOAD_STATE, state.baseCasterUploadState)
                putExtra(EXTRA_STATE_BASE_CASTER_UPLOAD_URL, state.baseCasterUploadUrl)
                putExtra(EXTRA_STATE_BASE_CASTER_UPLOAD_BYTES, state.baseCasterUploadBytes)
                putExtra(EXTRA_STATE_BASE_CASTER_UPLOAD_DROPPED_BYTES, state.baseCasterUploadDroppedBytes)
                putExtra(EXTRA_STATE_BASE_CASTER_UPLOAD_LAST_ERROR, state.baseCasterUploadLastError)
                putExtra(EXTRA_STATE_RTKLIB_STATE, state.rtklibState)
                putExtra(EXTRA_STATE_RTKLIB_ROUTE_PLAN, state.rtklibRoutePlan)
                putExtra(EXTRA_STATE_RTKLIB_SNAPSHOT_ID, state.rtklibSnapshotId)
                putExtra(EXTRA_STATE_RTKLIB_LAST_ERROR, state.rtklibLastError)
                putExtra(EXTRA_STATE_RTKLIB_FIX_CLASS, state.rtklibFixClass)
                putExtra(EXTRA_STATE_RTKLIB_SOLUTION_AGE_MS, state.rtklibSolutionAgeMs ?: -1L)
                putExtra(EXTRA_STATE_RTKLIB_ROVER_QUEUE_BYTES, state.rtklibRoverQueueBytes)
                putExtra(EXTRA_STATE_RTKLIB_CORRECTION_QUEUE_BYTES, state.rtklibCorrectionQueueBytes)
                putExtra(EXTRA_STATE_RTKLIB_DROPPED_ROVER_BYTES, state.rtklibDroppedRoverBytes)
                putExtra(EXTRA_STATE_RTKLIB_DROPPED_CORRECTION_BYTES, state.rtklibDroppedCorrectionBytes)
                putExtra(EXTRA_STATE_RTKLIB_DECODED_ROVER_EPOCHS, state.rtklibDecodedRoverEpochs)
                putExtra(EXTRA_STATE_RTKLIB_DECODED_CORRECTION_MESSAGES, state.rtklibDecodedCorrectionMessages)
                putExtra(EXTRA_STATE_RTKLIB_OUTPUT_NMEA_LINES, state.rtklibOutputNmeaLines)
                putExtra(EXTRA_STATE_RTKLIB_OUTPUT_POS_LINES, state.rtklibOutputPosLines)
                putExtra(EXTRA_STATE_GGA_FIX_QUALITY, state.ggaFixQuality ?: -1)
                putExtra(EXTRA_STATE_BESTNAV_POSITION_TYPE, state.bestnavPositionType)
                putExtra(EXTRA_STATE_PPP_STATUS, state.pppStatus)
                putExtra(EXTRA_STATE_RECEIVER_RTK_STATUS, state.receiverRtkStatus)
                putExtra(EXTRA_STATE_RTK_POSITION_TYPE, state.rtkPositionType)
                putExtra(EXTRA_STATE_RTK_CALCULATE_STATUS, state.rtkCalculateStatus ?: -1)
                putExtra(EXTRA_STATE_RTK_CALCULATE_STATUS_DESCRIPTION, state.rtkCalculateStatusDescription)
                putExtra(EXTRA_STATE_RTCM_LAST_MESSAGE_ID, state.rtcmLastMessageId ?: -1)
                putExtra(EXTRA_STATE_RTCM_LAST_BASE_ID, state.rtcmLastBaseId ?: -1)
                putExtra(EXTRA_STATE_LAT_LON, state.latLon)
                putExtra(EXTRA_STATE_ELLIPSOIDAL_HEIGHT, state.ellipsoidalHeight)
                putExtra(EXTRA_STATE_ALTITUDE, state.altitude)
                putExtra(EXTRA_STATE_UTC_TIME, state.utcTime)
                putExtra(EXTRA_STATE_SATELLITES, state.satellites)
                putExtra(EXTRA_STATE_PDOP, state.pdop)
                putExtra(EXTRA_STATE_HDOP_VDOP, state.hdopVdop)
                putExtra(EXTRA_STATE_HORIZONTAL_ACCURACY, state.horizontalAccuracy)
                putExtra(EXTRA_STATE_VERTICAL_ACCURACY, state.verticalAccuracy)
                putExtra(EXTRA_STATE_LAT_ERROR, state.latError)
                putExtra(EXTRA_STATE_LON_ERROR, state.lonError)
                putExtra(EXTRA_STATE_DIFFERENTIAL_AGE, state.differentialAge)
                putExtra(EXTRA_STATE_BASELINE, state.baseline)
                putExtra(EXTRA_STATE_RTCM_FRAMES, state.rtcmFrames)
                putExtra(EXTRA_STATE_ERROR, state.lastError)
                putExtra(EXTRA_STATE_LIFECYCLE, state.lifecycle.name)
                putExtra(EXTRA_STATE_ERROR_CATEGORY, state.errorCategory.name)
                putExtra(EXTRA_STATE_ERROR_SEVERITY, state.errorSeverity.name)
                putExtra(EXTRA_STATE_RAW_ACTIVE, state.rawRecordingActive)
                putExtra(EXTRA_STATE_CORRECTIONS_ACTIVE, state.correctionsActive)
                putExtra(EXTRA_STATE_UM980_FREQUENCY, state.um980Frequency)
                putExtra(EXTRA_STATE_UM980_MODE, state.um980Mode)
                putExtra(EXTRA_STATE_BEST_SOLUTION_SOURCE, state.bestSolutionSource)
                putExtra(EXTRA_STATE_BEST_SOLUTION_FIX, state.bestSolutionFix)
                putExtra(EXTRA_STATE_MOCK_LOCATION_SOLUTION_AGE_MS, state.mockLocationSolutionAgeMs ?: -1L)
                putExtra(EXTRA_STATE_BASE_AVERAGE_SUMMARY, state.baseAverageSummary)
                putExtra(EXTRA_STATE_BASE_AVERAGE_WARNING, state.baseAverageWarning)
                putExtra(EXTRA_STATE_BASE_AVERAGE_ACTIVE, state.baseAverageActive)
                putExtra(EXTRA_STATE_BASE_AVERAGE_LAT, state.baseAverageLatDeg ?: Double.NaN)
                putExtra(EXTRA_STATE_BASE_AVERAGE_LON, state.baseAverageLonDeg ?: Double.NaN)
                putExtra(EXTRA_STATE_BASE_AVERAGE_HEIGHT, state.baseAverageHeightM ?: Double.NaN)
                putExtra(EXTRA_STATE_BASE_AVERAGE_SAMPLE_COUNT, state.baseAverageSampleCount)
                putExtra(EXTRA_STATE_MOCK_LOCATION_STATE, state.mockLocationState)
                putExtra(EXTRA_STATE_MOCK_LOCATION_INTERVAL_MS, state.mockLocationLastIntervalMs ?: -1L)
                putExtra(EXTRA_STATE_MOCK_LOCATION_RATE_HZ, state.mockLocationRateHz)
                putExtra(EXTRA_STATE_UBLOX_FREQUENCY, state.ubloxFrequency)
            },
        )
        recordPerformanceDiagnosticIfDue()
    }

    private fun broadcastRoutineState() {
        if (routineStateBroadcastRateLimiter.shouldBroadcast(System.currentTimeMillis())) {
            broadcastState()
        }
    }

    private fun recordBinaryFrequency(frame: ByteArray, frequencyTracker: Um980MessageFrequencyTracker) {
        val now = System.currentTimeMillis()
        val receiverNow = Um980BinaryParser.receiverTimestampMillis(frame)
        when (Um980BinaryParser.messageId(frame)) {
            12, 138 -> frequencyTracker.record(Um980MessageKind.OBSVM, now, receiverNow)
            142 -> frequencyTracker.record(Um980MessageKind.ADRNAV, now, receiverNow)
            509 -> frequencyTracker.record(Um980MessageKind.RTKSTATUS, now, receiverNow)
            1026 -> frequencyTracker.record(Um980MessageKind.PPPNAV, now, receiverNow)
            2118 -> frequencyTracker.record(Um980MessageKind.BESTNAV, now, receiverNow)
        }
    }

    private fun parseUbloxAdvisory(
        bytes: ByteArray,
        nowMillis: Long,
        sessionWriters: RecordingSessionWriters,
        exportNmea: Boolean,
        exportJsonSolution: Boolean,
    ) {
        ubloxStreamParser.accept(bytes).forEach { record ->
            if (record.kind == "ubx") {
                when {
                    record.bytes.getOrNull(2) == 0x01.toByte() && record.bytes.getOrNull(3) == 0x07.toByte() -> {
                        ubloxFrequencyTracker.record(UbloxMessageKind.NAV_PVT, nowMillis)
                        UbloxNavPvtParser.parse(record.bytes, nowMillis)?.let { telemetry ->
                            if (exportJsonSolution) {
                                sessionWriters.appendReceiverSolutionJson(telemetry.toJson())
                            }
                            if (exportNmea) {
                                UbloxNmeaExporter.exportGga(telemetry)?.let { sentence ->
                                    sessionWriters.appendReceiverSolutionNmea(sentence)
                                    activeRecorder?.recordNmeaBytes(sentence.toByteArray(Charsets.US_ASCII).size)
                                }
                            }
                            telemetry.toSolutionCandidate(satellitesInView = freshUbloxSatellitesInView(nowMillis))?.let {
                                applyPrimaryScreenCandidate(it, nowMillis)
                            }
                        }
                    }
                    record.bytes.getOrNull(2) == 0x02.toByte() && record.bytes.getOrNull(3) == 0x15.toByte() ->
                        ubloxFrequencyTracker.record(UbloxMessageKind.RAWX, nowMillis)
                    record.bytes.getOrNull(2) == 0x02.toByte() && record.bytes.getOrNull(3) == 0x13.toByte() ->
                        ubloxFrequencyTracker.record(UbloxMessageKind.SFRBX, nowMillis)
                    record.bytes.getOrNull(2) == 0x0D.toByte() && record.bytes.getOrNull(3) == 0x03.toByte() ->
                        ubloxFrequencyTracker.record(UbloxMessageKind.TM2, nowMillis)
                    record.bytes.getOrNull(2) == 0x01.toByte() && record.bytes.getOrNull(3) == 0x35.toByte() -> {
                        ubloxFrequencyTracker.record(UbloxMessageKind.NAV_SAT, nowMillis)
                        UbloxNavSatParser.parse(record.bytes, nowMillis)?.let { telemetry ->
                            lastUbloxNavSatAtMillis = nowMillis
                            state = state.copy(
                                satellitesUsed = telemetry.satellitesUsed,
                                satellitesInView = telemetry.satellitesInView,
                                satellites = satelliteDisplay(telemetry.satellitesUsed, telemetry.satellitesInView),
                            )
                        }
                    }
                }
            }
            if (record.kind == "nmea") {
                val fixes = runCatching { sharedNmeaGgaParser.accept(record.bytes) }
                    .getOrDefault(emptyList())
                fixes.forEach { fix ->
                    ubloxFrequencyTracker.record(UbloxMessageKind.GGA, nowMillis)
                    fix.toCandidate("ublox", nowMillis)?.let {
                        applyPrimaryScreenCandidate(it, nowMillis)
                    }
                }
            }
        }
    }

    private fun freshUbloxSatellitesInView(nowMillis: Long): Int? {
        val updatedAt = lastUbloxNavSatAtMillis ?: return null
        return state.satellitesInView
            ?.takeIf { nowMillis - updatedAt <= UBLOX_NAV_SAT_FRESH_MILLIS }
    }

    private fun runBestSolutionTick() {
        runCatching {
            val now = System.currentTimeMillis()
            val ageLimit = org.rtkcollector.core.solution.BestSolutionSelector.DEFAULT_MAX_AGE_MILLIS
            // Evict stale entries proactively so the map stays bounded.
            solutionCandidates.entries.removeIf { entry ->
                now - entry.value.updatedAtMillis > ageLimit
            }
            val tickInput = BestSolutionTickInput(
                candidates = solutionCandidates.values,
                nowMillis = now,
                mockEnabled = mockLocationRequested,
                mockProviderAvailable = mockLocationPublisher != null,
                lastMockPublishedAt = lastMockPublishedAt,
                lastMockPublishedIdentity = lastMockPublishedIdentity,
                lastMockPublishWallClockAtMillis = lastMockPublishWallClockAtMillis,
                previousMockResult = previousMockResult,
                screenPolicy = screenSolutionPolicy,
                mockPolicy = mockSolutionPolicy,
            )
            val tick = BestSolutionTickLogic.compute(tickInput)
            applyTickStateDelta(tick.stateDelta, now)

            when (val action = tick.publishAction) {
                PublishAction.None -> {
                    lastMockPublishedAt = tick.newLastMockPublishedAt
                    lastMockPublishedIdentity = tick.newLastMockPublishedIdentity
                    lastMockPublishWallClockAtMillis = tick.newLastMockPublishWallClockAtMillis
                    previousMockResult = tick.newPreviousMockResult
                }
                is PublishAction.Publish -> {
                    val publisher = mockLocationPublisher
                    if (publisher == null) {
                        previousMockResult = org.rtkcollector.app.mocklocation.MockLocationPublishResult.NOT_PERMITTED
                        state = state.copy(mockLocationState = previousMockResult!!.name)
                    } else {
                        val publishedWallClockAtMillis = System.currentTimeMillis()
                        val published = publisher.publish(action.snapshot, enabled = true)
                        val applied = BestSolutionTickLogic.applyPublishResult(
                            previous = tick,
                            publishedResult = published,
                            publishedAtMillis = action.snapshot.updatedAtMillis,
                            publishedWallClockAtMillis = publishedWallClockAtMillis,
                        )
                        lastMockPublishedAt = applied.newLastMockPublishedAt
                        lastMockPublishedIdentity = applied.newLastMockPublishedIdentity
                        lastMockPublishWallClockAtMillis = applied.newLastMockPublishWallClockAtMillis
                        previousMockResult = published
                        state = state.copy(
                            mockLocationState = published.name,
                            mockLocationLastIntervalMs = applied.lastMockPublishIntervalMillis
                                ?: state.mockLocationLastIntervalMs,
                            mockLocationSolutionAgeMs = tick.stateDelta.bestSolutionAgeMs,
                        )
                        if (applied.setLastError) {
                            state = state.copy(
                                lastError = "Android mock-location update failed. " +
                                    "Check Developer options mock-location app setting.",
                                errorCategory = RecordingErrorCategory.PARSER_EXPORT,
                                errorSeverity = RecordingErrorSeverity.DEGRADED,
                            )
                            recordRuntimeDiagnostic(
                                category = DiagnosticCategory.MOCK_LOCATION,
                                severity = RecordingErrorSeverity.DEGRADED.name,
                                message = { state.lastError ?: "Android mock-location update failed." },
                            )
                        }
                    }
                }
            }
            broadcastRoutineState()
        }
    }

    private fun applyTickStateDelta(
        delta: BestSolutionStateDelta,
        nowMillis: Long,
    ) {
        state = state.applyBestSolutionDisplayDelta(
            delta = delta,
            ubloxFrequency = ubloxFrequencyTracker.display(nowMillis),
            formatLatLon = ::latLonDisplay,
            formatMeters = ::metersDisplay,
            formatSatellites = ::satelliteDisplay,
        )
    }

    private fun applyPrimaryScreenCandidate(
        candidate: org.rtkcollector.core.solution.SolutionCandidate,
        nowMillis: Long,
    ) {
        if (candidate.isPrimaryScreenCandidateFor(activeReceiverFamily)) {
            state = state.withSelectedSolution(candidate, nowMillis)
            if (activeReceiverFamily.startsWith("ublox", ignoreCase = true)) {
                val mockResult = previousMockResult ?: if (mockLocationRequested) {
                    MockLocationPublishResult.STALE
                } else {
                    MockLocationPublishResult.DISABLED
                }
                applyTickStateDelta(
                    BestSolutionTickLogic.stateDeltaForCandidate(candidate, nowMillis, mockResult),
                    nowMillis,
                )
            }
            if (coordinateAveragingController.active) {
                val result = coordinateAveragingController.onSelectedSolution(candidate)
                updateCoordinateAverageState(result)
            }
        }
        solutionCandidates[candidate.sourceId] = candidate
    }

    private fun startCoordinateAveraging() {
        if (!running.get()) {
            state = state.copy(
                baseAverageActive = false,
                baseAverageWarning = "Cannot start averaging: recording is not active.",
            )
            broadcastState()
            return
        }
        val candidate = latestPrimaryScreenCandidate()
        if (candidate == null) {
            state = state.copy(
                baseAverageActive = false,
                baseAverageWarning = "Cannot start averaging: no selected receiver solution.",
            )
            broadcastState()
            return
        }
        coordinateAveragingController.start(candidate.fixClass)
        val result = coordinateAveragingController.onSelectedSolution(candidate)
        updateCoordinateAverageState(result)
        broadcastState()
    }

    private fun stopCoordinateAveraging() {
        coordinateAveragingController.stop("Stopped")
        updateCoordinateAverageState(null)
        broadcastState()
    }

    private fun latestPrimaryScreenCandidate(): SolutionCandidate? =
        solutionCandidates.values
            .filter { it.isPrimaryScreenCandidateFor(activeReceiverFamily) }
            .maxByOrNull { it.updatedAtMillis }

    private fun updateCoordinateAverageState(result: CoordinateAverageAddResult?) {
        val summary = coordinateAveragingController.summary()
        val warning = when {
            result != null && !result.accepted -> result.reason
            coordinateAveragingController.lastStopReason != null -> coordinateAveragingController.lastStopReason
            else -> null
        }
        state = state.copy(
            baseAverageSummary = summary?.toDisplayText(),
            baseAverageWarning = warning,
            baseAverageActive = coordinateAveragingController.active,
            baseAverageLatDeg = summary?.latMeanDeg,
            baseAverageLonDeg = summary?.lonMeanDeg,
            baseAverageHeightM = summary?.heightMeanM,
            baseAverageSampleCount = summary?.sampleCount ?: 0,
        )
    }

    private fun CoordinateAverageSummary.toDisplayText(): String =
        "Avg ${latMeanDeg.formatFixed(9)}, ${lonMeanDeg.formatFixed(9)}, " +
            "h=${heightMeanM.formatFixed(3)} m, n=$sampleCount"

    private fun Double.formatFixed(decimals: Int): String =
        "%.${decimals}f".format(java.util.Locale.US, this)

    private fun configureMockLocation(enabled: Boolean) {
        if (!enabled) {
            teardownMockLocation()
            return
        }
        val manager = getSystemService(android.location.LocationManager::class.java)
        if (manager == null) {
            mockLocationPublisher = null
            state = state.copy(
                mockLocationState =
                    org.rtkcollector.app.mocklocation.MockLocationPublishResult.NOT_PERMITTED.name,
            )
            return
        }
        val outcome = runCatching {
            manager.addTestProvider(
                android.location.LocationManager.GPS_PROVIDER,
                /* requiresNetwork = */ false,
                /* requiresSatellite = */ false,
                /* requiresCell = */ false,
                /* hasMonetaryCost = */ false,
                /* supportsAltitude = */ true,
                /* supportsSpeed = */ true,
                /* supportsBearing = */ true,
                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE,
            )
            manager.setTestProviderEnabled(
                android.location.LocationManager.GPS_PROVIDER,
                true,
            )
        }
        if (outcome.isFailure) {
            mockLocationPublisher = null
            previousMockResult =
                org.rtkcollector.app.mocklocation.MockLocationPublishResult.NOT_PERMITTED
            state = state.copy(
                mockLocationState = previousMockResult!!.name,
                lastError = mockLocationSetupFailureMessage(outcome.exceptionOrNull()!!),
                errorCategory = RecordingErrorCategory.PARSER_EXPORT,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
            recordRuntimeDiagnostic(
                category = DiagnosticCategory.MOCK_LOCATION,
                severity = RecordingErrorSeverity.DEGRADED.name,
                message = { state.lastError ?: "Android mock-location provider setup failed." },
            )
            return
        }
        mockLocationPublisher = org.rtkcollector.app.mocklocation.MockLocationPublisher(
            org.rtkcollector.app.mocklocation.AndroidMockLocationSink(
                manager,
                android.location.LocationManager.GPS_PROVIDER,
            ),
        )
    }

    private fun teardownMockLocation() {
        if (mockLocationPublisher == null) return
        mockLocationPublisher = null
        runCatching {
            val manager = getSystemService(android.location.LocationManager::class.java) ?: return
            manager.setTestProviderEnabled(
                android.location.LocationManager.GPS_PROVIDER,
                false,
            )
            manager.removeTestProvider(android.location.LocationManager.GPS_PROVIDER)
        }
    }

    private fun compileReceiverCommands(
        phase: CompilePhase,
        receiverFamily: String,
        script: String,
    ): List<org.rtkcollector.receiver.api.ReceiverCommand> {
        return try {
            if (receiverFamily.startsWith("ublox", ignoreCase = true)) {
                org.rtkcollector.receiver.ublox.UbloxScriptCompiler.compile(script)
            } else {
                script.lineSequence()
                    .map(String::trim)
                    .filter { it.isNotBlank() }
                    .map {
                        org.rtkcollector.receiver.api.ReceiverCommand(
                            label = it.take(40),
                            payload = "$it\r\n".toByteArray(Charsets.US_ASCII),
                        )
                    }
                    .toList()
            }
        } catch (error: Exception) {
            val severity = when (phase) {
                CompilePhase.START -> RecordingErrorSeverity.FATAL
                CompilePhase.STOP -> RecordingErrorSeverity.DEGRADED
            }
            state = state.copy(
                lastError = error.message ?: error.javaClass.simpleName,
                errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
                errorSeverity = severity,
            )
            broadcastState()
            if (phase == CompilePhase.START) {
                throw error
            }
            emptyList()
        }
    }

    private fun buildAdvisoryFanout(
        sessionWriters: RecordingSessionWriters,
        eventSink: CaptureEventSink,
        exportNmea: Boolean,
        nmeaExportOptions: Um980NmeaExportOptions,
        exportJsonSolution: Boolean,
        casterUploadController: NtripCasterUploadController?,
    ): AdvisoryFanout {
        val ggaParser = NmeaGgaParser()
        val gsaParser = NmeaGsaParser()
        val gstParser = NmeaGstParser()
        val gsvParser = NmeaGsvParser()
        val gsvTracker = NmeaGsvInViewTracker()
        val nmeaExporter = NmeaSentenceExtractor()
        val solutionParser = Um980AsciiSolutionParser()
        val streamParser = Um980StreamParser()
        val frequencyTracker = Um980MessageFrequencyTracker()
        val rtcmExtractor = Rtcm3Extractor(validateCrc = true)
        return AdvisoryFanout(
            eventSink = eventSink,
            consumers = listOf(
                AdvisoryConsumer("rtklib-rover-input") { bytes ->
                    rtklibWorker?.offerRoverBytes(
                        bytes = bytes,
                        timestampMillis = System.currentTimeMillis(),
                        sessionOffsetBytes = activeRecorder?.receiverRxBytes,
                    )
                },
                AdvisoryConsumer("um980-mixed-stream") { bytes ->
                    if (activeReceiverFamily.startsWith("ublox", ignoreCase = true)) {
                        return@AdvisoryConsumer
                    }
                    streamParser.accept(bytes).forEach { record ->
                        when (record.kind) {
                            "nmea" -> {
                                if (exportNmea) {
                                    nmeaExporter.accept(record.bytes).forEach { sentence ->
                                        sessionWriters.appendReceiverSolutionNmea(sentence)
                                        activeRecorder?.recordNmeaBytes(sentence.toByteArray(Charsets.US_ASCII).size)
                                    }
                                }
                                ggaParser.accept(record.bytes).forEach { fix ->
                                    frequencyTracker.record(Um980MessageKind.GGA, System.currentTimeMillis())
                                    if (exportJsonSolution) {
                                        sessionWriters.appendReceiverSolutionJson(fix.toJson())
                                    }
                                    state = state.copy(
                                        ggaFixQuality = fix.fixQuality,
                                        latDeg = fix.latDeg ?: state.latDeg,
                                        lonDeg = fix.lonDeg ?: state.lonDeg,
                                        latLon = latLonDisplay(fix.latDeg, fix.lonDeg).takeUnless { it == "n/a" } ?: state.latLon,
                                        altitude = metersDisplay(fix.altitudeM).takeUnless { it == "n/a" } ?: state.altitude,
                                        ellipsoidalHeight = metersDisplay(fix.ellipsoidalHeightM).takeUnless { it == "n/a" }
                                            ?: state.ellipsoidalHeight,
                                        utcTime = fix.utcTime.ifBlank { state.utcTime },
                                        satellitesUsed = fix.satelliteCount ?: state.satellitesUsed,
                                        satellites = satelliteDisplay(fix.satelliteCount, state.satellitesInView),
                                        hdopVdop = dopPairDisplay(fix.hdop, state.vdop),
                                        differentialAge = fix.differentialAgeS?.let { "%.1f s".format(java.util.Locale.US, it) }
                                            ?: state.differentialAge,
                                    )
                                    val now = System.currentTimeMillis()
                                    fix.toCandidate("um980", now)?.let {
                                        applyPrimaryScreenCandidate(it, now)
                                    }
                                }
                                gsaParser.accept(record.bytes).forEach { dop ->
                                    state = state.copy(
                                        satellitesUsed = dop.satellitesUsed ?: state.satellitesUsed,
                                        pdop = dop.pdop?.let { "%.1f".format(java.util.Locale.US, it) } ?: state.pdop,
                                        vdop = dop.vdop ?: state.vdop,
                                        hdopVdop = dopPairDisplay(dop.hdop, dop.vdop ?: state.vdop),
                                        satellites = satelliteDisplay(
                                            used = dop.satellitesUsed ?: state.satellitesUsed,
                                            inView = state.satellitesInView,
                                        ).takeUnless { it == "n/a" } ?: state.satellites,
                                    )
                                }
                                gstParser.accept(record.bytes).forEach { error ->
                                    val horizontal = listOfNotNull(error.latErrorM, error.lonErrorM).maxOrNull()
                                    state = state.copy(
                                        utcTime = error.utcTime.ifBlank { state.utcTime },
                                        latError = error.latErrorM?.let(::metersDisplay) ?: state.latError,
                                        lonError = error.lonErrorM?.let(::metersDisplay) ?: state.lonError,
                                        horizontalAccuracy = horizontal?.let(::metersDisplay) ?: state.horizontalAccuracy,
                                        verticalAccuracy = error.heightErrorM?.let(::metersDisplay) ?: state.verticalAccuracy,
                                    )
                                }
                                gsvParser.accept(record.bytes).forEach { view ->
                                    val satellitesInView = gsvTracker.accept(view)
                                    state = state.copy(
                                        satellitesInView = satellitesInView ?: state.satellitesInView,
                                        satellites = satelliteDisplay(
                                            used = state.satellitesUsed,
                                            inView = satellitesInView ?: state.satellitesInView,
                                        ).takeUnless { it == "n/a" } ?: state.satellites,
                                    )
                                }
                            }
                            "unicore_ascii" -> {
                                solutionParser.accept(record.bytes).forEach { solution ->
                                    when (solution.logName) {
                                        "PPPNAVA" -> {
                                            frequencyTracker.record(Um980MessageKind.PPPNAV, System.currentTimeMillis())
                                            if (exportJsonSolution) {
                                                sessionWriters.appendReceiverPppSolutionJson(solution.toJson())
                                            }
                                            state = state.copy(
                                                pppStatus = um980PppStatusLabel(
                                                    solutionStatus = solution.solutionStatus,
                                                    positionType = solution.positionType,
                                                ) ?: state.pppStatus,
                                            )
                                        }
                                        "ADRNAVA" -> {
                                            frequencyTracker.record(Um980MessageKind.ADRNAV, System.currentTimeMillis())
                                            if (exportJsonSolution) {
                                                sessionWriters.appendReceiverSolutionJson(solution.toJson())
                                            }
                                            state = state.withReceiverRtkAsciiSolution(solution)
                                        }
                                        "BESTNAVA" -> {
                                            frequencyTracker.record(Um980MessageKind.BESTNAV, System.currentTimeMillis())
                                            if (exportJsonSolution) {
                                                sessionWriters.appendReceiverSolutionJson(solution.toJson())
                                            }
                                            state = state.copy(
                                                bestnavPositionType = solution.positionType,
                                                latDeg = solution.latDeg ?: state.latDeg,
                                                lonDeg = solution.lonDeg ?: state.lonDeg,
                                                latLon = latLonDisplay(solution.latDeg, solution.lonDeg).takeUnless { it == "n/a" } ?: state.latLon,
                                                ellipsoidalHeight = metersDisplay(solution.heightM).takeUnless { it == "n/a" }
                                                    ?: state.ellipsoidalHeight,
                                            ).withReceiverRtkAsciiSolution(solution)
                                            val now = System.currentTimeMillis()
                                            solution.toBestnavCandidate(now)?.let {
                                                applyPrimaryScreenCandidate(it, now)
                                            }
                                        }
                                    }
                                }
                            }
                            "unicore_binary" -> {
                                recordBinaryFrequency(record.bytes, frequencyTracker)
                                Um980BinaryParser.parseBestnavb(record.bytes)?.let { telemetry ->
                                    if (exportJsonSolution) {
                                        sessionWriters.appendReceiverSolutionJson(telemetry.toJson())
                                    }
                                    if (exportNmea) {
                                        Um980NmeaExporter.export(telemetry, nmeaExportOptions).forEach { sentence ->
                                            sessionWriters.appendReceiverSolutionNmea(sentence)
                                            activeRecorder?.recordNmeaBytes(sentence.toByteArray(Charsets.US_ASCII).size)
                                        }
                                    }
                                    state = state.withUm980Telemetry(telemetry)
                                        .withReceiverRtkTelemetry(telemetry)
                                    val now = System.currentTimeMillis()
                                    telemetry.toBestnavCandidate(now)?.let {
                                        applyPrimaryScreenCandidate(it, now)
                                    }
                                }
                                Um980BinaryParser.parseAdrnavb(record.bytes)?.let { telemetry ->
                                    if (exportJsonSolution) {
                                        sessionWriters.appendReceiverSolutionJson(telemetry.toJson())
                                    }
                                    state = state.withUm980Telemetry(telemetry)
                                        .withReceiverRtkTelemetry(telemetry)
                                }
                                Um980BinaryParser.parsePppnavb(record.bytes)?.let { telemetry ->
                                    if (exportJsonSolution) {
                                        sessionWriters.appendReceiverPppSolutionJson(telemetry.toJson())
                                    }
                                    state = state.copy(
                                        pppStatus = telemetry.pppStatusLabel() ?: state.pppStatus,
                                    )
                                    val now = System.currentTimeMillis()
                                    telemetry.toPppCandidate(now)?.let {
                                        solutionCandidates[it.sourceId] = it
                                    }
                                }
                                Um980BinaryParser.parseRtkstatusb(record.bytes)?.let { telemetry ->
                                    if (exportJsonSolution) {
                                        sessionWriters.appendQualityLiveJson(telemetry.toJson())
                                    }
                                    state = state.withReceiverRtkTelemetry(telemetry)
                                }
                                Um980BinaryParser.parseRtcmstatusb(record.bytes)?.let { telemetry ->
                                    if (exportJsonSolution) {
                                        sessionWriters.appendQualityLiveJson(telemetry.toJson())
                                    }
                                    state = state.withRtcmDecodedTelemetry(telemetry)
                                }
                                Um980BinaryParser.parseStadopb(record.bytes)?.let { telemetry ->
                                    if (exportJsonSolution) {
                                        sessionWriters.appendQualityLiveJson(telemetry.toJson())
                                    }
                                    state = state.withUm980Telemetry(telemetry)
                                }
                            }
                        }
                        val displayNow = System.currentTimeMillis()
                        val receiverNow = Um980BinaryParser.receiverTimestampMillis(record.bytes)
                        state = state.copy(
                            um980Frequency = frequencyTracker.display(displayNow, receiverNow),
                        )
                    }
                },
                AdvisoryConsumer("ublox-mixed-stream") { bytes ->
                    if (activeReceiverFamily.startsWith("ublox", ignoreCase = true)) {
                        parseUbloxAdvisory(
                            bytes = bytes,
                            nowMillis = System.currentTimeMillis(),
                            sessionWriters = sessionWriters,
                            exportNmea = exportNmea,
                            exportJsonSolution = exportJsonSolution,
                        )
                    }
                },
                AdvisoryConsumer("rtcm3-extractor") { bytes ->
                    rtcmExtractor.accept(bytes).forEach { frame ->
                        if (frame.crcValid != true) {
                            return@forEach
                        }
                        val baseUploadOffered = if (casterUploadController != null && frame.crcValid == true) {
                            sessionWriters.appendBaseCasterUploadRtcm(frame.bytes)
                            casterUploadController.offer(frame.bytes)
                        } else {
                            null
                        }
                        sessionWriters.appendExtractedRtcm(frame.bytes)
                        sessionWriters.appendQualityLiveJson(
                            """{"type":"rtcm3-frame","payloadLength":${frame.payloadLength},"messageType":${frame.messageType ?: "null"},"crcValid":${frame.crcValid ?: "null"},"baseCasterUploadOffered":${baseUploadOffered ?: "null"}}""",
                        )
                        val referenceStation = Rtcm3ReferenceStationParser.parse(frame)
                        state = if (referenceStation != null) {
                            state.withRtcmReferenceStation(referenceStation).copy(rtcmFrames = state.rtcmFrames + 1)
                        } else {
                            state.copy(rtcmFrames = state.rtcmFrames + 1)
                        }
                    }
                },
            ),
        )
    }

    private fun NmeaGgaFix.toJson(): String =
        """{"type":"nmea-gga","talker":"${talker.jsonEscape()}","utcTime":"${utcTime.jsonEscape()}","latDeg":${latDeg ?: "null"},"lonDeg":${lonDeg ?: "null"},"fixQuality":${fixQuality ?: "null"},"satelliteCount":${satelliteCount ?: "null"},"hdop":${hdop ?: "null"},"altitudeM":${altitudeM ?: "null"},"geoidSeparationM":${geoidSeparationM ?: "null"},"ellipsoidalHeightM":${ellipsoidalHeightM ?: "null"},"differentialAgeS":${differentialAgeS ?: "null"},"stationId":${stationId.jsonStringOrNull()}}"""

    private fun Um980AsciiSolution.toJson(): String =
        """{"type":"um980-ascii-solution","logName":"${logName.jsonEscape()}","solutionStatus":${solutionStatus.jsonStringOrNull()},"positionType":${positionType.jsonStringOrNull()},"latDeg":${latDeg ?: "null"},"lonDeg":${lonDeg ?: "null"},"heightM":${heightM ?: "null"}}"""

    private fun Um980Telemetry.toJson(): String =
        """{"type":"um980-binary-telemetry","source":"${source.jsonEscape()}","solutionStatus":${solutionStatus.jsonStringOrNull()},"positionType":${positionType.jsonStringOrNull()},"latDeg":${latDeg ?: "null"},"lonDeg":${lonDeg ?: "null"},"altitudeM":${altitudeM ?: "null"},"ellipsoidalHeightM":${ellipsoidalHeightM ?: "null"},"latErrorM":${latErrorM ?: "null"},"lonErrorM":${lonErrorM ?: "null"},"verticalAccuracyM":${verticalAccuracyM ?: "null"},"satellitesInView":${satellitesInView ?: "null"},"satellitesUsed":${satellitesUsed ?: "null"},"satellitesTracked":${satellitesTracked ?: "null"},"gdop":${gdop ?: "null"},"pdop":${pdop ?: "null"},"tdop":${tdop ?: "null"},"hdop":${hdop ?: "null"},"vdop":${vdop ?: "null"},"ndop":${ndop ?: "null"},"edop":${edop ?: "null"},"differentialAgeS":${differentialAgeS ?: "null"},"baselineLengthM":${baselineLengthM ?: "null"},"stationId":${stationId.jsonStringOrNull()},"utcTime":${utcTime.jsonStringOrNull()},"rtkPositionType":${rtkPositionType.jsonStringOrNull()},"rtkCalculateStatus":${rtkCalculateStatus ?: "null"},"rtkCalculateStatusDescription":${rtkCalculateStatusDescription.jsonStringOrNull()},"ionDetected":${ionDetected ?: "null"},"adrNumber":${adrNumber ?: "null"},"gpsSource":${gpsSource ?: "null"},"bdsSource1":${bdsSource1 ?: "null"},"bdsSource2":${bdsSource2 ?: "null"},"gloSource":${gloSource ?: "null"},"galSource1":${galSource1 ?: "null"},"galSource2":${galSource2 ?: "null"},"qzssSource":${qzssSource ?: "null"},"rtcmMessageId":${rtcmMessageId ?: "null"},"rtcmMessageCount":${rtcmMessageCount ?: "null"},"rtcmBaseId":${rtcmBaseId ?: "null"},"rtcmSatelliteCount":${rtcmSatelliteCount ?: "null"},"rtcmObservableCounts":[${rtcmObservableCounts.joinToString(",")}]}"""

    private fun UbloxTelemetry.toJson(): String =
        """{"type":"ublox-nav-pvt","source":"${source.jsonEscape()}","utcTime":${utcTime.jsonStringOrNull()},"fixClass":${fixClass?.name.jsonStringOrNull()},"latDeg":${latDeg ?: "null"},"lonDeg":${lonDeg ?: "null"},"ellipsoidalHeightM":${ellipsoidalHeightM ?: "null"},"altitudeM":${mslAltitudeM ?: "null"},"horizontalAccuracyM":${horizontalAccuracyM ?: "null"},"verticalAccuracyM":${verticalAccuracyM ?: "null"},"satellitesUsed":${satellitesUsed ?: "null"},"rawObservationsPresent":$rawObservationsPresent}"""

    private fun RecordingServiceState.withUm980Telemetry(telemetry: Um980Telemetry): RecordingServiceState =
        copy(
            bestnavPositionType = telemetry.positionType?.takeIf { telemetry.source == "BESTNAVB" } ?: bestnavPositionType,
            latDeg = telemetry.latDeg ?: latDeg,
            lonDeg = telemetry.lonDeg ?: lonDeg,
            latLon = latLonDisplay(telemetry.latDeg, telemetry.lonDeg).takeUnless { it == "n/a" } ?: latLon,
            altitude = metersDisplay(telemetry.altitudeM).takeUnless { it == "n/a" } ?: altitude,
            ellipsoidalHeight = metersDisplay(telemetry.ellipsoidalHeightM).takeUnless { it == "n/a" } ?: ellipsoidalHeight,
            latError = telemetry.latErrorM?.let(::metersDisplay) ?: latError,
            lonError = telemetry.lonErrorM?.let(::metersDisplay) ?: lonError,
            horizontalAccuracy = telemetry.latErrorM?.let(::metersDisplay) ?: horizontalAccuracy,
            verticalAccuracy = telemetry.verticalAccuracyM?.let(::metersDisplay) ?: verticalAccuracy,
            utcTime = telemetry.utcTime ?: utcTime,
            satellitesUsed = telemetry.satellitesUsed ?: satellitesUsed,
            satellitesInView = telemetry.satellitesInView ?: telemetry.satellitesTracked ?: satellitesInView,
            pdop = telemetry.pdop?.let { "%.1f".format(java.util.Locale.US, it) } ?: pdop,
            vdop = telemetry.vdop ?: vdop,
            hdopVdop = dopPairDisplay(telemetry.hdop, telemetry.vdop).takeUnless { it == "n/a" } ?: hdopVdop,
            differentialAge = telemetry.differentialAgeS?.let { "%.1f s".format(java.util.Locale.US, it) } ?: differentialAge,
            baseline = telemetry.baselineLengthM?.let(::distanceDisplay)
                ?: baselineDisplay(telemetry.latDeg ?: latDeg, telemetry.lonDeg ?: lonDeg, ntripBaseLatDeg, ntripBaseLonDeg)
                ?: baseline,
            satellites = satelliteDisplay(
                used = telemetry.satellitesUsed,
                inView = telemetry.satellitesInView ?: telemetry.satellitesTracked,
            ).takeUnless { it == "n/a" } ?: satellites,
        )

    private fun RecordingServiceState.withReceiverRtkAsciiSolution(solution: Um980AsciiSolution): RecordingServiceState {
        val nowMillis = android.os.SystemClock.elapsedRealtime()
        val status = classifyReceiverRtkStatus(
            positionType = solution.positionType,
            solutionStatus = solution.solutionStatus,
            calculateStatus = rtkCalculateStatus,
            differentialAgeS = null,
            recentCorrectionInput = hasRecentCorrectionInput(),
            recentReceiverRtcmDecoded = hasRecentRtcmDecoded(),
        )
        return copy(
            receiverRtkStatus = status,
            rtkPositionType = solution.positionType ?: rtkPositionType,
            receiverRtkEvidenceAtMillis = nowMillis,
        )
    }

    private fun RecordingServiceState.withReceiverRtkTelemetry(telemetry: Um980Telemetry): RecordingServiceState {
        val nowMillis = android.os.SystemClock.elapsedRealtime()
        val positionType = telemetry.rtkPositionType ?: telemetry.positionType
        val calculateStatus = telemetry.rtkCalculateStatus ?: rtkCalculateStatus
        val status = classifyReceiverRtkStatus(
            positionType = positionType,
            solutionStatus = telemetry.solutionStatus,
            calculateStatus = calculateStatus,
            differentialAgeS = telemetry.differentialAgeS,
            recentCorrectionInput = hasRecentCorrectionInput(),
            recentReceiverRtcmDecoded = hasRecentRtcmDecoded(),
        )
        return copy(
            receiverRtkStatus = status,
            rtkPositionType = positionType ?: rtkPositionType,
            rtkCalculateStatus = calculateStatus,
            rtkCalculateStatusDescription = telemetry.rtkCalculateStatusDescription ?: rtkCalculateStatusDescription,
            receiverRtkEvidenceAtMillis = nowMillis,
        )
    }

    private fun RecordingServiceState.withRtcmDecodedTelemetry(telemetry: Um980Telemetry): RecordingServiceState {
        val nowMillis = android.os.SystemClock.elapsedRealtime()
        return copy(
            rtcmDecodedAtMillis = nowMillis,
            rtcmLastMessageId = telemetry.rtcmMessageId ?: rtcmLastMessageId,
            rtcmLastBaseId = telemetry.rtcmBaseId ?: rtcmLastBaseId,
            receiverRtkStatus = receiverRtkStatusAfterRtcmDecoded(
                previousStatus = receiverRtkStatus,
                lastReceiverRtkEvidenceAtMillis = receiverRtkEvidenceAtMillis,
                nowMillis = nowMillis,
            ),
        )
    }

    private fun RecordingServiceState.hasRecentRtcmDecoded(): Boolean =
        rtcmDecodedAtMillis?.let { android.os.SystemClock.elapsedRealtime() - it < RTCM_STATUS_RECENT_MILLIS } == true

    private fun RecordingServiceState.hasRecentCorrectionInput(): Boolean =
        correctionInputRtcmAtMillis?.let { android.os.SystemClock.elapsedRealtime() - it < RTCM_STATUS_RECENT_MILLIS } == true

    private fun RecordingServiceState.withRtcmReferenceStation(station: Rtcm3ReferenceStation): RecordingServiceState {
        val baseLat = station.latDeg
        val baseLon = station.lonDeg
        return copy(
            ntripStationId = station.stationId.toString(),
            ntripBaseLatDeg = baseLat,
            ntripBaseLonDeg = baseLon,
            ntripBaseLatLon = latLonDisplay(baseLat, baseLon),
            baseline = baselineDisplay(latDeg, lonDeg, baseLat, baseLon) ?: baseline,
        )
    }

    private fun RecordingServiceState.withCasterUploadSnapshot(
        snapshot: NtripCasterUploadSnapshot,
    ): RecordingServiceState {
        val uploadHasProblem = !snapshot.lastError.isNullOrBlank() &&
            snapshot.state != "STOPPED" &&
            errorSeverity != RecordingErrorSeverity.FATAL
        return copy(
            baseCasterUploadState = snapshot.state,
            baseCasterUploadUrl = snapshot.mountpointUrl.ifBlank { baseCasterUploadUrl },
            baseCasterUploadBytes = snapshot.bytesUploaded,
            baseCasterUploadDroppedBytes = snapshot.bytesDropped,
            baseCasterUploadLastError = snapshot.lastError,
            lastError = if (uploadHasProblem) snapshot.lastError else lastError,
            errorCategory = if (uploadHasProblem) RecordingErrorCategory.NTRIP else errorCategory,
            errorSeverity = if (uploadHasProblem) RecordingErrorSeverity.DEGRADED else errorSeverity,
        )
    }

    private fun String?.jsonStringOrNull(): String =
        if (this == null) "null" else "\"${jsonEscape()}\""

    private fun String.jsonEscape(): String =
        buildString {
            this@jsonEscape.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }

    private fun sessionModeFrom(receiverRole: String?): SessionMode =
        when (receiverRole) {
            "FIXED_BASE" -> SessionMode.FIXED_BASE
            "BASE_CALIBRATION" -> SessionMode.TEMPORARY_BASE_PREPARATION
            "REPLAY_TEST" -> SessionMode.REPLAY_TEST
            else -> SessionMode.ROVER
        }

    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_USB_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_USB_DEVICE)
        }

    private fun Intent.optionalDoubleExtra(name: String): Double? =
        if (hasExtra(name)) getDoubleExtra(name, Double.NaN).takeUnless { it.isNaN() } else null

    private fun Intent.mockLocationRateHz(): Int =
        sanitizeMockLocationRateHz(
            getIntExtra(
                EXTRA_MOCK_LOCATION_RATE_HZ,
                RecordingPolicyProfile.DEFAULT_MOCK_LOCATION_RATE_HZ,
            ),
        )

    private fun startBestSolutionTicker() {
        bestSolutionTicker?.cancel(false)
        bestSolutionTicker = null
        if (!mockLocationRequested) return
        val periodMillis = mockLocationPublishPeriodMillis(mockLocationRateHz)
        bestSolutionTicker = bestSolutionExecutor.scheduleAtFixedRate(
            { runBestSolutionTick() },
            periodMillis,
            periodMillis,
            java.util.concurrent.TimeUnit.MILLISECONDS,
        )
    }

    private class SessionRawRecorder(
        private val writers: RecordingSessionWriters,
        private val recordCorrectionInput: Boolean,
    ) : RawRecorder {
        var receiverRxBytes: Long = 0
            private set
        var txToReceiverBytes: Long = 0
            private set
        var correctionInputBytes: Long = 0
            private set
        var nmeaBytes: Long = 0
            private set

        override fun appendReceiverBytes(bytes: ByteArray) {
            writers.appendReceiverRx(bytes)
            receiverRxBytes += bytes.size
        }

        override fun appendTransmittedBytes(bytes: ByteArray) {
            writers.appendTxToReceiver(bytes)
            txToReceiverBytes += bytes.size
        }

        override fun appendCorrectionInputBytes(bytes: ByteArray) {
            if (recordCorrectionInput) {
                writers.appendCorrectionInput(bytes)
                correctionInputBytes += bytes.size
            }
        }

        override fun close() {
            writers.flush()
        }

        fun recordNmeaBytes(bytes: Int) {
            nmeaBytes += bytes
        }
    }

    private class SessionEventSink(private val writers: RecordingSessionWriters) : CaptureEventSink {
        override fun recordEvent(event: CaptureEvent) {
            val json = """{"timestamp":"${event.timestamp}","type":"${event.type}","message":"${event.message.replace("\"", "\\\"")}"}"""
            writers.appendEventJson(json)
        }
    }

    companion object {
        const val ACTION_START = "org.rtkcollector.app.recording.START"
        const val ACTION_STOP = "org.rtkcollector.app.recording.STOP"
        const val ACTION_QUERY = "org.rtkcollector.app.recording.QUERY"
        const val ACTION_UPDATE_NTRIP = "org.rtkcollector.app.recording.UPDATE_NTRIP"
        const val ACTION_DISABLE_NTRIP = "org.rtkcollector.app.recording.DISABLE_NTRIP"
        const val ACTION_UPDATE_MOCK_LOCATION = "org.rtkcollector.app.recording.UPDATE_MOCK_LOCATION"
        const val ACTION_WRITE_PERSISTENT_RECEIVER_CONFIG =
            "org.rtkcollector.app.recording.WRITE_PERSISTENT_RECEIVER_CONFIG"
        const val ACTION_START_COORDINATE_AVERAGING =
            "org.rtkcollector.app.recording.START_COORDINATE_AVERAGING"
        const val ACTION_STOP_COORDINATE_AVERAGING =
            "org.rtkcollector.app.recording.STOP_COORDINATE_AVERAGING"
        const val ACTION_STATE = "org.rtkcollector.app.recording.STATE"

        const val EXTRA_USB_DEVICE = "usbDevice"
        const val EXTRA_SERIAL_BAUD = "serialBaud"
        const val EXTRA_PROFILE_BAUD = "profileBaud"
        const val EXTRA_INIT_COMMANDS = "initCommands"
        const val EXTRA_BAUD_SWITCH_COMMANDS = "baudSwitchCommands"
        const val EXTRA_MODE_COMMANDS = "modeCommands"
        const val EXTRA_SHUTDOWN_COMMANDS = "shutdownCommands"
        const val EXTRA_PERSISTENT_COMMANDS = "persistentCommands"
        const val EXTRA_PERSISTENT_WRITE_LABEL = "persistentWriteLabel"
        const val EXTRA_PERSISTENT_TARGET_BAUD = "persistentTargetBaud"
        const val EXTRA_NTRIP_ENABLED = "ntripEnabled"
        const val EXTRA_NTRIP_HOST = "ntripHost"
        const val EXTRA_NTRIP_PORT = "ntripPort"
        const val EXTRA_NTRIP_MOUNTPOINT = "ntripMountpoint"
        const val EXTRA_NTRIP_USERNAME = "ntripUsername"
        const val EXTRA_NTRIP_PASSWORD = "ntripPassword"
        const val EXTRA_NTRIP_SECRET_REF = "ntripSecretRef"
        const val EXTRA_NTRIP_GGA = "ntripGga"
        const val EXTRA_NTRIP_SEND_TO_RECEIVER = "ntripSendToReceiver"
        const val EXTRA_NTRIP_STATION_ID = "ntripStationId"
        const val EXTRA_NTRIP_BASE_LAT = "ntripBaseLat"
        const val EXTRA_NTRIP_BASE_LON = "ntripBaseLon"
        const val EXTRA_WORKFLOW_ID = "workflowId"
        const val EXTRA_WORKFLOW_NAME = "workflowName"
        const val EXTRA_RECEIVER_ROLE = "receiverRole"
        const val EXTRA_RECEIVER_PROFILE_ID = "receiverProfileId"
        const val EXTRA_UM980_PROFILE_ID = "um980ProfileId"
        const val EXTRA_COMMAND_PROFILE_ID = "commandProfileId"
        const val EXTRA_COMMAND_RECEIVER_FAMILY = "commandReceiverFamily"
        const val EXTRA_USB_BAUD_PROFILE_ID = "usbBaudProfileId"
        const val EXTRA_NTRIP_CASTER_PROFILE_ID = "ntripCasterProfileId"
        const val EXTRA_NTRIP_MOUNTPOINT_PROFILE_ID = "ntripMountpointProfileId"
        const val EXTRA_RECORDING_POLICY_ID = "recordingPolicyId"
        const val EXTRA_RTKLIB_PROFILE_ID = "rtklibProfileId"
        const val EXTRA_RTKLIB_ENABLED = "rtklibEnabled"
        const val EXTRA_RTKLIB_PRESET = "rtklibPreset"
        const val EXTRA_RTKLIB_SNAPSHOT_ID = "rtklibSnapshotId"
        const val EXTRA_RTKLIB_ROUTE_PLAN = "rtklibRoutePlan"
        const val EXTRA_RTKLIB_VALIDATION_SUMMARY = "rtklibValidationSummary"
        const val EXTRA_RTKLIB_OUTPUT_NMEA = "rtklibOutputNmea"
        const val EXTRA_RTKLIB_OUTPUT_POS = "rtklibOutputPos"
        const val EXTRA_RTKLIB_MAX_ROVER_QUEUE_BYTES = "rtklibMaxRoverQueueBytes"
        const val EXTRA_RTKLIB_MAX_CORRECTION_QUEUE_BYTES = "rtklibMaxCorrectionQueueBytes"
        const val EXTRA_STORAGE_PROFILE_ID = "storageProfileId"
        const val EXTRA_STORAGE_KIND = "storageKind"
        const val EXTRA_STORAGE_TREE_URI = "storageTreeUri"
        const val EXTRA_RECORD_NTRIP_CORRECTION_INPUT = "recordNtripCorrectionInput"
        const val EXTRA_EXPORT_NMEA = "exportNmea"
        const val EXTRA_PPP_NMEA_GGA_QUALITY = "pppNmeaGgaQuality"
        const val EXTRA_EXPORT_JSON_SOLUTION = "exportJsonSolution"
        const val EXTRA_RECORD_REMOTE_BASE_RAW = "recordRemoteBaseRaw"
        const val EXTRA_ENABLE_MOCK_LOCATION = "enableMockLocation"
        const val EXTRA_MOCK_LOCATION_RATE_HZ = "mockLocationRateHz"
        const val EXTRA_SOLUTION_POLICY_PROFILE_ID = "solutionPolicyProfileId"
        const val EXTRA_SOLUTION_SCREEN_POLICY = "solutionScreenPolicy"
        const val EXTRA_SOLUTION_MOCK_POLICY = "solutionMockPolicy"
        const val EXTRA_COORDINATE_SOURCE = "coordinateSource"
        const val EXTRA_BASE_POSITION_JSON = "basePositionJson"
        const val EXTRA_BASE_COORDINATE_ID = "baseCoordinateId"
        const val EXTRA_BASE_COORDINATE_NAME = "baseCoordinateName"
        const val EXTRA_BASE_COORDINATE_METHOD = "baseCoordinateMethod"
        const val EXTRA_BASE_CASTER_UPLOAD_ENABLED = "baseCasterUploadEnabled"
        const val EXTRA_BASE_CASTER_UPLOAD_HOST = "baseCasterUploadHost"
        const val EXTRA_BASE_CASTER_UPLOAD_PORT = "baseCasterUploadPort"
        const val EXTRA_BASE_CASTER_UPLOAD_MOUNTPOINT = "baseCasterUploadMountpoint"
        const val EXTRA_BASE_CASTER_UPLOAD_USERNAME = "baseCasterUploadUsername"
        const val EXTRA_BASE_CASTER_UPLOAD_USERNAME_PRESENT = "baseCasterUploadUsernamePresent"
        const val EXTRA_BASE_CASTER_UPLOAD_SECRET_REF = "baseCasterUploadSecretRef"
        const val EXTRA_BASE_CASTER_UPLOAD_PASSWORD = "baseCasterUploadPassword"
        const val EXTRA_BASE_CASTER_UPLOAD_PROTOCOL_POLICY = "baseCasterUploadProtocolPolicy"
        const val EXTRA_BASE_CASTER_UPLOAD_FINAL_STATUS = "baseCasterUploadFinalStatus"
        const val EXTRA_VALIDATION_SUMMARY = "validationSummary"
        const val EXTRA_EXPECTED_ARTIFACTS = "expectedArtifacts"
        const val EXTRA_SETTINGS_SET_NAME = "settingsSetName"
        const val EXTRA_SETTINGS_COMMAND_PROFILE_NAME = "settingsCommandProfileName"
        const val EXTRA_SETTINGS_USB_BAUD_PROFILE_NAME = "settingsUsbBaudProfileName"
        const val EXTRA_SETTINGS_NTRIP_CASTER_PROFILE_NAME = "settingsNtripCasterProfileName"
        const val EXTRA_SETTINGS_RECORDING_OUTPUT_PROFILE_NAME = "settingsRecordingOutputProfileName"
        const val EXTRA_SETTINGS_STORAGE_PROFILE_NAME = "settingsStorageProfileName"

        const val EXTRA_STATE_RUNNING = "running"
        const val EXTRA_STATE_WORKFLOW_LABEL = "workflowLabel"
        const val EXTRA_STATE_RECEIVER_LABEL = "receiverLabel"
        const val EXTRA_STATE_RECEIVER_FAMILY = "receiverFamily"
        const val EXTRA_STATE_STORAGE_LABEL = "storageLabel"
        const val EXTRA_STATE_SESSION_PATH = "sessionPath"
        const val EXTRA_STATE_SETTINGS_SET_LABEL = "settingsSetLabel"
        const val EXTRA_STATE_SETTINGS_COMMAND_PROFILE_LABEL = "settingsCommandProfileLabel"
        const val EXTRA_STATE_SETTINGS_BAUD_PROFILE_LABEL = "settingsBaudProfileLabel"
        const val EXTRA_STATE_SETTINGS_NTRIP_CASTER_PROFILE_LABEL = "settingsNtripCasterProfileLabel"
        const val EXTRA_STATE_SETTINGS_RECORDING_OUTPUT_PROFILE_LABEL = "settingsRecordingOutputProfileLabel"
        const val EXTRA_STATE_SETTINGS_STORAGE_PROFILE_LABEL = "settingsStorageProfileLabel"
        const val EXTRA_STATE_RX_BYTES = "receiverRxBytes"
        const val EXTRA_STATE_TX_BYTES = "txToReceiverBytes"
        const val EXTRA_STATE_CORRECTION_BYTES = "correctionInputBytes"
        const val EXTRA_STATE_NMEA_BYTES = "nmeaBytes"
        const val EXTRA_STATE_SESSION_TOTAL_BYTES = "sessionTotalBytes"
        const val EXTRA_STATE_NTRIP = "ntripState"
        const val EXTRA_STATE_NTRIP_URL = "ntripUrl"
        const val EXTRA_STATE_NTRIP_TRANSFERRED = "ntripTransferred"
        const val EXTRA_STATE_NTRIP_RATES = "ntripRates"
        const val EXTRA_STATE_NTRIP_STATION_ID = "ntripStationId"
        const val EXTRA_STATE_NTRIP_BASE_LAT_LON = "ntripBaseLatLon"
        const val EXTRA_STATE_BASE_CASTER_UPLOAD_STATE = "baseCasterUploadState"
        const val EXTRA_STATE_BASE_CASTER_UPLOAD_URL = "baseCasterUploadUrl"
        const val EXTRA_STATE_BASE_CASTER_UPLOAD_BYTES = "baseCasterUploadBytes"
        const val EXTRA_STATE_BASE_CASTER_UPLOAD_DROPPED_BYTES = "baseCasterUploadDroppedBytes"
        const val EXTRA_STATE_BASE_CASTER_UPLOAD_LAST_ERROR = "baseCasterUploadLastError"
        const val EXTRA_STATE_RTKLIB_STATE = "rtklibState"
        const val EXTRA_STATE_RTKLIB_ROUTE_PLAN = "rtklibRoutePlan"
        const val EXTRA_STATE_RTKLIB_SNAPSHOT_ID = "rtklibSnapshotId"
        const val EXTRA_STATE_RTKLIB_LAST_ERROR = "rtklibLastError"
        const val EXTRA_STATE_RTKLIB_FIX_CLASS = "rtklibFixClass"
        const val EXTRA_STATE_RTKLIB_SOLUTION_AGE_MS = "rtklibSolutionAgeMs"
        const val EXTRA_STATE_RTKLIB_ROVER_QUEUE_BYTES = "rtklibRoverQueueBytes"
        const val EXTRA_STATE_RTKLIB_CORRECTION_QUEUE_BYTES = "rtklibCorrectionQueueBytes"
        const val EXTRA_STATE_RTKLIB_DROPPED_ROVER_BYTES = "rtklibDroppedRoverBytes"
        const val EXTRA_STATE_RTKLIB_DROPPED_CORRECTION_BYTES = "rtklibDroppedCorrectionBytes"
        const val EXTRA_STATE_RTKLIB_DECODED_ROVER_EPOCHS = "rtklibDecodedRoverEpochs"
        const val EXTRA_STATE_RTKLIB_DECODED_CORRECTION_MESSAGES = "rtklibDecodedCorrectionMessages"
        const val EXTRA_STATE_RTKLIB_OUTPUT_NMEA_LINES = "rtklibOutputNmeaLines"
        const val EXTRA_STATE_RTKLIB_OUTPUT_POS_LINES = "rtklibOutputPosLines"
        const val EXTRA_STATE_GGA_FIX_QUALITY = "ggaFixQuality"
        const val EXTRA_STATE_BESTNAV_POSITION_TYPE = "bestnavPositionType"
        const val EXTRA_STATE_PPP_STATUS = "pppStatus"
        const val EXTRA_STATE_RECEIVER_RTK_STATUS = "receiverRtkStatus"
        const val EXTRA_STATE_RTK_POSITION_TYPE = "rtkPositionType"
        const val EXTRA_STATE_RTK_CALCULATE_STATUS = "rtkCalculateStatus"
        const val EXTRA_STATE_RTK_CALCULATE_STATUS_DESCRIPTION = "rtkCalculateStatusDescription"
        const val EXTRA_STATE_RTCM_LAST_MESSAGE_ID = "rtcmLastMessageId"
        const val EXTRA_STATE_RTCM_LAST_BASE_ID = "rtcmLastBaseId"
        const val EXTRA_STATE_LAT_LON = "latLon"
        const val EXTRA_STATE_ELLIPSOIDAL_HEIGHT = "ellipsoidalHeight"
        const val EXTRA_STATE_ALTITUDE = "altitude"
        const val EXTRA_STATE_UTC_TIME = "utcTime"
        const val EXTRA_STATE_SATELLITES = "satellites"
        const val EXTRA_STATE_PDOP = "pdop"
        const val EXTRA_STATE_HDOP_VDOP = "hdopVdop"
        const val EXTRA_STATE_HORIZONTAL_ACCURACY = "horizontalAccuracy"
        const val EXTRA_STATE_LAT_ERROR = "latError"
        const val EXTRA_STATE_LON_ERROR = "lonError"
        const val EXTRA_STATE_VERTICAL_ACCURACY = "verticalAccuracy"
        const val EXTRA_STATE_DIFFERENTIAL_AGE = "differentialAge"
        const val EXTRA_STATE_BASELINE = "baseline"
        const val EXTRA_STATE_RTCM_FRAMES = "rtcmFrames"
        const val EXTRA_STATE_ERROR = "lastError"
        const val EXTRA_STATE_LIFECYCLE = "lifecycle"
        const val EXTRA_STATE_ERROR_CATEGORY = "errorCategory"
        const val EXTRA_STATE_ERROR_SEVERITY = "errorSeverity"
        const val EXTRA_STATE_RAW_ACTIVE = "rawRecordingActive"
        const val EXTRA_STATE_CORRECTIONS_ACTIVE = "correctionsActive"
        const val EXTRA_STATE_UM980_FREQUENCY = "um980Frequency"
        const val EXTRA_STATE_UM980_MODE = "um980Mode"
        const val EXTRA_STATE_BEST_SOLUTION_SOURCE = "bestSolutionSource"
        const val EXTRA_STATE_BEST_SOLUTION_FIX = "bestSolutionFix"
        const val EXTRA_STATE_MOCK_LOCATION_SOLUTION_AGE_MS = "mockLocationSolutionAgeMs"
        const val EXTRA_STATE_BASE_AVERAGE_SUMMARY = "baseAverageSummary"
        const val EXTRA_STATE_BASE_AVERAGE_WARNING = "baseAverageWarning"
        const val EXTRA_STATE_BASE_AVERAGE_ACTIVE = "baseAverageActive"
        const val EXTRA_STATE_BASE_AVERAGE_LAT = "baseAverageLatDeg"
        const val EXTRA_STATE_BASE_AVERAGE_LON = "baseAverageLonDeg"
        const val EXTRA_STATE_BASE_AVERAGE_HEIGHT = "baseAverageHeightM"
        const val EXTRA_STATE_BASE_AVERAGE_SAMPLE_COUNT = "baseAverageSampleCount"
        const val EXTRA_STATE_MOCK_LOCATION_STATE = "mockLocationState"
        const val EXTRA_STATE_MOCK_LOCATION_INTERVAL_MS = "mockLocationIntervalMs"
        const val EXTRA_STATE_MOCK_LOCATION_RATE_HZ = "mockLocationRateHz"
        const val EXTRA_STATE_UBLOX_FREQUENCY = "ubloxFrequency"

        private const val CHANNEL_ID = "rtkcollector-recording"
        private const val NOTIFICATION_ID = 101
        private const val NOTIFICATION_UPDATE_INTERVAL_MILLIS = 1_000L
        private const val READ_BUFFER_BYTES = 16 * 1024
        private const val ROUTINE_STATE_BROADCAST_INTERVAL_MILLIS = 250L
        internal const val DEFAULT_MOCK_LOCATION_PUBLISH_PERIOD_MILLIS = 1_000L
        private const val PROFILE_DRAIN_MILLIS = 2000L
        private const val SAVE_CONFIG_OK_TIMEOUT_MILLIS = 3_000L
        private const val DEFAULT_UM980_FREQUENCY_DISPLAY =
            "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM -/-/-/-/-/- Hz"
        private const val DEFAULT_UBLOX_FREQUENCY_DISPLAY =
            "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/GGA -/-/-/-/-/- Hz"
        private const val UBLOX_NAV_SAT_FRESH_MILLIS = 5_000L

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)

        fun mockLocationUpdateIntent(
            context: Context,
            enabled: Boolean,
            rateHz: Int,
        ): Intent =
            Intent(context, RecordingForegroundService::class.java)
                .setAction(ACTION_UPDATE_MOCK_LOCATION)
                .putExtra(EXTRA_ENABLE_MOCK_LOCATION, enabled)
                .putExtra(EXTRA_MOCK_LOCATION_RATE_HZ, sanitizeMockLocationRateHz(rateHz))
    }

    private data class OpenedRecordingSession(
        val displayPath: String,
        val writers: RecordingSessionWriters,
    )
}

private const val RTCM_STATUS_RECENT_MILLIS = 10_000L
private const val RTK_EVIDENCE_RECENT_MILLIS = 10_000L
private const val RTK_STALE_DIFFERENTIAL_AGE_SECONDS = 5.0

internal fun sanitizeMockLocationRateHz(rateHz: Int): Int =
    if (rateHz in RecordingPolicyProfile.ALLOWED_MOCK_LOCATION_RATES_HZ) {
        rateHz
    } else {
        RecordingPolicyProfile.DEFAULT_MOCK_LOCATION_RATE_HZ
    }

internal fun mockLocationPublishPeriodMillis(rateHz: Int): Long =
    RecordingForegroundService.DEFAULT_MOCK_LOCATION_PUBLISH_PERIOD_MILLIS / sanitizeMockLocationRateHz(rateHz)

private fun Intent.solutionSourcePolicyExtra(name: String): SolutionSourcePolicy =
    runCatching {
        SolutionSourcePolicy.valueOf(getStringExtra(name).orEmpty())
    }.getOrDefault(SolutionSourcePolicy.AUTO_BEST)

internal fun recordingReceiverFamily(receiverProfileId: String?, commandReceiverFamily: String?): String {
    val id = commandReceiverFamily?.takeIf { it.isNotBlank() } ?: receiverProfileId
    if (id.isNullOrBlank()) return ""
    val normalized = id.lowercase()
    return when {
        normalized.startsWith("ublox") -> "ublox"
        normalized.startsWith("um980") || normalized.startsWith("unicore") -> "um980"
        else -> normalized.substringBefore('-')
    }
}

internal fun sessionReceiverDriverId(receiverProfileId: String?, commandReceiverFamily: String?): String {
    val commandFamily = commandReceiverFamily?.takeIf { it.isNotBlank() }
    val receiverProfile = receiverProfileId?.takeIf { it.isNotBlank() }
    return commandFamily ?: receiverProfile ?: "unknown"
}

internal fun receiverRtkStatusAfterRtcmDecoded(
    previousStatus: String,
    lastReceiverRtkEvidenceAtMillis: Long?,
    nowMillis: Long,
): String {
    val hasRecentReceiverRtkEvidence = lastReceiverRtkEvidenceAtMillis
        ?.let { nowMillis - it < RTK_EVIDENCE_RECENT_MILLIS } == true
    return if (hasRecentReceiverRtkEvidence && previousStatus != "n/a" && previousStatus != "No RTCM") {
        previousStatus
    } else {
        "RTCM decoded"
    }
}

internal fun classifyReceiverRtkStatus(
    positionType: String?,
    solutionStatus: String?,
    calculateStatus: Int?,
    differentialAgeS: Double?,
    recentCorrectionInput: Boolean,
    recentReceiverRtcmDecoded: Boolean,
): String {
    if (differentialAgeS != null && differentialAgeS > RTK_STALE_DIFFERENTIAL_AGE_SECONDS) return "RTK stale"
    if (solutionStatus.equals("SOL_COMPUTED", ignoreCase = true)) {
        when (positionType?.uppercase()) {
            "NARROW_INT", "INS_RTKFIXED" -> return "RTK fixed"
            "NARROW_FLOAT", "IONOFREE_FLOAT", "L1_FLOAT", "INS_RTKFLOAT" -> return "RTK float"
        }
    }
    when (calculateStatus) {
        0 -> return when {
            recentReceiverRtcmDecoded -> "RTCM decoded, receiver pending"
            recentCorrectionInput -> "RTCM input, receiver pending"
            else -> "No RTCM"
        }
        1 -> return "Base obs insufficient"
        2 -> return "RTK stale"
        4 -> return "Rover obs insufficient"
    }
    if (solutionStatus == null && calculateStatus == 5) {
        when (positionType?.uppercase()) {
            "NARROW_INT", "INS_RTKFIXED" -> return "RTK fixed"
            "NARROW_FLOAT", "IONOFREE_FLOAT", "L1_FLOAT", "INS_RTKFLOAT" -> return "RTK float"
        }
    }
    return when (calculateStatus) {
        5 -> when {
            recentReceiverRtcmDecoded -> "RTCM decoded"
            recentCorrectionInput -> "RTCM input"
            else -> "n/a"
        }
        else -> when {
            recentReceiverRtcmDecoded -> "RTCM decoded"
            recentCorrectionInput -> "RTCM input"
            else -> "n/a"
        }
    }
}
