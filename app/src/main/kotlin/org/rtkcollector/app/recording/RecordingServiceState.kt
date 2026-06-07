package org.rtkcollector.app.recording

data class RecordingServiceState(
    val running: Boolean = false,
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
)
