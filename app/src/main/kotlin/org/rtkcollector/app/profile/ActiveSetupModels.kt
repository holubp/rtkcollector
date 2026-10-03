package org.rtkcollector.app.profile

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

enum class ActiveSetupOptionKey {
    WORKFLOW,
    RECEIVER_COMMAND,
    USB_BAUD,
    NTRIP_CASTER,
    NTRIP_MOUNTPOINT,
    NTRIP_CASTER_UPLOAD,
    RTKLIB,
    SOLUTION_POLICY,
    RECORDING_OUTPUT,
    STORAGE,
    BASE_COORDINATE,
}

enum class SettingsSetOptionPolicy {
    DEFAULT_OVERRIDABLE,
    LOCKED,
    CHOOSE_ONCE_REMEMBER,
    ASK_EVERY_TIME,
}

data class SettingsSetOptionPolicies(
    val values: Map<ActiveSetupOptionKey, SettingsSetOptionPolicy> = defaultsMap(),
) {
    fun policyFor(key: ActiveSetupOptionKey): SettingsSetOptionPolicy =
        values[key] ?: SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE

    fun withPolicy(
        key: ActiveSetupOptionKey,
        policy: SettingsSetOptionPolicy,
    ): SettingsSetOptionPolicies =
        copy(values = values + (key to policy))

    companion object {
        fun defaults(): SettingsSetOptionPolicies = SettingsSetOptionPolicies()

        fun defaultsMap(): Map<ActiveSetupOptionKey, SettingsSetOptionPolicy> =
            ActiveSetupOptionKey.entries.associateWith {
                SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE
            }
    }
}

data class EffectiveSetupOption(
    val key: ActiveSetupOptionKey,
    val label: String,
    val defaultValueId: String?,
    val rememberedOverrideValueId: String?,
    val transientValueId: String?,
    val policy: SettingsSetOptionPolicy,
    val compatible: Boolean,
    val selectedChoice: SelectionChoice? = null,
    val applicable: Boolean = true,
    val dependencyActive: Boolean = applicable,
    val required: Boolean = false,
    val selectionPresent: Boolean? = null,
    val provenance: SelectionProvenance = SelectionProvenance.DEFAULT,
) {
    val effectiveValueId: String?
        get() = if (!applicable) null else if (selectedChoice != null) selectedChoice.profileId else when (policy) {
            SettingsSetOptionPolicy.LOCKED -> defaultValueId
            SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE -> transientValueId ?: rememberedOverrideValueId ?: defaultValueId
            SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER -> transientValueId ?: rememberedOverrideValueId
            SettingsSetOptionPolicy.ASK_EVERY_TIME -> transientValueId
        }?.takeIf(String::isNotBlank)

    val requiresUserSelection: Boolean
        get() = applicable && (
            (selectionPresent == false && policy in setOf(SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER,
                SettingsSetOptionPolicy.ASK_EVERY_TIME)) ||
            (required && dependencyActive && effectiveValueId == null) ||
            (selectionPresent == null && selectedChoice?.kind != SelectionChoiceKind.NONE && effectiveValueId == null &&
                policy in setOf(SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER, SettingsSetOptionPolicy.ASK_EVERY_TIME))
        )

    val isOverridden: Boolean
        get() = applicable && policy != SettingsSetOptionPolicy.LOCKED &&
            selectedChoice?.kind != SelectionChoiceKind.UNCHOSEN &&
            when (policy) {
                SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER, SettingsSetOptionPolicy.ASK_EVERY_TIME ->
                    selectionPresent == true || effectiveValueId != null || selectedChoice?.kind == SelectionChoiceKind.NONE
                else -> selectedChoice?.kind == SelectionChoiceKind.NONE && defaultValueId != null ||
                    effectiveValueId != defaultValueId
            }

    val canStart: Boolean
        get() = !applicable || (!dependencyActive || compatible) && !requiresUserSelection

    val problem: String?
        get() = when {
            !applicable -> null
            dependencyActive && !compatible -> "$label is not compatible with the active setup."
            requiresUserSelection -> "$label must be selected for this recording."
            else -> null
        }
}

enum class SelectionProvenance { DEFAULT, LOCKED, ACTIVE, REMEMBERED, TRANSIENT, UNCHOSEN, WORKFLOW_BASELINE }

enum class SelectionChoiceKind { UNCHOSEN, PROFILE, NONE }

data class SelectionChoice(val kind: SelectionChoiceKind, val profileId: String? = null) {
    init {
        require(if (kind == SelectionChoiceKind.PROFILE) !profileId.isNullOrBlank() else profileId == null) {
            "Only a profile choice may contain a nonblank profile ID."
        }
    }

    companion object {
        fun profile(id: String): SelectionChoice = SelectionChoice(SelectionChoiceKind.PROFILE, id)
        fun none(): SelectionChoice = SelectionChoice(SelectionChoiceKind.NONE)
        fun unchosen(): SelectionChoice = SelectionChoice(SelectionChoiceKind.UNCHOSEN)
    }
}

data class UploadSelection(val enabled: Boolean, val profile: SelectionChoice)

data class ActiveSetupSelections(
    val settingsSetId: String,
    val rememberedChoices: Map<ActiveSetupOptionKey, SelectionChoice> = emptyMap(),
    val activeChoices: Map<ActiveSetupOptionKey, SelectionChoice> = emptyMap(),
    val transientChoices: Map<ActiveSetupOptionKey, SelectionChoice> = emptyMap(),
    val uploadSelection: UploadSelection? = null,
    val transientUploadSelection: UploadSelection? = null,
    val workflowBaselineId: String? = null,
    val transientAnswerGenerations: Map<ActiveSetupOptionKey, Long> = emptyMap(),
    val transientUploadGeneration: Long? = null,
) {
    init { require(settingsSetId.isNotBlank()) { "Settings set ID is required." } }

    internal fun withPublishedChoice(candidate: ActiveSetupSelections, key: ActiveSetupOptionKey): ActiveSetupSelections {
        require(settingsSetId == candidate.settingsSetId) { "Published choice belongs to another settings set." }
        require(key in setOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, ActiveSetupOptionKey.RECORDING_OUTPUT)) {
            "Only source and recording-output choices may be published live."
        }
        return copy(
            activeChoices = (activeChoices - key) + candidate.activeChoices.filterKeys { it == key },
            rememberedChoices = (rememberedChoices - key) + candidate.rememberedChoices.filterKeys { it == key },
            transientChoices = (transientChoices - key) + candidate.transientChoices.filterKeys { it == key },
            transientAnswerGenerations = (transientAnswerGenerations - key) + candidate.transientAnswerGenerations.filterKeys { it == key },
        )
    }

    fun choose(set: RecordingSettingsSet, key: ActiveSetupOptionKey, choice: SelectionChoice): ActiveSetupSelections {
        require(set.id == settingsSetId) { "Selection belongs to another settings set." }
        require(key != ActiveSetupOptionKey.NTRIP_CASTER && key != ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD) {
            "Caster is source-owned; use chooseUpload for the upload pair."
        }
        require(!set.isOptionLocked(key)) { "$key is fixed by the settings set." }
        val cleared = copy(
            activeChoices = activeChoices - key,
            rememberedChoices = rememberedChoices - key,
            transientChoices = transientChoices - key,
            transientAnswerGenerations = transientAnswerGenerations - key,
        )
        return when (set.optionPolicies.policyFor(key)) {
            SettingsSetOptionPolicy.LOCKED -> error("$key is fixed by the settings set.")
            SettingsSetOptionPolicy.ASK_EVERY_TIME -> cleared.copy(
                transientChoices = cleared.transientChoices + (key to choice),
                transientAnswerGenerations = cleared.transientAnswerGenerations + (key to nextAnswerGeneration()),
            )
            SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER -> cleared.copy(rememberedChoices = cleared.rememberedChoices + (key to choice))
            SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE -> cleared.copy(activeChoices = cleared.activeChoices + (key to choice))
        }
    }

    fun chooseUpload(set: RecordingSettingsSet, selection: UploadSelection): ActiveSetupSelections {
        require(set.id == settingsSetId) { "Selection belongs to another settings set." }
        return when (set.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD)) {
            SettingsSetOptionPolicy.LOCKED -> throw IllegalArgumentException("Upload enable and profile are fixed by the settings set.")
            SettingsSetOptionPolicy.ASK_EVERY_TIME -> copy(transientUploadSelection = selection,
                transientUploadGeneration = nextAnswerGeneration())
            else -> copy(uploadSelection = selection)
        }
    }

    fun afterStopOrFailedStart(): ActiveSetupSelections = copy(
        transientChoices = emptyMap(), transientUploadSelection = null,
        transientAnswerGenerations = emptyMap(), transientUploadGeneration = null,
    )

    fun afterPolicyChange(previous: RecordingSettingsSet, updated: RecordingSettingsSet): ActiveSetupSelections {
        require(previous.id == settingsSetId && updated.id == settingsSetId) { "Selection belongs to another settings set." }
        val changed = ActiveSetupOptionKey.entries.filter {
            previous.optionPolicies.policyFor(it) != updated.optionPolicies.policyFor(it)
        }.toMutableSet()
        if (previous.workflowApplicationPolicy != updated.workflowApplicationPolicy) {
            changed += ActiveSetupOptionKey.WORKFLOW
        }
        val uploadChanged = ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD in changed
        return copy(
            rememberedChoices = rememberedChoices - changed,
            activeChoices = activeChoices - changed,
            transientChoices = transientChoices - changed,
            transientAnswerGenerations = transientAnswerGenerations - changed,
            uploadSelection = uploadSelection.takeUnless { uploadChanged },
            transientUploadSelection = transientUploadSelection.takeUnless { uploadChanged },
            transientUploadGeneration = transientUploadGeneration.takeUnless { uploadChanged },
            workflowBaselineId = workflowBaselineId.takeIf {
                previous.workflowApplicationPolicy == updated.workflowApplicationPolicy
            },
        )
    }

    fun copyForSettingsSet(newSettingsSetId: String): ActiveSetupSelections = ActiveSetupSelections(newSettingsSetId)

    fun reapply(set: RecordingSettingsSet, currentWorkflowId: String? = null): ActiveSetupSelections {
        require(set.id == settingsSetId) { "Selection belongs to another settings set." }
        return ActiveSetupSelections(
            settingsSetId,
            workflowBaselineId = if (set.workflowApplicationPolicy == WorkflowApplicationPolicy.LEAVE_INTACT) {
                currentWorkflowId ?: workflowBaselineId
            } else null,
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("settingsSetId", settingsSetId)
        .put("rememberedChoices", rememberedChoices.toJson())
        .put("activeChoices", activeChoices.toJson())
        .apply { workflowBaselineId?.let { put("workflowBaselineId", it) } }
        .apply { uploadSelection?.let { put("uploadSelection", it.toJson()) } }

    companion object {
        private val answerGeneration = AtomicLong()
        internal fun nextAnswerGeneration(): Long = answerGeneration.incrementAndGet()

        fun fromJson(json: JSONObject): ActiveSetupSelections = ActiveSetupSelections(
            settingsSetId = json.getString("settingsSetId"),
            rememberedChoices = json.optJSONObject("rememberedChoices").toSelectionMap(),
            activeChoices = json.optJSONObject("activeChoices").toSelectionMap(),
            workflowBaselineId = json.optString("workflowBaselineId").takeIf(String::isNotBlank),
            uploadSelection = json.optJSONObject("uploadSelection")?.let {
                UploadSelection(it.getBoolean("enabled"), it.getJSONObject("profile").toSelectionChoice())
            },
        )
    }
}

private fun SelectionChoice.toJson(): JSONObject = JSONObject().put("kind", kind.name)
    .apply { profileId?.let { put("profileId", it) } }

private fun JSONObject.toSelectionChoice(): SelectionChoice = SelectionChoice(
    SelectionChoiceKind.valueOf(getString("kind")), optString("profileId").takeIf(String::isNotBlank),
)

private fun Map<ActiveSetupOptionKey, SelectionChoice>.toJson(): JSONObject = JSONObject().also { json ->
    forEach { (key, choice) -> json.put(key.name, choice.toJson()) }
}

private fun JSONObject?.toSelectionMap(): Map<ActiveSetupOptionKey, SelectionChoice> =
    this?.keys()?.asSequence()?.associate { name ->
        ActiveSetupOptionKey.valueOf(name) to getJSONObject(name).toSelectionChoice()
    } ?: emptyMap()

private fun UploadSelection.toJson(): JSONObject = JSONObject()
    .put("enabled", enabled).put("profile", profile.toJson())
