package org.rtkcollector.core.correction

import java.io.OutputStream
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val UPLOAD_MESSAGE_RATE_WINDOW_MILLIS = 60_000L

data class NtripCasterUploadRuntimeConfig(
    val request: NtripCasterUploadRequest,
    val policy: NtripCasterUploadPolicy = NtripCasterUploadPolicy(),
    val reconnectDelayMillis: Long? = null,
) {
    init {
        require(reconnectDelayMillis == null || reconnectDelayMillis >= 0L) {
            "Reconnect delay must not be negative."
        }
    }

    val mountpointUrl: String
        get() = "${request.host}:${request.port}/${request.mountpoint.trimStart('/')}"

    val effectivePolicy: NtripCasterUploadPolicy
        get() = reconnectDelayMillis?.let { legacyDelayMillis ->
            policy.copy(
                retry = policy.retry.copy(
                    mode = NtripCasterUploadRetryMode.FIXED,
                    fixedReconnectDelayMillis = legacyDelayMillis.coerceAtLeast(10_000L),
                ),
            )
        } ?: policy
}

data class NtripCasterUploadSnapshot(
    val state: String,
    val bytesUploaded: Long,
    val bytesDropped: Long,
    val lastError: String?,
    val mountpointUrl: String,
    val bitrateKbps: Double,
    val totalRtcmHz: Double,
    val messageRates: List<NtripCasterUploadMessageRate>,
    val currentRetryDelayMillis: Long?,
    val consecutiveFailures: Int,
    val stopReason: String?,
    val safetyEnabled: Boolean,
    val safetyForced: Boolean,
)

class NtripCasterUploadController(
    private val capacityChunks: Int = DEFAULT_CAPACITY_CHUNKS,
    private val sessionFactory: (NtripCasterUploadRuntimeConfig) -> CasterUploadSession = { config ->
        ClientCasterUploadSession(NtripCasterUploadClient(config.request))
    },
    private val delay: (Long) -> Unit = { Thread.sleep(it) },
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val eventSink: ((NtripCasterUploadEvent) -> Unit)? = null,
) {
    constructor(
        capacityChunks: Int = DEFAULT_CAPACITY_CHUNKS,
        uploadOnce: (
            config: NtripCasterUploadRuntimeConfig,
            onState: (NtripConnectionState) -> Unit,
            writeRtcmBytes: (OutputStream) -> Unit,
        ) -> NtripCasterUploadResult,
        delay: (Long) -> Unit = { Thread.sleep(it) },
        clockMillis: () -> Long = { System.currentTimeMillis() },
        eventSink: ((NtripCasterUploadEvent) -> Unit)? = null,
    ) : this(
        capacityChunks = capacityChunks,
        sessionFactory = { config -> FunctionCasterUploadSession(config, uploadOnce) },
        delay = delay,
        clockMillis = clockMillis,
        eventSink = eventSink,
    )

    init {
        require(capacityChunks > 0) { "Caster upload queue capacity must be positive." }
    }

    private data class UploadChunk(
        val bytes: ByteArray,
        val messageType: Int?,
    )

    private val queue = ArrayBlockingQueue<UploadChunk>(capacityChunks)
    private val state = AtomicReference("IDLE")
    private val bytesUploaded = AtomicLong(0)
    private val bytesDropped = AtomicLong(0)
    private val lastError = AtomicReference<String?>(null)
    private val mountpointUrl = AtomicReference("")
    private val currentRetryDelayMillis = AtomicReference<Long?>(null)
    private val consecutiveFailures = AtomicLong(0)
    private val stopReason = AtomicReference<String?>(null)
    private val safetyEnabled = AtomicBoolean(false)
    private val safetyForced = AtomicBoolean(false)
    private val activeSession = AtomicReference<CasterUploadSession?>()
    private val finalSummaryEmitted = AtomicBoolean(false)
    private val stats = UploadStats()
    private val lifecycleLock = Any()

    @Volatile
    private var running = false

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var activePolicy: NtripCasterUploadPolicy = NtripCasterUploadPolicy()

    fun start(config: NtripCasterUploadRuntimeConfig) {
        check(stop()) { "Previous NTRIP caster upload worker did not stop." }
        val effectivePolicy = config.effectivePolicy
        synchronized(lifecycleLock) {
            queue.clear()
            bytesUploaded.set(0)
            bytesDropped.set(0)
            lastError.set(null)
            currentRetryDelayMillis.set(null)
            consecutiveFailures.set(0)
            stopReason.set(null)
            mountpointUrl.set(config.mountpointUrl)
            safetyEnabled.set(effectivePolicy.safety.enabled)
            safetyForced.set(effectivePolicy.safety.forced)
            activePolicy = effectivePolicy
            stats.reset()
            finalSummaryEmitted.set(false)
            running = true
            state.set("CONNECTING")
            worker = Thread(
                { runWorker(config.copy(policy = effectivePolicy, reconnectDelayMillis = null)) },
                WORKER_NAME,
            ).also { it.start() }
        }
    }

    fun offer(bytes: ByteArray, messageType: Int? = null): Boolean {
        if (!running) return false
        val accepted = queue.offer(UploadChunk(bytes.copyOf(), messageType))
        if (!accepted) {
            bytesDropped.addAndGet(bytes.size.toLong())
            state.compareAndSet("STREAMING", "DEGRADED")
            emitEvent(
                kind = "queue_drop",
                message = "Dropped ${bytes.size} upload bytes because the caster upload queue was full.",
            )
        }
        return accepted
    }

    fun stop(timeoutMillis: Long = STOP_JOIN_MILLIS): Boolean {
        require(timeoutMillis >= 0L) { "Caster upload stop timeout must not be negative." }
        val (thread, session) = synchronized(lifecycleLock) {
            running = false
            queue.clear()
            worker to activeSession.get()
        }
        runCatching { session?.cancel() }
        thread?.interrupt()
        if (thread != null && thread !== Thread.currentThread()) {
            thread.join(timeoutMillis)
        }
        val terminated = thread == null || (thread !== Thread.currentThread() && !thread.isAlive)
        if (!terminated) {
            return false
        }
        synchronized(lifecycleLock) {
            if (worker === thread) {
                worker = null
            }
            activeSession.compareAndSet(session, null)
            if (state.get() != "AUTH_ERROR") {
                state.set("STOPPED")
            }
        }
        if (thread != null || mountpointUrl.get().isNotBlank()) emitFinalSummaryIfNeeded()
        return true
    }

    fun snapshot(): NtripCasterUploadSnapshot {
        val statsSnapshot = stats.snapshot(
            nowMillis = clockMillis(),
            bitrateWindowMillis = activePolicy.safety.bitrateWindowMillis,
        )
        return NtripCasterUploadSnapshot(
            state = state.get(),
            bytesUploaded = bytesUploaded.get(),
            bytesDropped = bytesDropped.get(),
            lastError = lastError.get(),
            mountpointUrl = mountpointUrl.get(),
            bitrateKbps = statsSnapshot.bitrateKbps,
            totalRtcmHz = statsSnapshot.totalRtcmHz,
            messageRates = statsSnapshot.messageRates,
            currentRetryDelayMillis = currentRetryDelayMillis.get(),
            consecutiveFailures = consecutiveFailures.get().toInt(),
            stopReason = stopReason.get(),
            safetyEnabled = safetyEnabled.get(),
            safetyForced = safetyForced.get(),
        )
    }

    private fun runWorker(config: NtripCasterUploadRuntimeConfig) {
        try {
            while (running) {
                emitEvent("connect_attempt", "Connecting to ${config.mountpointUrl}.")
                val session = sessionFactory(config)
                val mayConnect = synchronized(lifecycleLock) {
                    if (running) {
                        activeSession.set(session)
                        true
                    } else {
                        false
                    }
                }
                if (!mayConnect) break
                val connectedEventSent = AtomicBoolean(false)
                val result = runCatching {
                    session.connectOnce(
                        { nextState ->
                            state.set(nextState.name)
                            if (nextState == NtripConnectionState.STREAMING && connectedEventSent.compareAndSet(false, true)) {
                                emitEvent("connected", "Caster upload authenticated and streaming.")
                            }
                        },
                        { output -> drainQueueTo(output, config.effectivePolicy) },
                    )
                }.getOrElse { thrown ->
                    NtripCasterUploadResult.Failure(classifyUnexpectedFailure(thrown))
                }
                activeSession.compareAndSet(session, null)
                when (result) {
                    is NtripCasterUploadResult.Completed -> handleCompleted(config.effectivePolicy)
                    is NtripCasterUploadResult.Failure -> handleFailure(config.effectivePolicy, result.failure)
                }
            }
        } finally {
            activeSession.set(null)
            try {
                if (state.get() != "AUTH_ERROR" && state.get() != "STOPPED") {
                    state.set("STOPPED")
                }
                emitFinalSummaryIfNeeded()
            } finally {
                synchronized(lifecycleLock) {
                    if (worker === Thread.currentThread()) {
                        worker = null
                    }
                }
            }
        }
    }

    private fun handleCompleted(policy: NtripCasterUploadPolicy) {
        consecutiveFailures.set(0)
        currentRetryDelayMillis.set(null)
        if (running) {
            state.set("RECONNECT_WAIT")
            sleep(nextRetryDelay(policy.retry, failures = 1))
        }
    }

    private fun handleFailure(
        policy: NtripCasterUploadPolicy,
        failure: NtripCasterUploadFailure,
    ) {
        when (failure.kind) {
            NtripCasterUploadFailureKind.AUTHENTICATION_FAILED,
            NtripCasterUploadFailureKind.AUTHORIZATION_FAILED,
            -> {
                lastError.set(failure.message)
                currentRetryDelayMillis.set(null)
                emitEvent("auth_stop", failure.message)
                state.set("AUTH_ERROR")
                running = false
            }

            NtripCasterUploadFailureKind.SAFETY_STOP -> {
                lastError.set(null)
                currentRetryDelayMillis.set(null)
                val reason = failure.stopReason ?: NtripCasterUploadStopReason.BITRATE_LIMIT
                stopReason.set(reason.name)
                emitEvent("safety_stop", failure.message)
                state.set("STOPPED")
                running = false
            }

            NtripCasterUploadFailureKind.CANCELLED -> {
                currentRetryDelayMillis.set(null)
                state.set("STOPPED")
                running = false
            }

            else -> {
                if (failure.kind == NtripCasterUploadFailureKind.NO_RTCM_DATA) {
                    lastError.set(null)
                    stopReason.set(NtripCasterUploadStopReason.NO_RTCM_DATA.name)
                    emitEvent("no_data", failure.message)
                    emitEvent("safety_stop", failure.message)
                    currentRetryDelayMillis.set(null)
                    state.set("STOPPED")
                    running = false
                    return
                } else {
                    lastError.set(failure.message)
                }
                val failures = consecutiveFailures.incrementAndGet().toInt()
                if (
                    policy.retry.stopAfterFailuresEnabled &&
                    failures >= policy.retry.stopAfterConsecutiveFailures
                ) {
                    currentRetryDelayMillis.set(null)
                    stopReason.set(NtripCasterUploadStopReason.RETRY_LIMIT.name)
                    state.set("STOPPED")
                    running = false
                    return
                }
                if (running) {
                    val retryDelayMillis = nextRetryDelay(policy.retry, failures)
                    currentRetryDelayMillis.set(retryDelayMillis)
                    state.set("RECONNECT_WAIT")
                    emitEvent(
                        "retry_scheduled",
                        "Retrying caster upload in ${retryDelayMillis} ms after ${failure.kind.name.lowercase()}.",
                    )
                    sleep(retryDelayMillis)
                } else {
                    state.set("STOPPED")
                }
            }
        }
    }

    private fun drainQueueTo(
        output: OutputStream,
        policy: NtripCasterUploadPolicy,
    ) {
        val connectedAtMillis = clockMillis()
        var lastUploadedAtMillis: Long? = null
        while (running) {
            val next = queue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            val nowMillis = clockMillis()
            if (next == null) {
                if (
                    lastUploadedAtMillis == null &&
                    nowMillis - connectedAtMillis >= policy.safety.noDataTimeoutMillis
                ) {
                    throw NtripCasterUploadNoDataException(
                        "No RTCM data uploaded within ${policy.safety.noDataTimeoutMillis / 1_000} seconds.",
                    )
                }
                continue
            }
            output.write(next.bytes)
            output.flush()
            val writeMillis = clockMillis()
            bytesUploaded.addAndGet(next.bytes.size.toLong())
            consecutiveFailures.set(0)
            currentRetryDelayMillis.set(null)
            lastError.set(null)
            stopReason.set(null)
            stats.recordUploaded(writeMillis, next.bytes.size, next.messageType)
            lastUploadedAtMillis = writeMillis
            checkSafety(policy.safety, writeMillis)
        }
    }

    private fun checkSafety(
        policy: NtripCasterUploadSafetyPolicy,
        nowMillis: Long,
    ) {
        if (!policy.enabled && !policy.forced) {
            return
        }
        val statsSnapshot = stats.snapshot(nowMillis, policy.bitrateWindowMillis)
        if (statsSnapshot.bitrateKbps > policy.maxBitrateKbps) {
            throw NtripCasterUploadSafetyException(
                stopReason = NtripCasterUploadStopReason.BITRATE_LIMIT,
                message = "Upload bitrate exceeded ${policy.maxBitrateKbps} kbps.",
            )
        }
        if (bytesUploaded.get() > policy.maxSessionUploadBytes) {
            throw NtripCasterUploadSafetyException(
                stopReason = NtripCasterUploadStopReason.SESSION_VOLUME_LIMIT,
                message = "Upload volume exceeded session limit.",
            )
        }
    }

    private fun sleep(delayMillis: Long) {
        runCatching { delay(delayMillis) }
    }

    private fun nextRetryDelay(
        policy: NtripCasterUploadRetryPolicy,
        failures: Int,
    ): Long =
        when (policy.mode) {
            NtripCasterUploadRetryMode.FIXED -> policy.fixedReconnectDelayMillis
            NtripCasterUploadRetryMode.ADAPTIVE -> {
                var computedDelay = policy.adaptiveInitialDelayMillis
                repeat((failures - 1).coerceAtLeast(0)) {
                    computedDelay = (computedDelay * 2).coerceAtMost(policy.adaptiveMaxDelayMillis)
                }
                computedDelay
            }
        }

    private fun emitEvent(
        kind: String,
        message: String,
    ) {
        eventSink?.invoke(
            NtripCasterUploadEvent(
                kind = kind,
                message = message,
                timestampMillis = clockMillis(),
            ),
        )
    }

    private fun classifyUnexpectedFailure(throwable: Throwable): NtripCasterUploadFailure =
        when (throwable) {
            is NtripCasterUploadNoDataException -> NtripCasterUploadFailure(
                kind = NtripCasterUploadFailureKind.NO_RTCM_DATA,
                message = throwable.message ?: "No RTCM data uploaded before watchdog timeout.",
                state = NtripConnectionState.STREAMING,
                stopReason = NtripCasterUploadStopReason.NO_RTCM_DATA,
                cause = throwable,
            )

            is NtripCasterUploadSafetyException -> NtripCasterUploadFailure(
                kind = NtripCasterUploadFailureKind.SAFETY_STOP,
                message = throwable.message ?: "Caster upload safety policy stopped streaming.",
                state = NtripConnectionState.STREAMING,
                stopReason = throwable.stopReason,
                cause = throwable,
            )

            else -> NtripCasterUploadFailure(
                kind = NtripCasterUploadFailureKind.STREAM_FAILED,
                message = throwable.message ?: "NTRIP caster upload stream failed.",
                state = NtripConnectionState.STREAMING,
                cause = throwable,
            )
        }

    private fun emitFinalSummaryIfNeeded() {
        if (!finalSummaryEmitted.compareAndSet(false, true)) {
            return
        }
        emitEvent(
            kind = "final_summary",
            message = buildString {
                append("Caster upload finished for ")
                append(mountpointUrl.get())
                append("; bytesUploaded=")
                append(bytesUploaded.get())
                append(", bytesDropped=")
                append(bytesDropped.get())
                append(", stopReason=")
                append(stopReason.get() ?: "none")
            },
        )
    }

    private companion object {
        const val DEFAULT_CAPACITY_CHUNKS = 256
        const val POLL_TIMEOUT_MILLIS = 100L
        const val STOP_JOIN_MILLIS = 2_000L
        const val WORKER_NAME = "rtkcollector-caster-upload"
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
    ) -> NtripCasterUploadResult = { runtimeConfig, onState, writeRtcmBytes ->
        NtripCasterUploadClient(runtimeConfig.request).connectOnce(onState, writeRtcmBytes)
    },
) : CasterUploadSession {
    override fun connectOnce(
        onState: (NtripConnectionState) -> Unit,
        writeRtcmBytes: (OutputStream) -> Unit,
    ): NtripCasterUploadResult = uploadOnce(config, onState, writeRtcmBytes)

    override fun cancel() = Unit
}

private data class UploadStatsSnapshot(
    val bitrateKbps: Double,
    val totalRtcmHz: Double,
    val messageRates: List<NtripCasterUploadMessageRate>,
)

private data class UploadSample(
    val timestampMillis: Long,
    val bytes: Int,
    val messageType: Int?,
)

private class UploadStats {
    private val samples = ArrayDeque<UploadSample>()

    @Synchronized
    fun reset() {
        samples.clear()
    }

    @Synchronized
    fun recordUploaded(
        nowMillis: Long,
        bytes: Int,
        messageType: Int?,
    ) {
        samples.addLast(
            UploadSample(
                timestampMillis = nowMillis,
                bytes = bytes,
                messageType = messageType,
            ),
        )
        prune(nowMillis, UPLOAD_MESSAGE_RATE_WINDOW_MILLIS)
    }

    @Synchronized
    fun snapshot(
        nowMillis: Long,
        bitrateWindowMillis: Long,
    ): UploadStatsSnapshot {
        prune(nowMillis, maxOf(bitrateWindowMillis, UPLOAD_MESSAGE_RATE_WINDOW_MILLIS))
        val bitrateBytes = samples
            .asSequence()
            .filter { nowMillis - it.timestampMillis < bitrateWindowMillis }
            .sumOf { it.bytes }
        val totalRtcmHz = samples.size.toDouble() / (UPLOAD_MESSAGE_RATE_WINDOW_MILLIS / 1_000.0)
        val messageRates = samples
            .asSequence()
            .mapNotNull { it.messageType }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key }
            .map { (messageType, count) ->
                NtripCasterUploadMessageRate(
                    messageType = messageType,
                    hz = count / (UPLOAD_MESSAGE_RATE_WINDOW_MILLIS / 1_000.0),
                )
            }
        return UploadStatsSnapshot(
            bitrateKbps = if (bitrateWindowMillis > 0L) {
                (bitrateBytes * 8.0) / bitrateWindowMillis.toDouble()
            } else {
                0.0
            },
            totalRtcmHz = totalRtcmHz,
            messageRates = messageRates,
        )
    }

    private fun prune(
        nowMillis: Long,
        keepWindowMillis: Long,
    ) {
        while (samples.isNotEmpty() && nowMillis - samples.first().timestampMillis >= keepWindowMillis) {
            samples.removeFirst()
        }
    }
}
