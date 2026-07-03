package org.rtkcollector.app.base

import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.RecordingSettingsSet

object FixedBaseCommandProfileSelection {
    fun hasModeBase(profile: CommandProfile): Boolean =
        profile.runtimeScript
            .lineSequence()
            .any { line -> line.trimStart().startsWith("MODE BASE ", ignoreCase = true) }

    fun templateProfiles(commandProfiles: List<CommandProfile>): List<CommandProfile> =
        commandProfiles.filter(::hasModeBase)

    fun overwriteProfiles(
        commandProfiles: List<CommandProfile>,
        settingsSets: List<RecordingSettingsSet>,
        selectedSettingsSetId: String,
    ): List<CommandProfile> =
        commandProfiles.filter { profile ->
            hasModeBase(profile) &&
                !profile.isProtected &&
                !FixedBaseCommandValidator.isCommandProfileUsedByOtherSettingsSet(
                    settingsSets = settingsSets,
                    selectedSettingsSetId = selectedSettingsSetId,
                    commandProfileId = profile.id,
                )
        }
}
