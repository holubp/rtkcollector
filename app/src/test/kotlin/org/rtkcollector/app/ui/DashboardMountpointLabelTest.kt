package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.NtripMountpointOverride
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.SettingsSetOverrides
import org.rtkcollector.app.profile.WorkflowApplicationPolicy

class DashboardMountpointLabelTest {
    @Test
    fun `selected mountpoint label follows current settings set profile reference`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripMountpointProfileRef = ProfileReference("mount-tubo", "TUBO"),
        )
        val profiles = listOf(
            NtripMountpointProfile(
                id = "mount-gope",
                name = "GOPE",
                casterProfileId = "caster",
                mountpoint = "GOPE00CZE0",
            ),
            NtripMountpointProfile(
                id = "mount-tubo",
                name = "TUBO",
                casterProfileId = "caster",
                mountpoint = "TUBO00CZE0",
            ),
        )

        assertEquals("TUBO00CZE0", settingsSet.selectedMountpointLabel(profiles))
    }

    @Test
    fun `selected mountpoint label prefers local override`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripMountpointProfileRef = ProfileReference("mount-tubo", "TUBO"),
            overrides = SettingsSetOverrides(
                ntripMountpoint = NtripMountpointOverride(mountpoint = "USER_TYPED"),
            ),
        )

        assertEquals("USER_TYPED", settingsSet.selectedMountpointLabel(emptyList()))
    }

    @Test
    fun `missing remembered mountpoint is displayed as missing`() {
        val label = selectedMountpointLabelFromProfileId("missing", emptyList())

        assertEquals("n/a", label)
    }

    @Test
    fun `remembered fake mountpoint is displayed as missing`() {
        val label = selectedMountpointLabelFromProfileId("a", emptyList())

        assertEquals("n/a", label)
    }

    @Test
    fun `unknown imported workflow is rejected`() {
        assertEquals(null, restoredWorkflowIdOrNull("typo-rover"))
    }

    @Test
    fun `known imported workflow is restored`() {
        assertEquals("rover-ntrip", restoredWorkflowIdOrNull("rover-ntrip"))
    }

    @Test
    fun `ntrip workflow detection is explicit`() {
        assertEquals(true, "rover-ntrip".workflowUsesNtrip())
        assertEquals(false, "nontrip-debug".workflowUsesNtrip())
    }

    @Test
    fun `unknown workflow in imported settings set is normalised to user selection`() {
        val imported = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "typo-rover")

        val restored = sanitizedImportedSettingsSets(listOf(imported)).single()

        assertEquals("plain-rover", restored.workflowId)
        assertEquals(WorkflowApplicationPolicy.LET_USER_SELECT, restored.workflowApplicationPolicy)
    }
}
