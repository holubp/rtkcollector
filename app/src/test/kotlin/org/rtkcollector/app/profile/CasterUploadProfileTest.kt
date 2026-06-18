package org.rtkcollector.app.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CasterUploadProfileTest {
    @Test
    fun `profile validates editable drafts but requires host and mountpoint for start`() {
        val draft = NtripCasterUploadProfile(id = "upload", name = "Upload")

        draft.validate()
        assertFailsWith<IllegalArgumentException> { draft.validateForStart() }
    }

    @Test
    fun `profile json round trips`() {
        val profile = NtripCasterUploadProfile(
            id = "upload",
            name = "Upload",
            host = "caster.example.org",
            port = 2101,
            mountpoint = "BASE",
            username = "user",
            secretId = "secret",
            protocolPolicy = "NTRIP_V2_ONLY",
            enabledByDefault = true,
        )

        val parsed = NtripCasterUploadProfile.fromJson(profile.toJson())

        assertEquals(profile, parsed)
    }

    @Test
    fun `copy assigns upload-specific secret id`() {
        val copy = NtripCasterUploadProfile(
            id = "source",
            name = "Source",
            secretId = "old-secret",
        ).copyProfile(id = "copy", name = "Copy")

        assertEquals("copy", copy.id)
        assertEquals("Copy", copy.name)
        assertEquals("ntrip-caster-upload-profile:copy", copy.secretId)
    }

    @Test
    fun `empty upload password is preserved when explicitly exported`() {
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(),
            usbBaudProfiles = emptyList(),
            ntripCasterProfiles = emptyList(),
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(id = "upload", name = "Upload", secretId = "upload-secret"),
            ),
            ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(),
            settingsSets = emptyList(),
            selectedSettingsSetId = null,
            selectedWorkflowId = null,
            lastActiveNtripMountpointProfileId = null,
            passwordsBySecretId = mapOf("upload-secret" to ""),
            options = SettingsSetExportOptions(includePlaintextPasswords = true),
        )

        val parsed = SettingsBackupFile.fromJson(backup.toJson())

        assertEquals("", parsed.plaintextPasswordsBySecretId["upload-secret"])
    }

    @Test
    fun `invalid protocol policy is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            NtripCasterUploadProfile(
                id = "upload",
                name = "Upload",
                protocolPolicy = "NTRIP_V3_ONLY",
            ).validate()
        }
    }
}
