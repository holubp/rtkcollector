package org.rtkcollector.app.base

import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.ProfileDeviceFilter
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.effectiveCommandProfileRef
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FixedBaseSettingsSetAction {
    USE_EXISTING,
    DERIVE_NEW,
}

data class FixedBaseSettingsSetCandidate(
    val settingsSet: RecordingSettingsSet,
    val commandProfile: CommandProfile?,
    val defaultAction: FixedBaseSettingsSetAction,
    val reason: String,
) {
    val requiresDerivedSettingsSet: Boolean
        get() = defaultAction == FixedBaseSettingsSetAction.DERIVE_NEW
}

object FixedBaseHandoffPlanner {
    const val FIXED_BASE_WORKFLOW_ID = "fixed-base"

    fun eligibleSettingsSets(
        settingsSets: List<RecordingSettingsSet>,
        commandProfiles: List<CommandProfile>,
        filter: ProfileDeviceFilter,
    ): List<FixedBaseSettingsSetCandidate> {
        val commandsById = commandProfiles.associateBy(CommandProfile::id)
        return settingsSets
            .filter { it.workflowId == FIXED_BASE_WORKFLOW_ID }
            .filter { filter.matchesSettingsSet(it) }
            .mapNotNull { set ->
                val commandProfile = commandsById[set.effectiveCommandProfileRef().id]
                if (commandProfile == null || !FixedBaseCommandProfileSelection.hasModeBase(commandProfile)) {
                    null
                } else {
                    FixedBaseSettingsSetCandidate(
                        settingsSet = set,
                        commandProfile = commandProfile,
                        defaultAction = if (set.isProtected || commandProfile.isProtected) {
                            FixedBaseSettingsSetAction.DERIVE_NEW
                        } else {
                            FixedBaseSettingsSetAction.USE_EXISTING
                        },
                        reason = if (set.isProtected || commandProfile.isProtected) {
                            "Immutable settings set: derive a new set"
                        } else {
                            "Editable fixed-base settings set"
                        },
                    )
                }
            }
    }

    fun preferredSettingsSetId(
        candidates: List<FixedBaseSettingsSetCandidate>,
        lastSettingsSetId: String?,
    ): String? =
        candidates.firstOrNull { it.settingsSet.id == lastSettingsSetId }?.settingsSet?.id
            ?: candidates.firstOrNull { it.defaultAction == FixedBaseSettingsSetAction.USE_EXISTING }?.settingsSet?.id
            ?: candidates.firstOrNull()?.settingsSet?.id

    fun derivedName(sourceName: String, now: Date = Date()): String =
        "$sourceName ${timestampSuffix(now)}"

    fun timestampSuffix(now: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd'T'HHmm", Locale.US).format(now)
}
