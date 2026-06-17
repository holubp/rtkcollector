package org.rtkcollector.receiver.unicore

data class Um980Telemetry(
    val source: String,
    val utcTime: String? = null,
    val solutionStatus: String? = null,
    val positionType: String? = null,
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
    val altitudeM: Double? = null,
    val ellipsoidalHeightM: Double? = null,
    val latErrorM: Double? = null,
    val lonErrorM: Double? = null,
    val verticalAccuracyM: Double? = null,
    val satellitesInView: Int? = null,
    val satellitesUsed: Int? = null,
    val satellitesTracked: Int? = null,
    val gdop: Double? = null,
    val pdop: Double? = null,
    val tdop: Double? = null,
    val hdop: Double? = null,
    val vdop: Double? = null,
    val ndop: Double? = null,
    val edop: Double? = null,
    val cutoffDeg: Double? = null,
    val differentialAgeS: Double? = null,
    val solutionAgeS: Double? = null,
    val baselineLengthM: Double? = null,
    val horizontalSpeedMps: Double? = null,
    val trackDeg: Double? = null,
    val verticalSpeedMps: Double? = null,
    val stationId: String? = null,
    val rtkPositionType: String? = null,
    val rtkCalculateStatus: Int? = null,
    val rtkCalculateStatusDescription: String? = null,
    val ionDetected: Boolean? = null,
    val adrNumber: Int? = null,
    val gpsSource: Int? = null,
    val bdsSource1: Int? = null,
    val bdsSource2: Int? = null,
    val gloSource: Int? = null,
    val galSource1: Int? = null,
    val galSource2: Int? = null,
    val qzssSource: Int? = null,
    val rtcmMessageId: Int? = null,
    val rtcmMessageCount: Int? = null,
    val rtcmBaseId: Int? = null,
    val rtcmSatelliteCount: Int? = null,
    val rtcmObservableCounts: List<Int> = emptyList(),
)

fun Um980Telemetry.pppStatusLabel(): String? =
    um980PppStatusLabel(solutionStatus = solutionStatus, positionType = positionType)

fun um980PppStatusLabel(solutionStatus: String?, positionType: String?): String? {
    val type = positionType?.trim()?.uppercase()?.takeIf(String::isNotBlank)
    val status = solutionStatus?.trim()?.uppercase()?.takeIf(String::isNotBlank)
    return when {
        type == "PPP" -> "PPP converged"
        type == "PPP_CONVERGING" -> "PPP converging"
        type == null && status == null -> null
        type == "NONE" && status == "INSUFFICIENT_OBS" -> "PPP not started"
        type == "NONE" && status == "NO_CONVERGENCE" -> "PPP no convergence"
        type == "NONE" -> status?.let { "PPP ${it.displayToken()}" } ?: "PPP no solution"
        status != null && status != "SOL_COMPUTED" -> "PPP ${status.displayToken()}"
        else -> positionType
    }
}

private fun String.displayToken(): String =
    lowercase()
        .split('_')
        .filter(String::isNotBlank)
        .joinToString(" ") { token -> token.replaceFirstChar { it.uppercaseChar() } }
