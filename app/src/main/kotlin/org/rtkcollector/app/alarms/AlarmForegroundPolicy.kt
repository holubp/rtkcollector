package org.rtkcollector.app.alarms

import android.content.pm.ServiceInfo

enum class AlarmServiceMode { RECORDING, ALERT_ONLY, IDLE }
object AlarmForegroundPolicy {
    fun types(recordingOwnsService: Boolean, audioEnabled: Boolean): Int =
        (if (recordingOwnsService) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0) or
            (if (audioEnabled) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)

    fun mode(recordingOwnsService: Boolean, terminalPending: Boolean, audioStatus: AlarmEffectStatus,
        audioEnabled: Boolean, focusDeadlineActive: Boolean): AlarmServiceMode = when {
        recordingOwnsService -> AlarmServiceMode.RECORDING
        terminalPending && audioEnabled && (audioStatus == AlarmEffectStatus.ACTIVE ||
            audioStatus == AlarmEffectStatus.PENDING && focusDeadlineActive) -> AlarmServiceMode.ALERT_ONLY
        else -> AlarmServiceMode.IDLE
    }
}
