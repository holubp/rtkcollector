package org.rtkcollector.app.profile

object WorkflowActivationMode {
    const val SELECT_CHANGEABLE = "SELECT_CHANGEABLE"
    const val SELECT_LOCKED = "SELECT_LOCKED"
    const val LET_USER_SELECT_BEFORE_START = "LET_USER_SELECT_BEFORE_START"
    const val LET_USER_SELECT_EACH_RECORDING = "LET_USER_SELECT_EACH_RECORDING"
    const val LEAVE_CURRENT_INTACT = "LEAVE_CURRENT_INTACT"
    const val NEEDS_REVIEW = "NEEDS_REVIEW"
}

private val WORKFLOW_ACTIVATION_MODES = setOf(
    WorkflowActivationMode.SELECT_CHANGEABLE,
    WorkflowActivationMode.SELECT_LOCKED,
    WorkflowActivationMode.LET_USER_SELECT_BEFORE_START,
    WorkflowActivationMode.LET_USER_SELECT_EACH_RECORDING,
    WorkflowActivationMode.LEAVE_CURRENT_INTACT,
)

fun canChangeActiveSetup(isRecording: Boolean, startInProgress: Boolean): Boolean =
    !isRecording && !startInProgress

fun RecordingSettingsSet.workflowActivationMode(): String =
    when {
        workflowActivationPolicyProblem() != null -> WorkflowActivationMode.NEEDS_REVIEW
        isOptionLocked(ActiveSetupOptionKey.WORKFLOW) -> WorkflowActivationMode.SELECT_LOCKED
        workflowApplicationPolicy == WorkflowApplicationPolicy.LEAVE_INTACT -> WorkflowActivationMode.LEAVE_CURRENT_INTACT
        workflowApplicationPolicy == WorkflowApplicationPolicy.LET_USER_SELECT &&
            optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW) == SettingsSetOptionPolicy.ASK_EVERY_TIME ->
            WorkflowActivationMode.LET_USER_SELECT_EACH_RECORDING
        workflowApplicationPolicy == WorkflowApplicationPolicy.LET_USER_SELECT -> WorkflowActivationMode.LET_USER_SELECT_BEFORE_START
        else -> WorkflowActivationMode.SELECT_CHANGEABLE
    }

fun RecordingSettingsSet.withWorkflowActivationMode(mode: String): RecordingSettingsSet {
    if (mode == workflowActivationMode()) return this
    require(mode in WORKFLOW_ACTIVATION_MODES) { "Workflow activation mode is invalid." }
    val workflowApplicationPolicy = when (mode) {
        WorkflowActivationMode.LET_USER_SELECT_BEFORE_START, WorkflowActivationMode.LET_USER_SELECT_EACH_RECORDING -> WorkflowApplicationPolicy.LET_USER_SELECT
        WorkflowActivationMode.LEAVE_CURRENT_INTACT -> WorkflowApplicationPolicy.LEAVE_INTACT
        else -> WorkflowApplicationPolicy.SET_SPECIFIC
    }
    val workflowOptionPolicy = when (mode) {
        WorkflowActivationMode.SELECT_LOCKED -> SettingsSetOptionPolicy.LOCKED
        WorkflowActivationMode.LET_USER_SELECT_BEFORE_START -> SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER
        WorkflowActivationMode.LET_USER_SELECT_EACH_RECORDING -> SettingsSetOptionPolicy.ASK_EVERY_TIME
        else -> SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE
    }
    return copy(
        workflowApplicationPolicy = workflowApplicationPolicy,
        optionPolicies = optionPolicies.withPolicy(ActiveSetupOptionKey.WORKFLOW, workflowOptionPolicy),
    )
}

fun RecordingSettingsSet.workflowIdAfterSettingsSetActivation(currentWorkflowId: String?): String? =
    when (workflowActivationMode()) {
        WorkflowActivationMode.NEEDS_REVIEW, WorkflowActivationMode.LET_USER_SELECT_BEFORE_START,
        WorkflowActivationMode.LET_USER_SELECT_EACH_RECORDING -> null
        WorkflowActivationMode.LEAVE_CURRENT_INTACT -> currentWorkflowId
        else -> workflowId.takeIf(String::isNotBlank) ?: currentWorkflowId
    }

fun RecordingSettingsSet.workflowIdForActiveSetup(selectedWorkflowId: String?): String? =
    if (workflowActivationPolicyProblem() != null) {
        null
    } else if (isOptionLocked(ActiveSetupOptionKey.WORKFLOW)) {
        workflowId.takeIf(String::isNotBlank)
    } else {
        selectedWorkflowId?.takeIf(String::isNotBlank)
            ?: if (workflowActivationMode() == WorkflowActivationMode.SELECT_CHANGEABLE) {
                workflowId.takeIf(String::isNotBlank)
            } else {
                null
            }
    }

fun RecordingSettingsSet.workflowActivationPolicyProblem(): String? {
    val policy = optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW)
    val valid = when (workflowApplicationPolicy) {
        WorkflowApplicationPolicy.SET_SPECIFIC -> policy in setOf(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE, SettingsSetOptionPolicy.LOCKED)
        WorkflowApplicationPolicy.LET_USER_SELECT -> policy in setOf(SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER, SettingsSetOptionPolicy.ASK_EVERY_TIME)
        WorkflowApplicationPolicy.LEAVE_INTACT -> policy == SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE
        else -> false
    }
    return "Workflow activation and selection policies conflict; review this settings set.".takeUnless { valid }
}

fun RecordingSettingsSet.workflowIdAfterSettingsSetActivation(
    selections: ActiveSetupSelections,
    currentWorkflowId: String? = null,
): String? = ActiveSetupResolver.resolve(this, selections, currentWorkflowId)
    .takeIf { workflowActivationPolicyProblem() == null }
    ?.option(ActiveSetupOptionKey.WORKFLOW)?.effectiveValueId
