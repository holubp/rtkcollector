package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

class SelectedSolutionDisplayTest {
    @Test
    fun `um980 screen candidate is bestnav only`() {
        assertTrue(candidate("UM980-BESTNAV", "um980").isPrimaryScreenCandidateFor("um980"))
        assertFalse(candidate("NMEA-GGA", "um980").isPrimaryScreenCandidateFor("um980"))
        assertFalse(candidate("UM980-PPP", "um980").isPrimaryScreenCandidateFor("um980"))
    }

    @Test
    fun `ublox screen candidate accepts nav pvt source`() {
        assertTrue(candidate("UBX-NAV-PVT", "ublox-m8").isPrimaryScreenCandidateFor("ublox-m8t"))
        assertFalse(candidate("NMEA-GGA", "ublox-m8").isPrimaryScreenCandidateFor("ublox-m8t"))
    }

    private fun candidate(sourceId: String, family: String): SolutionCandidate =
        SolutionCandidate(
            sourceId = sourceId,
            receiverFamily = family,
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = FixClass.RTK_FIXED,
            updatedAtMillis = 1_000L,
            latDeg = 50.0,
            lonDeg = 14.0,
        )
}
