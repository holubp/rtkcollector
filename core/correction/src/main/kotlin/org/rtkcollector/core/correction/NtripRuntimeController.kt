package org.rtkcollector.core.correction

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    /** Returns false without starting the replacement when the previous worker has not terminated. */
    fun update(
        config: NtripRuntimeConfig,
        timeoutMillis: Long = JOIN_TIMEOUT_MILLIS,
    ): Boolean {
        require(timeoutMillis >= 0L) { "NTRIP runtime stop timeout must not be negative." }
        synchronized(lock) {
            if (!stopWorkerLocked(timeoutMillis)) return false
            startWorkerLocked(config)
            return true
        }
    }

    /** Returns true only when the deactivated worker is confirmed terminated. */
    fun disable(
        message: String = "NTRIP disabled",
        timeoutMillis: Long = JOIN_TIMEOUT_MILLIS,
    ): Boolean {
        require(timeoutMillis >= 0L) { "NTRIP runtime stop timeout must not be negative." }
        synchronized(lock) {
            val terminated = stopWorkerLocked(timeoutMillis)
            emit(
                NtripRuntimeSnapshot(
                    state = NtripRuntimeState.DISABLED,
                    rawRecordingActive = true,
                    correctionsActive = false,
                    message = message,
                ),
            )
            return terminated
        }
    }

    /** Returns true only when no owned worker can make another correction callback. */
    fun stop(timeoutMillis: Long = JOIN_TIMEOUT_MILLIS): Boolean {
        require(timeoutMillis >= 0L) { "NTRIP runtime stop timeout must not be negative." }
        synchronized(lock) {
            val terminated = stopWorkerLocked(timeoutMillis)
            emit(NtripRuntimeSnapshot(NtripRuntimeState.STOPPED, rawRecordingActive = true, correctionsActive = false))
            return terminated
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

    private fun stopWorkerLocked(timeoutMillis: Long): Boolean {
        val startedAtNanos = System.nanoTime()
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        generation.incrementAndGet()
        val active = activeWorker
        active?.deactivate()
        runCatching { active?.client?.cancel() }
        val currentWorker = worker
        val workerTerminated = awaitWorkerTermination(
            currentWorker,
            remainingNanos(startedAtNanos, timeoutNanos),
        )
        val callbacksQuiesced = active?.awaitCallbacksQuiesced(
            remainingNanos(startedAtNanos, timeoutNanos),
        ) ?: true
        val terminated = workerTerminated && callbacksQuiesced
        if (terminated && worker === currentWorker) {
            worker = null
            if (activeWorker === active) {
                activeWorker = null
            }
        }
        return terminated
    }

    private fun awaitWorkerTermination(thread: Thread?, timeoutNanos: Long): Boolean {
        if (thread == null) return true
        if (thread === Thread.currentThread()) return false
        if (!thread.isAlive) return true
        if (timeoutNanos <= 0L) return false
        return try {
            thread.join(
                TimeUnit.NANOSECONDS.toMillis(timeoutNanos),
                (timeoutNanos % NANOS_PER_MILLISECOND).toInt(),
            )
            !thread.isAlive
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun remainingNanos(startedAtNanos: Long, timeoutNanos: Long): Long =
        (timeoutNanos - (System.nanoTime() - startedAtNanos)).coerceAtLeast(0L)

    private fun runClient(config: NtripRuntimeConfig, active: ActiveNtripWorker) {
        active.deliverIfCurrent(snapshot = {
            NtripRuntimeSnapshot(NtripRuntimeState.CONNECTING, rawRecordingActive = true, correctionsActive = false)
        })
        val result = try {
            active.client.run(
                ggaLines = config.ggaLines,
                onState = { status -> active.deliverIfCurrent(snapshot = { status.toRuntimeSnapshot() }) },
                onRtcmBytes = { bytes ->
                    active.deliverIfCurrent(
                        snapshot = { streamingSnapshot() },
                        afterEmit = { onRtcmBytes(bytes) },
                    )
                },
            )
        } catch (exception: Exception) {
            NtripConnectionResult.Failure(
                NtripFailure(
                    kind = NtripFailureKind.STREAM_FAILED,
                    state = NtripConnectionState.STREAMING,
                    message = exception.message ?: "NTRIP runtime callback failed",
                    cause = exception,
                ),
            )
        }
        active.deliverIfCurrent(snapshot = { result.toFinalSnapshot() })
    }

    private inner class ActiveNtripWorker(
        val client: NtripRuntimeClient,
        private val generation: Long,
    ) {
        private val callbackState = AtomicLong(0L)
        private val callbacksQuiesced = CountDownLatch(1)

        fun deactivate() {
            while (true) {
                val current = callbackState.get()
                if (current and CALLBACK_DEACTIVATED_MASK != 0L) return
                val deactivated = current or CALLBACK_DEACTIVATED_MASK
                if (callbackState.compareAndSet(current, deactivated)) {
                    if (deactivated == CALLBACK_DEACTIVATED_MASK) callbacksQuiesced.countDown()
                    return
                }
            }
        }

        fun awaitCallbacksQuiesced(timeoutNanos: Long): Boolean {
            if (callbacksQuiesced.count == 0L) return true
            if (timeoutNanos <= 0L) return false
            return try {
                callbacksQuiesced.await(timeoutNanos, TimeUnit.NANOSECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }

        fun deliverIfCurrent(
            snapshot: () -> NtripRuntimeSnapshot,
            afterEmit: () -> Unit = {},
        ) {
            if (!tryEnterCallback()) return
            try {
                if (!isActiveAndCurrent()) return
                val nextSnapshot = snapshot()
                if (!isActiveAndCurrent()) return
                emit(nextSnapshot)
                if (isActiveAndCurrent()) afterEmit()
            } finally {
                exitCallback()
            }
        }

        private fun isActiveAndCurrent(): Boolean =
            callbackState.get() and CALLBACK_DEACTIVATED_MASK == 0L && isCurrent(generation)

        private fun tryEnterCallback(): Boolean {
            while (true) {
                val current = callbackState.get()
                if (current and CALLBACK_DEACTIVATED_MASK != 0L || !isCurrent(generation)) return false
                check(current < CALLBACK_COUNT_MASK) { "NTRIP callback count overflow." }
                if (callbackState.compareAndSet(current, current + 1L)) return true
            }
        }

        private fun exitCallback() {
            while (true) {
                val current = callbackState.get()
                val callbackCount = current and CALLBACK_COUNT_MASK
                check(callbackCount > 0L) { "NTRIP callback count underflow." }
                val updated = current - 1L
                if (callbackState.compareAndSet(current, updated)) {
                    if (updated == CALLBACK_DEACTIVATED_MASK) callbacksQuiesced.countDown()
                    return
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
        const val CALLBACK_DEACTIVATED_MASK = Long.MIN_VALUE
        const val CALLBACK_COUNT_MASK = Long.MAX_VALUE
        const val JOIN_TIMEOUT_MILLIS = 1_500L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
