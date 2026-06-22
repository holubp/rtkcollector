package org.rtkcollector.app.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rtkcollector.core.rtklib.RtklibFixClass
import org.rtkcollector.core.rtklib.RtklibSolutionSnapshot
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionEngine

class RtklibSolutionCandidateMapperTest {
    @Test
    fun `rtklib fixed solution becomes realtime solution candidate`() {
        val candidate = requireNotNull(
            RtklibSolutionCandidateMapper.toCandidate(
                snapshot = RtklibSolutionSnapshot(
                    fixClass = RtklibFixClass.RTK_FIXED,
                    timestampMillis = 1_000L,
                    latDeg = 50.123456,
                    lonDeg = 14.654321,
                    ellipsoidalHeightM = 321.5,
                    horizontalAccuracyM = 0.012,
                    verticalAccuracyM = 0.034,
                    satellitesUsed = 15,
                ),
                receiverFamily = "ublox-m8t",
            ),
        )

        assertEquals("RTKLIB", candidate.sourceId)
        assertEquals("ublox-m8t", candidate.receiverFamily)
        assertEquals(SolutionEngine.RTKLIB_REALTIME, candidate.engine)
        assertEquals(FixClass.RTK_FIXED, candidate.fixClass)
        assertEquals(1_000L, candidate.updatedAtMillis)
        assertEquals(50.123456, candidate.latDeg)
        assertEquals(14.654321, candidate.lonDeg)
        assertEquals(321.5, candidate.ellipsoidalHeightM)
        assertEquals(0.012, candidate.horizontalAccuracyM)
        assertEquals(0.034, candidate.verticalAccuracyM)
        assertEquals(15, candidate.satellitesUsed)
    }

    @Test
    fun `rtklib float and dgps map to common fix classes`() {
        assertEquals(
            FixClass.RTK_FLOAT,
            candidateFor(RtklibFixClass.RTK_FLOAT)?.fixClass,
        )
        assertEquals(
            FixClass.DGPS,
            candidateFor(RtklibFixClass.DGPS)?.fixClass,
        )
        assertEquals(
            FixClass.SINGLE,
            candidateFor(RtklibFixClass.SINGLE)?.fixClass,
        )
        assertEquals(
            FixClass.PPP_CONVERGED,
            candidateFor(RtklibFixClass.PPP)?.fixClass,
        )
    }

    @Test
    fun `invalid or coordinate-less rtklib snapshots are not candidates`() {
        assertNull(candidateFor(RtklibFixClass.NONE))
        assertNull(candidateFor(RtklibFixClass.INVALID))
        assertNull(
            RtklibSolutionCandidateMapper.toCandidate(
                snapshot = RtklibSolutionSnapshot(
                    fixClass = RtklibFixClass.RTK_FIXED,
                    timestampMillis = 1_000L,
                    latDeg = null,
                    lonDeg = 14.0,
                ),
                receiverFamily = "ublox-m8t",
            ),
        )
    }

    private fun candidateFor(fix: RtklibFixClass) =
        RtklibSolutionCandidateMapper.toCandidate(
            snapshot = RtklibSolutionSnapshot(
                fixClass = fix,
                timestampMillis = 1_000L,
                latDeg = 50.0,
                lonDeg = 14.0,
            ),
            receiverFamily = "ublox-m8t",
        )
}
