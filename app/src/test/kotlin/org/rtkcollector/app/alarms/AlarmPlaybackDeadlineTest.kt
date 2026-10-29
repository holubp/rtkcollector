package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlarmPlaybackDeadlineTest {
    private val selection = AlarmAudioSelection(AlarmEpisodeId(1, RecordingAlarmEvidence.CONNECTION, 1),
        AlarmSoundChoice.BuiltIn(BuiltInAlarmSound.TRIPLE_HIGH))
    @Test fun `provider or foreground hang expires independently of player callbacks`() {
        val deadline = AlarmPlaybackDeadline()
        deadline.select(selection, 0)
        assertNull(deadline.expire(29_999))
        assertEquals(selection, deadline.expire(30_000))
        assertFalse(deadline.allowed(selection))
        deadline.report(selection, AlarmEffectStatus.ACTIVE, 30_001)
        assertFalse(deadline.allowed(selection))
        assertNull(deadline.expire(100_000))
    }
    @Test fun `long healthy playback gets a new deadline when focus is lost`() {
        val deadline = AlarmPlaybackDeadline()
        deadline.select(selection, 0); deadline.report(selection, AlarmEffectStatus.ACTIVE, 1)
        assertNull(deadline.expire(1_000_000))
        deadline.report(selection, AlarmEffectStatus.PENDING, 1_000_000)
        assertNull(deadline.expire(1_029_999))
        assertEquals(selection, deadline.expire(1_030_000))
    }
    @Test fun `a stalled active player is detected without timing out healthy long audio`() {
        val deadline = AlarmPlaybackDeadline()
        deadline.select(selection, 0); deadline.report(selection, AlarmEffectStatus.ACTIVE, 1)
        assertNull(deadline.expire(1_000_000, 999_999))
        assertEquals(selection, deadline.expire(1_030_000, 1_000_000))
    }
}
