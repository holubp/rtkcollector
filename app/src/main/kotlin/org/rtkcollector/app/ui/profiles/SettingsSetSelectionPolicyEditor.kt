package org.rtkcollector.app.ui.profiles

import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.SettingsSetOptionPolicy

private val profileSelectionOptions = listOf(
    ActiveSetupOptionKey.RECEIVER_COMMAND to "Init/shutdown profile",
    ActiveSetupOptionKey.USB_BAUD to "USB/baud profile",
    ActiveSetupOptionKey.NTRIP_MOUNTPOINT to "NTRIP mountpoint",
    ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD to "NTRIP upload enable and profile",
    ActiveSetupOptionKey.RTKLIB to "RTKLIB profile",
    ActiveSetupOptionKey.SOLUTION_POLICY to "Solution policy",
    ActiveSetupOptionKey.RECORDING_OUTPUT to "Recording outputs",
    ActiveSetupOptionKey.STORAGE to "Storage",
    ActiveSetupOptionKey.BASE_COORDINATE to "Base coordinate",
)

private fun SettingsSetOptionPolicy.editorLabel(): String = when (this) {
    SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE -> "Default, changeable"
    SettingsSetOptionPolicy.LOCKED -> "Fixed selection"
    SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER -> "Choose once, remember"
    SettingsSetOptionPolicy.ASK_EVERY_TIME -> "Ask each recording"
}

fun settingsSetSelectionPolicyFields(set: RecordingSettingsSet): List<EditableProfileField> =
    profileSelectionOptions.map { (key, label) ->
        EditableProfileField(
            key = "policy${key.name}",
            label = "$label selection",
            value = set.optionPolicies.policyFor(key).name,
            optionItems = SettingsSetOptionPolicy.entries.map { EditableProfileOption(it.name, it.editorLabel()) },
        )
    }

fun RecordingSettingsSet.withSettingsSetSelectionPolicies(values: Map<String, String>): RecordingSettingsSet {
    var policies = optionPolicies
    profileSelectionOptions.forEach { (key, _) ->
        val raw = values["policy${key.name}"] ?: return@forEach
        val policy = SettingsSetOptionPolicy.entries.singleOrNull { it.name == raw }
            ?: throw IllegalArgumentException("${key.name} selection policy is invalid.")
        policies = policies.withPolicy(key, policy)
    }
    require(policies.policyFor(ActiveSetupOptionKey.BASE_COORDINATE) != SettingsSetOptionPolicy.LOCKED ||
        basePositionProfileRef != null) {
        "Select a base coordinate before fixing it in this settings set."
    }
    return copy(optionPolicies = policies)
}
