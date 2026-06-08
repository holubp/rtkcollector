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
    val pdop: Double? = null,
    val hdop: Double? = null,
    val vdop: Double? = null,
    val differentialAgeS: Double? = null,
    val baselineLengthM: Double? = null,
    val stationId: String? = null,
)
