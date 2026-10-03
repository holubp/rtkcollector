package org.rtkcollector.app.profile

import kotlin.test.*

class LegacyMigrationRepairTest {
    private val command = CommandProfile("command", "Command", runtimeScript = "MODE ROVER")
    private val baud = UsbBaudProfile("baud", "Baud")
    private val output = RecordingPolicyProfile("output", "Output")
    private val storage = StorageProfile("storage", "Storage")
    private val caster = NtripCasterProfile("caster", "Caster", host = "caster.example", secretId = "exact-owner")
    private val source = NtripMountpointProfile("source", "Source", caster.id, mountpoint = "MOUNT")
    private val set = RecordingSettingsSet.builtInRoverNtrip().copy(id = "editable", isProtected = false,
        commandProfileRef = ProfileReference(command.id, command.name), usbBaudProfileRef = ProfileReference(baud.id, baud.name),
        recordingOutputProfileRef = ProfileReference(output.id, output.name), storageProfileRef = ProfileReference(storage.id, storage.name),
        ntripMountpointProfileRef = ProfileReference(source.id, source.name), ntripCasterProfileRef = null)
    private val graph = ActiveSetupProfileGraph(commandProfiles = listOf(command), usbBaudProfiles = listOf(baud),
        recordingOutputProfiles = listOf(output), storageProfiles = listOf(storage),
        ntripCasterProfiles = listOf(caster), ntripMountpointProfiles = listOf(source))

    private fun recovery(old: RecordingSettingsSet = set, vararg issues: MigrationReviewIssue) =
        LegacyMigrationRecovery(old, issues.mapNotNull { it.reason }.toSet(), issues.toList())
    private fun issue(key: ActiveSetupOptionKey, reason: MigrationReviewReason, field: String = "selection", id: String? = null) =
        MigrationReviewIssue(key, field, LegacyFieldDisposition.UNCERTAIN, reason, id)

    @Test fun `retired caster repair restores applicable Start and preserves history and unrelated issues`() {
        val old = set.copy(ntripCasterPolicyNeedsReview = true, optionPolicies = set.optionPolicies
            .withPolicy(ActiveSetupOptionKey.NTRIP_CASTER, SettingsSetOptionPolicy.ASK_EVERY_TIME))
        val policy = issue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, MigrationReviewReason.POLICY_CONFLICT, "ntripCasterPolicy")
        val dormant = issue(ActiveSetupOptionKey.RTKLIB, MigrationReviewReason.DORMANT_OVERRIDE)
        val unknown = issue(ActiveSetupOptionKey.STORAGE, MigrationReviewReason.UNCLASSIFIED_INPUT)
        val record = recovery(old, policy, dormant, unknown)
        assertFalse(ActiveSetupResolver.resolve(old, ActiveSetupSelections(old.id), profileGraph = graph).canStart)
        val repaired = planLegacyMigrationRepair(old, record, graph, setOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT))
        assertTrue(ActiveSetupResolver.resolve(repaired.settingsSet, ActiveSetupSelections(set.id), profileGraph = graph).canStart)
        assertTrue(repaired.recovery.blockingIssues(repaired.settingsSet, ActiveSetupSelections(set.id)).isEmpty())
        assertEquals(policy.reason, repaired.recovery.issues.first().reason)
        assertNotNull(repaired.recovery.issues.first().resolution)
        assertEquals(listOf(dormant, unknown), repaired.recovery.issues.drop(1))
        assertEquals(repaired.recovery, LegacyMigrationRecovery.fromJson(repaired.recovery.toJson()))
    }

    @Test fun `raw replacement is not acknowledgement and explicit repair validates whole replacement graph`() {
        val old = set.copy(commandProfileRef = ProfileReference("missing", "Missing"))
        val record = recovery(old, issue(ActiveSetupOptionKey.RECEIVER_COMMAND, MigrationReviewReason.MISSING_PROFILE, id = "missing"))
        assertEquals(1, record.blockingIssues(set, ActiveSetupSelections(set.id)).size)
        assertFailsWith<IllegalArgumentException> {
            planLegacyMigrationRepair(set, record, graph.copy(commandProfiles = emptyList()), setOf(ActiveSetupOptionKey.RECEIVER_COMMAND))
        }
        val repaired = planLegacyMigrationRepair(set, record, graph, setOf(ActiveSetupOptionKey.RECEIVER_COMMAND))
        assertTrue(repaired.recovery.blockingIssues(set, ActiveSetupSelections(set.id)).isEmpty())
    }

    @Test fun `lineage repair requires exact selected source caster and restriction`() {
        val record = recovery(set, issue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, MigrationReviewReason.CASTER_LINEAGE, "source"))
        assertFailsWith<IllegalArgumentException> { planLegacyMigrationRepair(set.copy(
            ntripCasterRestrictionRef = ProfileReference("other", "Other")), record, graph, setOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)) }
        assertFailsWith<IllegalArgumentException> { planLegacyMigrationRepair(set, record,
            graph.copy(ntripCasterProfiles = emptyList()), setOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)) }
        assertNotNull(planLegacyMigrationRepair(set.copy(ntripCasterRestrictionRef = ProfileReference(caster.id, caster.name)),
            record, graph, setOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)).recovery.issues.single().resolution)
    }

    @Test fun `credential repair uses exact bound decryption not canonical aliases`() {
        val record = recovery(set, issue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, MigrationReviewReason.MISSING_CREDENTIAL, "secretId", source.id))
        val reads = mutableListOf<String>()
        assertFailsWith<IllegalArgumentException> { planLegacyMigrationRepair(set, record, graph,
            setOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT), credentialLookup = { reads += it; LegacyCredential.Missing }) }
        assertEquals(listOf("exact-owner"), reads)
        val repaired = planLegacyMigrationRepair(set, record, graph, setOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT),
            credentialLookup = { LegacyCredential.Available("fixture-only") })
        assertNotNull(repaired.recovery.issues.single().resolution)
        assertFalse(repaired.recovery.toJson().toString().contains("fixture-only"))
    }

    @Test fun `SAF repair requires picker granted write authority`() {
        val saf = storage.copy(kind = "SAF_TREE", treeUri = "content://documents/tree/selected")
        val record = recovery(set, issue(ActiveSetupOptionKey.STORAGE, MigrationReviewReason.SAF_RESELECTION, "treeUri", storage.id))
        assertFailsWith<IllegalArgumentException> { planLegacyMigrationRepair(set, record,
            graph.copy(storageProfiles = listOf(saf)), setOf(ActiveSetupOptionKey.STORAGE)) }
        assertNotNull(planLegacyMigrationRepair(set, record, graph.copy(storageProfiles = listOf(saf)),
            setOf(ActiveSetupOptionKey.STORAGE), persistedSafTreeUrisWithWriteAccess = setOf(saf.treeUri!!)).recovery.issues.single().resolution)
    }

    @Test fun `coordinate repair validates MODE BASE agreement`() {
        val base = modelTestBaseCoordinate()
        val fixed = set.copy(workflowId = "fixed-base", basePositionProfileRef = ProfileReference(base.id, base.name))
        val record = recovery(fixed, issue(ActiveSetupOptionKey.BASE_COORDINATE, MigrationReviewReason.BASE_COORDINATE))
        assertFailsWith<IllegalArgumentException> { planLegacyMigrationRepair(fixed, record,
            graph.copy(baseCoordinates = listOf(base)), setOf(ActiveSetupOptionKey.BASE_COORDINATE)) }
        val repaired = planLegacyMigrationRepair(fixed, record, graph.copy(baseCoordinates = listOf(base),
            commandProfiles = listOf(command.copy(runtimeScript = base.toUm980FixedBaseModeCommand()))), setOf(ActiveSetupOptionKey.BASE_COORDINATE))
        assertNotNull(repaired.recovery.issues.single().resolution)
    }
}
