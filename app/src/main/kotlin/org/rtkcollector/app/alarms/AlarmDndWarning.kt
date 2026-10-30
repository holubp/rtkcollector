package org.rtkcollector.app.alarms

import android.app.NotificationManager

fun alarmDndWarning(recording: Boolean, acousticEnabled: Boolean, dndEnabled: Boolean?): String? =
    "Do Not Disturb is on. Audible alarms may not work."
        .takeIf { recording && acousticEnabled && dndEnabled == true }

fun isAlarmDndEnabled(filter: Int): Boolean? = when (filter) {
    NotificationManager.INTERRUPTION_FILTER_ALL -> false
    NotificationManager.INTERRUPTION_FILTER_PRIORITY,
    NotificationManager.INTERRUPTION_FILTER_NONE,
    NotificationManager.INTERRUPTION_FILTER_ALARMS -> true
    else -> null
}
