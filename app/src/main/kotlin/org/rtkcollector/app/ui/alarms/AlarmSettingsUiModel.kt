package org.rtkcollector.app.ui.alarms

import org.rtkcollector.app.alarms.*

class AlarmSettingsUiModel(private val snapshot: AlarmSettingsSnapshot) {
    fun bluetooth(enabled: Boolean) = snapshot.copy(preferBluetoothAudio = enabled)
    fun visual(category: AlarmCategory, enabled: Boolean) = change(category) { it.copy(visual = enabled) }
    fun acoustic(category: AlarmCategory, enabled: Boolean) = change(category) { it.copy(acoustic = enabled) }
    fun sound(category: AlarmCategory, sound: AlarmSoundChoice) = change(category) { it.copy(sound = sound) }
    fun delaySeconds(seconds: Int): AlarmSettingsSnapshot {
        require(seconds in 1..60)
        return snapshot.copy(positionLossDelayMillis = seconds * 1_000L)
    }
    private fun change(category: AlarmCategory, edit: (AlarmCategorySettings) -> AlarmCategorySettings) =
        snapshot.copy(categories = snapshot.categories + (category to edit(snapshot.categories.getValue(category))))
}
