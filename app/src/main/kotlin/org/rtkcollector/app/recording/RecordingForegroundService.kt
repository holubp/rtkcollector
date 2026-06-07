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
import org.rtkcollector.core.capture.CaptureEvent
import org.rtkcollector.core.capture.CaptureEventSink
import org.rtkcollector.core.capture.CaptureRuntime
import org.rtkcollector.core.capture.RawRecorder
import org.rtkcollector.core.correction.CorrectionStatus
import org.rtkcollector.core.correction.NtripClient
import org.rtkcollector.core.correction.NtripCredentials
import org.rtkcollector.core.correction.NtripReconnectPolicy
import org.rtkcollector.core.correction.NtripRequest
import org.rtkcollector.core.session.SessionWriters
import org.rtkcollector.core.session.AntennaMetadata
import org.rtkcollector.core.session.NtripSessionMetadata
import org.rtkcollector.core.session.SerialParameters
import org.rtkcollector.core.session.SessionMetadata
import org.rtkcollector.core.session.SessionMode
import org.rtkcollector.core.session.exportSessionMetadata
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
                        receiverDriverId = "um980-n4",
                        receiverIdentification = null,
                        usbVid = usbDevice.vendorId,
                        usbPid = usbDevice.productId,
                        baudRate = serialBaud,
                        serialParameters = SerialParameters(),
                        mode = SessionMode.ROVER,
                        startedAt = startedAt,
                        stoppedAt = null,
                        ntrip = ntripSessionMetadata(intent),
                        antenna = AntennaMetadata(),
                        sessionUuid = sessionUuid,
                        linkedBaseSessionUuid = null,
                    ),
                ),
            )
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
                putExtra(EXTRA_STATE_ERROR, state.lastError)
            },
        )
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
        const val EXTRA_NTRIP_GGA = "ntripGga"

        const val EXTRA_STATE_RUNNING = "running"
        const val EXTRA_STATE_SESSION_PATH = "sessionPath"
        const val EXTRA_STATE_RX_BYTES = "receiverRxBytes"
        const val EXTRA_STATE_TX_BYTES = "txToReceiverBytes"
        const val EXTRA_STATE_CORRECTION_BYTES = "correctionInputBytes"
        const val EXTRA_STATE_NTRIP = "ntripState"
        const val EXTRA_STATE_ERROR = "lastError"

        private const val CHANNEL_ID = "rtkcollector-recording"
        private const val NOTIFICATION_ID = 101
        private const val READ_BUFFER_BYTES = 16 * 1024
        private const val PROFILE_DRAIN_MILLIS = 2000L

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)
    }
}
