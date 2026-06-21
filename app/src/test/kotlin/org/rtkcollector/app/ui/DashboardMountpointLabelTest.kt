package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.NtripCasterProfile
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
    fun `selected mountpoint profile determines active caster even when settings set caster is stale`() {
        val oldCaster = NtripCasterProfile(id = "old", name = "Old", host = "old.example.org")
        val newCaster = NtripCasterProfile(id = "new", name = "New", host = "new.example.org")
        val mountpoint = NtripMountpointProfile(
            id = "mount",
            name = "Mount",
            casterProfileId = "new",
            mountpoint = "TUBO00CZE0",
        )
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripCasterProfileRef = ProfileReference("old", "Old"),
            ntripMountpointProfileRef = ProfileReference("mount", "Mount"),
        )

        val resolved = settingsSet.resolveNtripProfiles(
            casterProfiles = listOf(oldCaster, newCaster),
            mountpointProfiles = listOf(mountpoint),
        )

        assertEquals("new", resolved.caster?.id)
        assertEquals("new.example.org", resolved.caster?.host)
        assertEquals(ProfileReference("new", "New"), resolved.settingsSet.ntripCasterProfileRef)
    }

    @Test
    fun `active caster falls back to settings set only when no mountpoint profile is selected`() {
        val caster = NtripCasterProfile(id = "caster", name = "Caster", host = "caster.example.org")
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripCasterProfileRef = ProfileReference("caster", "Caster"),
            ntripMountpointProfileRef = null,
        )

        val resolved = settingsSet.resolveNtripProfiles(
            casterProfiles = listOf(caster),
            mountpointProfiles = emptyList(),
        )

        assertEquals("caster", resolved.caster?.id)
        assertEquals(settingsSet, resolved.settingsSet)
    }

    @Test
    fun `active caster falls back to selected configured caster when mountpoint caster is missing`() {
        val selectedCaster = NtripCasterProfile(id = "selected", name = "Selected", host = "caster.example.org")
        val mountpoint = NtripMountpointProfile(
            id = "mount",
            name = "Mount",
            casterProfileId = "missing",
            mountpoint = "TUBO00CZE0",
        )
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripCasterProfileRef = ProfileReference("selected", "Selected"),
            ntripMountpointProfileRef = ProfileReference("mount", "Mount"),
        )

        val resolved = settingsSet.resolveNtripProfiles(
            casterProfiles = listOf(selectedCaster),
            mountpointProfiles = listOf(mountpoint),
        )

        assertEquals("selected", resolved.caster?.id)
        assertEquals("TUBO00CZE0", resolved.mountpoint?.mountpoint)
    }

    @Test
    fun `active caster falls back to selected configured caster when mountpoint caster is unconfigured`() {
        val selectedCaster = NtripCasterProfile(id = "selected", name = "Selected", host = "caster.example.org")
        val blankCaster = NtripCasterProfile(id = "blank", name = "Blank", host = "")
        val mountpoint = NtripMountpointProfile(
            id = "mount",
            name = "Mount",
            casterProfileId = "blank",
            mountpoint = "TUBO00CZE0",
        )
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripCasterProfileRef = ProfileReference("selected", "Selected"),
            ntripMountpointProfileRef = ProfileReference("mount", "Mount"),
        )

        val resolved = settingsSet.resolveNtripProfiles(
            casterProfiles = listOf(selectedCaster, blankCaster),
            mountpointProfiles = listOf(mountpoint),
        )

        assertEquals("selected", resolved.caster?.id)
        assertEquals(ProfileReference("selected", "Selected"), resolved.settingsSet.ntripCasterProfileRef)
    }

    @Test
    fun `remembered mountpoint is materialised into selected settings set when missing`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            id = "settings",
            ntripMountpointProfileRef = null,
        )
        val remembered = NtripMountpointProfile(
            id = "mount-tubo",
            name = "TUBO",
            casterProfileId = "caster",
            mountpoint = "TUBO00CZE0",
        )

        val updated = listOf(settingsSet).withRememberedMountpointProfile("settings", remembered)

        assertEquals(ProfileReference("mount-tubo", "TUBO"), updated.single().ntripMountpointProfileRef)
    }

    @Test
    fun `remembered mountpoint does not replace explicit typed override`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            id = "settings",
            ntripMountpointProfileRef = null,
            overrides = SettingsSetOverrides(
                ntripMountpoint = NtripMountpointOverride(mountpoint = "USER_TYPED"),
            ),
        )
        val remembered = NtripMountpointProfile(
            id = "mount-tubo",
            name = "TUBO",
            casterProfileId = "caster",
            mountpoint = "TUBO00CZE0",
        )

        val updated = listOf(settingsSet).withRememberedMountpointProfile("settings", remembered)

        assertEquals(null, updated.single().ntripMountpointProfileRef)
        assertEquals("USER_TYPED", updated.single().overrides.ntripMountpoint?.mountpoint)
    }

    @Test
    fun `remembered mountpoint does not replace blank typed override`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            id = "settings",
            ntripMountpointProfileRef = null,
            overrides = SettingsSetOverrides(
                ntripMountpoint = NtripMountpointOverride(mountpoint = ""),
            ),
        )
        val remembered = NtripMountpointProfile(
            id = "mount-tubo",
            name = "TUBO",
            casterProfileId = "caster",
            mountpoint = "TUBO00CZE0",
        )

        val updated = listOf(settingsSet).withRememberedMountpointProfile("settings", remembered)

        assertEquals(null, updated.single().ntripMountpointProfileRef)
        assertEquals("", updated.single().overrides.ntripMountpoint?.mountpoint)
    }

    @Test
    fun `missing remembered mountpoint leaves selected settings set unchanged`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            id = "settings",
            ntripMountpointProfileRef = null,
        )

        val updated = listOf(settingsSet).withRememberedMountpointProfile("settings", null)

        assertEquals(null, updated.single().ntripMountpointProfileRef)
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
        assertEquals(false, "plain-rover".workflowUsesNtrip())
        assertEquals(true, "rover-ntrip".workflowUsesNtrip())
        assertEquals(true, "rover-rtklib".workflowUsesNtrip())
        assertEquals(true, "rover-ntrip-rtklib".workflowUsesNtrip())
        assertEquals(true, "base-calibration".workflowUsesNtrip())
        assertEquals(false, "fixed-base".workflowUsesNtrip())
        assertEquals(false, "nontrip-debug".workflowUsesNtrip())
    }

    @Test
    fun `rtklib only rover workflow does not feed ntrip corrections to receiver`() {
        assertEquals(false, "rover-rtklib".workflowSendsNtripToReceiver())
        assertEquals(true, "rover-ntrip-rtklib".workflowSendsNtripToReceiver())
        assertEquals(true, "rover-ntrip".workflowSendsNtripToReceiver())
    }

    @Test
    fun `known rtklib rover workflow is restored`() {
        assertEquals("rover-rtklib", restoredWorkflowIdOrNull("rover-rtklib"))
        assertEquals("rover-ntrip-rtklib", restoredWorkflowIdOrNull("rover-ntrip-rtklib"))
    }

    @Test
    fun `unknown workflow in imported settings set is normalised to user selection`() {
        val imported = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "typo-rover")

        val restored = sanitizedImportedSettingsSets(listOf(imported)).single()

        assertEquals("plain-rover", restored.workflowId)
        assertEquals(WorkflowApplicationPolicy.LET_USER_SELECT, restored.workflowApplicationPolicy)
    }
}
