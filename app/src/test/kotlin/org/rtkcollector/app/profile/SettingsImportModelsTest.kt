package org.rtkcollector.app.profile

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertEquals(0, result.summary.ntripCasterUploadProfileCount)
        assertEquals(1, result.summary.ntripMountpointProfileCount)
        assertEquals(1, result.summary.recordingPolicyProfileCount)
        assertEquals(1, result.summary.rtklibProfileCount)
        assertEquals(0, result.summary.solutionPolicyProfileCount)
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
    fun `missing legacy profile-family array preserves installed category`() {
        listOf("ntripCasterUploadProfiles", "rtklibProfiles", "solutionPolicyProfiles").forEach { key ->
            val json = sampleBackup(includePassword = false).toJson()
            json.remove(key)

            val result = validateSettingsImportJson(json.toString())

            assertTrue(result is SettingsImportValidationResult.Valid)
            assertEquals(listOf(key), result.summary.omittedProfileFamilies)
        }
    }

    @Test
    fun `present legacy profile-family key must be an array`() {
        listOf("ntripCasterUploadProfiles", "rtklibProfiles", "solutionPolicyProfiles").forEach { key ->
            val json = sampleBackup(includePassword = false).toJson()
            json.put(key, JSONObject())

            val result = validateSettingsImportJson(json.toString())

            assertTrue(result is SettingsImportValidationResult.Invalid)
            assertEquals("Settings backup contains invalid $key.", result.message)
        }
    }

    @Test
    fun `legacy omitted family reference resolves only against retained installed profiles`() {
        val json = sampleBackup(includePassword = false)
            .copy(
                settingsSets = listOf(
                    sampleSettingsSet().copy(
                        rtklibProfileRef = ProfileReference("rtklib", "RTKLIB"),
                    ),
                ),
            )
            .toJson()
        json.remove("rtklibProfiles")

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        val plan = settingsBackupImportPlan(
            backup = result.backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            retainedProfileIds = RetainedSettingsProfileIds(rtklibProfileIds = setOf("rtklib")),
        )
        assertEquals("rtklib", plan.backup.settingsSets.single().rtklibProfileRef?.id)
        assertFailsWith<IllegalArgumentException> {
            settingsBackupImportPlan(
                backup = result.backup,
                persistedSafTreeUrisWithWriteAccess = emptySet(),
            )
        }
    }

    @Test
    fun `unsupported version is rejected`() {
        val json = sampleBackup(includePassword = false).toJson().put("formatVersion", 999)

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Unsupported settings backup format version.", result.message)
    }

    @Test
    fun `backup without a settings set is rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.put("settingsSets", org.json.JSONArray())
        json.put("selectedSettingsSetId", JSONObject.NULL)

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Settings backup contains no settings sets.", result.message)
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
    fun `settings set referencing missing rtklib profile is rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.getJSONArray("settingsSets")
            .getJSONObject(0)
            .put("rtklibProfile", JSONObject().put("id", "missing-rtklib").put("name", "Missing RTKLIB"))

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals(
            "Settings set 'UM980 rover + NTRIP' references missing RTKLIB profile 'missing-rtklib'.",
            result.message,
        )
    }

    @Test
    fun `import plan preserves RTKLIB and solution policies while resetting ungranted SAF folders`() {
        val backup = sampleBackup(includePassword = false).copy(
            storageProfiles = listOf(
                StorageProfile(
                    id = "granted-saf",
                    name = "Granted SAF",
                    kind = "SAF_TREE",
                    treeUri = "content://documents/tree/granted",
                ),
                StorageProfile(
                    id = "missing-saf",
                    name = "Missing SAF",
                    kind = "SAF_TREE",
                    treeUri = "content://documents/tree/missing",
                ),
            ),
            solutionPolicyProfiles = listOf(SolutionPolicyProfile(id = "solution", name = "Solution")),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    storageProfileRef = ProfileReference("granted-saf", "Granted SAF"),
                    overrides = SettingsSetOverrides(
                        storage = StorageProfileOverride(
                            kind = "SAF_TREE",
                            treeUri = "content://documents/tree/override-missing",
                        ),
                    ),
                ),
            ),
        )

        val plan = settingsBackupImportPlan(
            backup = backup,
            persistedSafTreeUrisWithWriteAccess = setOf("content://documents/tree/granted"),
        )

        assertEquals(2, plan.safTreeUriReselectionCount)
        assertEquals(listOf("rtklib"), plan.backup.rtklibProfiles.map(RtklibProfile::id))
        assertEquals(listOf("solution"), plan.backup.solutionPolicyProfiles.map(SolutionPolicyProfile::id))
        assertEquals("SAF_TREE", plan.backup.storageProfiles[0].kind)
        assertEquals("content://documents/tree/granted", plan.backup.storageProfiles[0].treeUri)
        assertEquals("SAF_TREE", plan.backup.storageProfiles[1].kind)
        assertNull(plan.backup.storageProfiles[1].treeUri)
        assertTrue(plan.backup.storageProfiles[1].requiresTreeReselection)
        assertEquals("SAF_TREE", plan.backup.settingsSets.single().overrides.storage?.kind)
        assertNull(plan.backup.settingsSets.single().overrides.storage?.treeUri)
        assertTrue(plan.backup.settingsSets.single().overrides.storage?.requiresTreeReselection == true)

        val reparsed = SettingsBackupFile.fromJson(plan.backup.toJson())
        assertTrue(reparsed.storageProfiles[1].requiresTreeReselection)
        assertTrue(reparsed.settingsSets.single().overrides.storage?.requiresTreeReselection == true)
    }

    @Test
    fun `sanitized valid import exposes SAF reselection in its preview summary`() {
        val backup = sampleBackup(includePassword = false).copy(
            storageProfiles = listOf(
                StorageProfile(
                    id = "missing-saf",
                    name = "Missing SAF",
                    kind = "SAF_TREE",
                    treeUri = "content://documents/tree/missing",
                ),
            ),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    storageProfileRef = ProfileReference("missing-saf", "Missing SAF"),
                ),
            ),
        )

        val result = validateSettingsImportJson(backup.toJson().toString())
            .sanitizedForPersistedSafWriteAccess(emptySet())

        assertTrue(result is SettingsImportValidationResult.Valid)
        assertEquals(1, result.summary.safTreeUriReselectionCount)
        assertEquals("SAF_TREE", result.backup.storageProfiles.single().kind)
        assertNull(result.backup.storageProfiles.single().treeUri)
        assertTrue(result.backup.storageProfiles.single().requiresTreeReselection)
    }

    @Test
    fun `storage override inherits SAF kind from effective override profile reference`() {
        val backup = sampleBackup(includePassword = false).copy(
            storageProfiles = listOf(
                StorageProfile(id = "app-storage", name = "App storage", kind = "APP_PRIVATE"),
                StorageProfile(
                    id = "saf-storage",
                    name = "SAF storage",
                    kind = "SAF_TREE",
                    treeUri = "content://documents/tree/granted-profile",
                ),
            ),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    storageProfileRef = ProfileReference("app-storage", "App storage"),
                    overrides = SettingsSetOverrides(
                        storageProfileRef = ProfileReference("saf-storage", "SAF storage"),
                        storage = StorageProfileOverride(
                            kind = null,
                            treeUri = "content://documents/tree/ungranted-override",
                        ),
                    ),
                ),
            ),
        )

        val plan = settingsBackupImportPlan(
            backup = backup,
            persistedSafTreeUrisWithWriteAccess = setOf("content://documents/tree/granted-profile"),
            idFactory = deterministicIdFactory(),
        )

        val settingsSet = plan.backup.settingsSets.single()
        assertEquals(1, plan.safTreeUriReselectionCount)
        assertEquals("saf-storage", settingsSet.overrides.storageProfileRef?.id)
        assertEquals("SAF_TREE", settingsSet.overrides.storage?.kind)
        assertNull(settingsSet.overrides.storage?.treeUri)
        assertTrue(settingsSet.overrides.storage?.requiresTreeReselection == true)
        assertEquals(
            "content://documents/tree/granted-profile",
            plan.backup.storageProfiles.single { it.id == "saf-storage" }.treeUri,
        )
    }

    @Test
    fun `missing storage override profile reference is rejected`() {
        val json = sampleBackup(includePassword = false).copy(
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    overrides = SettingsSetOverrides(
                        storageProfileRef = ProfileReference("missing-storage", "Missing storage"),
                        storage = StorageProfileOverride(treeUri = "content://documents/tree/imported"),
                    ),
                ),
            ),
        ).toJson()

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals(
            "Settings set 'UM980 rover + NTRIP' references missing storage profile 'missing-storage'.",
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

    @Test
    fun `import plan rekeys represented profiles and disarms old secret references`() {
        val localSecrets = mutableMapOf(
            ntripCasterSecretId("caster") to "local-caster-password",
            "secret" to "local-legacy-password",
            ntripCasterUploadSecretId("upload") to "local-upload-password",
            "upload-legacy" to "local-upload-legacy-password",
            "caster-override" to "local-caster-override-password",
            "upload-override" to "local-upload-override-password",
        )
        val originalLocalSecrets = localSecrets.toMap()
        val backup = sampleBackup(includePassword = false).copy(
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(id = "upload", name = "Upload", secretId = "upload-legacy"),
            ),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
                    overrides = SettingsSetOverrides(
                        ntripCasterProfileRef = ProfileReference("caster", "Caster"),
                        ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
                        ntripCaster = NtripCasterOverride(secretId = "caster-override"),
                        ntripCasterUpload = NtripCasterUploadOverride(secretId = "upload-override"),
                    ),
                ),
            ),
        )

        val imported = settingsBackupImportPlan(
            backup = backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            idFactory = deterministicIdFactory(),
        ).backup

        val caster = imported.ntripCasterProfiles.single()
        val upload = imported.ntripCasterUploadProfiles.single()
        val settingsSet = imported.settingsSets.single()
        assertTrue(caster.id != "caster")
        assertTrue(upload.id != "upload")
        assertEquals(ntripCasterSecretId(caster.id), caster.secretId)
        assertEquals(ntripCasterUploadSecretId(upload.id), upload.secretId)
        assertEquals(caster.id, imported.ntripMountpointProfiles.single().casterProfileId)
        assertEquals(caster.id, settingsSet.ntripCasterProfileRef?.id)
        assertEquals(caster.id, settingsSet.overrides.ntripCasterProfileRef?.id)
        assertEquals(upload.id, settingsSet.ntripCasterUploadProfileRef?.id)
        assertEquals(upload.id, settingsSet.overrides.ntripCasterUploadProfileRef?.id)
        assertNull(settingsSet.overrides.ntripCaster?.secretId)
        assertNull(settingsSet.overrides.ntripCasterUpload?.secretId)
        assertTrue(imported.plaintextPasswordsBySecretId.isEmpty())
        assertTrue(resolvableImportedSecretIds(imported).intersect(localSecrets.keys).isEmpty())
        assertEquals(originalLocalSecrets, localSecrets)
    }

    @Test
    fun `import plan remaps only plaintext passwords carried by backup`() {
        val backup = sampleBackup(includePassword = false).copy(
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(id = "upload", name = "Upload", secretId = "upload-legacy"),
            ),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
                    overrides = SettingsSetOverrides(
                        ntripCaster = NtripCasterOverride(secretId = "caster-override"),
                        ntripCasterUpload = NtripCasterUploadOverride(secretId = "upload-override-without-plaintext"),
                    ),
                ),
            ),
            plaintextPasswordsBySecretId = mapOf(
                "secret" to "caster-password",
                ntripCasterUploadSecretId("upload") to "upload-password",
                "caster-override" to "override-password",
            ),
        )

        val imported = settingsBackupImportPlan(
            backup = backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            idFactory = deterministicIdFactory(),
        ).backup

        val casterSecretId = imported.ntripCasterProfiles.single().secretId
        val uploadSecretId = imported.ntripCasterUploadProfiles.single().secretId
        val casterOverrideSecretId = imported.settingsSets.single().overrides.ntripCaster?.secretId
        assertEquals("caster-password", imported.plaintextPasswordsBySecretId[casterSecretId])
        assertEquals("upload-password", imported.plaintextPasswordsBySecretId[uploadSecretId])
        assertEquals("override-password", imported.plaintextPasswordsBySecretId[casterOverrideSecretId])
        assertNull(imported.settingsSets.single().overrides.ntripCasterUpload?.secretId)
        assertEquals(3, imported.plaintextPasswordsBySecretId.size)
        assertTrue(
            imported.plaintextPasswordsBySecretId.keys.intersect(backup.plaintextPasswordsBySecretId.keys).isEmpty(),
        )
    }

    @Test
    fun `preview and operation boundary remapping preserve behavior with fresh ids`() {
        val source = sampleBackup(includePassword = false).copy(
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    overrides = SettingsSetOverrides(
                        ntripCaster = NtripCasterOverride(secretId = "caster-override"),
                    ),
                ),
            ),
            plaintextPasswordsBySecretId = mapOf(
                "secret" to "caster-password",
                "caster-override" to "override-password",
            ),
        )
        val idFactory = deterministicIdFactory()

        val previewResult = validateSettingsImportJson(source.toJson().toString())
            .sanitizedForPersistedSafWriteAccess(
                persistedSafTreeUrisWithWriteAccess = emptySet(),
                idFactory = idFactory,
            )
        assertTrue(previewResult is SettingsImportValidationResult.Valid)
        val preview = previewResult.backup
        val operation = settingsBackupImportPlan(
            backup = preview,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            idFactory = idFactory,
        ).backup

        assertTrue(preview.ntripCasterProfiles.single().id != source.ntripCasterProfiles.single().id)
        assertTrue(operation.ntripCasterProfiles.single().id != preview.ntripCasterProfiles.single().id)
        assertEquals(
            operation.ntripCasterProfiles.single().id,
            operation.ntripMountpointProfiles.single().casterProfileId,
        )
        assertEquals(
            operation.ntripCasterProfiles.single().id,
            operation.settingsSets.single().ntripCasterProfileRef?.id,
        )
        assertEquals(
            setOf("caster-password", "override-password"),
            operation.plaintextPasswordsBySecretId.values.toSet(),
        )
        assertEquals(2, operation.plaintextPasswordsBySecretId.size)
    }

    @Test
    fun `omitted optional upload family retains installed profile reference and secret`() {
        val json = sampleBackup(includePassword = false).copy(
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(id = "upload", name = "Upload", secretId = "upload-legacy"),
            ),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
                    overrides = SettingsSetOverrides(
                        ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
                        ntripCasterUpload = NtripCasterUploadOverride(secretId = "imported-upload-override"),
                    ),
                ),
            ),
        ).toJson()
        json.remove("ntripCasterUploadProfiles")

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        val imported = settingsBackupImportPlan(
            backup = result.backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            retainedProfileIds = RetainedSettingsProfileIds(ntripCasterUploadProfileIds = setOf("upload")),
            idFactory = deterministicIdFactory(),
        ).backup
        assertTrue(SettingsBackupProfileFamily.NTRIP_CASTER_UPLOAD !in imported.includedProfileFamilies)
        assertTrue(imported.ntripCasterUploadProfiles.isEmpty())
        assertEquals("upload", imported.settingsSets.single().ntripCasterUploadProfileRef?.id)
        assertEquals("upload", imported.settingsSets.single().overrides.ntripCasterUploadProfileRef?.id)
        val remappedOverrideSecretId = assertNotNull(
            imported.settingsSets.single().overrides.ntripCasterUpload?.secretId,
        )
        assertTrue(remappedOverrideSecretId != "imported-upload-override")
        assertFalse(imported.plaintextPasswordsBySecretId.containsKey(remappedOverrideSecretId))
        assertFalse(imported.plaintextPasswordsBySecretId.containsKey(ntripCasterUploadSecretId("upload")))
    }

    @Test
    fun `omitted upload family endpoint override shadows retained local password`() {
        val retainedProfileSecretId = ntripCasterUploadSecretId("upload")
        val localSecrets = mapOf(retainedProfileSecretId to "retained-local-password")
        val json = sampleBackup(includePassword = false).copy(
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(id = "upload", name = "Upload"),
            ),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
                    overrides = SettingsSetOverrides(
                        ntripCasterUpload = NtripCasterUploadOverride(host = "imported.example.org"),
                    ),
                ),
            ),
        ).toJson()
        json.remove("ntripCasterUploadProfiles")

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        val imported = settingsBackupImportPlan(
            backup = result.backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            retainedProfileIds = RetainedSettingsProfileIds(ntripCasterUploadProfileIds = setOf("upload")),
            idFactory = deterministicIdFactory(),
        ).backup
        val uploadOverride = assertNotNull(imported.settingsSets.single().overrides.ntripCasterUpload)
        val shadowSecretId = assertNotNull(uploadOverride.secretId)
        assertEquals("imported.example.org", uploadOverride.host)
        assertTrue(shadowSecretId != retainedProfileSecretId)
        assertFalse(imported.plaintextPasswordsBySecretId.containsKey(shadowSecretId))
        val runtimePassword = localSecrets[uploadOverride.secretId ?: retainedProfileSecretId]
        assertNull(runtimePassword)
        assertEquals("retained-local-password", localSecrets[retainedProfileSecretId])
    }

    @Test
    fun `omitted upload family explicit empty secret remains disarmed`() {
        val json = sampleBackup(includePassword = false).copy(
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(id = "upload", name = "Upload"),
            ),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
                    overrides = SettingsSetOverrides(
                        ntripCasterUpload = NtripCasterUploadOverride(secretId = ""),
                    ),
                ),
            ),
        ).toJson().apply { remove("ntripCasterUploadProfiles") }
        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        val imported = settingsBackupImportPlan(
            backup = result.backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            retainedProfileIds = RetainedSettingsProfileIds(ntripCasterUploadProfileIds = setOf("upload")),
            idFactory = deterministicIdFactory(),
        ).backup

        val shadowSecretId = assertNotNull(imported.settingsSets.single().overrides.ntripCasterUpload?.secretId)
        assertTrue(shadowSecretId.isNotBlank())
        assertFalse(imported.plaintextPasswordsBySecretId.containsKey(shadowSecretId))
    }

    @Test
    fun `omitted upload family endpoint override remaps exact plaintext password`() {
        val json = sampleBackup(includePassword = false).copy(
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(id = "upload", name = "Upload"),
            ),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
                    overrides = SettingsSetOverrides(
                        ntripCasterUpload = NtripCasterUploadOverride(
                            host = "imported.example.org",
                            secretId = "upload-override-secret",
                        ),
                    ),
                ),
            ),
            plaintextPasswordsBySecretId = mapOf("upload-override-secret" to "imported-password"),
        ).toJson()
        json.remove("ntripCasterUploadProfiles")

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        val imported = settingsBackupImportPlan(
            backup = result.backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            retainedProfileIds = RetainedSettingsProfileIds(ntripCasterUploadProfileIds = setOf("upload")),
            idFactory = deterministicIdFactory(),
        ).backup
        val remappedSecretId = assertNotNull(
            imported.settingsSets.single().overrides.ntripCasterUpload?.secretId,
        )
        assertTrue(remappedSecretId != "upload-override-secret")
        assertEquals("imported-password", imported.plaintextPasswordsBySecretId[remappedSecretId])
        assertFalse(imported.plaintextPasswordsBySecretId.containsKey("upload-override-secret"))
    }

    @Test
    fun `plaintext password for profile-owned secret id is accepted`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.put("plaintextPasswords", JSONObject().put(ntripCasterSecretId("caster"), "secret-password"))

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
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
            rtklibProfiles = listOf(RtklibProfile(id = "rtklib", name = "RTKLIB")),
            storageProfiles = listOf(StorageProfile(id = "storage", name = "Storage")),
            settingsSets = listOf(sampleSettingsSet()),
            selectedSettingsSetId = "settings",
            selectedWorkflowId = "rover-ntrip",
            lastActiveNtripMountpointProfileId = "mount",
            passwordsBySecretId = if (includePassword) mapOf("secret" to "secret-password") else emptyMap(),
            options = SettingsSetExportOptions(includePlaintextPasswords = includePassword),
        )

    private fun sampleSettingsSet(): RecordingSettingsSet =
        RecordingSettingsSet.builtInRoverNtrip().copy(
            id = "settings",
            commandProfileRef = ProfileReference("command", "Command"),
            usbBaudProfileRef = ProfileReference("usb", "USB"),
            ntripCasterProfileRef = ProfileReference("caster", "Caster"),
            ntripMountpointProfileRef = ProfileReference("mount", "Mount"),
            recordingOutputProfileRef = ProfileReference("policy", "Policy"),
            storageProfileRef = ProfileReference("storage", "Storage"),
        )

    private fun deterministicIdFactory(): SettingsImportIdFactory {
        var counter = 0
        return SettingsImportIdFactory { namespace -> "$namespace-test-${counter++}" }
    }

    private fun resolvableImportedSecretIds(backup: SettingsBackupFile): Set<String> = buildSet {
        backup.ntripCasterProfiles.forEach { profile ->
            add(ntripCasterSecretId(profile.id))
            profile.secretId.takeIf(String::isNotBlank)?.let(::add)
        }
        backup.ntripCasterUploadProfiles.forEach { profile ->
            add(ntripCasterUploadSecretId(profile.id))
            profile.secretId.takeIf(String::isNotBlank)?.let(::add)
        }
        backup.settingsSets.forEach { settingsSet ->
            settingsSet.overrides.ntripCaster?.secretId?.takeIf(String::isNotBlank)?.let(::add)
            settingsSet.overrides.ntripCasterUpload?.secretId?.takeIf(String::isNotBlank)?.let(::add)
        }
    }
}
