package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkflowActivationPolicyTest {
    @Test
    fun `active setup cannot change while recording starts or runs`() {
        assertTrue(canChangeActiveSetup(isRecording = false, startInProgress = false))
        assertFalse(canChangeActiveSetup(isRecording = false, startInProgress = true))
        assertFalse(canChangeActiveSetup(isRecording = true, startInProgress = false))
    }
    @Test
    fun `saving unchanged workflow mode preserves advanced imported policy`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults().withPolicy(
                ActiveSetupOptionKey.WORKFLOW,
                SettingsSetOptionPolicy.ASK_EVERY_TIME,
            ),
        )

        assertEquals(WorkflowActivationMode.SELECT_CHANGEABLE, set.workflowActivationMode())
        assertEquals(SettingsSetOptionPolicy.ASK_EVERY_TIME,
            set.withWorkflowActivationMode(WorkflowActivationMode.SELECT_CHANGEABLE)
                .optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW))
    }
    @Test
    fun `workflow activation mode distinguishes changeable and locked specific workflow`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip()

        val changeable = settingsSet.withWorkflowActivationMode(WorkflowActivationMode.SELECT_CHANGEABLE)
        val locked = settingsSet.withWorkflowActivationMode(WorkflowActivationMode.SELECT_LOCKED)

        assertEquals(WorkflowApplicationPolicy.SET_SPECIFIC, changeable.workflowApplicationPolicy)
        assertEquals(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE, changeable.optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW))
        assertEquals(WorkflowActivationMode.SELECT_CHANGEABLE, changeable.workflowActivationMode())

        assertEquals(WorkflowApplicationPolicy.SET_SPECIFIC, locked.workflowApplicationPolicy)
        assertEquals(SettingsSetOptionPolicy.LOCKED, locked.optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW))
        assertEquals(WorkflowActivationMode.SELECT_LOCKED, locked.workflowActivationMode())
    }

    @Test
    fun `workflow activation mode preserves user select and leave intact semantics`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip()

        val userSelect = settingsSet.withWorkflowActivationMode(WorkflowActivationMode.LET_USER_SELECT_BEFORE_START)
        val leaveIntact = settingsSet.withWorkflowActivationMode(WorkflowActivationMode.LEAVE_CURRENT_INTACT)

        assertEquals(WorkflowApplicationPolicy.LET_USER_SELECT, userSelect.workflowApplicationPolicy)
        assertEquals(SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER, userSelect.optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW))
        assertEquals(WorkflowActivationMode.LET_USER_SELECT_BEFORE_START, userSelect.workflowActivationMode())

        assertEquals(WorkflowApplicationPolicy.LEAVE_INTACT, leaveIntact.workflowApplicationPolicy)
        assertEquals(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE, leaveIntact.optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW))
        assertEquals(WorkflowActivationMode.LEAVE_CURRENT_INTACT, leaveIntact.workflowActivationMode())
    }

    @Test
    fun `workflow activation applies selected mode when settings set is activated`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip()

        assertEquals(
            "rover-ntrip",
            settingsSet.withWorkflowActivationMode(WorkflowActivationMode.SELECT_CHANGEABLE)
                .workflowIdAfterSettingsSetActivation(currentWorkflowId = "plain-rover"),
        )
        assertEquals(
            "rover-ntrip",
            settingsSet.withWorkflowActivationMode(WorkflowActivationMode.SELECT_LOCKED)
                .workflowIdAfterSettingsSetActivation(currentWorkflowId = "plain-rover"),
        )
        assertEquals(
            null,
            settingsSet.withWorkflowActivationMode(WorkflowActivationMode.LET_USER_SELECT_BEFORE_START)
                .workflowIdAfterSettingsSetActivation(currentWorkflowId = "plain-rover"),
        )
        assertEquals(
            "plain-rover",
            settingsSet.withWorkflowActivationMode(WorkflowActivationMode.LEAVE_CURRENT_INTACT)
                .workflowIdAfterSettingsSetActivation(currentWorkflowId = "plain-rover"),
        )
    }

    @Test
    fun `recording uses selected changeable workflow but fixed workflow uses set value`() {
        val settingsSet = RecordingSettingsSet.builtInPlainRover()

        assertEquals(
            "rover-ntrip",
            settingsSet.withWorkflowActivationMode(WorkflowActivationMode.SELECT_CHANGEABLE)
                .workflowIdForActiveSetup("rover-ntrip"),
        )
        assertEquals(
            "plain-rover",
            settingsSet.withWorkflowActivationMode(WorkflowActivationMode.SELECT_LOCKED)
                .workflowIdForActiveSetup("rover-ntrip"),
        )
        assertEquals(
            null,
            settingsSet.withWorkflowActivationMode(WorkflowActivationMode.LET_USER_SELECT_BEFORE_START)
                .workflowIdForActiveSetup(null),
        )
    }
}
