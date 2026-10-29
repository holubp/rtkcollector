package org.rtkcollector.app.alarms

enum class AlarmServiceMode { RECORDING, ALERT_ONLY, IDLE }
object AlarmForegroundPolicy {
    fun mode(recordingOwnsService: Boolean, terminalPending: Boolean, audioStatus: AlarmEffectStatus,
        audioEnabled: Boolean, focusDeadlineActive: Boolean): AlarmServiceMode = when {
        recordingOwnsService -> AlarmServiceMode.RECORDING
        terminalPending && audioEnabled && (audioStatus == AlarmEffectStatus.ACTIVE ||
            audioStatus == AlarmEffectStatus.PENDING && focusDeadlineActive) -> AlarmServiceMode.ALERT_ONLY
        else -> AlarmServiceMode.IDLE
    }
}
