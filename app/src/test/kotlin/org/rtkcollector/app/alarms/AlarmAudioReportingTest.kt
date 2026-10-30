package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlarmAudioReportingTest {
    private fun selection(revision: Long) = AlarmAudioSelection(AlarmEpisodeId(1,
        RecordingAlarmEvidence.CONNECTION, revision), AlarmSettingsSnapshot.defaultSound(AlarmCategory.CONNECTION_LOST))
    private fun state(revision: Long, audio: AlarmAudioSelection?) = AlarmDesiredState(1, revision, emptyList(), audio, false)

    @Test fun `blocked A failure cannot be relabeled as newer episode B`() {
        val a = selection(1); val b = selection(2)
        val origin = AlarmAudioReportOrigin(state(1, a), a)
        assertTrue(origin.accepts(state(1, a), a))
        assertFalse(origin.accepts(state(2, b), b))
        assertEquals(1L to 1L, origin.serviceIdentity())
    }
    @Test fun `cancel and new preview reject old report even when real revision is unchanged`() {
        val a = selection(1); val b = selection(2)
        val origin = AlarmAudioReportOrigin(state(1, a), a)
        assertFalse(origin.accepts(state(1, null), null))
        val preview = AlarmAudioReportOrigin(state(1, null), a)
        assertNull(preview.serviceIdentity())
        assertFalse(preview.accepts(state(1, null), b))
    }
    @Test fun `failure abandons focus before possibly blocked or throwing native stop`() {
        val calls = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            cleanupFailedAlarmPlayback({ calls.add("focus released") }, { calls.add("native stop"); error("blocked release") })
        }
        assertEquals(listOf("focus released", "native stop"), calls)
    }
}
