package org.rtkcollector.receiver.unicore

import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

private const val UM980_FAMILY = "um980"

fun Um980Telemetry.toBestnavCandidate(nowMillis: Long): SolutionCandidate? {
    val lat = latDeg ?: return null
    val lon = lonDeg ?: return null
    val fix = bestnavFixClass(positionType) ?: return null
    val horizontal = horizontalAccuracyEstimateM()
    return SolutionCandidate(
        sourceId = "UM980-BESTNAV",
        receiverFamily = UM980_FAMILY,
        engine = SolutionEngine.DEVICE_INTERNAL,
        fixClass = fix,
        updatedAtMillis = nowMillis,
        utcTime = utcTime,
        latDeg = lat,
        lonDeg = lon,
        ellipsoidalHeightM = ellipsoidalHeightM,
        mslAltitudeM = altitudeM,
        horizontalAccuracyM = horizontal,
        verticalAccuracyM = verticalAccuracyM,
        satellitesUsed = satellitesUsed,
        satellitesInView = satellitesInView,
    )
}

fun Um980Telemetry.toPppCandidate(nowMillis: Long): SolutionCandidate? {
    val lat = latDeg ?: return null
    val lon = lonDeg ?: return null
    val fix = pppFixClass(positionType) ?: return null
    val horizontal = horizontalAccuracyEstimateM()
    return SolutionCandidate(
        sourceId = "UM980-PPP",
        receiverFamily = UM980_FAMILY,
        engine = SolutionEngine.RECEIVER_PPP,
        fixClass = fix,
        updatedAtMillis = nowMillis,
        utcTime = utcTime,
        latDeg = lat,
        lonDeg = lon,
        ellipsoidalHeightM = ellipsoidalHeightM,
        mslAltitudeM = altitudeM,
        horizontalAccuracyM = horizontal,
        verticalAccuracyM = verticalAccuracyM,
        satellitesUsed = satellitesUsed,
        satellitesInView = satellitesInView,
    )
}

fun Um980AsciiSolution.toBestnavCandidate(nowMillis: Long): SolutionCandidate? {
    val lat = latDeg ?: return null
    val lon = lonDeg ?: return null
    val fix = bestnavFixClass(positionType) ?: return null
    return SolutionCandidate(
        sourceId = "UM980-BESTNAV",
        receiverFamily = UM980_FAMILY,
        engine = SolutionEngine.DEVICE_INTERNAL,
        fixClass = fix,
        updatedAtMillis = nowMillis,
        latDeg = lat,
        lonDeg = lon,
        ellipsoidalHeightM = heightM,
        mslAltitudeM = null,
        horizontalAccuracyM = null,
        verticalAccuracyM = null,
    )
}

private fun bestnavFixClass(positionType: String?): FixClass? =
    when (positionType?.uppercase()) {
        null, "", "NONE" -> null
        "NARROW_INT", "WIDE_INT", "L1_INT", "INS_RTKFIXED" -> FixClass.RTK_FIXED
        "NARROW_FLOAT", "IONOFREE_FLOAT", "L1_FLOAT", "INS_RTKFLOAT" -> FixClass.RTK_FLOAT
        "PSRDIFF", "SBAS", "INS_PSRDIFF" -> FixClass.DGPS
        "SINGLE", "INS_PSRSP" -> FixClass.SINGLE
        "PPP" -> FixClass.PPP_CONVERGED
        "PPP_CONVERGING" -> FixClass.PPP_CONVERGING
        else -> FixClass.SINGLE
    }

private fun pppFixClass(positionType: String?): FixClass? =
    when (positionType?.uppercase()) {
        "PPP" -> FixClass.PPP_CONVERGED
        "PPP_CONVERGING" -> FixClass.PPP_CONVERGING
        else -> null
    }

private fun Um980Telemetry.horizontalAccuracyEstimateM(): Double? {
    val lat = latErrorM
    val lon = lonErrorM
    if (lat == null && lon == null) return null
    if (lat == null) return lon
    if (lon == null) return lat
    return kotlin.math.sqrt(lat * lat + lon * lon)
}
