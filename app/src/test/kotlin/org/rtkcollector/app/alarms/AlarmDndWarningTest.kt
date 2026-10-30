package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlarmDndWarningTest {
    @Test fun `confirmed silence requires recording and acoustic alarms`() {
        for (recording in listOf(false, true)) for (acoustic in listOf(false, true)) {
            for ((volume, mute) in listOf(0 to false, 5 to true, null to true)) {
                val warning = alarmAudibilityWarning(recording, acoustic,
                    AlarmAudioEnvironment(volume, mute, android.app.NotificationManager.INTERRUPTION_FILTER_ALL))
                assertEquals(recording && acoustic, warning != null)
                if (warning != null) assertTrue(warning.contains("No audible alarm will be heard."))
            }
        }
    }
    @Test fun `only total-silence DND warrants a definite DND warning`() {
        for (recording in listOf(false, true)) for (acoustic in listOf(false, true)) {
            assertEquals(recording && acoustic, alarmAudibilityWarning(recording, acoustic,
                AlarmAudioEnvironment(5, false, android.app.NotificationManager.INTERRUPTION_FILTER_NONE)) != null)
        }
        assertEquals("Do Not Disturb is silencing alarms. No audible alarm will be heard.",
            alarmAudibilityWarning(true, true, AlarmAudioEnvironment(5, false,
                android.app.NotificationManager.INTERRUPTION_FILTER_NONE)))
        for (filter in listOf(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS)) {
            assertEquals("Do Not Disturb is on. Audible alarms may not work.",
                alarmAudibilityWarning(true, true, AlarmAudioEnvironment(5, false, filter)))
        }
    }
    @Test fun `unknown readings and positive unmuted volume do not claim silence`() {
        for (volume in listOf(null, -1, 5)) for (muted in listOf(null, false)) {
            assertNull(alarmAudibilityWarning(true, true, AlarmAudioEnvironment(volume, muted, null)))
            assertNull(alarmAudibilityWarning(true, true, AlarmAudioEnvironment(volume, muted,
                android.app.NotificationManager.INTERRUPTION_FILTER_ALL)))
        }
        assertNotNull(alarmAudibilityWarning(true, true, AlarmAudioEnvironment(0, null, null)))
        assertNotNull(alarmAudibilityWarning(true, true, AlarmAudioEnvironment(null, true, null)))
    }
    @Test fun `volume recovery and disabling alarms clear silence without acknowledgement`() {
        val silent = AlarmAudioEnvironment(0, false, android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
        assertEquals("Alarm audio is muted or at zero volume. No audible alarm will be heard.",
            alarmAudibilityWarning(true, true, silent))
        assertNull(alarmAudibilityWarning(true, true, silent.copy(alarmVolume = 5)))
        assertNull(alarmAudibilityWarning(false, true, silent))
        assertNull(alarmAudibilityWarning(true, false, silent))
    }
    @Test fun `all Android DND modes warn while unknown values remain unknown`() {
        assertFalse(isAlarmDndEnabled(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)!!)
        assertTrue(isAlarmDndEnabled(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)!!)
        assertTrue(isAlarmDndEnabled(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)!!)
        assertTrue(isAlarmDndEnabled(android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS)!!)
        assertNull(isAlarmDndEnabled(android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN))
        assertNull(isAlarmDndEnabled(999))
    }
    @Test fun `warning requires recording acoustic alarms and known enabled DND`() {
        for (recording in listOf(false, true)) for (acoustic in listOf(false, true))
            for (dnd in listOf(false, true)) {
                val warning = alarmDndWarning(recording, acoustic, dnd)
                assertEquals(recording && acoustic && dnd, warning != null,
                    "recording=$recording acoustic=$acoustic dnd=$dnd")
            }
        assertEquals("Do Not Disturb is on. Audible alarms may not work.", alarmDndWarning(true, true, true))
        assertNull(alarmDndWarning(true, true, null))
    }
    @Test fun `DND advisory clears without acknowledging an alarm episode`() {
        assertNotNull(alarmDndWarning(true, true, true))
        assertNull(alarmDndWarning(true, true, false))
        assertNull(alarmDndWarning(false, true, true))
        assertNull(alarmDndWarning(true, false, true))
    }
}
