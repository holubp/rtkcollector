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
    fun `SAF reconciliation requires both persisted grants and retains resolution history`() {
        val uri = "content://documents/tree/selected"
        val backup = sampleBackup(false)
        val set = backup.settingsSets.single()
        val storage = backup.storageProfiles.single().copy(kind = "SAF_TREE", treeUri = uri)
        val issue = MigrationReviewIssue(ActiveSetupOptionKey.STORAGE, "treeUri", LegacyFieldDisposition.UNCERTAIN,
            MigrationReviewReason.SAF_RESELECTION, storage.id)
        val recovery = LegacyMigrationRecovery(set, setOf(MigrationReviewReason.SAF_RESELECTION), listOf(issue))
        listOf(false, true).forEach { read -> listOf(false, true).forEach { write ->
            val grants = if (hasPersistedSafTreeAuthority(read, write)) setOf(uri) else emptySet()
            val reconciled = backup.copy(storageProfiles = listOf(storage), migrationRecovery = mapOf(set.id to recovery))
                .withValidatedSafAuthority(grants)
            val record = reconciled.migrationRecovery.getValue(set.id)
            assertEquals(read && write, !reconciled.storageProfiles.single().requiresTreeReselection)
            assertEquals(read && write, record.blockingIssues(set, ActiveSetupSelections(set.id)).isEmpty())
            assertEquals(issue.reason, record.issues.first().reason)
            assertEquals(read && write, record.issues.first().resolution != null)
        } }
    }
    @Test
    fun `validated repair history survives backup export parse and import remapping`() {
        val backup = sampleBackup(false)
        val set = backup.settingsSets.single()
        val issue = MigrationReviewIssue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, "ntripCasterPolicy",
            LegacyFieldDisposition.UNCERTAIN, MigrationReviewReason.POLICY_CONFLICT,
            resolution = MigrationIssueResolution.VALIDATED_OPERATOR_REPAIR)
        val withHistory = backup.copy(formatVersion = SettingsBackupFile.CURRENT_FORMAT_VERSION,
            migrationRecovery = mapOf(set.id to LegacyMigrationRecovery(
            set, emptySet(), listOf(issue))))
        val exported = SettingsBackupFile.fromJson(withHistory.toJson())
        assertEquals(issue, exported.migrationRecovery.getValue(set.id).issues.single())
        val imported = settingsBackupImportPlan(exported, emptySet()).backup
        assertTrue(imported.migrationRecovery.getValue(set.id).issues.contains(issue))
        assertFalse(imported.migrationRecovery.getValue(set.id).blockingIssues(
            imported.settingsSets.single(), ActiveSetupSelections(set.id)).contains(issue))
    }
    @Test
    fun `idle migration revoked SAF authority preserves reference and requires reselection`() {
        val backup = sampleBackup(false).copy(storageProfiles = listOf(StorageProfile("storage", "Storage",
            kind = "SAF_TREE", treeUri = "content://documents/tree/revoked")))
        val sanitized = backup.withValidatedSafAuthority(emptySet())
        assertEquals("storage", sanitized.settingsSets.single().storageProfileRef.id)
        assertNull(sanitized.storageProfiles.single().treeUri)
        assertTrue(sanitized.storageProfiles.single().requiresTreeReselection)
        assertTrue(sanitized.migrationRecovery.getValue("settings").blockingIssues(
            sanitized.settingsSets.single(), ActiveSetupSelections("settings")).any {
            it.reason == MigrationReviewReason.SAF_RESELECTION })
        val granted = backup.withValidatedSafAuthority(setOf("content://documents/tree/revoked"))
        assertEquals(backup.storageProfiles, granted.storageProfiles)
    }

    @Test
    fun `SAF authority reconciliation clears only repaired profile issue`() {
        val set = sampleSettingsSet()
        val lost = StorageProfile("storage", "Storage", kind = "SAF_TREE",
            requiresTreeReselection = true)
        val other = StorageProfile("other", "Other", kind = "SAF_TREE",
            requiresTreeReselection = true)
        val backup = sampleBackup(false).copy(storageProfiles = listOf(lost, other),
            migrationRecovery = mapOf(set.id to LegacyMigrationRecovery(set,
                setOf(MigrationReviewReason.SAF_RESELECTION), issues = listOf(
                    MigrationReviewIssue(ActiveSetupOptionKey.STORAGE, "treeUri",
                        LegacyFieldDisposition.UNCERTAIN, MigrationReviewReason.SAF_RESELECTION, lost.id),
                    MigrationReviewIssue(ActiveSetupOptionKey.STORAGE, "treeUri",
                        LegacyFieldDisposition.UNCERTAIN, MigrationReviewReason.SAF_RESELECTION, other.id),
                ))))
        val repaired = backup.copy(storageProfiles = listOf(
            lost.copy(treeUri = "content://documents/tree/reselected", requiresTreeReselection = false), other,
        )).withValidatedSafAuthority(setOf("content://documents/tree/reselected"))

        assertEquals(listOf(other.id), repaired.migrationRecovery.getValue(set.id).issues
            .filter { it.reason == MigrationReviewReason.SAF_RESELECTION && it.resolution == null }.map { it.profileId })
        assertTrue(repaired.migrationRecovery.getValue(set.id).issues.any { it.profileId == lost.id && it.resolution != null })
        assertTrue(MigrationReviewReason.SAF_RESELECTION in repaired.migrationRecovery.getValue(set.id).reviewReasons)
    }

    @Test
    fun `format two missing explicit owner never imports stale canonical password`() {
        val backup = sampleBackup(false).copy(formatVersion = 2,
            plaintextPasswordsBySecretId = mapOf(ntripCasterSecretId("caster") to "stale-fixture"))
        val imported = settingsBackupImportPlan(backup, emptySet()).backup
        assertTrue(imported.plaintextPasswordsBySecretId.isEmpty())
        assertTrue(imported.migrationRecovery.getValue("settings").issues.any {
            it.reason == MigrationReviewReason.MISSING_CREDENTIAL })
    }

    @Test
    fun `unclassified input becomes an allowlisted descriptor not normal recovery content`() {
        val raw = sampleBackup(false).toJson()
        raw.getJSONArray("commandProfiles").getJSONObject(0).put("futurePrivateField",
            JSONObject().put("unknownToken", "private-only-fixture"))
        val parsed = SettingsBackupFile.fromJson(raw)
        assertFalse(parsed.toJson().toString().contains("private-only-fixture"))
        assertFalse(parsed.toJson().toString().contains("futurePrivateField"))
        assertTrue(parsed.migrationRecovery.getValue("settings").issues.any {
            it.option == ActiveSetupOptionKey.RECEIVER_COMMAND && it.field == "unclassifiedInput"
        })
    }
    @Test
    fun `format two remaps restriction and active upload dependency`() {
        val set = sampleSettingsSet().copy(
            ntripCasterRestrictionRef = ProfileReference("caster", "Caster"),
            ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
        )
        val backup = sampleBackup(includePassword = false).copy(
            formatVersion = 2,
            ntripCasterUploadProfiles = listOf(NtripCasterUploadProfile(id = "upload", name = "Upload")),
            settingsSets = listOf(set),
            activeSetupSelections = mapOf(
                set.id to ActiveSetupSelections(
                    settingsSetId = set.id,
                    uploadSelection = UploadSelection(true, SelectionChoice.profile("upload")),
                ),
            ),
            migrationRecovery = mapOf(
                set.id to LegacyMigrationRecovery(set, setOf(MigrationReviewReason.DORMANT_OVERRIDE)),
            ),
        )

        val imported = settingsBackupImportPlan(
            backup, persistedSafTreeUrisWithWriteAccess = emptySet(),
            idFactory = deterministicIdFactory(),
        ).backup

        assertEquals(imported.ntripCasterProfiles.single().id,
            imported.settingsSets.single().ntripCasterRestrictionRef?.id)
        assertEquals(imported.ntripCasterUploadProfiles.single().id,
            imported.activeSetupSelections.getValue(set.id).uploadSelection?.profile?.profileId)
        assertEquals(imported.ntripCasterProfiles.single().id,
            imported.migrationRecovery.getValue(set.id).legacySettingsSet.ntripCasterRestrictionRef?.id)
    }

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
        val missingPlan = settingsBackupImportPlan(result.backup, emptySet())
        val imported = missingPlan.backup.settingsSets.single()
        assertEquals("rtklib", imported.rtklibProfileRef?.id)
        val recovery = missingPlan.backup.migrationRecovery.getValue(imported.id)
        assertTrue(recovery.blockingIssues(imported, ActiveSetupSelections(imported.id))
            .none { it.option == ActiveSetupOptionKey.RTKLIB })
        assertTrue(recovery.blockingIssues(imported.copy(workflowId = "rover-rtklib"),
            ActiveSetupSelections(imported.id)).any { it.option == ActiveSetupOptionKey.RTKLIB })
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
    fun `missing inactive rtklib reference remains recoverable without blocking rover`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.getJSONArray("settingsSets")
            .getJSONObject(0)
            .put("rtklibProfile", JSONObject().put("id", "missing-rtklib").put("name", "Missing RTKLIB"))

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        val plan = settingsBackupImportPlan(result.backup, emptySet())
        val imported = plan.backup.settingsSets.single()
        assertEquals("missing-rtklib", imported.rtklibProfileRef?.id)
        val recovery = plan.backup.migrationRecovery.getValue(imported.id)
        assertTrue(recovery.issues.any { it.option == ActiveSetupOptionKey.RTKLIB &&
            it.reason == MigrationReviewReason.MISSING_PROFILE })
        assertTrue(recovery.blockingIssues(imported, ActiveSetupSelections(imported.id))
            .none { it.option == ActiveSetupOptionKey.RTKLIB })
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
        val selectedStorage = selectedStorage(plan.backup)
        assertEquals("SAF_TREE", selectedStorage.kind)
        assertNull(selectedStorage.treeUri)
        assertTrue(selectedStorage.requiresTreeReselection)
        assertEquals(SettingsSetOverrides(), plan.backup.settingsSets.single().overrides)
        assertEquals("content://documents/tree/override-missing",
            plan.backup.migrationRecovery.getValue("settings").legacySettingsSet.overrides.storage?.treeUri)

        val reparsed = SettingsBackupFile.fromJson(plan.backup.toJson())
        assertTrue(reparsed.storageProfiles[1].requiresTreeReselection)
        assertTrue(selectedStorage(reparsed).requiresTreeReselection)
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
        assertEquals("app-storage", settingsSet.storageProfileRef.id)
        assertEquals(SettingsSetOverrides(), settingsSet.overrides)
        assertEquals("SAF_TREE", selectedStorage(plan.backup).kind)
        assertNull(selectedStorage(plan.backup).treeUri)
        assertTrue(selectedStorage(plan.backup).requiresTreeReselection)
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
    fun `legacy RC2 caster password is accepted and rekeyed`() {
        val legacySecretId = "ntrip:euref-ip.net:caster:pholub"
        val backup = sampleBackup(includePassword = false).copy(
            ntripCasterProfiles = listOf(
                NtripCasterProfile(
                    id = "caster",
                    name = "EUREF",
                    host = "euref-ip.net",
                    username = "pholub",
                    secretId = ntripCasterSecretId("caster"),
                ),
            ),
            plaintextPasswordsBySecretId = mapOf(legacySecretId to "rc2-password"),
        )

        val result = validateSettingsImportJson(backup.toJson().toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        val imported = settingsBackupImportPlan(
            backup = result.backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            idFactory = deterministicIdFactory(),
        ).backup
        val importedSecretId = imported.ntripCasterProfiles.single().secretId
        assertEquals("rc2-password", imported.plaintextPasswordsBySecretId[importedSecretId])
        assertFalse(imported.plaintextPasswordsBySecretId.containsKey(legacySecretId))
    }

    @Test
    fun `legacy RC2 mountpoint keyed caster password is accepted and rekeyed`() {
        val profile = NtripCasterProfile(
            id = "caster",
            name = "EUREF",
            host = "euref-ip.net",
            username = "pholub",
            secretId = ntripCasterSecretId("caster"),
        )
        val mountpoint = NtripMountpointProfile(
            id = "tubo",
            name = "TUBO",
            casterProfileId = profile.id,
            mountpoint = "TUBO00CZE0",
        )
        val legacySecretId = "ntrip:euref-ip.net:TUBO00CZE0:pholub"
        val backup = sampleBackup(includePassword = false).copy(
            ntripCasterProfiles = listOf(profile),
            ntripMountpointProfiles = listOf(mountpoint),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    ntripMountpointProfileRef = ProfileReference(mountpoint.id, mountpoint.name),
                ),
            ),
            lastActiveNtripMountpointProfileId = mountpoint.id,
            plaintextPasswordsBySecretId = mapOf(legacySecretId to "rc2-password"),
        )

        val result = validateSettingsImportJson(backup.toJson().toString())

        assertTrue(result is SettingsImportValidationResult.Valid, result.toString())
        val imported = settingsBackupImportPlan(
            backup = result.backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            idFactory = deterministicIdFactory(),
        ).backup
        val importedSecretId = imported.ntripCasterProfiles.single().secretId
        assertEquals("rc2-password", imported.plaintextPasswordsBySecretId[importedSecretId])
        assertFalse(imported.plaintextPasswordsBySecretId.containsKey(legacySecretId))
    }

    @Test
    fun `legacy RC2 mountpoint keyed password honors historical profile endpoint`() {
        val profile = NtripCasterProfile(
            id = "caster-1781686506572",
            name = "EUREF",
            host = "www.euref-ip.net",
            username = "pholub",
            secretId = "ntrip:euref-ip.net:caster:pholub",
        )
        val mountpoint = NtripMountpointProfile(
            id = "tubo",
            name = "TUBO",
            casterProfileId = profile.id,
            mountpoint = "TUBO00CZE0",
        )
        val legacySecretId = "ntrip:euref-ip.net:TUBO00CZE0:pholub"
        val backup = sampleBackup(includePassword = false).copy(
            ntripCasterProfiles = listOf(profile),
            ntripMountpointProfiles = listOf(mountpoint),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    ntripCasterProfileRef = ProfileReference(profile.id, profile.name),
                    ntripMountpointProfileRef = ProfileReference(mountpoint.id, mountpoint.name),
                ),
            ),
            lastActiveNtripMountpointProfileId = mountpoint.id,
            plaintextPasswordsBySecretId = mapOf(legacySecretId to "rc2-password"),
        )

        val result = validateSettingsImportJson(backup.toJson().toString())

        assertTrue(result is SettingsImportValidationResult.Valid, result.toString())
        val imported = settingsBackupImportPlan(
            backup = result.backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            idFactory = deterministicIdFactory(),
        ).backup
        assertEquals(
            "rc2-password",
            imported.plaintextPasswordsBySecretId[imported.ntripCasterProfiles.single().secretId],
        )
    }

    @Test
    fun `legacy RC2 mountpoint keyed password is rejected when mountpoint belongs to another caster`() {
        val caster = NtripCasterProfile(
            id = "caster",
            name = "EUREF",
            host = "euref-ip.net",
            username = "pholub",
            secretId = ntripCasterSecretId("caster"),
        )
        val otherCaster = NtripCasterProfile(
            id = "other-caster",
            name = "Other",
            host = "other.example",
            username = "other-user",
            secretId = ntripCasterSecretId("other-caster"),
        )
        val otherMountpoint = NtripMountpointProfile(
            id = "tubo",
            name = "TUBO",
            casterProfileId = otherCaster.id,
            mountpoint = "TUBO00CZE0",
        )
        val legacySecretId = "ntrip:euref-ip.net:TUBO00CZE0:pholub"
        val json = sampleBackup(includePassword = false).copy(
            ntripCasterProfiles = listOf(caster, otherCaster),
            ntripMountpointProfiles = listOf(otherMountpoint),
            settingsSets = listOf(
                sampleSettingsSet().copy(
                    ntripMountpointProfileRef = ProfileReference(otherMountpoint.id, otherMountpoint.name),
                ),
            ),
            lastActiveNtripMountpointProfileId = otherMountpoint.id,
            plaintextPasswordsBySecretId = mapOf(legacySecretId to "rc2-password"),
        ).toJson()

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals(
            "Plaintext NTRIP password references unknown secret '$legacySecretId'.",
            result.message,
        )
    }

    @Test
    fun `canonical caster password takes precedence over matching legacy RC2 password`() {
        val profile = NtripCasterProfile(
            id = "caster",
            name = "EUREF",
            host = "euref-ip.net",
            username = "pholub",
            secretId = ntripCasterSecretId("caster"),
        )
        val backup = sampleBackup(includePassword = false).copy(
            ntripCasterProfiles = listOf(profile),
            plaintextPasswordsBySecretId = mapOf(
                ntripCasterSecretId(profile.id) to "canonical-password",
                legacyNtripCasterSecretId(profile) to "legacy-password",
            ),
        )

        val result = validateSettingsImportJson(backup.toJson().toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        val imported = settingsBackupImportPlan(
            backup = result.backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            idFactory = deterministicIdFactory(),
        ).backup
        assertEquals(
            "canonical-password",
            imported.plaintextPasswordsBySecretId[imported.ntripCasterProfiles.single().secretId],
        )
    }

    @Test
    fun `explicit owner binding wins over stale canonical alias during import`() {
        val profile = NtripCasterProfile(
            id = "caster", name = "Caster", secretId = "owner-binding",
        )
        val backup = sampleBackup(includePassword = false).copy(
            ntripCasterProfiles = listOf(profile),
            plaintextPasswordsBySecretId = mapOf(
                profile.secretId to "owner-password",
                ntripCasterSecretId(profile.id) to "stale-password",
            ),
        )

        val imported = settingsBackupImportPlan(
            backup = backup,
            persistedSafTreeUrisWithWriteAccess = emptySet(),
            idFactory = deterministicIdFactory(),
        ).backup

        val binding = imported.ntripCasterProfiles.single().secretId
        assertEquals("owner-password", imported.plaintextPasswordsBySecretId[binding])
        assertTrue(binding != ntripCasterSecretId(imported.ntripCasterProfiles.single().id))
    }

    @Test
    fun `near matching legacy RC2 caster password is rejected`() {
        val json = sampleBackup(includePassword = false).copy(
            ntripCasterProfiles = listOf(
                NtripCasterProfile(
                    id = "caster",
                    name = "EUREF",
                    host = "euref-ip.net",
                    username = "pholub",
                    secretId = ntripCasterSecretId("caster"),
                ),
            ),
            plaintextPasswordsBySecretId = mapOf(
                "ntrip:euref-ip.net:other-caster:pholub" to "secret-password",
            ),
        ).toJson()

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals(
            "Plaintext NTRIP password references unknown secret 'ntrip:euref-ip.net:other-caster:pholub'.",
            result.message,
        )
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

        val caster = imported.ntripCasterProfiles.single { it.id == imported.settingsSets.single().ntripCasterProfileRef?.id }
        val upload = imported.ntripCasterUploadProfiles.single { it.id == imported.settingsSets.single().ntripCasterUploadProfileRef?.id }
        val settingsSet = imported.settingsSets.single()
        assertTrue(caster.id != "caster")
        assertTrue(upload.id != "upload")
        assertTrue(caster.secretId != ntripCasterSecretId(caster.id))
        assertTrue(upload.secretId != ntripCasterUploadSecretId(upload.id))
        assertEquals(caster.id, imported.ntripMountpointProfiles.single { it.id == "mount" }.casterProfileId)
        assertEquals(caster.id, settingsSet.ntripCasterProfileRef?.id)
        assertEquals(SettingsSetOverrides(), settingsSet.overrides)
        assertEquals(upload.id, settingsSet.ntripCasterUploadProfileRef?.id)
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

        val casterSecretId = imported.ntripCasterProfiles.single { it.id == imported.settingsSets.single().ntripCasterProfileRef?.id }.secretId
        val uploadSecretId = imported.ntripCasterUploadProfiles.single { it.id == imported.settingsSets.single().ntripCasterUploadProfileRef?.id }.secretId
        val sourceId = imported.activeSetupSelections.getValue("settings").activeChoices.getValue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).profileId
        val casterOverrideSecretId = imported.ntripCasterProfiles.single { caster ->
            caster.id == imported.ntripMountpointProfiles.single { it.id == sourceId }.casterProfileId
        }.secretId
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

        val previewDefault = preview.ntripCasterProfiles.single { it.id == preview.settingsSets.single().ntripCasterProfileRef?.id }
        val operationDefault = operation.ntripCasterProfiles.single { it.id == operation.settingsSets.single().ntripCasterProfileRef?.id }
        assertTrue(previewDefault.id != source.ntripCasterProfiles.single().id)
        assertTrue(operationDefault.id != previewDefault.id)
        assertEquals(
            operationDefault.id,
            operation.ntripMountpointProfiles.single { it.id == "mount" }.casterProfileId,
        )
        assertEquals(
            operationDefault.id,
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
            retainedUploadProfiles = listOf(retainedUploadProfile()),
            idFactory = deterministicIdFactory(),
        ).backup
        assertTrue(SettingsBackupProfileFamily.NTRIP_CASTER_UPLOAD !in imported.includedProfileFamilies)
        assertEquals(1, imported.ntripCasterUploadProfiles.size)
        assertEquals("upload", imported.settingsSets.single().ntripCasterUploadProfileRef?.id)
        assertEquals(SettingsSetOverrides(), imported.settingsSets.single().overrides)
        val remappedOverrideSecretId = selectedUpload(imported).secretId
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
            retainedUploadProfiles = listOf(retainedUploadProfile()),
            idFactory = deterministicIdFactory(),
        ).backup
        val uploadOwner = selectedUpload(imported)
        val shadowSecretId = uploadOwner.secretId
        assertEquals("imported.example.org", uploadOwner.host)
        assertTrue(shadowSecretId != retainedProfileSecretId)
        assertFalse(imported.plaintextPasswordsBySecretId.containsKey(shadowSecretId))
        val runtimePassword = localSecrets[uploadOwner.secretId]
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
            retainedUploadProfiles = listOf(retainedUploadProfile()),
            idFactory = deterministicIdFactory(),
        ).backup

        val shadowSecretId = selectedUpload(imported).secretId
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
            retainedUploadProfiles = listOf(retainedUploadProfile()),
            idFactory = deterministicIdFactory(),
        ).backup
        val remappedSecretId = selectedUpload(imported).secretId
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

    private fun retainedUploadProfile() = NtripCasterUploadProfile("upload", "Upload", host = "retained.example.invalid",
        mountpoint = "RET", secretId = ntripCasterUploadSecretId("upload"), safetyRulesEnabled = true)

    private fun selectedUpload(backup: SettingsBackupFile): NtripCasterUploadProfile {
        val id = backup.activeSetupSelections.getValue("settings").uploadSelection!!.profile.profileId
        return backup.ntripCasterUploadProfiles.single { it.id == id }
    }

    private fun selectedStorage(backup: SettingsBackupFile): StorageProfile {
        val id = backup.activeSetupSelections.getValue("settings").activeChoices.getValue(ActiveSetupOptionKey.STORAGE).profileId
        return backup.storageProfiles.single { it.id == id }
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
        ).copy(formatVersion = 1)

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
