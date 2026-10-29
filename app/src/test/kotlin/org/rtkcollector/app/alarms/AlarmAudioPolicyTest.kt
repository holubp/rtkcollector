package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlarmAudioPolicyTest {
    private fun selection(revision: Long = 1) = AlarmAudioSelection(
        AlarmEpisodeId(1, RecordingAlarmEvidence.CONNECTION, revision),
        AlarmSoundChoice.BuiltIn(BuiltInAlarmSound.TRIPLE_HIGH))
    @Test fun `focus denial has bounded retries and late gain cannot revive expired sound`() {
        val p = AlarmAudioPolicy(); val s = selection()
        assertTrue(p.decide(s, 0, AlarmFocusState.WAITING).requestFocus)
        assertFalse(p.decide(s, 999, AlarmFocusState.WAITING).requestFocus)
        assertTrue(p.decide(s, 1_000, AlarmFocusState.WAITING).requestFocus)
        assertFalse(p.decide(s, 29_999, AlarmFocusState.WAITING).unavailable)
        assertTrue(p.decide(s, 30_000, AlarmFocusState.WAITING).unavailable)
        assertFalse(p.decide(s, 30_001, AlarmFocusState.AVAILABLE).play)
        assertTrue(p.decide(selection(2), 30_002, AlarmFocusState.AVAILABLE).play)
    }
    @Test fun `transient loss deadline starts at loss not at beginning of long playback`() {
        val p = AlarmAudioPolicy(); val s = selection()
        assertTrue(p.decide(s, 0, AlarmFocusState.AVAILABLE).play)
        assertTrue(p.decide(s, 100_000, AlarmFocusState.AVAILABLE).play)
        assertFalse(p.decide(s, 200_000, AlarmFocusState.WAITING).unavailable)
        assertTrue(p.decide(s, 200_100, AlarmFocusState.AVAILABLE).play)
        assertFalse(p.decide(null, 200_101, AlarmFocusState.AVAILABLE).play)
    }
    @Test fun `permanent focus loss does not retry the same episode`() {
        val p = AlarmAudioPolicy(); val s = selection()
        assertTrue(p.decide(s, 0, AlarmFocusState.PERMANENTLY_LOST).unavailable)
        assertFalse(p.decide(s, 1, AlarmFocusState.AVAILABLE).play)
    }
    @Test fun `network and file sound URIs are rejected`() {
        assertFalse(isAlarmSoundUri("https://example.org/a.wav"))
        assertFalse(isAlarmSoundUri("file:///storage/emulated/0/private"))
        assertTrue(isAlarmSoundUri("content://settings/system/notification_sound"))
    }
}
