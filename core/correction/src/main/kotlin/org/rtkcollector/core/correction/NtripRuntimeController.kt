package org.rtkcollector.core.correction

import java.util.concurrent.atomic.AtomicLong

enum class NtripRuntimeState {
    DISABLED,
    CONNECTING,
    AUTHENTICATING,
    STREAMING,
    RECONNECT_WAIT,
    STOPPED,
    AUTH_ERROR,
    NETWORK_ERROR,
}

data class NtripRuntimeConfig(
    val request: NtripRequest,
    val ggaLines: List<String> = emptyList(),
)

data class NtripRuntimeSnapshot(
    val state: NtripRuntimeState,
    val rawRecordingActive: Boolean,
    val correctionsActive: Boolean,
    val message: String? = null,
)

interface NtripRuntimeClient {
    fun run(
        ggaLines: Iterable<String>,
        onState: (CorrectionStatus) -> Unit,
        onRtcmBytes: (ByteArray) -> Unit,
    ): NtripConnectionResult

    fun cancel()
}

class DefaultNtripRuntimeClient(private val delegate: NtripClient) : NtripRuntimeClient {
    override fun run(
        ggaLines: Iterable<String>,
        onState: (CorrectionStatus) -> Unit,
        onRtcmBytes: (ByteArray) -> Unit,
    ): NtripConnectionResult =
        delegate.runWithReconnect(ggaLines = ggaLines, onState = onState, onRtcmBytes = onRtcmBytes)

    override fun cancel() {
        delegate.cancel()
    }
}

class NtripRuntimeController(
    private val clientFactory: (NtripRuntimeConfig) -> NtripRuntimeClient,
    private val emit: (NtripRuntimeSnapshot) -> Unit,
    private val onRtcmBytes: (ByteArray) -> Unit = {},
) {
    private val lock = Any()
    private val generation = AtomicLong(0)

    @Volatile
    private var activeWorker: ActiveNtripWorker? = null

    @Volatile
    private var worker: Thread? = null

    fun start(config: NtripRuntimeConfig) {
        synchronized(lock) {
            startWorkerLocked(config)
        }
    }

    fun update(config: NtripRuntimeConfig) {
        synchronized(lock) {
            stopWorkerLocked()
            startWorkerLocked(config)
        }
    }

    fun disable(message: String = "NTRIP disabled") {
        synchronized(lock) {
            stopWorkerLocked()
            emit(
                NtripRuntimeSnapshot(
                    state = NtripRuntimeState.DISABLED,
                    rawRecordingActive = true,
                    correctionsActive = false,
                    message = message,
                ),
            )
        }
    }

    fun stop() {
        synchronized(lock) {
            stopWorkerLocked()
            emit(NtripRuntimeSnapshot(NtripRuntimeState.STOPPED, rawRecordingActive = true, correctionsActive = false))
        }
    }

    private fun startWorkerLocked(config: NtripRuntimeConfig) {
        val client = clientFactory(config)
        val workerGeneration = generation.incrementAndGet()
        val active = ActiveNtripWorker(client = client, generation = workerGeneration)
        activeWorker = active
        worker = Thread(
            { runClient(config, active) },
            "rtkcollector-ntrip-runtime",
        ).also { it.start() }
    }

    private fun stopWorkerLocked() {
        generation.incrementAndGet()
        val active = activeWorker
        active?.deactivate()
        active?.client?.cancel()
        val currentWorker = worker
        if (currentWorker != null && currentWorker != Thread.currentThread()) {
            currentWorker.join(JOIN_TIMEOUT_MILLIS)
        }
        activeWorker = null
        worker = null
    }

    private fun runClient(config: NtripRuntimeConfig, active: ActiveNtripWorker) {
        active.deliverIfCurrent(snapshot = {
            NtripRuntimeSnapshot(NtripRuntimeState.CONNECTING, rawRecordingActive = true, correctionsActive = false)
        })
        val result = active.client.run(
            ggaLines = config.ggaLines,
            onState = { status -> active.deliverIfCurrent(snapshot = { status.toRuntimeSnapshot() }) },
            onRtcmBytes = { bytes ->
                active.deliverIfCurrent(
                    snapshot = { streamingSnapshot() },
                    afterEmit = { onRtcmBytes(bytes) },
                )
            },
        )
        active.deliverIfCurrent(snapshot = { result.toFinalSnapshot() })
    }

    private inner class ActiveNtripWorker(
        val client: NtripRuntimeClient,
        private val generation: Long,
    ) {
        private val callbackLock = Any()
        private var active = true

        fun deactivate() {
            synchronized(callbackLock) {
                active = false
            }
        }

        fun deliverIfCurrent(
            snapshot: () -> NtripRuntimeSnapshot,
            afterEmit: () -> Unit = {},
        ) {
            synchronized(callbackLock) {
                if (active && isCurrent(generation)) {
                    emit(snapshot())
                    afterEmit()
                }
            }
        }
    }

    private fun isCurrent(workerGeneration: Long): Boolean =
        generation.get() == workerGeneration

    private fun emit(snapshot: NtripRuntimeSnapshot) {
        this.emit.invoke(snapshot)
    }

    private fun streamingSnapshot(): NtripRuntimeSnapshot =
        NtripRuntimeSnapshot(
            state = NtripRuntimeState.STREAMING,
            rawRecordingActive = true,
            correctionsActive = true,
        )

    private fun CorrectionStatus.toRuntimeSnapshot(): NtripRuntimeSnapshot =
        NtripRuntimeSnapshot(
            state = when (state) {
                NtripConnectionState.CONNECTING,
                NtripConnectionState.RESOLVING,
                -> NtripRuntimeState.CONNECTING
                NtripConnectionState.AUTHENTICATING -> NtripRuntimeState.AUTHENTICATING
                NtripConnectionState.STREAMING -> NtripRuntimeState.STREAMING
                NtripConnectionState.RECONNECT_WAIT -> NtripRuntimeState.RECONNECT_WAIT
                NtripConnectionState.STOPPED,
                NtripConnectionState.IDLE,
                -> NtripRuntimeState.STOPPED
            },
            rawRecordingActive = true,
            correctionsActive = state == NtripConnectionState.STREAMING,
            message = lastError,
        )

    private fun NtripConnectionResult.toFinalSnapshot(): NtripRuntimeSnapshot =
        when (this) {
            is NtripConnectionResult.Completed ->
                NtripRuntimeSnapshot(NtripRuntimeState.STOPPED, rawRecordingActive = true, correctionsActive = false)
            is NtripConnectionResult.Failure -> when (failure.kind) {
                NtripFailureKind.AUTHENTICATION_FAILED,
                NtripFailureKind.AUTHORIZATION_FAILED,
                -> NtripRuntimeSnapshot(
                    state = NtripRuntimeState.AUTH_ERROR,
                    rawRecordingActive = true,
                    correctionsActive = false,
                    message = failure.message,
                )
                NtripFailureKind.CANCELLED -> NtripRuntimeSnapshot(
                    state = NtripRuntimeState.STOPPED,
                    rawRecordingActive = true,
                    correctionsActive = false,
                    message = failure.message,
                )
                else -> NtripRuntimeSnapshot(
                    state = NtripRuntimeState.NETWORK_ERROR,
                    rawRecordingActive = true,
                    correctionsActive = false,
                    message = failure.message,
                )
            }
        }

    private companion object {
        const val JOIN_TIMEOUT_MILLIS = 1_500L
    }
}
