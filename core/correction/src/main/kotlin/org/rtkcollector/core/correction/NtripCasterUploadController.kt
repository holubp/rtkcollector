package org.rtkcollector.core.correction

import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class NtripCasterUploadRuntimeConfig(
    val request: NtripCasterUploadRequest,
    val reconnectDelayMillis: Long = 5_000,
) {
    init {
        require(reconnectDelayMillis >= 0) { "Reconnect delay must not be negative." }
    }

    val mountpointUrl: String
        get() = "${request.host}:${request.port}/${request.mountpoint.trimStart('/')}"
}

data class NtripCasterUploadSnapshot(
    val state: String,
    val bytesUploaded: Long,
    val bytesDropped: Long,
    val lastError: String?,
    val mountpointUrl: String,
)

class NtripCasterUploadController(
    private val capacityChunks: Int = DEFAULT_CAPACITY_CHUNKS,
    private val sessionFactory: (NtripCasterUploadRuntimeConfig) -> CasterUploadSession = { config ->
        ClientCasterUploadSession(NtripCasterUploadClient(config.request))
    },
    private val delay: (Long) -> Unit = { Thread.sleep(it) },
) {
    constructor(
        capacityChunks: Int = DEFAULT_CAPACITY_CHUNKS,
        uploadOnce: (
            config: NtripCasterUploadRuntimeConfig,
            onState: (NtripConnectionState) -> Unit,
            writeRtcmBytes: (OutputStream) -> Unit,
        ) -> NtripCasterUploadResult,
        delay: (Long) -> Unit = { Thread.sleep(it) },
    ) : this(
        capacityChunks = capacityChunks,
        sessionFactory = { config -> FunctionCasterUploadSession(config, uploadOnce) },
        delay = delay,
    )

    init {
        require(capacityChunks > 0) { "Caster upload queue capacity must be positive." }
    }

    private val queue = ArrayBlockingQueue<ByteArray>(capacityChunks)
    private val state = AtomicReference("IDLE")
    private val bytesUploaded = AtomicLong(0)
    private val bytesDropped = AtomicLong(0)
    private val lastError = AtomicReference<String?>(null)
    private val mountpointUrl = AtomicReference("")
    private val activeSession = AtomicReference<CasterUploadSession?>()

    @Volatile
    private var running = false

    @Volatile
    private var worker: Thread? = null

    fun start(config: NtripCasterUploadRuntimeConfig) {
        stop()
        queue.clear()
        running = true
        state.set("CONNECTING")
        lastError.set(null)
        mountpointUrl.set(config.mountpointUrl)
        worker = Thread({ runWorker(config) }, "rtkcollector-caster-upload").also { it.start() }
    }

    fun offer(bytes: ByteArray): Boolean {
        if (!running) return false
        val copy = bytes.copyOf()
        val accepted = queue.offer(copy)
        if (!accepted) {
            bytesDropped.addAndGet(bytes.size.toLong())
            state.compareAndSet("STREAMING", "DEGRADED")
        }
        return accepted
    }

    fun stop() {
        running = false
        activeSession.get()?.cancel()
        worker?.interrupt()
        worker?.join(STOP_JOIN_MILLIS)
        worker = null
        activeSession.set(null)
        queue.clear()
        state.set("STOPPED")
    }

    fun snapshot(): NtripCasterUploadSnapshot =
        NtripCasterUploadSnapshot(
            state = state.get(),
            bytesUploaded = bytesUploaded.get(),
            bytesDropped = bytesDropped.get(),
            lastError = lastError.get(),
            mountpointUrl = mountpointUrl.get(),
        )

    private fun runWorker(config: NtripCasterUploadRuntimeConfig) {
        while (running) {
            val session = sessionFactory(config)
            activeSession.set(session)
            val result = session.connectOnce(
                { nextState -> state.set(nextState.name) },
                { output -> drainQueueTo(output) },
            )
            activeSession.compareAndSet(session, null)
            when (result) {
                is NtripCasterUploadResult.Completed -> {
                    if (running) {
                        state.set("RECONNECT_WAIT")
                        sleepBeforeReconnect(config)
                    }
                }
                is NtripCasterUploadResult.Failure -> {
                    lastError.set(result.failure.message)
                    if (isAuthFailure(result.failure.kind)) {
                        state.set("AUTH_ERROR")
                        running = false
                    } else if (running) {
                        state.set("RECONNECT_WAIT")
                        sleepBeforeReconnect(config)
                    } else {
                        state.set("STOPPED")
                    }
                }
            }
        }
        if (state.get() != "AUTH_ERROR") {
            state.set("STOPPED")
        }
    }

    private fun drainQueueTo(output: OutputStream) {
        while (running) {
            val next = queue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) ?: continue
            output.write(next)
            output.flush()
            bytesUploaded.addAndGet(next.size.toLong())
        }
    }

    private fun sleepBeforeReconnect(config: NtripCasterUploadRuntimeConfig) {
        runCatching { delay(config.reconnectDelayMillis) }
    }

    private fun isAuthFailure(kind: NtripCasterUploadFailureKind): Boolean =
        kind == NtripCasterUploadFailureKind.AUTHENTICATION_FAILED ||
            kind == NtripCasterUploadFailureKind.AUTHORIZATION_FAILED

    private companion object {
        const val DEFAULT_CAPACITY_CHUNKS = 256
        const val POLL_TIMEOUT_MILLIS = 100L
        const val STOP_JOIN_MILLIS = 2_000L
    }
}

interface CasterUploadSession {
    fun connectOnce(
        onState: (NtripConnectionState) -> Unit,
        writeRtcmBytes: (OutputStream) -> Unit,
    ): NtripCasterUploadResult

    fun cancel()
}

private class ClientCasterUploadSession(
    private val client: NtripCasterUploadClient,
) : CasterUploadSession {
    override fun connectOnce(
        onState: (NtripConnectionState) -> Unit,
        writeRtcmBytes: (OutputStream) -> Unit,
    ): NtripCasterUploadResult = client.connectOnce(onState, writeRtcmBytes)

    override fun cancel() {
        client.cancel()
    }
}

private class FunctionCasterUploadSession(
    private val config: NtripCasterUploadRuntimeConfig,
    private val uploadOnce: (
        config: NtripCasterUploadRuntimeConfig,
        onState: (NtripConnectionState) -> Unit,
        writeRtcmBytes: (OutputStream) -> Unit,
    ) -> NtripCasterUploadResult = { config, onState, writeRtcmBytes ->
        NtripCasterUploadClient(config.request).connectOnce(onState, writeRtcmBytes)
    },
) : CasterUploadSession {
    override fun connectOnce(
        onState: (NtripConnectionState) -> Unit,
        writeRtcmBytes: (OutputStream) -> Unit,
    ): NtripCasterUploadResult = uploadOnce(config, onState, writeRtcmBytes)

    override fun cancel() = Unit
}
