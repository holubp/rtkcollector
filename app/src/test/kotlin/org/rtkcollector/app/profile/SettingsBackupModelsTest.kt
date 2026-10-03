package org.rtkcollector.app.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsBackupModelsTest {
    @Test
    fun `unclassified input stays private through migration and is absent from portable recovery`() {
        val set = RecordingSettingsSet.builtInRoverNtrip()
        val json = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(), usbBaudProfiles = emptyList(), ntripCasterProfiles = emptyList(),
            ntripCasterUploadProfiles = emptyList(), ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(), storageProfiles = emptyList(), settingsSets = listOf(set),
            selectedSettingsSetId = set.id, selectedWorkflowId = null, lastActiveNtripMountpointProfileId = null,
            passwordsBySecretId = emptyMap(), options = SettingsSetExportOptions(),
        ).toJson().put("unrecognizedSensitiveField", "private-fixture")
        val parsed = SettingsBackupFile.fromJson(json)
        val migrated = planLegacyProfileOwnership(parsed)
        assertTrue(migrated.privateRecoveryInput!!.contentForPrivateStorage().contains("private-fixture"))
        assertFalse(migrated.privateRecoveryInput.toString().contains("private-fixture"))
        assertFalse(migrated.toJson().toString().contains("private-fixture"))
        assertFalse(migrated.toJson().toString().contains("unrecognizedSensitiveField"))
        assertTrue(migrated.migrationRecovery.getValue(set.id).issues.any { it.field == "unclassifiedInput" })
    }

    @Test
    fun `consented export looks up owner bindings without enumerating secrets`() {
        val lookedUp = mutableListOf<String>()
        val source = object : AbstractMap<String, String>() {
            override val entries: Set<Map.Entry<String, String>> get() = error("Secret enumeration is forbidden")
            override fun get(key: String): String? {
                lookedUp += key
                return if (key == "owner") "export-fixture" else error("Unexpected secret lookup")
            }
        }
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(), usbBaudProfiles = emptyList(),
            ntripCasterProfiles = listOf(NtripCasterProfile("caster", "Caster", secretId = "owner")),
            ntripCasterUploadProfiles = emptyList(), ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(), storageProfiles = emptyList(), settingsSets = emptyList(),
            selectedSettingsSetId = null, selectedWorkflowId = null, lastActiveNtripMountpointProfileId = null,
            passwordsBySecretId = source, options = SettingsSetExportOptions(includePlaintextPasswords = true),
        )
        assertEquals(listOf("owner"), lookedUp)
        assertEquals(mapOf("owner" to "export-fixture"), backup.plaintextPasswordsBySecretId)
    }
    @Test
    fun `format two retains separate selections and redacted migration recovery`() {
        val legacySet = RecordingSettingsSet.builtInRoverNtrip().copy(
            id = "settings",
            overrides = SettingsSetOverrides(command = CommandProfileOverride(initScript = "MODE ROVER")),
        )
        val selection = ActiveSetupSelections(
            settingsSetId = "settings",
            rememberedChoices = mapOf(
                ActiveSetupOptionKey.RECEIVER_COMMAND to SelectionChoice.profile("derived-command"),
            ),
        )
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(), usbBaudProfiles = emptyList(),
            ntripCasterProfiles = emptyList(), ntripCasterUploadProfiles = emptyList(),
            ntripMountpointProfiles = emptyList(), recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(), settingsSets = listOf(legacySet),
            selectedSettingsSetId = "settings", selectedWorkflowId = null,
            lastActiveNtripMountpointProfileId = null, passwordsBySecretId = emptyMap(),
            options = SettingsSetExportOptions(),
            activeSetupSelections = mapOf("settings" to selection),
            migrationRecovery = mapOf(
                "settings" to LegacyMigrationRecovery(legacySet, setOf(MigrationReviewReason.CASTER_LINEAGE)),
            ),
        )

        val parsed = SettingsBackupFile.fromJson(backup.toJson())

        assertEquals(2, parsed.formatVersion)
        assertEquals(selection, parsed.activeSetupSelections["settings"])
        assertEquals(legacySet, parsed.migrationRecovery["settings"]?.legacySettingsSet)
        assertEquals(setOf(MigrationReviewReason.CASTER_LINEAGE), parsed.migrationRecovery["settings"]?.reviewReasons)
        assertFalse(backup.toJson().toString().contains("plaintextPasswords"))
    }

    @Test
    fun `format one remains readable without new state fields`() {
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(), usbBaudProfiles = emptyList(),
            ntripCasterProfiles = emptyList(), ntripCasterUploadProfiles = emptyList(),
            ntripMountpointProfiles = emptyList(), recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(), settingsSets = emptyList(),
            selectedSettingsSetId = null, selectedWorkflowId = null,
            lastActiveNtripMountpointProfileId = null, passwordsBySecretId = emptyMap(),
            options = SettingsSetExportOptions(),
        )
        val legacyJson = backup.toJson().put("formatVersion", 1)
            .also { it.remove("activeSetupSelections"); it.remove("migrationRecovery") }

        val parsed = SettingsBackupFile.fromJson(legacyJson)

        assertEquals(1, parsed.formatVersion)
        assertTrue(parsed.activeSetupSelections.isEmpty())
        assertTrue(parsed.migrationRecovery.isEmpty())
    }

    @Test
    fun `export round trips all profile collections and selected ids`() {
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = listOf(CommandProfile(id = "command", name = "Command")),
            usbBaudProfiles = listOf(UsbBaudProfile(id = "usb", name = "USB")),
            ntripCasterProfiles = listOf(NtripCasterProfile(id = "caster", name = "Caster", secretId = "secret")),
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(id = "upload", name = "Upload", secretId = "upload-secret"),
            ),
            ntripMountpointProfiles = listOf(
                NtripMountpointProfile(id = "mount", name = "Mount", casterProfileId = "caster", mountpoint = "BASE"),
            ),
            recordingPolicyProfiles = listOf(RecordingPolicyProfile(id = "policy", name = "Policy")),
            rtklibProfiles = listOf(RtklibProfile(id = "rtklib", name = "RTKLIB", enabled = true)),
            storageProfiles = listOf(StorageProfile(id = "storage", name = "Storage")),
            settingsSets = listOf(RecordingSettingsSet.builtInRoverNtrip()),
            selectedSettingsSetId = "settings",
            selectedWorkflowId = "rover-ntrip",
            lastActiveNtripMountpointProfileId = "mount",
            passwordsBySecretId = emptyMap(),
            options = SettingsSetExportOptions(),
            exportedAtEpochMillis = 42L,
        )

        val parsed = SettingsBackupFile.fromJson(backup.toJson())

        assertEquals(SettingsBackupFile.CURRENT_FORMAT_VERSION, parsed.formatVersion)
        assertEquals(42L, parsed.exportedAtEpochMillis)
        assertEquals(listOf("command"), parsed.commandProfiles.map(CommandProfile::id))
        assertEquals(listOf("usb"), parsed.usbBaudProfiles.map(UsbBaudProfile::id))
        assertEquals(listOf("caster"), parsed.ntripCasterProfiles.map(NtripCasterProfile::id))
        assertEquals(listOf("upload"), parsed.ntripCasterUploadProfiles.map(NtripCasterUploadProfile::id))
        assertEquals(listOf("mount"), parsed.ntripMountpointProfiles.map(NtripMountpointProfile::id))
        assertEquals(listOf("policy"), parsed.recordingPolicyProfiles.map(RecordingPolicyProfile::id))
        assertEquals(listOf("rtklib"), parsed.rtklibProfiles.map(RtklibProfile::id))
        assertEquals(listOf("storage"), parsed.storageProfiles.map(StorageProfile::id))
        assertEquals(listOf("um980-rover-ntrip"), parsed.settingsSets.map(RecordingSettingsSet::id))
        assertEquals("settings", parsed.selectedSettingsSetId)
        assertEquals("rover-ntrip", parsed.selectedWorkflowId)
        assertEquals("mount", parsed.lastActiveNtripMountpointProfileId)
    }

    @Test
    fun `backup retains only supported TLS fields and import clears local acknowledgement`() {
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(),
            usbBaudProfiles = emptyList(),
            ntripCasterProfiles = listOf(
                NtripCasterProfile(
                    id = "caster", name = "Caster", host = "caster.example",
                    tlsVerification = org.rtkcollector.core.correction.NtripTlsVerification.Unsafe,
                    unsafeTlsAcknowledged = true,
                ),
            ),
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(
                    id = "upload", name = "Upload", host = "upload.example",
                    tlsVerification = org.rtkcollector.core.correction.NtripTlsVerification.Unsafe,
                    unsafeTlsAcknowledged = true,
                ),
            ),
            ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(),
            settingsSets = emptyList(),
            selectedSettingsSetId = null,
            selectedWorkflowId = null,
            lastActiveNtripMountpointProfileId = null,
            passwordsBySecretId = emptyMap(),
            options = SettingsSetExportOptions(),
        )

        val json = backup.toJson()
        val casterJson = json.getJSONArray("ntripCasterProfiles").getJSONObject(0)
        setOf("transportMode", "tlsVerification", "unsafeTlsAcknowledged").forEach { key ->
            assertTrue(casterJson.has(key))
        }
        assertFalse(json.toString().contains("customCa", ignoreCase = true))

        val imported = settingsBackupImportPlan(
            backup = SettingsBackupFile.fromJson(json),
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            idFactory = SettingsImportIdFactory { "$it-imported" },
        ).backup
        assertFalse(imported.ntripCasterProfiles.single().unsafeTlsAcknowledged)
        assertFalse(imported.ntripCasterUploadProfiles.single().unsafeTlsAcknowledged)
    }

    @Test
    fun `export excludes plaintext passwords by default`() {
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(),
            usbBaudProfiles = emptyList(),
            ntripCasterProfiles = listOf(NtripCasterProfile(id = "caster", name = "Caster", secretId = "secret")),
            ntripCasterUploadProfiles = emptyList(),
            ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(),
            settingsSets = emptyList(),
            selectedSettingsSetId = "settings",
            selectedWorkflowId = "rover-ntrip",
            lastActiveNtripMountpointProfileId = "mount",
            passwordsBySecretId = mapOf("secret" to "secret-password"),
            options = SettingsSetExportOptions(includePlaintextPasswords = false),
        )

        val json = backup.toJson().toString()

        assertFalse(json.contains("secret-password"))
        assertFalse(json.contains("plaintextPasswords"))
    }

    @Test
    fun `export includes plaintext passwords only when requested`() {
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(),
            usbBaudProfiles = emptyList(),
            ntripCasterProfiles = listOf(NtripCasterProfile(id = "caster", name = "Caster", secretId = "secret")),
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(id = "upload", name = "Upload", secretId = "upload-secret"),
            ),
            ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(),
            settingsSets = emptyList(),
            selectedSettingsSetId = "settings",
            selectedWorkflowId = "rover-ntrip",
            lastActiveNtripMountpointProfileId = "mount",
            passwordsBySecretId = mapOf("secret" to "secret-password", "upload-secret" to ""),
            options = SettingsSetExportOptions(includePlaintextPasswords = true),
        )

        val parsed = SettingsBackupFile.fromJson(backup.toJson())

        assertEquals("secret-password", parsed.plaintextPasswordsBySecretId["secret"])
        assertEquals("", parsed.plaintextPasswordsBySecretId["upload-secret"])
        assertTrue(backup.toJson().toString().contains("plaintextPasswords"))
    }

    @Test
    fun `plaintext export excludes orphaned secret store entries`() {
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(),
            usbBaudProfiles = emptyList(),
            ntripCasterProfiles = listOf(
                NtripCasterProfile(id = "caster", name = "Caster", secretId = "secret"),
            ),
            ntripCasterUploadProfiles = emptyList(),
            ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(),
            settingsSets = emptyList(),
            selectedSettingsSetId = null,
            selectedWorkflowId = null,
            lastActiveNtripMountpointProfileId = null,
            passwordsBySecretId = mapOf(
                "secret" to "profile-password",
                "ntrip:euref-ip.net:TUBO00CZE0:pholub" to "orphaned-password",
            ),
            options = SettingsSetExportOptions(includePlaintextPasswords = true),
        )

        assertEquals(mapOf("secret" to "profile-password"), backup.plaintextPasswordsBySecretId)
    }

    @Test
    fun `plaintext export ignores matching legacy RC2 alias when owner binding differs`() {
        val profile = NtripCasterProfile(
            id = "TUBO00CZE0",
            name = "EUREF TUBO",
            host = "euref-ip.net",
            username = "pholub",
            secretId = ntripCasterSecretId("TUBO00CZE0"),
        )
        val legacySecretId = legacyNtripCasterSecretId(profile)
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(),
            usbBaudProfiles = emptyList(),
            ntripCasterProfiles = listOf(profile),
            ntripCasterUploadProfiles = emptyList(),
            ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(),
            settingsSets = emptyList(),
            selectedSettingsSetId = null,
            selectedWorkflowId = null,
            lastActiveNtripMountpointProfileId = null,
            passwordsBySecretId = mapOf(legacySecretId to "legacy-password"),
            options = SettingsSetExportOptions(includePlaintextPasswords = true),
        )

        assertTrue(backup.plaintextPasswordsBySecretId.isEmpty())
    }

    @Test
    fun `plaintext export uses explicit owner binding even with canonical collision`() {
        val profile = NtripCasterProfile(id = "caster", name = "Caster", secretId = "owner-binding")
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(), usbBaudProfiles = emptyList(),
            ntripCasterProfiles = listOf(profile), ntripCasterUploadProfiles = emptyList(),
            ntripMountpointProfiles = emptyList(), recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(), settingsSets = emptyList(),
            selectedSettingsSetId = null, selectedWorkflowId = null,
            lastActiveNtripMountpointProfileId = null,
            passwordsBySecretId = mapOf(
                "owner-binding" to "owner-password",
                ntripCasterSecretId("caster") to "stale-password",
            ),
            options = SettingsSetExportOptions(includePlaintextPasswords = true),
        )

        assertEquals(mapOf("owner-binding" to "owner-password"), backup.plaintextPasswordsBySecretId)
    }
}
