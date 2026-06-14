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
import org.rtkcollector.core.correction.NtripCredentials
import org.rtkcollector.core.correction.NtripReconnectPolicy
import org.rtkcollector.core.correction.NtripRequest
import org.rtkcollector.core.correction.NtripRuntimeConfig
import org.rtkcollector.core.correction.NtripRuntimeController
import org.rtkcollector.core.correction.NtripRuntimeSnapshot
import org.rtkcollector.core.correction.NtripRuntimeState
import org.rtkcollector.core.correction.Rtcm3Extractor
import org.rtkcollector.core.correction.Rtcm3ReferenceStation
import org.rtkcollector.core.correction.Rtcm3ReferenceStationParser
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
import org.rtkcollector.receiver.unicore.Um980PersistentBaudPlan
import org.rtkcollector.receiver.unicore.Um980PersistentBaudStep
import org.rtkcollector.receiver.unicore.Um980RuntimeCommandValidator
import org.rtkcollector.receiver.unicore.Um980StreamParser
import org.rtkcollector.receiver.unicore.Um980Telemetry
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
    private var activeWorkflowUsesNtrip: Boolean = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var shutdownCommands: List<String> = emptyList()
    private var state = RecordingServiceState()
    private val persistentWriteInProgress = AtomicBoolean(false)
    private val runtimeLock = Any()
    private val txLock = Any()
    private var ntripRateWindowStartedAtMillis: Long = 0L
    private var ntripRateWindowCorrectionBytes: Long = 0L
    private var ntripRateWindowTxBytes: Long = 0L
    private var lastNotificationUpdateAtMillis: Long = 0L
    private var lastNotificationText: String = ""

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
            ACTION_WRITE_PERSISTENT_RECEIVER_CONFIG -> writePersistentReceiverConfig(intent)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecording(sendShutdown = false)
        super.onDestroy()
    }

    private fun startRecording(intent: Intent) {
        if (!running.compareAndSet(false, true)) {
            return
        }
        stopping.set(false)
        shutdownSent.set(false)
        activeWorkflowUsesNtrip = false
        state = state.copy(
            lifecycle = RecordingLifecycleState.STARTING,
            errorCategory = RecordingErrorCategory.NONE,
            errorSeverity = RecordingErrorSeverity.NONE,
            lastError = null,
            receiverRxBytes = 0,
            txToReceiverBytes = 0,
            correctionInputBytes = 0,
            nmeaBytes = 0,
            ntripTransferred = "0 B",
            ntripRates = "n/a",
            rawRecordingActive = false,
            correctionsActive = false,
            receiverRtkStatus = "n/a",
            rtkPositionType = null,
            rtkCalculateStatus = null,
            rtkCalculateStatusDescription = null,
            receiverRtkEvidenceAtMillis = null,
            rtcmDecodedAtMillis = null,
            rtcmLastMessageId = null,
            rtcmLastBaseId = null,
            um980Frequency = DEFAULT_UM980_FREQUENCY_DISPLAY,
            um980Mode = "n/a",
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
                receiverDriverId = intent.getStringExtra(EXTRA_RECEIVER_PROFILE_ID) ?: "um980-n4",
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
                storageProfileId = intent.getStringExtra(EXTRA_STORAGE_PROFILE_ID),
                storageKind = intent.getStringExtra(EXTRA_STORAGE_KIND),
                coordinateSource = intent.getStringExtra(EXTRA_COORDINATE_SOURCE),
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
                    exportJsonSolution = intent.getBooleanExtra(EXTRA_EXPORT_JSON_SOLUTION, true),
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

            val initCommands = intent.getStringArrayListExtra(EXTRA_INIT_COMMANDS).orEmpty().validatedCommands()
            val baudSwitchCommands = intent.getStringArrayListExtra(EXTRA_BAUD_SWITCH_COMMANDS).orEmpty().validatedCommands()
            val modeCommands = intent.getStringArrayListExtra(EXTRA_MODE_COMMANDS).orEmpty().validatedCommands()
            shutdownCommands = intent.getStringArrayListExtra(EXTRA_SHUTDOWN_COMMANDS).orEmpty().validatedCommands()
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

            state = state.copy(
                running = true,
                lifecycle = RecordingLifecycleState.RECORDING,
                workflowLabel = intent.getStringExtra(EXTRA_WORKFLOW_NAME) ?: intent.getStringExtra(EXTRA_WORKFLOW_ID) ?: "n/a",
                receiverLabel = intent.getStringExtra(EXTRA_RECEIVER_PROFILE_ID) ?: "n/a",
                storageLabel = intent.getStringExtra(EXTRA_STORAGE_PROFILE_ID) ?: intent.getStringExtra(EXTRA_STORAGE_KIND) ?: "n/a",
                settingsSetLabel = intent.getStringExtra(EXTRA_SETTINGS_SET_NAME) ?: "n/a",
                settingsCommandProfileLabel = intent.getStringExtra(EXTRA_SETTINGS_COMMAND_PROFILE_NAME) ?: "n/a",
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
            )
            ntripRateWindowStartedAtMillis = System.currentTimeMillis()
            ntripRateWindowCorrectionBytes = 0L
            ntripRateWindowTxBytes = 0L
            activeWorkflowUsesNtrip = workflowUsesNtrip
            broadcastState()

            captureThread = Thread({ captureLoop(recorder) }, "rtkcollector-capture").also { it.start() }
            maybeStartNtrip(intent, recorder)
        } catch (error: Throwable) {
            state = state.copy(
                running = false,
                lifecycle = RecordingLifecycleState.FAILED,
                lastError = error.message,
                errorCategory = classifyStartError(error),
                errorSeverity = RecordingErrorSeverity.FATAL,
                rawRecordingActive = false,
                correctionsActive = false,
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
                    ntripTransferred = bytesDisplay(recorder.correctionInputBytes),
                    rawRecordingActive = true,
                ).clearRecoverableUsbError()
                broadcastState()
                updateForegroundNotification()
            }.onFailure { error ->
                state = state.copy(
                    lastError = error.message,
                    errorCategory = RecordingErrorCategory.USB,
                    errorSeverity = RecordingErrorSeverity.DEGRADED,
                    rawRecordingActive = false,
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
        startNtripController(config, recorder)
    }

    private fun startNtripController(
        config: NtripRuntimeConfig,
        recorder: SessionRawRecorder,
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
                    val txResult = runCatching {
                        synchronized(txLock) {
                            runtime?.sendToReceiver(bytes) ?: error("USB runtime is not available.")
                        }
                    }
                    ntripRateWindowCorrectionBytes += bytes.size
                    if (txResult.isSuccess) {
                        ntripRateWindowTxBytes += bytes.size
                    }
                    val updatedState = state.copy(
                        correctionInputBytes = recorder.correctionInputBytes,
                        nmeaBytes = recorder.nmeaBytes,
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
        rtcmExtractor.accept(bytes).forEach { frame ->
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
        broadcastState()
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
            startNtripController(config, recorder)
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

    private fun writePersistentReceiverConfig(intent: Intent) {
        if (!running.get()) {
            state = state.copy(
                lastError = "Cannot write receiver configuration: recording service is not connected.",
                errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
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
        state = state.copy(lifecycle = RecordingLifecycleState.STOPPING, running = false)
        broadcastState()
        if (sendShutdown && shutdownSent.compareAndSet(false, true)) {
            runCatching {
                runtime?.let { captureRuntime ->
                    synchronized(txLock) {
                        sendCommandLines(captureRuntime, shutdownCommands)
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
        runCatching { ntripController?.stop() }
        runCatching { captureThread?.join(1500) }
        runCatching { advisoryFanout?.close() }
        synchronized(runtimeLock) {
            runCatching {
                activeSessionMetadata
                    ?.copy(stoppedAt = Instant.now().toString())
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
        activeWorkflowUsesNtrip = false
        shutdownCommands = emptyList()
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
        )
        stopping.set(false)
        broadcastState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendCommandLines(captureRuntime: CaptureRuntime, commands: List<String>) {
        commands.asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { command ->
                synchronized(txLock) {
                    captureRuntime.sendToReceiver("$command\r\n".toByteArray(Charsets.US_ASCII))
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

    private fun List<String>.validatedCommands(): List<String> =
        asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .onEach(Um980RuntimeCommandValidator::validateRuntimeCommand)
            .toList()

    private fun validateBaud(value: Int, label: String): Int {
        require(value in 9600..921600) { "$label must be between 9600 and 921600." }
        return value
    }

    private fun validatePort(value: Int): Int {
        require(value in 1..65535) { "NTRIP port must be between 1 and 65535." }
        return value
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

    private fun broadcastState() {
        sendBroadcast(
            Intent(ACTION_STATE).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATE_RUNNING, state.running)
                putExtra(EXTRA_STATE_WORKFLOW_LABEL, state.workflowLabel)
                putExtra(EXTRA_STATE_RECEIVER_LABEL, state.receiverLabel)
                putExtra(EXTRA_STATE_STORAGE_LABEL, state.storageLabel)
                putExtra(EXTRA_STATE_SESSION_PATH, state.sessionPath)
                putExtra(EXTRA_STATE_RX_BYTES, state.receiverRxBytes)
                putExtra(EXTRA_STATE_TX_BYTES, state.txToReceiverBytes)
                putExtra(EXTRA_STATE_CORRECTION_BYTES, state.correctionInputBytes)
                putExtra(EXTRA_STATE_NMEA_BYTES, state.nmeaBytes)
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
            },
        )
    }

    private fun recordBinaryFrequency(frame: ByteArray, frequencyTracker: Um980MessageFrequencyTracker) {
        when (Um980BinaryParser.messageId(frame)) {
            12, 138 -> frequencyTracker.record(Um980MessageKind.OBSVM, System.currentTimeMillis())
            142 -> frequencyTracker.record(Um980MessageKind.ADRNAV, System.currentTimeMillis())
            509 -> frequencyTracker.record(Um980MessageKind.RTKSTATUS, System.currentTimeMillis())
            1026 -> frequencyTracker.record(Um980MessageKind.PPPNAV, System.currentTimeMillis())
            2118 -> frequencyTracker.record(Um980MessageKind.BESTNAV, System.currentTimeMillis())
        }
    }

    private fun buildAdvisoryFanout(
        sessionWriters: RecordingSessionWriters,
        eventSink: CaptureEventSink,
        exportNmea: Boolean,
        exportJsonSolution: Boolean,
    ): AdvisoryFanout {
        val ggaParser = NmeaGgaParser()
        val gsaParser = NmeaGsaParser()
        val gstParser = NmeaGstParser()
        val gsvParser = NmeaGsvParser()
        val gsvTracker = NmeaGsvInViewTracker()
        val nmeaExporter = NmeaSentenceExporter()
        val solutionParser = Um980AsciiSolutionParser()
        val streamParser = Um980StreamParser()
        val frequencyTracker = Um980MessageFrequencyTracker()
        val rtcmExtractor = Rtcm3Extractor(validateCrc = true)
        return AdvisoryFanout(
            eventSink = eventSink,
            consumers = listOf(
                AdvisoryConsumer("um980-mixed-stream") { bytes ->
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
                                                pppStatus = solution.positionType ?: state.pppStatus,
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
                                        Um980NmeaExporter.export(telemetry).forEach { sentence ->
                                            sessionWriters.appendReceiverSolutionNmea(sentence)
                                            activeRecorder?.recordNmeaBytes(sentence.toByteArray(Charsets.US_ASCII).size)
                                        }
                                    }
                                    state = state.withUm980Telemetry(telemetry)
                                        .withReceiverRtkTelemetry(telemetry)
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
                                        pppStatus = telemetry.positionType ?: state.pppStatus,
                                    )
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
                        state = state.copy(um980Frequency = frequencyTracker.display(System.currentTimeMillis()))
                    }
                },
                AdvisoryConsumer("rtcm3-extractor") { bytes ->
                    rtcmExtractor.accept(bytes).forEach { frame ->
                        if (frame.crcValid != false) {
                            sessionWriters.appendExtractedRtcm(frame.bytes)
                        }
                        sessionWriters.appendQualityLiveJson(
                            """{"type":"rtcm3-frame","payloadLength":${frame.payloadLength},"messageType":${frame.messageType ?: "null"},"crcValid":${frame.crcValid ?: "null"}}""",
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
            recentRtcmDecoded = hasRecentRtcmDecoded(),
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
            recentRtcmDecoded = hasRecentRtcmDecoded(),
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

    private class NmeaSentenceExporter {
        private val lineBuffer = StringBuilder()

        fun accept(bytes: ByteArray): List<String> {
            val sentences = mutableListOf<String>()
            bytes.toString(Charsets.US_ASCII).forEach { character ->
                when (character) {
                    '\n' -> {
                        val line = lineBuffer.toString().trim()
                        if (line.startsWith("$") && line.length >= 6) {
                            sentences += "$line\r\n"
                        }
                        lineBuffer.clear()
                    }
                    '\r' -> Unit
                    else -> lineBuffer.append(character)
                }
            }
            return sentences
        }
    }

    companion object {
        const val ACTION_START = "org.rtkcollector.app.recording.START"
        const val ACTION_STOP = "org.rtkcollector.app.recording.STOP"
        const val ACTION_QUERY = "org.rtkcollector.app.recording.QUERY"
        const val ACTION_UPDATE_NTRIP = "org.rtkcollector.app.recording.UPDATE_NTRIP"
        const val ACTION_DISABLE_NTRIP = "org.rtkcollector.app.recording.DISABLE_NTRIP"
        const val ACTION_WRITE_PERSISTENT_RECEIVER_CONFIG =
            "org.rtkcollector.app.recording.WRITE_PERSISTENT_RECEIVER_CONFIG"
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
        const val EXTRA_NTRIP_STATION_ID = "ntripStationId"
        const val EXTRA_NTRIP_BASE_LAT = "ntripBaseLat"
        const val EXTRA_NTRIP_BASE_LON = "ntripBaseLon"
        const val EXTRA_WORKFLOW_ID = "workflowId"
        const val EXTRA_WORKFLOW_NAME = "workflowName"
        const val EXTRA_RECEIVER_ROLE = "receiverRole"
        const val EXTRA_RECEIVER_PROFILE_ID = "receiverProfileId"
        const val EXTRA_UM980_PROFILE_ID = "um980ProfileId"
        const val EXTRA_COMMAND_PROFILE_ID = "commandProfileId"
        const val EXTRA_USB_BAUD_PROFILE_ID = "usbBaudProfileId"
        const val EXTRA_NTRIP_CASTER_PROFILE_ID = "ntripCasterProfileId"
        const val EXTRA_NTRIP_MOUNTPOINT_PROFILE_ID = "ntripMountpointProfileId"
        const val EXTRA_RECORDING_POLICY_ID = "recordingPolicyId"
        const val EXTRA_STORAGE_PROFILE_ID = "storageProfileId"
        const val EXTRA_STORAGE_KIND = "storageKind"
        const val EXTRA_STORAGE_TREE_URI = "storageTreeUri"
        const val EXTRA_RECORD_NTRIP_CORRECTION_INPUT = "recordNtripCorrectionInput"
        const val EXTRA_EXPORT_NMEA = "exportNmea"
        const val EXTRA_EXPORT_JSON_SOLUTION = "exportJsonSolution"
        const val EXTRA_RECORD_REMOTE_BASE_RAW = "recordRemoteBaseRaw"
        const val EXTRA_COORDINATE_SOURCE = "coordinateSource"
        const val EXTRA_BASE_POSITION_JSON = "basePositionJson"
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
        const val EXTRA_STATE_NTRIP = "ntripState"
        const val EXTRA_STATE_NTRIP_URL = "ntripUrl"
        const val EXTRA_STATE_NTRIP_TRANSFERRED = "ntripTransferred"
        const val EXTRA_STATE_NTRIP_RATES = "ntripRates"
        const val EXTRA_STATE_NTRIP_STATION_ID = "ntripStationId"
        const val EXTRA_STATE_NTRIP_BASE_LAT_LON = "ntripBaseLatLon"
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

        private const val CHANNEL_ID = "rtkcollector-recording"
        private const val NOTIFICATION_ID = 101
        private const val NOTIFICATION_UPDATE_INTERVAL_MILLIS = 1_000L
        private const val READ_BUFFER_BYTES = 16 * 1024
        private const val PROFILE_DRAIN_MILLIS = 2000L
        private const val SAVE_CONFIG_OK_TIMEOUT_MILLIS = 3_000L
        private const val DEFAULT_UM980_FREQUENCY_DISPLAY =
            "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM -/-/-/-/-/- Hz"

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)
    }

    private data class OpenedRecordingSession(
        val displayPath: String,
        val writers: RecordingSessionWriters,
    )
}

private const val RTCM_STATUS_RECENT_MILLIS = 10_000L
private const val RTK_EVIDENCE_RECENT_MILLIS = 10_000L
private const val RTK_STALE_DIFFERENTIAL_AGE_SECONDS = 5.0

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
    recentRtcmDecoded: Boolean,
): String {
    if (differentialAgeS != null && differentialAgeS > RTK_STALE_DIFFERENTIAL_AGE_SECONDS) return "RTK stale"
    when (calculateStatus) {
        0 -> return "No RTCM"
        1 -> return "Base obs insufficient"
        2 -> return "RTK stale"
        4 -> return "Rover obs insufficient"
    }
    if (solutionStatus.equals("SOL_COMPUTED", ignoreCase = true)) {
        when (positionType?.uppercase()) {
            "NARROW_INT", "INS_RTKFIXED" -> return "RTK fixed"
            "NARROW_FLOAT", "IONOFREE_FLOAT", "L1_FLOAT", "INS_RTKFLOAT" -> return "RTK float"
        }
    }
    if (solutionStatus == null && calculateStatus == 5) {
        when (positionType?.uppercase()) {
            "NARROW_INT", "INS_RTKFIXED" -> return "RTK fixed"
            "NARROW_FLOAT", "IONOFREE_FLOAT", "L1_FLOAT", "INS_RTKFLOAT" -> return "RTK float"
        }
    }
    return when (calculateStatus) {
        5 -> if (recentRtcmDecoded) "RTCM decoded" else "n/a"
        else -> if (recentRtcmDecoded) "RTCM decoded" else "n/a"
    }
}
