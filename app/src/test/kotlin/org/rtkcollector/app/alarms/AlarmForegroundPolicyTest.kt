package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlarmForegroundPolicyTest {
    @Test fun `capture and playback types do not consume the data sync time budget`() {
        assertEquals(16, AlarmForegroundPolicy.types(true, false))
        assertEquals(2, AlarmForegroundPolicy.types(false, true))
        assertEquals(18, AlarmForegroundPolicy.types(true, true))
        assertEquals(0, AlarmForegroundPolicy.types(false, false))
    }
    @Test fun `finalization retains recording ownership regardless of audio failure`() {
        assertEquals(AlarmServiceMode.RECORDING, AlarmForegroundPolicy.mode(true, true, AlarmEffectStatus.FAILED, true, false))
    }
    @Test fun `terminal shell requires eligible active or bounded pending audio`() {
        assertEquals(AlarmServiceMode.ALERT_ONLY, AlarmForegroundPolicy.mode(false, true, AlarmEffectStatus.ACTIVE, true, false))
        assertEquals(AlarmServiceMode.ALERT_ONLY, AlarmForegroundPolicy.mode(false, true, AlarmEffectStatus.PENDING, true, true))
        assertEquals(AlarmServiceMode.IDLE, AlarmForegroundPolicy.mode(false, true, AlarmEffectStatus.PENDING, true, false))
        assertEquals(AlarmServiceMode.IDLE, AlarmForegroundPolicy.mode(false, true, AlarmEffectStatus.FAILED, true, true))
        assertEquals(AlarmServiceMode.IDLE, AlarmForegroundPolicy.mode(false, true, AlarmEffectStatus.ACTIVE, false, true))
        assertEquals(AlarmServiceMode.IDLE, AlarmForegroundPolicy.mode(false, false, AlarmEffectStatus.ACTIVE, true, true))
    }
    @Test fun `valid coordinate requires finite in-range coordinates and usable fix`() {
        assertTrue(RecordingAlarmEvidence.validPosition(0.0, 0.0, true))
        assertFalse(RecordingAlarmEvidence.validPosition(Double.NaN, 1.0, true))
        assertFalse(RecordingAlarmEvidence.validPosition(91.0, 1.0, true))
        assertFalse(RecordingAlarmEvidence.validPosition(49.0, 15.0, false))
        assertFalse(RecordingAlarmEvidence.validPosition(null, 15.0, true))
    }
}
