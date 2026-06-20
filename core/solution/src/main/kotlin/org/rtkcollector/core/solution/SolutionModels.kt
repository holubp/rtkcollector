package org.rtkcollector.core.solution

enum class SolutionEngine {
    DEVICE_INTERNAL,
    RECEIVER_PPP,
    RTKLIB_REALTIME,
    GENERIC_NMEA,
}

enum class SolutionSourcePolicy {
    AUTO_BEST,
    DEVICE_INTERNAL_ONLY,
    RTKLIB_ONLY,
    OFF,
}

enum class FixClass(val rank: Int) {
    NONE(0),
    PPP_CONVERGING(1),
    SINGLE(2),
    DGPS(3),
    SBAS(3),
    PPP_CONVERGED(4),
    RTK_FLOAT(5),
    RTK_FIXED(6),
}

data class SolutionCandidate(
    val sourceId: String,
    val receiverFamily: String,
    val engine: SolutionEngine,
    val fixClass: FixClass,
    val updatedAtMillis: Long,
    val utcTime: String? = null,
    val latDeg: Double,
    val lonDeg: Double,
    val ellipsoidalHeightM: Double? = null,
    val mslAltitudeM: Double? = null,
    val horizontalAccuracyM: Double? = null,
    val verticalAccuracyM: Double? = null,
    val satellitesUsed: Int? = null,
    val satellitesInView: Int? = null,
)

data class BestSolutionSnapshot(
    val sourceId: String,
    val receiverFamily: String,
    val engine: SolutionEngine,
    val fixClass: FixClass,
    val updatedAtMillis: Long,
    val utcTime: String?,
    val latDeg: Double,
    val lonDeg: Double,
    val ellipsoidalHeightM: Double?,
    val mslAltitudeM: Double?,
    val horizontalAccuracyM: Double?,
    val verticalAccuracyM: Double?,
    val satellitesUsed: Int?,
    val satellitesInView: Int?,
    val ageMillis: Long,
) {
    val isFresh: Boolean
        get() = isFreshFor(BestSolutionSelector.DEFAULT_MAX_AGE_MILLIS)

    fun isFreshFor(maxAgeMillis: Long): Boolean = ageMillis <= maxAgeMillis
}
