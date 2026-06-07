package org.rtkcollector.core.quality

data class QualityWarning(
    val code: String,
    val message: String,
)

data class RecordingQualitySnapshot(
    val elapsedSeconds: Long,
    val receiverRxBytes: Long,
    val txToReceiverBytes: Long,
    val correctionInputBytes: Long,
    val ntripState: String?,
    val ggaFixQuality: Int?,
    val bestnavPositionType: String?,
    val pppStatus: String?,
    val rtcmMessageCounts: Map<Int, Long>,
    val rawObservationRateHz: Double?,
    val warnings: List<QualityWarning>,
)
