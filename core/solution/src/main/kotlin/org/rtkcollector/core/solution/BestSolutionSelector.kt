package org.rtkcollector.core.solution

object BestSolutionSelector {
    const val DEFAULT_MAX_AGE_MILLIS: Long = 5_000L

    fun select(
        candidates: Iterable<SolutionCandidate>,
        nowMillis: Long,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    ): BestSolutionSnapshot? =
        candidates
            .filter { it.fixClass != FixClass.NONE }
            .filter { nowMillis >= it.updatedAtMillis }
            .filter { nowMillis - it.updatedAtMillis <= maxAgeMillis }
            .maxWithOrNull(compareBy<SolutionCandidate> { it.fixClass.rank }
                .thenByDescending { it.horizontalAccuracyM ?: Double.MAX_VALUE }
                .thenBy { it.updatedAtMillis })
            ?.toSnapshot(nowMillis)

    private fun SolutionCandidate.toSnapshot(nowMillis: Long): BestSolutionSnapshot =
        BestSolutionSnapshot(
            sourceId = sourceId,
            receiverFamily = receiverFamily,
            engine = engine,
            fixClass = fixClass,
            updatedAtMillis = updatedAtMillis,
            utcTime = utcTime,
            latDeg = latDeg,
            lonDeg = lonDeg,
            ellipsoidalHeightM = ellipsoidalHeightM,
            mslAltitudeM = mslAltitudeM,
            horizontalAccuracyM = horizontalAccuracyM,
            verticalAccuracyM = verticalAccuracyM,
            satellitesUsed = satellitesUsed,
            satellitesInView = satellitesInView,
            ageMillis = nowMillis - updatedAtMillis,
        )
}
