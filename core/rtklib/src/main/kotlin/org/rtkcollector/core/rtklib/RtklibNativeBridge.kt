package org.rtkcollector.core.rtklib

class RtklibNativeBridge(
    private val loadLibrary: () -> Unit = { System.loadLibrary(LIBRARY_NAME) },
    private val nativeApi: NativeApi = JniNativeApi,
) : RtklibBackendFactory {
    override fun create(): RtklibBackend {
        loadLibrary()
        return NativeBackend(nativeApi)
    }

    interface NativeApi {
        fun version(): String
        fun create(): Long
        fun start(
            handle: Long,
            preset: String,
            roverFormat: String,
            correctionFormat: String,
            outputNmea: Boolean,
            outputPos: Boolean,
            frequencyCount: Int,
            serverCycleMillis: Int,
            serverBufferBytes: Int,
            solutionBufferBytes: Int,
        ): String?
        fun feed(handle: Long, streamKind: Int, bytes: ByteArray): Array<String>
        fun snapshot(handle: Long): Array<String>
        fun stop(handle: Long)
        fun destroy(handle: Long)
    }

    private class NativeBackend(private val api: NativeApi) : RtklibBackend {
        private var handle: Long = 0L
        private var closed = false

        override fun start(config: RtklibConfig): RtklibStartResult {
            if (closed) return RtklibStartResult.failed("RTKLIB native backend is closed")
            if (handle == 0L) {
                handle = api.create()
                if (handle == 0L) return RtklibStartResult.failed("RTKLIB native engine allocation failed")
            }
            val error = api.start(
                handle = handle,
                preset = config.preset.name,
                roverFormat = config.routePlan.roverInput.format?.name.orEmpty(),
                correctionFormat = config.routePlan.correctionInput.format?.name.orEmpty(),
                outputNmea = config.outputNmea,
                outputPos = config.outputPos,
                frequencyCount = config.frequencyCount,
                serverCycleMillis = config.serverCycleMillis,
                serverBufferBytes = config.serverBufferBytes,
                solutionBufferBytes = config.solutionBufferBytes,
            )
            return if (error.isNullOrBlank()) {
                RtklibStartResult.started()
            } else {
                RtklibStartResult.failed(error)
            }
        }

        override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
            if (closed || handle == 0L) {
                return RtklibNativeOutputBatch(warning = "RTKLIB native backend is not open")
            }
            return api.feed(
                handle = handle,
                streamKind = chunk.streamKind.nativeOrdinal(),
                bytes = chunk.bytes,
            ).toOutputBatch()
        }

        override fun snapshot(): RtklibEngineSnapshot {
            if (closed || handle == 0L) {
                return RtklibEngineSnapshot(
                    state = RtklibEngineState.STOPPED,
                    lastWarning = "RTKLIB native backend is not open",
                )
            }
            return api.snapshot(handle).toSnapshot()
        }

        override fun stop() {
            if (!closed && handle != 0L) {
                api.stop(handle)
            }
        }

        override fun close() {
            if (!closed) {
                runCatching { stop() }
                if (handle != 0L) {
                    api.destroy(handle)
                    handle = 0L
                }
                closed = true
            }
        }
    }

    private object JniNativeApi : NativeApi {
        override fun version(): String = nativeRtklibVersion()
        override fun create(): Long = nativeRtklibCreate()
        override fun start(
            handle: Long,
            preset: String,
            roverFormat: String,
            correctionFormat: String,
            outputNmea: Boolean,
            outputPos: Boolean,
            frequencyCount: Int,
            serverCycleMillis: Int,
            serverBufferBytes: Int,
            solutionBufferBytes: Int,
        ): String? = nativeRtklibStart(
            handle,
            preset,
            roverFormat,
            correctionFormat,
            outputNmea,
            outputPos,
            frequencyCount,
            serverCycleMillis,
            serverBufferBytes,
            solutionBufferBytes,
        )
        override fun feed(handle: Long, streamKind: Int, bytes: ByteArray): Array<String> =
            nativeRtklibFeed(handle, streamKind, bytes)
        override fun snapshot(handle: Long): Array<String> = nativeRtklibSnapshot(handle)
        override fun stop(handle: Long) = nativeRtklibStop(handle)
        override fun destroy(handle: Long) = nativeRtklibDestroy(handle)
    }

    companion object {
        const val LIBRARY_NAME: String = "rtkcollector_rtklib"
    }
}

private external fun nativeRtklibVersion(): String
private external fun nativeRtklibCreate(): Long
private external fun nativeRtklibStart(
    handle: Long,
    preset: String,
    roverFormat: String,
    correctionFormat: String,
    outputNmea: Boolean,
    outputPos: Boolean,
    frequencyCount: Int,
    serverCycleMillis: Int,
    serverBufferBytes: Int,
    solutionBufferBytes: Int,
): String?
private external fun nativeRtklibFeed(handle: Long, streamKind: Int, bytes: ByteArray): Array<String>
private external fun nativeRtklibSnapshot(handle: Long): Array<String>
private external fun nativeRtklibStop(handle: Long)
private external fun nativeRtklibDestroy(handle: Long)

private fun RtklibInputStreamKind.nativeOrdinal(): Int =
    when (this) {
        RtklibInputStreamKind.ROVER -> 0
        RtklibInputStreamKind.CORRECTION -> 1
    }

private fun Array<String>.toOutputBatch(): RtklibNativeOutputBatch {
    val parsed = NativeResult.from(this)
    return RtklibNativeOutputBatch(
        nmeaLines = parsed.nmeaLines,
        posLines = parsed.posLines,
        solution = parsed.solution,
        warning = parsed.warning,
    )
}

private fun Array<String>.toSnapshot(): RtklibEngineSnapshot {
    val parsed = NativeResult.from(this)
    return RtklibEngineSnapshot(
        state = parsed.state,
        latestSolution = parsed.solution,
        lastWarning = parsed.warning,
        lastError = parsed.error,
        decodedRoverEpochs = parsed.decodedRoverEpochs,
        decodedCorrectionMessages = parsed.decodedCorrectionMessages,
        serverRoverObservationMessages = parsed.decodedRoverEpochs,
        serverBaseObservationMessages = parsed.decodedCorrectionMessages,
    )
}

private data class NativeResult(
    val state: RtklibEngineState,
    val warning: String?,
    val error: String?,
    val nmeaLines: List<String>,
    val posLines: List<String>,
    val solution: RtklibSolutionSnapshot?,
    val decodedRoverEpochs: Long,
    val decodedCorrectionMessages: Long,
) {
    companion object {
        fun from(values: Array<String>): NativeResult {
            fun value(index: Int): String = values.getOrNull(index).orEmpty()
            val fixClass = value(5).takeIf(String::isNotBlank)?.let {
                runCatching { RtklibFixClass.valueOf(it) }.getOrDefault(RtklibFixClass.INVALID)
            }
            val timestampMillis = value(6).toLongOrNull()
            val solution = if (fixClass != null && timestampMillis != null) {
                RtklibSolutionSnapshot(
                    fixClass = fixClass,
                    timestampMillis = timestampMillis,
                    latDeg = value(7).toDoubleOrNull(),
                    lonDeg = value(8).toDoubleOrNull(),
                    ellipsoidalHeightM = value(9).toDoubleOrNull(),
                    horizontalAccuracyM = value(10).toDoubleOrNull(),
                    verticalAccuracyM = value(11).toDoubleOrNull(),
                    satellitesUsed = value(12).toIntOrNull(),
                )
            } else {
                null
            }
            return NativeResult(
                state = runCatching { RtklibEngineState.valueOf(value(0)) }.getOrDefault(RtklibEngineState.FAILED),
                warning = value(1).takeIf(String::isNotBlank),
                error = value(2).takeIf(String::isNotBlank),
                nmeaLines = value(3).lineSequence().filter(String::isNotBlank).toList(),
                posLines = value(4).lineSequence().filter(String::isNotBlank).toList(),
                solution = solution,
                decodedRoverEpochs = value(13).toLongOrNull() ?: 0L,
                decodedCorrectionMessages = value(14).toLongOrNull() ?: 0L,
            )
        }
    }
}
