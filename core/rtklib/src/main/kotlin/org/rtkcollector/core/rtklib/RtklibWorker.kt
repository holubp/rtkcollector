package org.rtkcollector.core.rtklib

import java.util.ArrayDeque

class RtklibWorker(
    private val backendFactory: RtklibBackendFactory,
    private val outputWriters: RtklibOutputWriters,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val statusSink: ((RtklibEngineSnapshot) -> Unit)? = null,
) : RtklibEngine {
    private val monitor = Object()
    private val queue = ArrayDeque<RtklibInputChunk>()

    private var backend: RtklibBackend? = null
    private var workerThread: Thread? = null
    private var accepting = false
    private var roverQueueBytes = 0
    private var correctionQueueBytes = 0
    private var droppedRoverBytes = 0L
    private var droppedCorrectionBytes = 0L
    private var outputNmeaLines = 0L
    private var outputPosLines = 0L
    private var latestSolution: RtklibSolutionSnapshot? = null
    private var lastWarning: String? = null
    private var lastError: String? = null
    private var lastWrittenNmeaLine: String? = null
    private var lastWrittenPosLine: String? = null
    private var state = RtklibEngineState.STOPPED
    private var config: RtklibConfig? = null
    private var finalizing = false
    private var cancellationThread: Thread? = null
    private var resourcesFinalized = false
    private var stopRequested = false
    private var initializing = false
    private var startupResult: RtklibStartResult? = null
    private var backendSnapshot: RtklibEngineSnapshot? = null
    private var lastSnapshotAtMillis: Long? = null
    private var lastStatusAtMillis: Long? = null

    /** Synchronous compatibility API. Capture/service control must use [startAsync]. */
    override fun start(config: RtklibConfig): RtklibStartResult {
        val admitted = startAsync(config)
        if (!admitted.started) return admitted
        val result = synchronized(monitor) {
            while (startupResult == null) monitor.wait()
            requireNotNull(startupResult)
        }
        if (!result.started) {
            synchronized(monitor) { workerThread }?.join()
            return RtklibStartResult.failed(snapshot().lastError ?: result.message ?: "RTKLIB startup stopped")
        }
        return result
    }

    /**
     * Admits one lifecycle owner without entering backend or writer IO. A started result means
     * admission, not native readiness; cached state remains STARTING until initialization succeeds.
     * STARTING input uses the existing bounded queues and is fed only after successful init.
     * An admitted owner is one-shot.
     */
    fun startAsync(config: RtklibConfig): RtklibStartResult =
        synchronized(monitor) {
            if (resourcesFinalized) {
                return RtklibStartResult.failed("RTKLIB worker resources are already closed")
            }
            if (stopRequested || workerThread != null || finalizing) {
                return RtklibStartResult.failed("RTKLIB worker is already running or shutting down")
            }
            val validation = config.validate()
            if (!validation.valid) {
                state = RtklibEngineState.FAILED
                lastError = validation.errors.joinToString("; ")
                return RtklibStartResult.failed(lastError!!)
            }
            state = RtklibEngineState.STARTING
            this.config = config
            initializing = true
            startupResult = null
            lastError = null
            if (launchOwnerLocked(config)) RtklibStartResult.started()
            else RtklibStartResult.failed(lastError ?: "RTKLIB owner could not start")
        }

    private fun launchOwnerLocked(config: RtklibConfig?): Boolean =
        try {
            workerThread = Thread({ runOwner(config) }, "rtkcollector-rtklib-worker").apply {
                isDaemon = true
            }
            requireNotNull(workerThread).start()
            true
        } catch (error: Throwable) {
            workerThread = null
            initializing = false
            stopRequested = true
            recordFinalizationFailure("RTKLIB owner could not start", error)
            startupResult = RtklibStartResult.failed(lastError ?: "RTKLIB owner could not start")
            monitor.notifyAll()
            false
        }

    private fun runOwner(config: RtklibConfig?) {
        var phase = "backend creation"
        try {
            if (config == null || synchronized(monitor) { stopRequested }) return
            val createdBackend = backendFactory.create()
            synchronized(monitor) { backend = createdBackend }
            if (synchronized(monitor) { stopRequested }) return
            phase = "backend start"
            val result = createdBackend.start(config)
            synchronized(monitor) {
                initializing = false
                if (!result.started) {
                    state = RtklibEngineState.FAILED
                    lastError = result.message ?: "RTKLIB backend refused to start"
                    startupResult = RtklibStartResult.failed(requireNotNull(lastError))
                } else if (stopRequested) {
                    startupResult = RtklibStartResult.failed("RTKLIB startup stopped")
                } else {
                    accepting = true
                    state = RtklibEngineState.RUNNING
                    startupResult = result
                }
                monitor.notifyAll()
            }
            phase = "processing"
            if (isAccepting()) runLoop()
        } catch (error: Throwable) {
            recordFinalizationFailure("RTKLIB $phase failed", error)
        } finally {
            finishOwnedResources()
        }
    }

    private fun finishOwnedResources() {
        val cancellation = synchronized(monitor) {
            accepting = false
            stopRequested = true
            initializing = false
            finalizing = true
            discardQueuedWorkLocked()
            if (startupResult == null) {
                startupResult = RtklibStartResult.failed(lastError ?: "RTKLIB startup stopped")
            }
            monitor.notifyAll()
            cancellationThread
        }
        // Native close must not race a still-running cancellation call. Only the owner waits;
        // shutdown callers remain bounded and retain all dependent writers until it terminates.
        var interrupted = false
        while (cancellation?.isAlive == true) {
            try { cancellation.join() } catch (_: InterruptedException) { interrupted = true }
        }
        try {
            try {
                synchronized(monitor) { backend }?.close()
            } catch (error: Throwable) {
                recordFinalizationFailure("RTKLIB backend close failed", error)
            }
            try {
                outputWriters.close()
            } catch (error: Throwable) {
                recordFinalizationFailure("RTKLIB output close failed", error)
            }
            if (snapshot().state == RtklibEngineState.FAILED) {
                try { statusSink?.invoke(snapshot()) } catch (error: Throwable) {
                    synchronized(monitor) {
                        lastWarning = "RTKLIB final status write failed: ${error.message ?: error::class.java.simpleName}"
                    }
                }
            }
        } finally {
            synchronized(monitor) {
                backend = null
                config = null
                resourcesFinalized = true
                finalizing = false
                if (state != RtklibEngineState.FAILED) state = RtklibEngineState.STOPPED
                monitor.notifyAll()
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    override fun offerRoverBytes(
        bytes: ByteArray,
        timestampMillis: Long,
        sessionOffsetBytes: Long?,
    ): RtklibOfferResult =
        offer(RtklibInputChunk(RtklibInputStreamKind.ROVER, bytes.copyOf(), timestampMillis, sessionOffsetBytes))

    override fun offerCorrectionBytes(
        bytes: ByteArray,
        timestampMillis: Long,
        sessionOffsetBytes: Long?,
    ): RtklibOfferResult =
        offer(RtklibInputChunk(RtklibInputStreamKind.CORRECTION, bytes.copyOf(), timestampMillis, sessionOffsetBytes))

    private fun offer(chunk: RtklibInputChunk): RtklibOfferResult {
        synchronized(monitor) {
            val starting = initializing && !stopRequested && state == RtklibEngineState.STARTING
            if (!accepting && !starting) {
                return RtklibOfferResult(
                    status = RtklibOfferStatus.STOPPED,
                    droppedBytes = chunk.bytes.size,
                    message = "RTKLIB worker is not accepting input",
                )
            }
            val activeConfig = config ?: return RtklibOfferResult(
                status = RtklibOfferStatus.FAILED,
                droppedBytes = chunk.bytes.size,
                message = "RTKLIB worker has no active config",
            )
            val currentBytes = if (chunk.streamKind == RtklibInputStreamKind.ROVER) {
                roverQueueBytes
            } else {
                correctionQueueBytes
            }
            val limit = if (chunk.streamKind == RtklibInputStreamKind.ROVER) {
                activeConfig.maxRoverQueueBytes
            } else {
                activeConfig.maxCorrectionQueueBytes
            }
            if (chunk.bytes.size > limit - currentBytes) {
                if (chunk.streamKind == RtklibInputStreamKind.ROVER) {
                    droppedRoverBytes += chunk.bytes.size.toLong()
                } else {
                    droppedCorrectionBytes += chunk.bytes.size.toLong()
                }
                if (!starting) state = RtklibEngineState.LAGGING
                return RtklibOfferResult(
                    status = RtklibOfferStatus.DROPPED_FULL,
                    droppedBytes = chunk.bytes.size,
                    message = "RTKLIB ${chunk.streamKind.name.lowercase()} queue is full",
                )
            }
            queue.addLast(chunk)
            if (chunk.streamKind == RtklibInputStreamKind.ROVER) {
                roverQueueBytes += chunk.bytes.size
            } else {
                correctionQueueBytes += chunk.bytes.size
            }
            monitor.notifyAll()
            return RtklibOfferResult(status = RtklibOfferStatus.ACCEPTED, acceptedBytes = chunk.bytes.size)
        }
    }

    private fun runLoop() {
        while (true) {
            refreshAdvisorySnapshot()
            val chunk = synchronized(monitor) {
                if (accepting && queue.isEmpty()) {
                    monitor.wait(SNAPSHOT_INTERVAL_MILLIS)
                }
                if (!accepting && queue.isEmpty()) return
                if (queue.isEmpty()) {
                    null
                } else {
                    queue.removeFirst().also(::subtractQueuedBytes)
                }
            } ?: continue

            val activeBackend = synchronized(monitor) { backend } ?: continue
            val batch = try {
                activeBackend.feed(chunk)
            } catch (error: Throwable) {
                synchronized(monitor) {
                    if (!stopRequested) {
                        state = RtklibEngineState.FAILED
                        lastError = error.message ?: error::class.java.simpleName
                    }
                    accepting = false
                    discardQueuedWorkLocked()
                    monitor.notifyAll()
                }
                continue
            }

            if (!isAccepting()) continue

            val activeConfig = synchronized(monitor) { config } ?: continue
            val writableBatch = RtklibSolutionOutputSynthesizer
                .withSyntheticOutputsIfNeeded(
                    batch = batch,
                    outputNmea = activeConfig.outputNmea,
                    outputPos = activeConfig.outputPos,
                )
                .filteredFor(activeConfig)
                .withoutRepeatedOutputLines()

            try {
                outputWriters.write(writableBatch)
            } catch (error: Throwable) {
                synchronized(monitor) {
                    state = RtklibEngineState.FAILED
                    lastError = "RTKLIB output write failed: ${error.message ?: error::class.java.simpleName}"
                    accepting = false
                    discardQueuedWorkLocked()
                    monitor.notifyAll()
                }
                continue
            }

            synchronized(monitor) {
                if (state != RtklibEngineState.FAILED) state = RtklibEngineState.RUNNING
                latestSolution = batch.solution ?: latestSolution
                lastWarning = batch.warning ?: lastWarning
                outputNmeaLines += writableBatch.nmeaLines.size.toLong()
                outputPosLines += writableBatch.posLines.size.toLong()
            }
        }
    }

    // Only this worker may enter native snapshot/status IO. Neither operation owns monitor;
    // shutdown retains the session writers until this thread (including its sink) terminates.
    private fun refreshAdvisorySnapshot() {
        if (!isAccepting()) return
        val nowMillis = clockMillis()
        val previous = lastSnapshotAtMillis
        if (previous != null && nowMillis >= previous && nowMillis - previous < SNAPSHOT_INTERVAL_MILLIS) return
        lastSnapshotAtMillis = nowMillis
        val activeBackend = synchronized(monitor) { backend } ?: return
        try {
            val refreshed = activeBackend.snapshot()
            synchronized(monitor) {
                backendSnapshot = refreshed
                if (!stopRequested && refreshed.state == RtklibEngineState.FAILED) {
                    state = RtklibEngineState.FAILED
                    lastError = refreshed.lastError ?: "RTKLIB native processing failed"
                    accepting = false
                    discardQueuedWorkLocked()
                    monitor.notifyAll()
                }
            }
        } catch (error: Throwable) {
            synchronized(monitor) {
                lastWarning = "RTKLIB snapshot failed: ${error.message ?: error::class.java.simpleName}"
            }
        }
        if (!isAccepting() || statusSink == null) return
        val lastStatus = lastStatusAtMillis
        if (lastStatus != null && nowMillis >= lastStatus && nowMillis - lastStatus < STATUS_INTERVAL_MILLIS) return
        lastStatusAtMillis = nowMillis
        try {
            statusSink.invoke(snapshot())
        } catch (error: Throwable) {
            synchronized(monitor) {
                lastWarning = "RTKLIB status write failed: ${error.message ?: error::class.java.simpleName}"
            }
        }
    }

    private fun RtklibNativeOutputBatch.filteredFor(config: RtklibConfig): RtklibNativeOutputBatch =
        copy(
            nmeaLines = if (config.outputNmea) nmeaLines else emptyList(),
            posLines = if (config.outputPos) posLines else emptyList(),
        )

    private fun RtklibNativeOutputBatch.withoutRepeatedOutputLines(): RtklibNativeOutputBatch {
        val filteredNmea = nmeaLines.withoutConsecutiveRepeats(lastWrittenNmeaLine).also {
            lastWrittenNmeaLine = it.lastLine
        }.lines
        val filteredPos = posLines.withoutConsecutiveRepeats(lastWrittenPosLine).also {
            lastWrittenPosLine = it.lastLine
        }.lines
        return copy(nmeaLines = filteredNmea, posLines = filteredPos)
    }

    private data class OutputLineFilterResult(val lines: List<String>, val lastLine: String?)

    private fun List<String>.withoutConsecutiveRepeats(previousLine: String?): OutputLineFilterResult {
        var previous = previousLine
        val filtered = buildList {
            for (line in this@withoutConsecutiveRepeats) {
                if (line != previous) {
                    add(line)
                    previous = line
                }
            }
        }
        return OutputLineFilterResult(lines = filtered, lastLine = previous)
    }

    private fun subtractQueuedBytes(chunk: RtklibInputChunk) {
        if (chunk.streamKind == RtklibInputStreamKind.ROVER) {
            roverQueueBytes -= chunk.bytes.size
        } else {
            correctionQueueBytes -= chunk.bytes.size
        }
    }

    private fun discardQueuedWorkLocked() {
        while (queue.isNotEmpty()) {
            val chunk = queue.removeFirst()
            if (chunk.streamKind == RtklibInputStreamKind.ROVER) {
                droppedRoverBytes += chunk.bytes.size.toLong()
            } else {
                droppedCorrectionBytes += chunk.bytes.size.toLong()
            }
        }
        roverQueueBytes = 0
        correctionQueueBytes = 0
    }

    private fun isAccepting(): Boolean = synchronized(monitor) { accepting }

    override fun snapshot(): RtklibEngineSnapshot =
        synchronized(monitor) {
            RtklibEngineSnapshot(
                state = state,
                latestSolution = latestSolution ?: backendSnapshot?.latestSolution,
                lastWarning = lastWarning ?: backendSnapshot?.lastWarning,
                lastError = lastError ?: backendSnapshot?.lastError,
                roverQueueBytes = roverQueueBytes,
                correctionQueueBytes = correctionQueueBytes,
                droppedRoverBytes = droppedRoverBytes,
                droppedCorrectionBytes = droppedCorrectionBytes,
                decodedRoverEpochs = backendSnapshot?.decodedRoverEpochs ?: 0L,
                decodedCorrectionMessages = backendSnapshot?.decodedCorrectionMessages ?: 0L,
                serverCpuTimeMillis = backendSnapshot?.serverCpuTimeMillis,
                serverRoverObservationMessages = backendSnapshot?.serverRoverObservationMessages ?: 0L,
                serverBaseObservationMessages = backendSnapshot?.serverBaseObservationMessages ?: 0L,
                serverMissingObservationCount = backendSnapshot?.serverMissingObservationCount ?: 0L,
                outputNmeaLines = outputNmeaLines,
                outputPosLines = outputPosLines,
                updatedAtMillis = clockMillis(),
            )
        }

    override fun shutdown(timeoutMillis: Long): Boolean {
        require(timeoutMillis in 0L..MAX_SHUTDOWN_TIMEOUT_MILLIS) {
            "RTKLIB shutdown timeout must be between 0 and $MAX_SHUTDOWN_TIMEOUT_MILLIS ms"
        }
        val deadlineNanos = System.nanoTime() + timeoutMillis * NANOS_PER_MILLISECOND
        val thread = synchronized(monitor) {
            stopRequested = true
            accepting = false
            discardQueuedWorkLocked()
            monitor.notifyAll()
            if (resourcesFinalized && workerThread?.isAlive != true) return true
            if (workerThread == null && !launchOwnerLocked(null)) return false
            val activeBackend = backend
            // The native backend's handle is initialized by start; do not race stop against it.
            if (activeBackend != null && !initializing && !finalizing && cancellationThread == null) {
                try {
                    cancellationThread = Thread(
                        { requestBackendCancellation(activeBackend) }, "rtkcollector-rtklib-cancel",
                    ).apply { isDaemon = true }
                    requireNotNull(cancellationThread).start()
                } catch (error: Throwable) {
                    cancellationThread = null
                    lastWarning = "RTKLIB backend cancellation could not start: ${error.message ?: error::class.java.simpleName}"
                }
            }
            requireNotNull(workerThread)
        }
        if (!joinBefore(thread, deadlineNanos)) {
            synchronized(monitor) {
                lastWarning = "RTKLIB worker shutdown timed out after ${timeoutMillis} ms"
            }
            return false
        }

        return synchronized(monitor) { resourcesFinalized }
    }

    override fun stop() {
        check(shutdown(DEFAULT_SHUTDOWN_TIMEOUT_MILLIS)) {
            "RTKLIB worker did not terminate within ${DEFAULT_SHUTDOWN_TIMEOUT_MILLIS} ms"
        }
    }

    private fun recordFinalizationFailure(prefix: String, error: Throwable) {
        synchronized(monitor) {
            state = RtklibEngineState.FAILED
            val failure = "$prefix: ${error.message ?: error::class.java.simpleName}"
            lastError = lastError?.let { "$it; $failure" } ?: failure
        }
    }

    private fun requestBackendCancellation(activeBackend: RtklibBackend?) {
        try {
            activeBackend?.stop()
        } catch (error: Throwable) {
            synchronized(monitor) {
                lastWarning = "RTKLIB backend cancellation failed: ${error.message ?: error::class.java.simpleName}"
            }
        }
    }

    private fun joinBefore(thread: Thread, deadlineNanos: Long): Boolean {
        if (!thread.isAlive) return true
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) return false
        try {
            thread.join((remainingNanos / NANOS_PER_MILLISECOND).coerceAtLeast(1L))
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
        return !thread.isAlive
    }

    private companion object {
        const val DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 2_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_SHUTDOWN_TIMEOUT_MILLIS = Long.MAX_VALUE / NANOS_PER_MILLISECOND
        const val SNAPSHOT_INTERVAL_MILLIS = 250L
        const val STATUS_INTERVAL_MILLIS = 1_000L
    }
}
