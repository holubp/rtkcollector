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
    private var state = RtklibEngineState.STOPPED
    private var config: RtklibConfig? = null

    override fun start(config: RtklibConfig): RtklibStartResult {
        synchronized(monitor) {
            if (accepting) return RtklibStartResult.failed("RTKLIB worker is already running")
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

        synchronized(monitor) {
            backend = createdBackend
            accepting = true
            state = RtklibEngineState.RUNNING
            workerThread = Thread(::runLoop, "rtkcollector-rtklib-worker").apply {
                isDaemon = true
                start()
            }
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
                    queue.clear()
                    roverQueueBytes = 0
                    correctionQueueBytes = 0
                    monitor.notifyAll()
                }
                continue
            }

            val activeConfig = synchronized(monitor) { config } ?: continue
            val writableBatch = RtklibSolutionOutputSynthesizer
                .withSyntheticOutputsIfNeeded(
                    batch = batch,
                    outputNmea = activeConfig.outputNmea,
                    outputPos = activeConfig.outputPos,
                )
                .filteredFor(activeConfig)

            try {
                outputWriters.write(writableBatch)
            } catch (error: Throwable) {
                synchronized(monitor) {
                    state = RtklibEngineState.FAILED
                    lastError = "RTKLIB output write failed: ${error.message ?: error::class.java.simpleName}"
                    accepting = false
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

    private fun subtractQueuedBytes(chunk: RtklibInputChunk) {
        if (chunk.streamKind == RtklibInputStreamKind.ROVER) {
            roverQueueBytes -= chunk.bytes.size
        } else {
            correctionQueueBytes -= chunk.bytes.size
        }
    }

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

    override fun stop() {
        val threadToJoin = synchronized(monitor) {
            if (!accepting && state == RtklibEngineState.STOPPED) return
            accepting = false
            monitor.notifyAll()
            workerThread
        }
        threadToJoin?.join(2_000L)
        synchronized(monitor) {
            backend?.stop()
            backend?.close()
            backend = null
            workerThread = null
            queue.clear()
            roverQueueBytes = 0
            correctionQueueBytes = 0
            if (state != RtklibEngineState.FAILED) state = RtklibEngineState.STOPPED
        }
        outputWriters.close()
    }
}
