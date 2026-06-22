package org.rtkcollector.app.recording

import org.rtkcollector.core.rtklib.RtklibFixClass
import org.rtkcollector.core.rtklib.RtklibSolutionSnapshot
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

internal object RtklibSolutionCandidateMapper {
    private const val SOURCE_ID = "RTKLIB"

    fun toCandidate(
        snapshot: RtklibSolutionSnapshot,
        receiverFamily: String,
    ): SolutionCandidate? {
        val lat = snapshot.latDeg ?: return null
        val lon = snapshot.lonDeg ?: return null
        val fix = snapshot.fixClass.toCommonFixClass() ?: return null
        return SolutionCandidate(
            sourceId = SOURCE_ID,
            receiverFamily = receiverFamily,
            engine = SolutionEngine.RTKLIB_REALTIME,
            fixClass = fix,
            updatedAtMillis = snapshot.timestampMillis,
            latDeg = lat,
            lonDeg = lon,
            ellipsoidalHeightM = snapshot.ellipsoidalHeightM,
            horizontalAccuracyM = snapshot.horizontalAccuracyM,
            verticalAccuracyM = snapshot.verticalAccuracyM,
            satellitesUsed = snapshot.satellitesUsed,
        )
    }

    private fun RtklibFixClass.toCommonFixClass(): FixClass? =
        when (this) {
            RtklibFixClass.NONE,
            RtklibFixClass.INVALID -> null
            RtklibFixClass.SINGLE -> FixClass.SINGLE
            RtklibFixClass.DGPS -> FixClass.DGPS
            RtklibFixClass.RTK_FLOAT -> FixClass.RTK_FLOAT
            RtklibFixClass.RTK_FIXED -> FixClass.RTK_FIXED
            RtklibFixClass.PPP -> FixClass.PPP_CONVERGED
        }
}
