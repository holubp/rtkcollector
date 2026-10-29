package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlarmEpisodeEvidenceTest {
    @Test fun `repeated and terminal cleanup evidence preserves identity acknowledgement and first receipt`() {
        val policy = RecordingAlarmPolicy()
        policy.accept(AlarmCommand.Begin(1, AlarmSettingsSnapshot.defaults(), 0))
        val first = policy.accept(AlarmCommand.CauseRaised(1, RecordingAlarmEvidence.CONNECTION, "first", 10)).audio!!.episodeId
        val repeated = policy.accept(AlarmCommand.CauseRaised(1, RecordingAlarmEvidence.CONNECTION, "latest", 20))
        assertEquals(first, repeated.audio!!.episodeId)
        assertEquals(10L, policy.evidence(first)!!.firstElapsedMillis)
        assertEquals(20L, policy.evidence(first)!!.lastElapsedMillis)
        assertEquals("latest", policy.evidence(first)!!.detail)
        val terminal = policy.accept(AlarmCommand.TerminalStop(1, RecordingAlarmEvidence.STORAGE, "storage", 30)).audio!!.episodeId
        policy.accept(AlarmCommand.Acknowledge(1, setOf(terminal), 31))
        val updated = policy.accept(AlarmCommand.TerminalStop(1, RecordingAlarmEvidence.SERVICE, "close failure", 40))
        assertNull(updated.audio)
        assertTrue(updated.visibleEpisodes.isEmpty())
        assertEquals(30L, policy.evidence(terminal)!!.firstElapsedMillis)
        assertEquals(40L, policy.evidence(terminal)!!.lastElapsedMillis)
        assertEquals("close failure", policy.evidence(terminal)!!.detail)
    }
}
