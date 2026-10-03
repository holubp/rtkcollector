package org.rtkcollector.app.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LegacyProfileMigrationPlannerTest {
    @Test
    fun `migrated nonempty prebaud init survives editor open unchanged save rename and distinct edits byte exact`() {
        val init = "\r\n UNLOGALL COM1\r\n"
        val runtime = "MODE ROVER\r\nBESTNAVB COM1 1\r\n"
        val shutdown = " UNLOGALL COM1\n \n"
        val set = sampleSet().copy(overrides = SettingsSetOverrides(command = CommandProfileOverride(initScript = init)))
        val migrated = planLegacyProfileOwnership(sampleBackup(set).copy(commandProfiles = listOf(
            CommandProfile("command", "Command", runtimeScript = runtime, shutdownScript = shutdown))))
        val selectedId = ActiveSetupResolver.resolve(migrated.settingsSets.single(),
            migrated.activeSetupSelections.getValue(set.id)).option(ActiveSetupOptionKey.RECEIVER_COMMAND).effectiveValueId
        val profile = migrated.commandProfiles.single { it.id == selectedId }
        assertEquals(init, profile.initScript)
        val opened = mapOf("initScript" to profile.initScript, "runtimeScript" to profile.runtimeScript,
            "shutdownScript" to profile.shutdownScript)
        assertEquals(profile, profile.withEditedCommandPhases(opened))
        val renamed = profile.copy(name = "Renamed").withEditedCommandPhases(opened)
        assertEquals(init, renamed.initScript)
        assertEquals(runtime, renamed.runtimeScript)
        assertEquals(shutdown, renamed.shutdownScript)
        assertEquals(init, profile.withEditedCommandPhases(emptyMap()).initScript)
        val edited = profile.withEditedCommandPhases(opened + ("initScript" to ""))
        assertEquals("", edited.initScript)
        assertEquals(runtime, edited.runtimeScript)
        assertEquals(shutdown, edited.shutdownScript)
    }
    @Test
    fun `legacy default owner binding recovers only proven endpoint alias`() {
        val caster = NtripCasterProfile("caster", "Caster", host = "caster.invalid", username = "operator")
        val result = planLegacyOwnershipMigration(sampleBackup(sampleSet()).copy(ntripCasterProfiles = listOf(caster)),
            { id -> if (id == legacyNtripCasterSecretId(caster)) LegacyCredential.Available("alias-fixture")
                else LegacyCredential.Missing }, newBinding = { "fresh-owner" })
        assertEquals(setOf("fresh-owner"), result.stagedPasswords.keys)
    }

    @Test
    fun `missing explicit owner does not adopt unrelated canonical credential`() {
        val caster = NtripCasterProfile("caster", "Caster", secretId = "explicit-owner")
        val reads = mutableListOf<String>()
        val result = planLegacyOwnershipMigration(sampleBackup(sampleSet()).copy(ntripCasterProfiles = listOf(caster)),
            { id -> reads += id; if (id == ntripCasterSecretId(caster.id)) LegacyCredential.Available("stale-fixture")
                else LegacyCredential.Missing }, newBinding = { "fresh-owner" })
        assertTrue(result.stagedPasswords.isEmpty())
        assertEquals(listOf("explicit-owner"), reads)
    }

    @Test
    fun `explicit upload Off leaves missing credential inactive`() {
        val set = sampleSet().copy(workflowId = "fixed-base", baseCasterUploadEnabled = false,
            ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"))
        val record = LegacyMigrationRecovery(set, setOf(MigrationReviewReason.MISSING_CREDENTIAL), listOf(
            MigrationReviewIssue(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD, "secretId",
                LegacyFieldDisposition.UNCERTAIN, MigrationReviewReason.MISSING_CREDENTIAL, "upload")))
        assertTrue(record.blockingIssues(set, ActiveSetupSelections(set.id)).isEmpty())
        assertTrue(record.blockingIssues(set.copy(baseCasterUploadEnabled = true),
            ActiveSetupSelections(set.id)).isNotEmpty())
    }

    @Test
    fun `missing explicit caster never falls back to source owner`() {
        val caster = NtripCasterProfile("caster", "Caster")
        val mount = NtripMountpointProfile("mount", "Mount", caster.id)
        val set = sampleSet().copy(ntripCasterProfileRef = ProfileReference("missing", "Missing"),
            ntripMountpointProfileRef = ProfileReference(mount.id, mount.name),
            overrides = SettingsSetOverrides(ntripMountpoint = NtripMountpointOverride(mountpoint = "OTHER")))
        val result = planLegacyProfileOwnership(sampleBackup(set).copy(ntripCasterProfiles = listOf(caster),
            ntripMountpointProfiles = listOf(mount)))
        assertEquals(listOf(mount), result.ntripMountpointProfiles)
        assertTrue(result.migrationRecovery.getValue(set.id).reviewReasons.contains(MigrationReviewReason.CASTER_LINEAGE))
    }

    @Test
    fun `missing effective command overlay is classified uncertain and blocks commands`() {
        val set = sampleSet().copy(overrides = SettingsSetOverrides(
            commandProfileRef = ProfileReference("missing", "Missing"),
            command = CommandProfileOverride(initScript = "MODE ROVER")))
        val result = planLegacyProfileOwnership(sampleBackup(set))
        val record = result.migrationRecovery.getValue(set.id)
        assertTrue(record.issues.any { it.option == ActiveSetupOptionKey.RECEIVER_COMMAND &&
            it.disposition == LegacyFieldDisposition.UNCERTAIN && it.reason == MigrationReviewReason.MISSING_PROFILE })
        assertTrue(record.blockingIssues(result.settingsSets.single(), result.activeSetupSelections.getValue(set.id)).isNotEmpty())
    }
    @Test
    fun `verified owner credential repair clears only that credential reason`() {
        val caster = NtripCasterProfile("caster", "Caster", secretId = "repaired-owner")
        val mount = NtripMountpointProfile("mount", "Mount", caster.id)
        val set = sampleSet().copy(ntripMountpointProfileRef = ProfileReference(mount.id, mount.name))
        val issues = listOf(
            MigrationReviewIssue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, "secretId", LegacyFieldDisposition.UNCERTAIN,
                MigrationReviewReason.MISSING_CREDENTIAL, mount.id),
            MigrationReviewIssue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, "source", LegacyFieldDisposition.UNCERTAIN,
                MigrationReviewReason.CASTER_LINEAGE, mount.id),
        )
        val result = planLegacyOwnershipMigration(sampleBackup(set).copy(ntripCasterProfiles = listOf(caster),
            ntripMountpointProfiles = listOf(mount), migrationRecovery = mapOf(set.id to
                LegacyMigrationRecovery(set, issues.mapNotNull { it.reason }.toSet(), issues))),
            { LegacyCredential.Available("repaired-fixture") }, newBinding = { "new-owner" })
        val reasons = result.backup.migrationRecovery.getValue(set.id).issues.filter { it.resolution == null }.mapNotNull { it.reason }.toSet()
        assertFalse(MigrationReviewReason.MISSING_CREDENTIAL in reasons)
        assertTrue(MigrationReviewReason.CASTER_LINEAGE in reasons)
    }
    @Test
    fun `secret binding migration isolates each owner and distinguishes unreadable credentials`() {
        val caster = NtripCasterProfile("caster", "Caster", secretId = "explicit")
        val upload = NtripCasterUploadProfile("upload", "Upload", secretId = "broken")
        val mount = NtripMountpointProfile("mount", "Mount", caster.id)
        val set = sampleSet().copy(ntripMountpointProfileRef = ProfileReference(mount.id, mount.name))
        val reads = mutableListOf<String>()
        var next = 0
        val result = planLegacyOwnershipMigration(sampleBackup(set).copy(ntripCasterProfiles = listOf(caster),
            ntripMountpointProfiles = listOf(mount), ntripCasterUploadProfiles = listOf(upload)), { id ->
            reads += id
            if (id == "explicit") LegacyCredential.Available("fixture-only") else LegacyCredential.Unreadable
        }, newBinding = { "fresh-${++next}" })
        assertEquals(listOf("explicit", "broken"), reads)
        assertEquals(setOf("fresh-1", "fresh-2"), result.newSecretBindings)
        assertEquals(setOf("fresh-1"), result.stagedPasswords.keys)
        assertTrue(result.backup.plaintextPasswordsBySecretId.isEmpty())
        assertTrue(result.backup.migrationRecovery.getValue(set.id).issues.any {
            it.reason == MigrationReviewReason.UNREADABLE_CREDENTIAL && it.profileId == upload.id
        })
    }

    @Test
    fun `scoped workflow provenance never leaks across settings sets`() {
        val first = sampleSet().copy(id = "first", workflowApplicationPolicy = WorkflowApplicationPolicy.LEAVE_INTACT)
        val second = first.copy(id = "second")
        val result = planLegacyOwnershipMigration(sampleBackup(first).copy(settingsSets = listOf(first, second)),
            { LegacyCredential.Missing }, provenance = LegacyChoiceProvenance(
                workflowsBySet = mapOf("first" to "fixed-base"), unscopedWorkflowId = "rover-ntrip"))
        assertEquals("fixed-base", result.backup.activeSetupSelections.getValue("first").workflowBaselineId)
        assertEquals(null, result.backup.activeSetupSelections.getValue("second").workflowBaselineId)
        assertEquals("rover-ntrip", result.backup.migrationRecovery.getValue("second").choiceProvenance["unscopedWorkflow"])
    }
    @Test
    fun `missing dormant NTRIP does not block plain rover but blocks that route`() {
        val set = sampleSet().copy(workflowId = "plain-rover", overrides = SettingsSetOverrides(
            ntripMountpointProfileRef = ProfileReference("missing", "Missing source"),
        ))
        val result = planLegacyProfileOwnership(sampleBackup(set))
        val migrated = result.settingsSets.single()
        val state = result.activeSetupSelections.getValue(set.id)
        val record = result.migrationRecovery.getValue(set.id)
        assertTrue(record.blockingIssues(migrated, state).isEmpty())
        assertFalse(record.blockingIssues(migrated.copy(workflowId = "rover-ntrip"), state).isEmpty())
    }

    @Test
    fun `repeated completed migration preserves graph and recovery`() {
        val set = sampleSet().copy(overrides = SettingsSetOverrides(
            command = CommandProfileOverride(initScript = "MODE ROVER SURVEY"),
        ))
        val once = planLegacyProfileOwnership(sampleBackup(set))
        assertEquals(once, planLegacyProfileOwnership(once))
    }

    @Test
    fun `metadata stays descriptive and does not invent GGA transmission`() {
        val caster = NtripCasterProfile("caster", "Caster", host = "example.invalid")
        val mount = NtripMountpointProfile("mount", "Mount", caster.id, ggaUploadPolicy = "disabled")
        val set = sampleSet().copy(ntripCasterProfileRef = ProfileReference(caster.id, caster.name),
            ntripMountpointProfileRef = ProfileReference(mount.id, mount.name), overrides = SettingsSetOverrides(
                ntripMountpoint = NtripMountpointOverride(stationId = "station", baseLatDeg = 50.0, baseLonDeg = 14.0),
            ))
        val result = planLegacyProfileOwnership(sampleBackup(set).copy(ntripCasterProfiles = listOf(caster), ntripMountpointProfiles = listOf(mount)))
        val id = result.activeSetupSelections.getValue(set.id).activeChoices.getValue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).profileId
        val derived = result.ntripMountpointProfiles.single { it.id == id }
        assertEquals("station", derived.stationId)
        assertEquals(50.0, derived.baseLatDeg)
        assertEquals(14.0, derived.baseLonDeg)
        assertEquals("disabled", derived.ggaUploadPolicy)
    }
    @Test
    fun `ignored USB matching stays dormant while baud is materialized`() {
        val set = sampleSet().copy(overrides = SettingsSetOverrides(
            usbBaud = UsbBaudProfileOverride(serialBaud = 115200, usbVid = 123, usbPid = 456,
                usbDeviceName = "legacy-device"),
        ))
        val result = planLegacyProfileOwnership(sampleBackup(set))
        val id = result.activeSetupSelections.getValue(set.id).activeChoices
            .getValue(ActiveSetupOptionKey.USB_BAUD).profileId
        val profile = result.usbBaudProfiles.single { it.id == id }
        assertEquals(115200, profile.serialBaud)
        assertEquals(null, profile.usbVid)
        assertEquals(null, profile.usbPid)
        assertEquals(null, profile.usbDeviceName)
        assertTrue(MigrationReviewReason.DORMANT_OVERRIDE in result.migrationRecovery.getValue(set.id).reviewReasons)
    }

    @Test
    fun `proven NTRIP lineage derives entire dependency and upload preserves safety`() {
        val caster = NtripCasterProfile("caster", "Caster", host = "example.invalid", secretId = "owner-old")
        val mount = NtripMountpointProfile("mount", "Mount", caster.id, mountpoint = "OLD", ggaUploadPolicy = "disabled")
        val upload = NtripCasterUploadProfile("upload", "Upload", host = "upload.invalid", mountpoint = "OLD",
            secretId = "upload-old", safetyRulesEnabled = true, safetyMaxBitrateKbps = 12)
        val set = sampleSet().copy(ntripCasterProfileRef = ProfileReference(caster.id, caster.name),
            ntripMountpointProfileRef = ProfileReference(mount.id, mount.name),
            ntripCasterUploadProfileRef = ProfileReference(upload.id, upload.name), overrides = SettingsSetOverrides(
                ntripMountpoint = NtripMountpointOverride(mountpoint = "NEW", stationId = "station", baseLatDeg = 50.0),
                ntripCasterUpload = NtripCasterUploadOverride(mountpoint = "NEW"), baseCasterUploadEnabled = true,
            ))
        val result = planLegacyProfileOwnership(sampleBackup(set).copy(ntripCasterProfiles = listOf(caster),
            ntripMountpointProfiles = listOf(mount), ntripCasterUploadProfiles = listOf(upload)))
        val state = result.activeSetupSelections.getValue(set.id)
        val chosen = result.ntripMountpointProfiles.single { it.id == state.activeChoices.getValue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).profileId }
        assertEquals("NEW", chosen.mountpoint)
        assertEquals("disabled", chosen.ggaUploadPolicy)
        val chosenUpload = result.ntripCasterUploadProfiles.single { it.id == state.uploadSelection!!.profile.profileId }
        assertEquals("NEW", chosenUpload.mountpoint)
        assertEquals(12, chosenUpload.safetyMaxBitrateKbps)
        assertTrue(chosenUpload.safetyRulesEnabled)
        assertTrue(state.uploadSelection!!.enabled)
        assertEquals(set, result.migrationRecovery.getValue(set.id).legacySettingsSet)
    }
    @Test
    fun `field overlays become complete user profiles without changing set defaults`() {
        val set = sampleSet().copy(
            overrides = SettingsSetOverrides(
                command = CommandProfileOverride(initScript = "MODE ROVER SURVEY"),
                recordingOutput = RecordingOutputOverride(enableMockLocation = true),
                storage = StorageProfileOverride(kind = "APP_PRIVATE"),
            ),
        )

        val planned = planLegacyProfileOwnership(sampleBackup(set))
        val migrated = planned.settingsSets.single()
        val choices = planned.activeSetupSelections.getValue(set.id)

        assertEquals(set.commandProfileRef, migrated.commandProfileRef)
        assertEquals(set.recordingOutputProfileRef, migrated.recordingOutputProfileRef)
        assertEquals(set.storageProfileRef, migrated.storageProfileRef)
        assertEquals(SettingsSetOverrides(), migrated.overrides)
        assertTrue(planned.commandProfiles.any { !it.isProtected && it.initScript == "MODE ROVER SURVEY" })
        assertTrue(planned.recordingPolicyProfiles.any { !it.isProtected && it.enableMockLocation })
        assertTrue(planned.storageProfiles.any { !it.isProtected && it.kind == "APP_PRIVATE" })
        assertNotEquals(ActiveSetupSelections(set.id), choices)
    }

    @Test
    fun `locked overlay stays inactive and recoverable`() {
        val set = sampleSet().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults().withPolicy(
                ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.LOCKED,
            ),
            overrides = SettingsSetOverrides(command = CommandProfileOverride(initScript = "MODE BASE TIME 120 2.5")),
        )

        val planned = planLegacyProfileOwnership(sampleBackup(set))

        assertEquals(ActiveSetupSelections(set.id), planned.activeSetupSelections.getValue(set.id))
        assertEquals(set, planned.migrationRecovery.getValue(set.id).legacySettingsSet)
        assertTrue(MigrationReviewReason.DORMANT_OVERRIDE in planned.migrationRecovery.getValue(set.id).reviewReasons)
        assertFalse(planned.commandProfiles.any { it.initScript == "MODE BASE TIME 120 2.5" })
    }

    @Test
    fun `same effective content reuses one derived profile deterministically`() {
        val first = sampleSet().copy(id = "first", overrides = SettingsSetOverrides(
            command = CommandProfileOverride(initScript = "MODE ROVER SURVEY"),
        ))
        val second = first.copy(id = "second")

        val once = planLegacyProfileOwnership(sampleBackup(first).copy(settingsSets = listOf(first, second)))
        val twice = planLegacyProfileOwnership(sampleBackup(first).copy(settingsSets = listOf(first, second)))

        assertEquals(2, once.commandProfiles.size)
        assertEquals(once.commandProfiles.map(CommandProfile::id), twice.commandProfiles.map(CommandProfile::id))
    }

    private fun sampleSet(): RecordingSettingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
        id = "legacy-set",
        name = "Legacy set",
        isProtected = false,
        commandProfileRef = ProfileReference("command", "Command"),
        usbBaudProfileRef = ProfileReference("baud", "Baud"),
        recordingOutputProfileRef = ProfileReference("output", "Output"),
        storageProfileRef = ProfileReference("storage", "Storage"),
        ntripCasterProfileRef = null,
    )

    private fun sampleBackup(set: RecordingSettingsSet): SettingsBackupFile = SettingsBackupFile.fromProfiles(
        commandProfiles = listOf(CommandProfile(id = "command", name = "Command")),
        usbBaudProfiles = listOf(UsbBaudProfile(id = "baud", name = "Baud")),
        ntripCasterProfiles = emptyList(), ntripCasterUploadProfiles = emptyList(),
        ntripMountpointProfiles = emptyList(),
        recordingPolicyProfiles = listOf(RecordingPolicyProfile(id = "output", name = "Output")),
        storageProfiles = listOf(StorageProfile(id = "storage", name = "Storage", kind = "SAF_TREE",
            treeUri = "content://documents/tree/old")),
        settingsSets = listOf(set), selectedSettingsSetId = set.id, selectedWorkflowId = null,
        lastActiveNtripMountpointProfileId = null, passwordsBySecretId = emptyMap(),
        options = SettingsSetExportOptions(),
    )
}
