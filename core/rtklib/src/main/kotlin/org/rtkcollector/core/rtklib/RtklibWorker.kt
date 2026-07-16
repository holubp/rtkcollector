package org.rtkcollector.core.rtklib

import java.util.ArrayDeque

class RtklibWorker(
    private val backendFactory: RtklibBackendFactory,
    private val outputWriters: RtklibOutputWriters,
    private val clockMillis: () -> Long = System::currentTimeMillis,
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

    override fun start(config: RtklibConfig): RtklibStartResult {
        synchronized(monitor) {
            if (resourcesFinalized) {
                return RtklibStartResult.failed("RTKLIB worker resources are already closed")
            }
            if (accepting || workerThread != null || backend != null || finalizing) {
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
        }

        val createdBackend = try {
            backendFactory.create()
        } catch (error: Throwable) {
            synchronized(monitor) {
                state = RtklibEngineState.FAILED
                lastError = error.message ?: error::class.java.simpleName
            }
            return RtklibStartResult.failed("RTKLIB backend creation failed: ${snapshot().lastError}")
        }

        val startResult = try {
            createdBackend.start(config)
        } catch (error: Throwable) {
            createdBackend.close()
            synchronized(monitor) {
                state = RtklibEngineState.FAILED
                lastError = error.message ?: error::class.java.simpleName
            }
            return RtklibStartResult.failed("RTKLIB backend start failed: ${snapshot().lastError}")
        }

        if (!startResult.started) {
            createdBackend.close()
            synchronized(monitor) {
                state = RtklibEngineState.FAILED
                lastError = startResult.message ?: "RTKLIB backend refused to start"
            }
            return startResult
        }

        val attached = synchronized(monitor) {
            if (resourcesFinalized || finalizing) {
                false
            } else {
                backend = createdBackend
                accepting = true
                state = RtklibEngineState.RUNNING
                lastWrittenNmeaLine = null
                lastWrittenPosLine = null
                workerThread = Thread(::runLoop, "rtkcollector-rtklib-worker").apply {
                    isDaemon = true
                    start()
                }
                true
            }
        }
        if (!attached) {
            try {
                createdBackend.close()
            } catch (error: Throwable) {
                synchronized(monitor) {
                    lastWarning = "RTKLIB backend close after shutdown failed: ${error.message ?: error::class.java.simpleName}"
                }
            }
            return RtklibStartResult.failed("RTKLIB worker was shut down while the backend was starting")
        }
        return RtklibStartResult.started()
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
            if (!accepting) {
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
            if (currentBytes + chunk.bytes.size > limit) {
                if (chunk.streamKind == RtklibInputStreamKind.ROVER) {
                    droppedRoverBytes += chunk.bytes.size.toLong()
                } else {
                    droppedCorrectionBytes += chunk.bytes.size.toLong()
                }
                state = RtklibEngineState.LAGGING
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
            val chunk = synchronized(monitor) {
                while (accepting && queue.isEmpty()) {
                    monitor.wait(250L)
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
                    state = RtklibEngineState.FAILED
                    lastError = error.message ?: error::class.java.simpleName
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
            val backendSnapshot = backend?.snapshot()
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
        val shutdownTarget = synchronized(monitor) {
            if (finalizing) return false
            val thread = workerThread ?: return@synchronized null
            accepting = false
            discardQueuedWorkLocked()
            monitor.notifyAll()
            val cancellation = cancellationThread ?: Thread(
                { requestBackendCancellation(backend) },
                "rtkcollector-rtklib-cancel",
            ).apply {
                isDaemon = true
                cancellationThread = this
                try {
                    start()
                } catch (error: Throwable) {
                    cancellationThread = null
                    lastWarning = "RTKLIB backend cancellation could not start: ${error.message ?: error::class.java.simpleName}"
                }
            }
            ShutdownTarget(thread, cancellation)
        }
        if (shutdownTarget == null) return finalizeInactiveResources()

        if (!awaitShutdownTargets(shutdownTarget, timeoutMillis)) {
            synchronized(monitor) {
                lastWarning = "RTKLIB worker shutdown timed out after ${timeoutMillis} ms"
            }
            return false
        }

        return finalizeTerminatedWorker(shutdownTarget.thread)
    }

    override fun stop() {
        check(shutdown(DEFAULT_SHUTDOWN_TIMEOUT_MILLIS)) {
            "RTKLIB worker did not terminate within ${DEFAULT_SHUTDOWN_TIMEOUT_MILLIS} ms"
        }
    }

    private fun finalizeTerminatedWorker(terminatedThread: Thread): Boolean {
        val backendToClose = synchronized(monitor) {
            if (
                workerThread !== terminatedThread ||
                terminatedThread.isAlive ||
                cancellationThread?.isAlive == true ||
                finalizing ||
                resourcesFinalized
            ) {
                return false
            }
            finalizing = true
            val activeBackend = backend
            backend = null
            activeBackend
        }

        finalizeOwnedResources(backendToClose, clearWorkerThread = true)
        return true
    }

    private fun finalizeInactiveResources(): Boolean {
        val backendToClose = synchronized(monitor) {
            if (workerThread != null || cancellationThread?.isAlive == true || finalizing) return false
            if (resourcesFinalized) return true
            finalizing = true
            val activeBackend = backend
            backend = null
            activeBackend
        }

        finalizeOwnedResources(backendToClose, clearWorkerThread = false)
        return true
    }

    private fun finalizeOwnedResources(backendToClose: RtklibBackend?, clearWorkerThread: Boolean) {
        try {
            try {
                backendToClose?.close()
            } catch (error: Throwable) {
                recordFinalizationFailure("RTKLIB backend close failed", error)
            }
            try {
                outputWriters.close()
            } catch (error: Throwable) {
                recordFinalizationFailure("RTKLIB output close failed", error)
            }
        } finally {
            synchronized(monitor) {
                if (clearWorkerThread) workerThread = null
                config = null
                cancellationThread = null
                resourcesFinalized = true
                finalizing = false
                if (state != RtklibEngineState.FAILED) state = RtklibEngineState.STOPPED
                monitor.notifyAll()
            }
        }
    }

    private fun recordFinalizationFailure(prefix: String, error: Throwable) {
        synchronized(monitor) {
            state = RtklibEngineState.FAILED
            lastError = "$prefix: ${error.message ?: error::class.java.simpleName}"
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

    private fun awaitShutdownTargets(target: ShutdownTarget, timeoutMillis: Long): Boolean {
        val deadlineNanos = System.nanoTime() + timeoutMillis * NANOS_PER_MILLISECOND
        return joinBefore(target.thread, deadlineNanos) && joinBefore(target.cancellationThread, deadlineNanos)
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

    private data class ShutdownTarget(
        val thread: Thread,
        val cancellationThread: Thread,
    )

    private companion object {
        const val DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 2_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_SHUTDOWN_TIMEOUT_MILLIS = Long.MAX_VALUE / NANOS_PER_MILLISECOND
    }
}
