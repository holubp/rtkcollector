package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecordingAlarmPolicyTest {
    private val connection = AlarmCauseKey(AlarmCategory.CONNECTION_LOST, "receiver", "disconnected")
    private val corrections = AlarmCauseKey(AlarmCategory.RECORDING_INTERRUPTED, "corrections", "unavailable")
    private val storage = AlarmCauseKey(AlarmCategory.RECORDING_INTERRUPTED, "storage", "write-failed")
    private fun started(settings: AlarmSettingsSnapshot = AlarmSettingsSnapshot.defaults()) =
        RecordingAlarmPolicy().apply { accept(AlarmCommand.Begin(1, settings, 0)) }
    private fun valid(policy: RecordingAlarmPolicy, time: Long = 0, sequence: Long = 1) =
        policy.accept(AlarmCommand.Position(1, AlarmPositionEvidence("main", sequence, time, true)))

    @Test fun `all operational causes remain available to Home with visual notifications disabled`() {
        val defaults = AlarmSettingsSnapshot.defaults()
        val policy = started(defaults.copy(categories = defaults.categories.mapValues { it.value.copy(visual = false) }))
        policy.accept(AlarmCommand.CauseRaised(1, corrections, "corrections", 1))
        val state = policy.accept(AlarmCommand.CauseRaised(1, RecordingAlarmEvidence.RTKLIB, "processing", 2))
        assertTrue(state.visibleEpisodes.isEmpty())
        assertNotNull(state.audio)
        assertEquals(setOf(corrections, RecordingAlarmEvidence.RTKLIB), state.activeEpisodes.map { it.id.cause }.toSet())
    }

    @Test fun `initial acquisition never raises a position alarm`() {
        assertNull(started().accept(AlarmCommand.Tick(1, 600_000)).audio)
    }
    @Test fun `five second boundary raises and recovery automatically clears both outputs`() {
        val p = started(); valid(p)
        assertTrue(p.accept(AlarmCommand.Tick(1, 4_999)).visibleEpisodes.isEmpty())
        val lost = p.accept(AlarmCommand.Tick(1, 5_000))
        assertEquals(AlarmCategory.POSITION_LOST, lost.audio?.episodeId?.cause?.category)
        assertEquals(AlarmSoundChoice.BuiltIn(BuiltInAlarmSound.SINGLE_LOW), lost.audio?.sound)
        val recovered = valid(p, 5_001, 2)
        assertNull(recovered.audio); assertTrue(recovered.visibleEpisodes.isEmpty())
    }
    @Test fun `invalid and duplicate solutions do not keep old coordinates fresh`() {
        val p = started(); valid(p)
        p.accept(AlarmCommand.Position(1, AlarmPositionEvidence("main", 2, 4_000, false)))
        valid(p, 4_500, 1)
        assertNotNull(p.accept(AlarmCommand.Tick(1, 5_000)).audio)
    }
    @Test fun `connection masks position but reconnection without fix reveals it`() {
        val p = started(); valid(p)
        p.accept(AlarmCommand.CauseRaised(1, connection, "Receiver disconnected", 1_000))
        val lost = p.accept(AlarmCommand.Tick(1, 6_000))
        assertEquals(listOf(AlarmCategory.CONNECTION_LOST), lost.visibleEpisodes.map { it.id.cause.category })
        assertEquals(AlarmCategory.CONNECTION_LOST, lost.audio?.episodeId?.cause?.category)
        val resumed = p.accept(AlarmCommand.CauseRecovered(1, connection, 6_001))
        assertEquals(AlarmCategory.POSITION_LOST, resumed.audio?.episodeId?.cause?.category)
        assertTrue(valid(p, 6_002, 2).visibleEpisodes.isEmpty())
    }
    @Test fun `connection recovery clears its sound without acknowledgement`() {
        val p = started()
        p.accept(AlarmCommand.CauseRaised(1, connection, "Receiver disconnected", 10))
        val recovered = p.accept(AlarmCommand.CauseRecovered(1, connection, 20))
        assertNull(recovered.audio); assertTrue(recovered.visibleEpisodes.isEmpty())
    }
    @Test fun `old acknowledgement cannot silence a new terminal storage failure`() {
        val p = started(); valid(p)
        val a = p.accept(AlarmCommand.CauseRaised(1, corrections, "Corrections unavailable", 10))
        p.accept(AlarmCommand.Acknowledge(1, a.visibleEpisodes.map { it.id }.toSet(), 11))
        val terminal = p.accept(AlarmCommand.TerminalStop(1, storage, "Recording stopped", 12))
        assertTrue(terminal.terminalAlertPending); assertNotNull(terminal.audio)
        val staleAck = p.accept(AlarmCommand.Acknowledge(1, a.visibleEpisodes.map { it.id }.toSet(), 13))
        assertNotNull(staleAck.audio)
        val acked = p.accept(AlarmCommand.Acknowledge(1, terminal.visibleEpisodes.map { it.id }.toSet(), 14))
        assertNull(acked.audio); assertFalse(acked.terminalAlertPending)
        assertTrue(acked.visibleEpisodes.isEmpty())
        assertNull(p.accept(AlarmCommand.Tick(1, 100_000)).audio)
    }
    @Test fun `deliberate stop fences subsequent failure and position callbacks`() {
        val p = started(); valid(p)
        p.accept(AlarmCommand.UserStop(1, 1))
        assertNull(p.accept(AlarmCommand.TerminalStop(1, storage, "late failure", 2)).audio)
        valid(p, 3, 3)
        assertTrue(p.accept(AlarmCommand.Tick(1, 10_000)).visibleEpisodes.isEmpty())
    }
    @Test fun `fresh start resets arming and ignores old generation events`() {
        val p = started(); valid(p)
        p.accept(AlarmCommand.Begin(2, AlarmSettingsSnapshot.defaults(), 6_000))
        p.accept(AlarmCommand.CauseRaised(1, connection, "old", 6_001))
        assertNull(p.accept(AlarmCommand.Tick(2, 100_000)).audio)
    }
    @Test fun `visual and acoustic toggles and priority are independent`() {
        val s = AlarmSettingsSnapshot.defaults()
        val p = started(s.copy(categories = s.categories + (AlarmCategory.RECORDING_INTERRUPTED to
            s.categories.getValue(AlarmCategory.RECORDING_INTERRUPTED).copy(acoustic = false))))
        valid(p)
        p.accept(AlarmCommand.CauseRaised(1, corrections, "Unavailable", 1))
        val lost = p.accept(AlarmCommand.Tick(1, 5_000))
        assertEquals(AlarmCategory.POSITION_LOST, lost.audio?.episodeId?.cause?.category)
        assertEquals(2, lost.visibleEpisodes.size)
        val silentVisual = s.copy(categories = s.categories.mapValues { (_, v) -> v.copy(visual = false) })
        val result = p.accept(AlarmCommand.SettingsChanged(1, silentVisual, 5_001))
        assertTrue(result.visibleEpisodes.isEmpty()); assertNotNull(result.audio)
    }
    @Test fun `recovered cause creates a fresh unacknowledged episode on next loss`() {
        val p = started()
        val first = p.accept(AlarmCommand.CauseRaised(1, connection, "lost", 1))
        p.accept(AlarmCommand.Acknowledge(1, first.visibleEpisodes.map { it.id }.toSet(), 2))
        p.accept(AlarmCommand.CauseRecovered(1, connection, 3))
        val next = p.accept(AlarmCommand.CauseRaised(1, connection, "lost", 4))
        assertNotEquals(first.audio?.episodeId, next.audio?.episodeId)
        assertNotNull(next.audio)
    }
    @Test fun `invalid delay is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlarmSettingsSnapshot.defaults().copy(positionLossDelayMillis = 0)
        }
    }
}
