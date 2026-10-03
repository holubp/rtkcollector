package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.testing.TestFiles

class SettingsHubSourceTest {
    @Test
    fun `ordinary editor save and value actions share lossless submission normalization`() {
        val source = TestFiles.readString(TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt"))
        val editor = source.substringAfter("fun ProfileEditorScreen(").substringBefore("fun NtripMountpointEditorScreen(")
        assertTrue(editor.contains("onSave(profileEditorSubmissionValues(data.fields, values))"))
        assertTrue(editor.contains("onClickWithValues?.invoke(profileEditorSubmissionValues(data.fields, values))"))
        assertFalse(editor.contains("values.mapValues { it.value.trim() }"))
    }

    @Test
    fun `accepted live choices publish only their option and join the consuming session`() {
        val source = TestFiles.readString(TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt"))
        val reconcile = source.substringAfter("fun reconcilePendingSetup(").substringBefore("DisposableEffect(context)")
        assertTrue(reconcile.contains("savePublishedLiveSelections("))
        assertTrue(reconcile.contains("pending.option"))
        assertTrue(reconcile.contains("consumePublishedSelections(receipt,"))
        assertFalse(reconcile.contains("saveActiveSelections(pending.selections"))
    }

    @Test
    fun `command editor exposes distinct prebaud init phase and preserves script text`() {
        val source = TestFiles.readString(TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt"))
        assertTrue(source.contains("EditableProfileField(\"initScript\", \"Pre-baud init script\""))
        assertTrue(source.contains("\"Post-baud runtime script\""))
        assertTrue(source.contains("withEditedCommandPhases(values)"))
        assertFalse(source.contains("initScript = \"\""))
    }

    @Test
    fun `every persisted SAF grant consumer requires shared read and write authority`() {
        val stores = TestFiles.readString(TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt"))
        val activity = TestFiles.readString(TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt"))
        assertTrue(stores.split("hasPersistedSafTreeAuthority(it.isReadPermission, it.isWritePermission)").size == 4)
        assertTrue(activity.contains("hasPersistedSafTreeAuthority(permission.isReadPermission, permission.isWritePermission)"))
        assertFalse(stores.contains("filter { it.isWritePermission }"))
    }
    @Test
    fun `migration repair is an explicit editor action and ordinary save does not acknowledge`() {
        val source = TestFiles.readString(TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt"))
        assertTrue(source.contains("Validate and apply migration repairs"))
        assertTrue(source.contains("validateMigrationRepair = true"))
        val save = source.substringAfter("private fun ProfileStores.saveProfileEditorData(")
            .substringBefore("ProfileKind.NTRIP_CASTER ->")
        assertTrue(save.contains("saveSettingsSetsWithValidatedRepair"))
        assertFalse(save.contains("ntripCasterPolicyNeedsReview = false"))
        val stores = TestFiles.readString(TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt"))
        val repair = stores.substringAfter("fun saveSettingsSetsWithValidatedRepair(").substringBefore("private fun publishSettingsSets(")
        assertTrue(repair.contains("hasPersistedSafTreeAuthority(it.isReadPermission, it.isWritePermission)"))
    }
    @Test
    fun `home and active choices use the same upload state labels`() {
        val source = TestFiles.readString(
            TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt"),
        )
        val activeChoices = source.substringAfter("if (showActiveChoicesDialog)")
            .substringBefore("if (activeOptionSelector")
        assertTrue(activeChoices.contains("uploadSelectionLabel("))
        assertTrue(source.contains("upload = uploadSelectionLabel("))
        assertFalse(source.contains("upload = if (casterUploadRequested)"))
    }

    @Test
    fun `idle persistent command write uses active USB baud selection`() {
        val source = TestFiles.readString(
            TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt"),
        )
        val maintenanceWrite = source.substringAfter("private fun writeCommandProfilePersistentlyToDevice(")
            .substringBefore("private fun writeUsbBaudPersistentlyToDevice(")

        assertTrue(maintenanceWrite.contains("profileStore.activeProfileId(settingsSet, ActiveSetupOptionKey.USB_BAUD)"))
        assertFalse(maintenanceWrite.contains("effectiveUsbBaudProfileRef"))
    }

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
