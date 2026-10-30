package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlarmDndWarningTest {
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
