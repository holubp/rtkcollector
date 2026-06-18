package org.rtkcollector.app.profile

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsImportModelsTest {
    @Test
    fun `valid backup produces summary counts and password warning`() {
        val backup = sampleBackup(includePassword = true)

        val result = validateSettingsImportJson(backup.toJson().toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        assertEquals(1, result.summary.commandProfileCount)
        assertEquals(1, result.summary.usbBaudProfileCount)
        assertEquals(1, result.summary.ntripCasterProfileCount)
        assertEquals(1, result.summary.ntripMountpointProfileCount)
        assertEquals(1, result.summary.recordingPolicyProfileCount)
        assertEquals(1, result.summary.storageProfileCount)
        assertEquals(1, result.summary.settingsSetCount)
        assertTrue(result.summary.containsPlaintextPasswords)
        assertEquals("settings", result.backup.selectedSettingsSetId)
    }

    @Test
    fun `valid backup without passwords has no password warning`() {
        val result = validateSettingsImportJson(sampleBackup(includePassword = false).toJson().toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        assertFalse(result.summary.containsPlaintextPasswords)
    }

    @Test
    fun `invalid json is rejected`() {
        val result = validateSettingsImportJson("{not-json")

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("This JSON file is not a RtkCollector settings backup.", result.message)
    }

    @Test
    fun `missing required array is rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.remove("commandProfiles")

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Settings backup is missing commandProfiles.", result.message)
    }

    @Test
    fun `unsupported version is rejected`() {
        val json = sampleBackup(includePassword = false).toJson().put("formatVersion", 999)

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Unsupported settings backup format version.", result.message)
    }

    @Test
    fun `non string plaintext password is rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.put("plaintextPasswords", JSONObject().put("secret", 12))

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Settings backup contains invalid NTRIP password data.", result.message)
    }

    @Test
    fun `non object plaintext password block is rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.put("plaintextPasswords", "secret-password")

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Settings backup contains invalid NTRIP password data.", result.message)
    }

    @Test
    fun `oversized backup text is rejected before parsing`() {
        val text = " ".repeat(MAX_SETTINGS_IMPORT_BYTES + 1)

        val result = validateSettingsImportJson(text)

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Settings backup is too large.", result.message)
    }

    @Test
    fun `duplicate profile ids are rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        val commandProfiles = json.getJSONArray("commandProfiles")
        commandProfiles.put(JSONObject(commandProfiles.getJSONObject(0).toString()).put("name", "Duplicate command"))

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Duplicate command profile id 'command' in settings backup.", result.message)
    }

    @Test
    fun `mountpoint referencing missing caster is rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.getJSONArray("ntripMountpointProfiles")
            .getJSONObject(0)
            .put("casterProfileId", "missing-caster")

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals(
            "NTRIP mountpoint 'Mount' references missing caster profile 'missing-caster'.",
            result.message,
        )
    }

    @Test
    fun `settings set referencing missing profile is rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.getJSONArray("settingsSets")
            .getJSONObject(0)
            .getJSONObject("commandProfile")
            .put("id", "missing-command")

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals(
            "Settings set 'UM980 rover + NTRIP' references missing command profile 'missing-command'.",
            result.message,
        )
    }

    @Test
    fun `unknown plaintext password secret id is rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.put("plaintextPasswords", JSONObject().put("unknown-secret", "secret-password"))

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Plaintext NTRIP password references unknown secret 'unknown-secret'.", result.message)
    }

    private fun sampleBackup(includePassword: Boolean): SettingsBackupFile =
        SettingsBackupFile.fromProfiles(
            commandProfiles = listOf(CommandProfile(id = "command", name = "Command")),
            usbBaudProfiles = listOf(UsbBaudProfile(id = "usb", name = "USB")),
            ntripCasterProfiles = listOf(NtripCasterProfile(id = "caster", name = "Caster", secretId = "secret")),
            ntripCasterUploadProfiles = emptyList(),
            ntripMountpointProfiles = listOf(
                NtripMountpointProfile(id = "mount", name = "Mount", casterProfileId = "caster"),
            ),
            recordingPolicyProfiles = listOf(RecordingPolicyProfile(id = "policy", name = "Policy")),
            storageProfiles = listOf(StorageProfile(id = "storage", name = "Storage")),
            settingsSets = listOf(RecordingSettingsSet.builtInRoverNtrip()),
            selectedSettingsSetId = "settings",
            selectedWorkflowId = "rover-ntrip",
            lastActiveNtripMountpointProfileId = "mount",
            passwordsBySecretId = if (includePassword) mapOf("secret" to "secret-password") else emptyMap(),
            options = SettingsSetExportOptions(includePlaintextPasswords = includePassword),
        )
}
