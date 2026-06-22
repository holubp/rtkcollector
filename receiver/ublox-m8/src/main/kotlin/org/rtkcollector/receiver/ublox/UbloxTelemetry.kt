package org.rtkcollector.receiver.ublox

import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

data class UbloxTelemetry(
    val source: String,
    val updatedAtMillis: Long,
    val fixClass: FixClass? = null,
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
    val ellipsoidalHeightM: Double? = null,
    val mslAltitudeM: Double? = null,
    val horizontalAccuracyM: Double? = null,
    val verticalAccuracyM: Double? = null,
    val satellitesUsed: Int? = null,
    val utcTime: String? = null,
    val rawObservationsPresent: Boolean = false,
) {
    fun toSolutionCandidate(satellitesInView: Int? = null): SolutionCandidate? {
        val lat = latDeg ?: return null
        val lon = lonDeg ?: return null
        val fix = fixClass ?: return null
        return SolutionCandidate(
            sourceId = source,
            receiverFamily = "ublox-m8",
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = fix,
            updatedAtMillis = updatedAtMillis,
            latDeg = lat,
            lonDeg = lon,
            ellipsoidalHeightM = ellipsoidalHeightM,
            mslAltitudeM = mslAltitudeM,
            horizontalAccuracyM = horizontalAccuracyM,
            verticalAccuracyM = verticalAccuracyM,
            satellitesUsed = satellitesUsed,
            satellitesInView = satellitesInView,
            utcTime = utcTime,
        )
    }
}

data class UbloxNavSatTelemetry(
    val updatedAtMillis: Long,
    val satellitesInView: Int,
    val satellitesUsed: Int,
)

data class UbloxNavDopTelemetry(
    val updatedAtMillis: Long,
    val gdop: Double,
    val pdop: Double,
    val tdop: Double,
    val vdop: Double,
    val hdop: Double,
    val ndop: Double,
    val edop: Double,
)
