package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.testing.TestFiles

class SettingsHubSourceTest {
    @Test
    fun `sessions group follows active setup and owns recent sessions action`() {
        val source = TestFiles.readString(
            TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt"),
        )
        val activeSetupStart = source.indexOf("SettingsSection(\"Active setup\")")
        val sessionsStart = source.indexOf("SettingsSection(\"Sessions\")", activeSetupStart)
        val sessionSetupStart = source.indexOf("SettingsSection(\"Session setup\")", sessionsStart)

        assertTrue(activeSetupStart >= 0 && sessionsStart > activeSetupStart && sessionSetupStart > sessionsStart)
        val activeSetup = source.substring(activeSetupStart, sessionsStart)
        val sessions = source.substring(sessionsStart, sessionSetupStart)

        assertFalse(activeSetup.contains("Recent sessions and sharing"))
        assertTrue(sessions.contains("Recent sessions and sharing"))
    }

    @Test
    fun `remembered mountpoint is not assigned while settings sets are loaded`() {
        val source = TestFiles.readString(
            TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt"),
        )

        assertFalse(source.contains("settingsSetsWithRememberedMountpoint"))
        assertFalse(source.contains("withRememberedMountpointProfile"))
    }

    @Test
    fun `mountpoint management stays global while legacy selection is caster scoped`() {
        val source = TestFiles.readString(
            TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt"),
        )
        val management = source.substringAfter("AppScreen.NTRIP_MOUNTPOINT_PROFILES -> ProfileListScreen(")
            .substringBefore("onSelect =")
        val selection = source.substringAfter("AppScreen.MOUNTPOINT_SELECTOR -> ProfileListScreen(")
            .substringBefore("onSelect =")

        assertTrue(management.contains("rows = profileStore.ntripMountpointProfiles().map"))
        assertFalse(management.contains("selectableNtripMountpoints"))
        assertTrue(selection.contains("selectableNtripMountpoints"))
    }
}
