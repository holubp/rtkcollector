package org.rtkcollector.app.profile

object WorkflowActivationMode {
    const val SELECT_CHANGEABLE = "SELECT_CHANGEABLE"
    const val SELECT_LOCKED = "SELECT_LOCKED"
    const val LET_USER_SELECT_BEFORE_START = "LET_USER_SELECT_BEFORE_START"
    const val LEAVE_CURRENT_INTACT = "LEAVE_CURRENT_INTACT"
}

private val WORKFLOW_ACTIVATION_MODES = setOf(
    WorkflowActivationMode.SELECT_CHANGEABLE,
    WorkflowActivationMode.SELECT_LOCKED,
    WorkflowActivationMode.LET_USER_SELECT_BEFORE_START,
    WorkflowActivationMode.LEAVE_CURRENT_INTACT,
)

fun canChangeActiveSetup(isRecording: Boolean, startInProgress: Boolean): Boolean =
    !isRecording && !startInProgress

fun RecordingSettingsSet.workflowActivationMode(): String =
    when {
        isOptionLocked(ActiveSetupOptionKey.WORKFLOW) -> WorkflowActivationMode.SELECT_LOCKED
        workflowApplicationPolicy == WorkflowApplicationPolicy.LEAVE_INTACT -> WorkflowActivationMode.LEAVE_CURRENT_INTACT
        workflowApplicationPolicy == WorkflowApplicationPolicy.LET_USER_SELECT -> WorkflowActivationMode.LET_USER_SELECT_BEFORE_START
        else -> WorkflowActivationMode.SELECT_CHANGEABLE
    }

fun RecordingSettingsSet.withWorkflowActivationMode(mode: String): RecordingSettingsSet {
    require(mode in WORKFLOW_ACTIVATION_MODES) { "Workflow activation mode is invalid." }
    if (mode == workflowActivationMode()) return this
    val workflowApplicationPolicy = when (mode) {
        WorkflowActivationMode.LET_USER_SELECT_BEFORE_START -> WorkflowApplicationPolicy.LET_USER_SELECT
        WorkflowActivationMode.LEAVE_CURRENT_INTACT -> WorkflowApplicationPolicy.LEAVE_INTACT
        else -> WorkflowApplicationPolicy.SET_SPECIFIC
    }
    val workflowOptionPolicy = when (mode) {
        WorkflowActivationMode.SELECT_LOCKED -> SettingsSetOptionPolicy.LOCKED
        WorkflowActivationMode.LET_USER_SELECT_BEFORE_START -> SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER
        else -> SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE
    }
    return copy(
        workflowApplicationPolicy = workflowApplicationPolicy,
        optionPolicies = optionPolicies.withPolicy(ActiveSetupOptionKey.WORKFLOW, workflowOptionPolicy),
    )
}

fun RecordingSettingsSet.workflowIdAfterSettingsSetActivation(currentWorkflowId: String?): String? =
    when (workflowActivationMode()) {
        WorkflowActivationMode.LET_USER_SELECT_BEFORE_START -> null
        WorkflowActivationMode.LEAVE_CURRENT_INTACT -> currentWorkflowId
        else -> workflowId.takeIf(String::isNotBlank) ?: currentWorkflowId
    }

fun RecordingSettingsSet.workflowIdForActiveSetup(selectedWorkflowId: String?): String? =
    if (isOptionLocked(ActiveSetupOptionKey.WORKFLOW)) {
        workflowId.takeIf(String::isNotBlank)
    } else {
        selectedWorkflowId?.takeIf(String::isNotBlank)
            ?: if (workflowActivationMode() == WorkflowActivationMode.SELECT_CHANGEABLE) {
                workflowId.takeIf(String::isNotBlank)
            } else {
                null
            }
    }
