package org.rtkcollector.app.alarms

import android.app.NotificationManager

data class AlarmAudioEnvironment(val alarmVolume: Int? = null, val alarmMuted: Boolean? = null,
    val interruptionFilter: Int? = null)

fun alarmAudibilityWarning(recording: Boolean, acousticEnabled: Boolean, environment: AlarmAudioEnvironment): String? {
    if (!recording || !acousticEnabled) return null
    if (environment.interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE)
        return "Do Not Disturb is silencing alarms. No audible alarm will be heard."
    if (environment.alarmVolume == 0 || environment.alarmMuted == true)
        return "Alarm audio is muted or at zero volume. No audible alarm will be heard."
    return alarmDndWarning(recording, acousticEnabled, environment.interruptionFilter?.let(::isAlarmDndEnabled))
}

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
