package org.rtkcollector.app.ui.profiles

import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.SettingsSetOptionPolicy
import org.rtkcollector.app.profile.isOptionLocked

private val lockableSettingsSetOptions = listOf(
    ActiveSetupOptionKey.RECEIVER_COMMAND to "Init/shutdown profile",
    ActiveSetupOptionKey.USB_BAUD to "USB/baud profile",
    ActiveSetupOptionKey.NTRIP_CASTER to "NTRIP caster",
    ActiveSetupOptionKey.NTRIP_MOUNTPOINT to "NTRIP mountpoint",
    ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD to "NTRIP upload",
    ActiveSetupOptionKey.RTKLIB to "RTKLIB profile",
    ActiveSetupOptionKey.SOLUTION_POLICY to "Solution policy",
    ActiveSetupOptionKey.RECORDING_OUTPUT to "Recording outputs",
    ActiveSetupOptionKey.STORAGE to "Storage",
    ActiveSetupOptionKey.BASE_COORDINATE to "Base coordinate",
)

fun settingsSetLockFields(set: RecordingSettingsSet): List<EditableProfileField> =
    lockableSettingsSetOptions.map { (key, label) ->
        EditableProfileField(
            key = "fixed${key.name}",
            label = "Fixed $label",
            value = set.isOptionLocked(key).toString(),
            boolean = true,
        )
    }

fun RecordingSettingsSet.withSettingsSetLockSelections(values: Map<String, String>): RecordingSettingsSet {
    var updated = optionPolicies
    lockableSettingsSetOptions.forEach { (key, _) ->
        val raw = values["fixed${key.name}"] ?: return@forEach
        val fixed = raw.toBooleanStrictOrNull()
            ?: throw IllegalArgumentException("Fixed ${key.name} must be true or false.")
        val current = updated.policyFor(key)
        val next = when {
            fixed -> SettingsSetOptionPolicy.LOCKED
            current == SettingsSetOptionPolicy.LOCKED -> SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE
            else -> current
        }
        updated = updated.withPolicy(key, next)
    }
    require(updated.policyFor(ActiveSetupOptionKey.BASE_COORDINATE) != SettingsSetOptionPolicy.LOCKED ||
        basePositionProfileRef != null) {
        "Select a base coordinate before fixing it in this settings set."
    }
    return copy(optionPolicies = updated)
}
