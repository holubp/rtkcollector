package org.rtkcollector.app.recording

enum class RecordingLifecycleState {
    IDLE,
    STARTING,
    RECORDING,
    STOPPING,
    STOPPED,
    FAILED,
}

enum class RecordingErrorCategory {
    NONE,
    USB,
    STORAGE,
    NTRIP,
    RECEIVER_COMMAND,
    PARSER_EXPORT,
    SERVICE_LIFECYCLE,
}

enum class RecordingErrorSeverity {
    NONE,
    INFO,
    DEGRADED,
    FATAL,
}

data class RecordingServiceState(
    val running: Boolean = false,
    val lifecycle: RecordingLifecycleState = RecordingLifecycleState.IDLE,
    val sessionPath: String? = null,
    val receiverRxBytes: Long = 0,
    val txToReceiverBytes: Long = 0,
    val correctionInputBytes: Long = 0,
    val ntripState: String = "Not configured",
    val ggaFixQuality: Int? = null,
    val bestnavPositionType: String? = null,
    val pppStatus: String? = null,
    val rtcmFrames: Long = 0,
    val lastError: String? = null,
    val errorCategory: RecordingErrorCategory = RecordingErrorCategory.NONE,
    val errorSeverity: RecordingErrorSeverity = RecordingErrorSeverity.NONE,
    val rawRecordingActive: Boolean = false,
    val correctionsActive: Boolean = false,
)
