package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.SolutionSourcePolicy
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine
import org.rtkcollector.core.solution.FixClass

class RecordingAlarmEvidenceTest {
    @Test fun `AUTO BEST RTKLIB arming and loss do not require a mock or display ticker`() {
        val selector = AlarmSolutionEvidence()
        val candidate = SolutionCandidate("RTKLIB", "ublox-m8t", SolutionEngine.RTKLIB_REALTIME,
            FixClass.RTK_FIXED, 1_000, latDeg = 49.0, lonDeg = 15.0)
        val policy = RecordingAlarmPolicy()
        policy.accept(AlarmCommand.Begin(1, AlarmSettingsSnapshot.defaults(), 0))
        val fresh = selector.accept(candidate, 1, 0, SolutionSourcePolicy.AUTO_BEST)!!
        policy.accept(AlarmCommand.Position(1, fresh))
        assertNull(selector.accept(candidate, 1, 4_999, SolutionSourcePolicy.AUTO_BEST))
        assertNotNull(policy.accept(AlarmCommand.Tick(1, 5_000)).audio)
        val recovered = selector.accept(candidate, 2, 5_001, SolutionSourcePolicy.AUTO_BEST)!!
        assertNull(policy.accept(AlarmCommand.Position(1, recovered)).audio)
        assertNull(selector.accept(candidate, 3, 5_002, SolutionSourcePolicy.DEVICE_INTERNAL_ONLY))
    }
    @Test fun `configured BESTNAV cannot be kept alive by unrelated GGA`() {
        val source = AlarmSourceSelector("um980", expectsBestnav = true)
        assertFalse(source.accept("NMEA-GGA"))
        assertTrue(source.accept("UM980-BESTNAV"))
        assertFalse(source.accept("NMEA-GGA"))
    }
    @Test fun `NMEA only and ublox receivers keep their actual selected source`() {
        val generic = AlarmSourceSelector("generic", false)
        assertTrue(generic.accept("NMEA-GGA"))
        val ublox = AlarmSourceSelector("ublox-m8t", false)
        assertTrue(ublox.accept("NMEA-GGA"))
        assertTrue(ublox.accept("UBX-NAV-PVT"))
        assertFalse(ublox.accept("NMEA-GGA"))
    }
    @Test fun `cached RTKLIB snapshots do not manufacture fresh observations`() {
        val tracker = AlarmRtklibEpochTracker()
        assertEquals(1L, tracker.accept(1_000))
        assertNull(tracker.accept(1_000))
        assertEquals(2L, tracker.accept(900))
        assertNull(tracker.accept(0))
    }
    @Test fun `selected engine is respected independently of mock publishing`() {
        assertTrue(RecordingAlarmEvidence.matchesMainEngine(true, SolutionSourcePolicy.RTKLIB_ONLY, false))
        assertFalse(RecordingAlarmEvidence.matchesMainEngine(false, SolutionSourcePolicy.RTKLIB_ONLY, false))
        assertTrue(RecordingAlarmEvidence.matchesMainEngine(false, SolutionSourcePolicy.DEVICE_INTERNAL_ONLY, true))
        assertFalse(RecordingAlarmEvidence.matchesMainEngine(true, SolutionSourcePolicy.AUTO_BEST, false))
        assertFalse(RecordingAlarmEvidence.matchesMainEngine(false, SolutionSourcePolicy.OFF, false))
    }
}
