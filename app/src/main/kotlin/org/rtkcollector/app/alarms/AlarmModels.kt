package org.rtkcollector.app.alarms

enum class AlarmCategory(val title: String, val priority: Int) {
    CONNECTION_LOST("Receiver connection lost", 2),
    POSITION_LOST("Position lost", 1),
    RECORDING_INTERRUPTED("Recording interrupted", 3),
}
enum class BuiltInAlarmSound(val label: String) { SINGLE_LOW("Low single beep"), TRIPLE_HIGH("High triple beep") }
sealed interface AlarmSoundChoice {
    data class BuiltIn(val pattern: BuiltInAlarmSound) : AlarmSoundChoice
    data class SystemNotification(val uri: String) : AlarmSoundChoice
}
data class AlarmCategorySettings(val visual: Boolean, val acoustic: Boolean, val sound: AlarmSoundChoice)
data class AlarmSettingsSnapshot(val categories: Map<AlarmCategory, AlarmCategorySettings>, val positionLossDelayMillis: Long = 5_000) {
    init {
        require(positionLossDelayMillis in 1_000..60_000)
        require(categories.keys.containsAll(AlarmCategory.entries))
    }
    companion object {
        fun defaultSound(category: AlarmCategory) = AlarmSoundChoice.BuiltIn(
            if (category == AlarmCategory.POSITION_LOST) BuiltInAlarmSound.SINGLE_LOW else BuiltInAlarmSound.TRIPLE_HIGH)
        fun defaults() = AlarmSettingsSnapshot(AlarmCategory.entries.associateWith {
            AlarmCategorySettings(true, true, defaultSound(it))
        })
    }
}
data class AlarmCauseKey(val category: AlarmCategory, val component: String, val code: String)
data class AlarmEpisodeId(val generation: Long, val cause: AlarmCauseKey, val revision: Long)
data class AlarmPositionEvidence(val sourceId: String, val sequence: Long, val receivedElapsedMillis: Long, val valid: Boolean)
data class AlarmEpisode(val id: AlarmEpisodeId, val detail: String, val terminal: Boolean)
data class AlarmAudioSelection(val episodeId: AlarmEpisodeId, val sound: AlarmSoundChoice)
data class AlarmDesiredState(val generation: Long, val revision: Long, val visibleEpisodes: List<AlarmEpisode>,
    val audio: AlarmAudioSelection?, val terminalAlertPending: Boolean) {
    companion object { val EMPTY = AlarmDesiredState(-1, 0, emptyList(), null, false) }
}
enum class AlarmEffectStatus { IDLE, PENDING, ACTIVE, BLOCKED, FAILED }
data class AlarmDeliveryStatus(val desiredRevision: Long, val visual: AlarmEffectStatus, val audio: AlarmEffectStatus, val detail: String?)

sealed interface AlarmCommand {
    val generation: Long
    data class Begin(override val generation: Long, val settings: AlarmSettingsSnapshot, val nowElapsedMillis: Long) : AlarmCommand
    data class Position(override val generation: Long, val evidence: AlarmPositionEvidence) : AlarmCommand
    data class CauseRaised(override val generation: Long, val key: AlarmCauseKey, val detail: String, val nowElapsedMillis: Long) : AlarmCommand
    data class CauseRecovered(override val generation: Long, val key: AlarmCauseKey, val nowElapsedMillis: Long) : AlarmCommand
    data class Tick(override val generation: Long, val nowElapsedMillis: Long) : AlarmCommand
    data class Acknowledge(override val generation: Long, val episodes: Set<AlarmEpisodeId>, val nowElapsedMillis: Long) : AlarmCommand
    data class SettingsChanged(override val generation: Long, val settings: AlarmSettingsSnapshot, val nowElapsedMillis: Long) : AlarmCommand
    data class UserStop(override val generation: Long, val nowElapsedMillis: Long) : AlarmCommand
    data class TerminalStop(override val generation: Long, val key: AlarmCauseKey, val detail: String, val nowElapsedMillis: Long) : AlarmCommand
    data class DeliveryChanged(override val generation: Long, val status: AlarmDeliveryStatus, val nowElapsedMillis: Long) : AlarmCommand
}
