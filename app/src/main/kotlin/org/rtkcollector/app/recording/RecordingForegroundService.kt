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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.rtkcollector.app.MainActivity
import org.rtkcollector.app.usb.AndroidUsbSerialTransport
import org.rtkcollector.app.usb.UsbSerialOpenOptions
import org.rtkcollector.core.capture.AdvisoryConsumer
import org.rtkcollector.core.capture.AdvisoryFanout
import org.rtkcollector.core.capture.CaptureEvent
import org.rtkcollector.core.capture.CaptureEventSink
import org.rtkcollector.core.capture.CaptureRuntime
import org.rtkcollector.core.capture.RawRecorder
import org.rtkcollector.core.correction.CorrectionStatus
import org.rtkcollector.core.correction.NtripClient
import org.rtkcollector.core.correction.NtripCredentials
import org.rtkcollector.core.correction.NtripReconnectPolicy
import org.rtkcollector.core.correction.NtripRequest
import org.rtkcollector.core.correction.Rtcm3Extractor
import org.rtkcollector.core.session.SessionWriters
import org.rtkcollector.core.session.AntennaMetadata
import org.rtkcollector.core.session.NtripSessionMetadata
import org.rtkcollector.core.session.SerialParameters
import org.rtkcollector.core.session.SessionMetadata
import org.rtkcollector.core.session.SessionMode
import org.rtkcollector.core.session.exportSessionMetadata
import org.rtkcollector.receiver.unicore.NmeaGgaFix
import org.rtkcollector.receiver.unicore.NmeaGgaParser
import org.rtkcollector.receiver.unicore.Um980AsciiSolution
import org.rtkcollector.receiver.unicore.Um980AsciiSolutionParser
import org.rtkcollector.receiver.unicore.Um980RuntimeCommandValidator
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RecordingForegroundService : Service() {
    private val running = AtomicBoolean(false)
    private var captureThread: Thread? = null
    private var ntripThread: Thread? = null
    private var runtime: CaptureRuntime? = null
    private var transport: AndroidUsbSerialTransport? = null
    private var writers: SessionWriters? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var shutdownCommands: List<String> = emptyList()
    private var state = RecordingServiceState()
    private val runtimeLock = Any()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording(sendShutdown = true)
            ACTION_QUERY -> broadcastState()
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

        startForeground(NOTIFICATION_ID, notification("Starting recording"))
        acquireWakeLock()

        try {
            val usbDevice = intent.usbDevice() ?: error("No USB device supplied.")
            val usbManager = getSystemService(USB_SERVICE) as UsbManager
            if (!usbManager.hasPermission(usbDevice)) {
                error("USB permission has not been granted.")
            }

            val serialBaud = validateBaud(intent.getIntExtra(EXTRA_SERIAL_BAUD, 230400), "serial baud")
            val profileBaud = validateBaud(intent.getIntExtra(EXTRA_PROFILE_BAUD, serialBaud), "profile baud")
            val sessionDirectory = createSessionDirectory()
            val sessionWriters = SessionWriters.open(sessionDirectory)
            val startedAt = Instant.now().toString()
            val sessionUuid = UUID.randomUUID().toString()
            sessionWriters.writeSessionJson(
                exportSessionMetadata(
                    SessionMetadata(
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
                        coordinateSource = intent.getStringExtra(EXTRA_COORDINATE_SOURCE),
                        validationSummary = intent.getStringExtra(EXTRA_VALIDATION_SUMMARY),
                        expectedArtifacts = intent.getStringArrayListExtra(EXTRA_EXPECTED_ARTIFACTS).orEmpty(),
                    ),
                ),
            )
            intent.getStringExtra(EXTRA_BASE_POSITION_JSON)
                ?.takeIf { it.isNotBlank() }
                ?.let(sessionWriters::writeBasePositionJson)
            val recorder = SessionRawRecorder(sessionWriters)
            val eventSink = SessionEventSink(sessionWriters)
            val usbTransport = AndroidUsbSerialTransport(
                usbManager = usbManager,
                device = usbDevice,
                options = UsbSerialOpenOptions(profileBaud),
            )
            usbTransport.open()

            val captureRuntime = CaptureRuntime(
                transport = usbTransport,
                recorder = recorder,
                eventSink = eventSink,
                advisoryFanout = buildAdvisoryFanout(sessionWriters, eventSink),
            )
            captureRuntime.open()

            val startupCommands = intent.getStringArrayListExtra(EXTRA_STARTUP_COMMANDS).orEmpty().validatedCommands()
            shutdownCommands = intent.getStringArrayListExtra(EXTRA_SHUTDOWN_COMMANDS).orEmpty().validatedCommands()
            sendCommandLines(captureRuntime, startupCommands)
            if (profileBaud != serialBaud) {
                usbTransport.reconfigureBaud(serialBaud)
            }
            drainAfterProfile(usbTransport)

            writers = sessionWriters
            transport = usbTransport
            runtime = captureRuntime
            state = state.copy(running = true, sessionPath = sessionDirectory.toString(), ntripState = "Not configured")
            broadcastState()

            captureThread = Thread({ captureLoop(captureRuntime, recorder) }, "rtkcollector-capture").also { it.start() }
            maybeStartNtrip(intent, captureRuntime, recorder)
        } catch (error: Throwable) {
            state = state.copy(running = false, lastError = error.message)
            broadcastState()
            stopRecording(sendShutdown = false)
        }
    }

    private fun captureLoop(captureRuntime: CaptureRuntime, recorder: SessionRawRecorder) {
        while (running.get()) {
            runCatching {
                synchronized(runtimeLock) {
                    captureRuntime.readOnce(READ_BUFFER_BYTES)
                }
                state = state.copy(
                    running = true,
                    receiverRxBytes = recorder.receiverRxBytes,
                    txToReceiverBytes = recorder.txToReceiverBytes,
                    correctionInputBytes = recorder.correctionInputBytes,
                )
                broadcastState()
            }.onFailure { error ->
                state = state.copy(lastError = error.message)
                broadcastState()
                Thread.sleep(250)
            }
        }
    }

    private fun maybeStartNtrip(
        intent: Intent,
        captureRuntime: CaptureRuntime,
        recorder: SessionRawRecorder,
    ) {
        val host = intent.getStringExtra(EXTRA_NTRIP_HOST).orEmpty()
        val mountpoint = intent.getStringExtra(EXTRA_NTRIP_MOUNTPOINT).orEmpty()
        if (host.isBlank() || mountpoint.isBlank()) {
            return
        }

        val request = NtripRequest(
            host = host,
            port = validatePort(intent.getIntExtra(EXTRA_NTRIP_PORT, 2101)),
            mountpoint = mountpoint,
            credentials = intent.getStringExtra(EXTRA_NTRIP_USERNAME)?.takeIf { it.isNotBlank() }?.let { username ->
                NtripCredentials(username = username, password = intent.getStringExtra(EXTRA_NTRIP_PASSWORD).orEmpty())
            },
        )
        val ggaLine = intent.getStringExtra(EXTRA_NTRIP_GGA)?.takeIf { it.isNotBlank() }
        ntripThread = Thread(
            {
                NtripClient(
                    request = request,
                    reconnectPolicy = NtripReconnectPolicy(maxAttempts = Int.MAX_VALUE, delayMillis = 5_000),
                ).runWithReconnect(
                    ggaLines = listOfNotNull(ggaLine),
                    onState = { correctionStatus: CorrectionStatus ->
                        state = state.copy(ntripState = correctionStatus.state.name, lastError = correctionStatus.lastError)
                        broadcastState()
                    },
                    onRtcmBytes = { bytes ->
                        if (running.get()) {
                            synchronized(runtimeLock) {
                                if (running.get()) {
                                    captureRuntime.injectCorrectionBytes(bytes)
                                    state = state.copy(
                                        correctionInputBytes = recorder.correctionInputBytes,
                                        txToReceiverBytes = recorder.txToReceiverBytes,
                                    )
                                }
                            }
                            broadcastState()
                        }
                    },
                )
            },
            "rtkcollector-ntrip",
        ).also { it.start() }
    }

    private fun stopRecording(sendShutdown: Boolean) {
        if (!running.getAndSet(false) && runtime == null) {
            return
        }
        if (sendShutdown) {
            runCatching {
                runtime?.let { captureRuntime ->
                    synchronized(runtimeLock) {
                        sendCommandLines(captureRuntime, shutdownCommands)
                    }
                }
            }
        }
        runCatching { ntripThread?.interrupt() }
        runCatching { ntripThread?.join(1500) }
        runCatching { captureThread?.join(1500) }
        synchronized(runtimeLock) {
            runCatching { runtime?.close() }
            runCatching { writers?.flush() }
            runCatching { writers?.close() }
        }
        runtime = null
        transport = null
        writers = null
        shutdownCommands = emptyList()
        releaseWakeLock()
        state = state.copy(running = false)
        broadcastState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendCommandLines(captureRuntime: CaptureRuntime, commands: List<String>) {
        commands.asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { command ->
                captureRuntime.sendToReceiver("$command\r\n".toByteArray(Charsets.US_ASCII))
                Thread.sleep(100)
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

    private fun drainAfterProfile(usbTransport: AndroidUsbSerialTransport) {
        val deadline = System.currentTimeMillis() + PROFILE_DRAIN_MILLIS
        while (System.currentTimeMillis() < deadline) {
            usbTransport.readAvailable(READ_BUFFER_BYTES)
        }
    }

    private fun createSessionDirectory(): Path {
        val root = getExternalFilesDir("sessions") ?: filesDir.resolve("sessions")
        val directory = root.resolve("session-${Instant.now().toString().replace(':', '-')}-${UUID.randomUUID()}")
        return directory.toPath()
    }

    private fun ntripSessionMetadata(intent: Intent): NtripSessionMetadata? {
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

    private fun broadcastState() {
        sendBroadcast(
            Intent(ACTION_STATE).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATE_RUNNING, state.running)
                putExtra(EXTRA_STATE_SESSION_PATH, state.sessionPath)
                putExtra(EXTRA_STATE_RX_BYTES, state.receiverRxBytes)
                putExtra(EXTRA_STATE_TX_BYTES, state.txToReceiverBytes)
                putExtra(EXTRA_STATE_CORRECTION_BYTES, state.correctionInputBytes)
                putExtra(EXTRA_STATE_NTRIP, state.ntripState)
                putExtra(EXTRA_STATE_GGA_FIX_QUALITY, state.ggaFixQuality ?: -1)
                putExtra(EXTRA_STATE_BESTNAV_POSITION_TYPE, state.bestnavPositionType)
                putExtra(EXTRA_STATE_PPP_STATUS, state.pppStatus)
                putExtra(EXTRA_STATE_RTCM_FRAMES, state.rtcmFrames)
                putExtra(EXTRA_STATE_ERROR, state.lastError)
            },
        )
    }

    private fun buildAdvisoryFanout(
        sessionWriters: SessionWriters,
        eventSink: CaptureEventSink,
    ): AdvisoryFanout {
        val ggaParser = NmeaGgaParser()
        val solutionParser = Um980AsciiSolutionParser()
        val rtcmExtractor = Rtcm3Extractor(validateCrc = true)
        return AdvisoryFanout(
            eventSink = eventSink,
            consumers = listOf(
                AdvisoryConsumer("nmea-gga") { bytes ->
                    ggaParser.accept(bytes).forEach { fix ->
                        sessionWriters.appendReceiverSolutionJson(fix.toJson())
                        state = state.copy(ggaFixQuality = fix.fixQuality)
                    }
                },
                AdvisoryConsumer("um980-ascii-solution") { bytes ->
                    solutionParser.accept(bytes).forEach { solution ->
                        if (solution.logName.startsWith("PPP")) {
                            sessionWriters.appendReceiverPppSolutionJson(solution.toJson())
                            state = state.copy(pppStatus = solution.positionType)
                        } else {
                            sessionWriters.appendReceiverSolutionJson(solution.toJson())
                            state = state.copy(bestnavPositionType = solution.positionType)
                        }
                    }
                },
                AdvisoryConsumer("rtcm3-extractor") { bytes ->
                    rtcmExtractor.accept(bytes).forEach { frame ->
                        sessionWriters.appendExtractedRtcm(frame.bytes)
                        sessionWriters.appendQualityLiveJson(
                            """{"type":"rtcm3-frame","payloadLength":${frame.payloadLength},"messageType":${frame.messageType ?: "null"},"crcValid":${frame.crcValid ?: "null"}}""",
                        )
                        state = state.copy(rtcmFrames = state.rtcmFrames + 1)
                    }
                },
            ),
        )
    }

    private fun NmeaGgaFix.toJson(): String =
        """{"type":"nmea-gga","talker":"${talker.jsonEscape()}","utcTime":"${utcTime.jsonEscape()}","latDeg":${latDeg ?: "null"},"lonDeg":${lonDeg ?: "null"},"fixQuality":${fixQuality ?: "null"},"satelliteCount":${satelliteCount ?: "null"},"hdop":${hdop ?: "null"},"altitudeM":${altitudeM ?: "null"}}"""

    private fun Um980AsciiSolution.toJson(): String =
        """{"type":"um980-ascii-solution","logName":"${logName.jsonEscape()}","solutionStatus":${solutionStatus.jsonStringOrNull()},"positionType":${positionType.jsonStringOrNull()},"latDeg":${latDeg ?: "null"},"lonDeg":${lonDeg ?: "null"},"heightM":${heightM ?: "null"}}"""

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

    private class SessionRawRecorder(private val writers: SessionWriters) : RawRecorder {
        var receiverRxBytes: Long = 0
            private set
        var txToReceiverBytes: Long = 0
            private set
        var correctionInputBytes: Long = 0
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
            writers.appendCorrectionInput(bytes)
            correctionInputBytes += bytes.size
        }

        override fun close() {
            writers.flush()
        }
    }

    private class SessionEventSink(private val writers: SessionWriters) : CaptureEventSink {
        override fun recordEvent(event: CaptureEvent) {
            val json = """{"timestamp":"${event.timestamp}","type":"${event.type}","message":"${event.message.replace("\"", "\\\"")}"}"""
            writers.appendEventJson(json)
        }
    }

    companion object {
        const val ACTION_START = "org.rtkcollector.app.recording.START"
        const val ACTION_STOP = "org.rtkcollector.app.recording.STOP"
        const val ACTION_QUERY = "org.rtkcollector.app.recording.QUERY"
        const val ACTION_STATE = "org.rtkcollector.app.recording.STATE"

        const val EXTRA_USB_DEVICE = "usbDevice"
        const val EXTRA_SERIAL_BAUD = "serialBaud"
        const val EXTRA_PROFILE_BAUD = "profileBaud"
        const val EXTRA_STARTUP_COMMANDS = "startupCommands"
        const val EXTRA_SHUTDOWN_COMMANDS = "shutdownCommands"
        const val EXTRA_NTRIP_HOST = "ntripHost"
        const val EXTRA_NTRIP_PORT = "ntripPort"
        const val EXTRA_NTRIP_MOUNTPOINT = "ntripMountpoint"
        const val EXTRA_NTRIP_USERNAME = "ntripUsername"
        const val EXTRA_NTRIP_PASSWORD = "ntripPassword"
        const val EXTRA_NTRIP_SECRET_REF = "ntripSecretRef"
        const val EXTRA_NTRIP_GGA = "ntripGga"
        const val EXTRA_WORKFLOW_ID = "workflowId"
        const val EXTRA_WORKFLOW_NAME = "workflowName"
        const val EXTRA_RECEIVER_ROLE = "receiverRole"
        const val EXTRA_RECEIVER_PROFILE_ID = "receiverProfileId"
        const val EXTRA_UM980_PROFILE_ID = "um980ProfileId"
        const val EXTRA_COORDINATE_SOURCE = "coordinateSource"
        const val EXTRA_BASE_POSITION_JSON = "basePositionJson"
        const val EXTRA_VALIDATION_SUMMARY = "validationSummary"
        const val EXTRA_EXPECTED_ARTIFACTS = "expectedArtifacts"

        const val EXTRA_STATE_RUNNING = "running"
        const val EXTRA_STATE_SESSION_PATH = "sessionPath"
        const val EXTRA_STATE_RX_BYTES = "receiverRxBytes"
        const val EXTRA_STATE_TX_BYTES = "txToReceiverBytes"
        const val EXTRA_STATE_CORRECTION_BYTES = "correctionInputBytes"
        const val EXTRA_STATE_NTRIP = "ntripState"
        const val EXTRA_STATE_GGA_FIX_QUALITY = "ggaFixQuality"
        const val EXTRA_STATE_BESTNAV_POSITION_TYPE = "bestnavPositionType"
        const val EXTRA_STATE_PPP_STATUS = "pppStatus"
        const val EXTRA_STATE_RTCM_FRAMES = "rtcmFrames"
        const val EXTRA_STATE_ERROR = "lastError"

        private const val CHANNEL_ID = "rtkcollector-recording"
        private const val NOTIFICATION_ID = 101
        private const val READ_BUFFER_BYTES = 16 * 1024
        private const val PROFILE_DRAIN_MILLIS = 2000L

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)
    }
}
