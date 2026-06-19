package org.rtkcollector.core.rtklib

enum class RtklibEngineState {
    STOPPED,
    STARTING,
    RUNNING,
    LAGGING,
    FAILED,
}

enum class RtklibFixClass {
    NONE,
    SINGLE,
    DGPS,
    RTK_FLOAT,
    RTK_FIXED,
    PPP,
    INVALID,
}

data class RtklibSolutionSnapshot(
    val fixClass: RtklibFixClass,
    val timestampMillis: Long,
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
    val ellipsoidalHeightM: Double? = null,
    val horizontalAccuracyM: Double? = null,
    val verticalAccuracyM: Double? = null,
    val satellitesUsed: Int? = null,
)

data class RtklibNativeOutputBatch(
    val nmeaLines: List<String> = emptyList(),
    val posLines: List<String> = emptyList(),
    val solution: RtklibSolutionSnapshot? = null,
    val warning: String? = null,
)

data class RtklibEngineSnapshot(
    val state: RtklibEngineState = RtklibEngineState.STOPPED,
    val latestSolution: RtklibSolutionSnapshot? = null,
    val lastWarning: String? = null,
    val lastError: String? = null,
    val roverQueueBytes: Int = 0,
    val correctionQueueBytes: Int = 0,
    val droppedRoverBytes: Long = 0,
    val droppedCorrectionBytes: Long = 0,
    val decodedRoverEpochs: Long = 0,
    val decodedCorrectionMessages: Long = 0,
    val outputNmeaLines: Long = 0,
    val outputPosLines: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    val running: Boolean
        get() = state == RtklibEngineState.RUNNING || state == RtklibEngineState.LAGGING
}
