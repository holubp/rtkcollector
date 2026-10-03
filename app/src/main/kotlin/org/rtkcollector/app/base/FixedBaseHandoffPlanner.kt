package org.rtkcollector.app.base

import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.ProfileDeviceFilter
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.ActiveSetupSelections
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.SelectionChoice
import org.rtkcollector.app.profile.withAcceptedBaseCoordinate
import org.rtkcollector.app.profile.reapplied
import org.rtkcollector.app.profile.isOptionLocked
import org.rtkcollector.app.profile.workflowActivationPolicyProblem
import org.rtkcollector.app.profile.effectiveCommandProfileRef
import org.rtkcollector.app.profile.effectiveForActiveSetup
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
    val requiresDerivedCommandProfile: Boolean = false,
    val affectedSettingsSetIds: Set<String> = emptySet(),
) {
    val requiresDerivedSettingsSet: Boolean
        get() = defaultAction == FixedBaseSettingsSetAction.DERIVE_NEW
}

data class FixedBaseHandoffPlan(
    val priorSettingsSets: List<RecordingSettingsSet>,
    val priorCommands: List<CommandProfile>,
    val settingsSets: List<RecordingSettingsSet>,
    val commands: List<CommandProfile>,
    val targetSet: RecordingSettingsSet,
    val selections: ActiveSetupSelections,
    val coordinate: AcceptedBaseCoordinate,
    val overwritesCommandId: String?,
    val confirmedAffectedSettingsSetIds: Set<String>,
)

enum class FixedBaseCommandAction { UPDATE_EXISTING, COPY_COMMAND }

object FixedBaseHandoffPlanner {
    const val FIXED_BASE_WORKFLOW_ID = "fixed-base"

    fun prepare(
        candidate: FixedBaseSettingsSetCandidate,
        settingsSets: List<RecordingSettingsSet>,
        commandProfiles: List<CommandProfile>,
        coordinate: AcceptedBaseCoordinate,
        newSetId: String,
        newCommandId: String,
        commandAction: FixedBaseCommandAction,
    ): FixedBaseHandoffPlan {
        coordinate.validate()
        val sourceSet = candidate.settingsSet
        val sourceCommand = requireNotNull(candidate.commandProfile) { "Fixed-base command profile is missing." }
        require(sourceSet.workflowId == FIXED_BASE_WORKFLOW_ID && sourceSet in settingsSets)
        require(sourceCommand in commandProfiles && FixedBaseCommandProfileSelection.hasModeBase(sourceCommand))
        FixedBaseCommandValidator.requireSupportedReceiverFamily(sourceCommand.receiverFamily)
        val script = FixedBaseProfileMaterializer.materialize(
            sourceCommand.runtimeScript, coordinate.toFixedBaseModeCommand(),
        ).runtimeScript
        val deriveSet = candidate.requiresDerivedSettingsSet
        val deriveCommand = commandAction == FixedBaseCommandAction.COPY_COMMAND
        require(deriveCommand || !sourceSet.isProtected && !sourceCommand.isProtected) {
            "Built-in fixed-base profiles must be copied."
        }
        val updatedCommand = if (deriveCommand) {
            require(commandProfiles.none { it.id == newCommandId }) { "New command profile ID is already used." }
            sourceCommand.copyProfile(newCommandId, derivedName(sourceCommand.name)).copy(runtimeScript = script)
        } else sourceCommand.copy(runtimeScript = script)
        updatedCommand.validate()
        FixedBaseCommandValidator.validateSelectedCoordinateMatchesProfile(updatedCommand, coordinate)
        val commandRef = ProfileReference(updatedCommand.id, updatedCommand.name)
        val target = (if (deriveSet) {
            require(settingsSets.none { it.id == newSetId }) { "New settings set ID is already used." }
            sourceSet.reapplied().copySet(newSetId, derivedName(sourceSet.name))
        } else sourceSet).copy(
            workflowId = FIXED_BASE_WORKFLOW_ID,
            commandProfileRef = commandRef,
        ).withAcceptedBaseCoordinate(coordinate.id, coordinate.name)
        target.validate()
        require(target.workflowActivationPolicyProblem() == null) {
            "Fixed-base workflow policy must be reviewed before handoff."
        }
        var selections = ActiveSetupSelections(target.id)
        if (target.workflowApplicationPolicy == org.rtkcollector.app.profile.WorkflowApplicationPolicy.LEAVE_INTACT) {
            selections = selections.copy(workflowBaselineId = FIXED_BASE_WORKFLOW_ID)
        }
        listOf(
            ActiveSetupOptionKey.WORKFLOW to FIXED_BASE_WORKFLOW_ID,
            ActiveSetupOptionKey.RECEIVER_COMMAND to updatedCommand.id,
            ActiveSetupOptionKey.BASE_COORDINATE to coordinate.id,
        ).forEach { (key, id) ->
            if (!target.isOptionLocked(key)) selections = selections.choose(target, key, SelectionChoice.profile(id))
        }
        return FixedBaseHandoffPlan(
            settingsSets, commandProfiles,
            if (deriveSet) settingsSets + target else settingsSets.map { if (it.id == target.id) target else it },
            if (deriveCommand) commandProfiles + updatedCommand else commandProfiles.map {
                if (it.id == updatedCommand.id) updatedCommand else it
            },
            target, selections, coordinate,
            sourceCommand.id.takeUnless { deriveCommand },
            if (deriveCommand) emptySet() else candidate.affectedSettingsSetIds,
        )
    }

    fun eligibleSettingsSets(
        settingsSets: List<RecordingSettingsSet>,
        commandProfiles: List<CommandProfile>,
        filter: ProfileDeviceFilter,
        effectiveCommandId: (RecordingSettingsSet) -> String = { it.effectiveForActiveSetup().effectiveCommandProfileRef().id },
        commandUsageIds: (RecordingSettingsSet) -> Set<String> = { setOf(it.commandProfileRef.id) },
    ): List<FixedBaseSettingsSetCandidate> {
        val commandsById = commandProfiles.associateBy(CommandProfile::id)
        return settingsSets
            .filter { it.workflowId == FIXED_BASE_WORKFLOW_ID }
            .filter { filter.matchesSettingsSet(it) }
            .mapNotNull { set ->
                val commandProfile = commandsById[effectiveCommandId(set)]
                if (commandProfile == null || !FixedBaseCommandProfileSelection.hasModeBase(commandProfile)) {
                    null
                } else {
                    val affected = settingsSets.asSequence().filter { it.id != set.id }
                        .filter { commandProfile.id in commandUsageIds(it) }
                        .map { it.id }.toSet()
                    FixedBaseSettingsSetCandidate(
                        settingsSet = set,
                        commandProfile = commandProfile,
                        defaultAction = if (set.isProtected || commandProfile.isProtected) {
                            FixedBaseSettingsSetAction.DERIVE_NEW
                        } else {
                            FixedBaseSettingsSetAction.USE_EXISTING
                        },
                        requiresDerivedCommandProfile = set.isProtected || commandProfile.isProtected || affected.isNotEmpty(),
                        affectedSettingsSetIds = affected,
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
