package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.NtripMountpointOverride
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.SettingsSetOverrides

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
}
