package org.rtkcollector.app.profile

import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileGraphJournalTest {
    @Test
    fun `ordinary restore publishes disarmed graph before explicit passwords and enables only after success`() {
        val backup = credentialBackup()
        val target = FakeTarget(mapOf("owner" to "local-binding"))
        val journal = ProfileGraphJournal(target)
        val secrets = mutableMapOf("local-binding" to "local-password")
        val publications = mutableListOf<String>()
        val result = restoreProfilesThenCredentials(backup, { ids ->
            assertTrue(ids.none(secrets::containsKey))
        }, { graph ->
            val binding = graph.ntripCasterProfiles.single().secretId
            assertTrue(graph.plaintextPasswordsBySecretId.isEmpty())
            journal.publish(mapOf("owner" to binding)) {}
            publications += binding
        }, { passwords ->
            assertEquals(1, publications.size)
            val binding = target.values.getValue("owner") as String
            assertFalse(binding in passwords)
            assertFalse(binding in secrets)
            assertFalse(ProfileGraphJournal.JOURNAL_KEY in target.values)
            assertEquals(mapOf("import-binding" to "explicit-password"), passwords)
            secrets.putAll(passwords)
        }, SettingsImportIdFactory { "$it-disarmed" })

        assertEquals(SecretStoreImportOutcome.STORED, result)
        assertEquals(2, publications.size)
        assertEquals("import-binding", target.values["owner"])
        assertEquals("explicit-password", secrets[target.values["owner"]])
        assertEquals("local-password", secrets["local-binding"])
    }

    @Test
    fun `confirmed and uncertain secret failures retain restored graph with unreachable credentials`() {
        listOf(PreferenceRollbackStatus.RESTORED, PreferenceRollbackStatus.FAILED).forEach { rollback ->
            val target = FakeTarget(mapOf("owner" to "local-binding"))
            val secrets = mutableMapOf("local-binding" to "local-password")
            var publications = 0
            val result = restoreProfilesThenCredentials(credentialBackup(), { ids ->
                assertTrue(ids.none(secrets::containsKey))
            }, { graph ->
                publications++
                ProfileGraphJournal(target).publish(mapOf("owner" to graph.ntripCasterProfiles.single().secretId)) {}
            }, { passwords ->
                assertEquals(1, publications)
                if (rollback == PreferenceRollbackStatus.FAILED) secrets.putAll(passwords)
                throw PreferenceCommitException("private secret details", rollback)
            }, SettingsImportIdFactory { "$it-disarmed" })

            assertEquals(if (rollback == PreferenceRollbackStatus.RESTORED)
                SecretStoreImportOutcome.PRIMARY_COMMIT_FAILED_ROLLBACK_SUCCEEDED
                else SecretStoreImportOutcome.PRIMARY_AND_ROLLBACK_FAILED, result)
            assertEquals(1, publications)
            assertFalse(target.values["owner"] == "local-binding")
            assertFalse(target.values["owner"] in secrets)
            val restarted = FakeTarget(target.durableValues())
            ProfileGraphJournal(restarted).requireRecovered()
            assertFalse(restarted.values["owner"] in secrets)
            assertEquals("local-password", secrets["local-binding"])
            assertTrue(result.userMessage.orEmpty().contains("Profiles restored"))
            assertFalse(result.userMessage.orEmpty().contains("private secret details"))
        }
    }

    @Test
    fun `interruption after secret write leaves durable restored profiles disarmed`() {
        val target = FakeTarget(mapOf("owner" to "local-binding"))
        val secrets = mutableMapOf("local-binding" to "local-password")
        assertFailsWith<SimulatedProcessDeath> {
            restoreProfilesThenCredentials(credentialBackup(), {}, { graph ->
                ProfileGraphJournal(target).publish(mapOf("owner" to graph.ntripCasterProfiles.single().secretId)) {}
            }, { passwords ->
                secrets.putAll(passwords)
                throw SimulatedProcessDeath()
            }, SettingsImportIdFactory { "$it-disarmed" })
        }
        val restarted = FakeTarget(target.durableValues())
        ProfileGraphJournal(restarted).requireRecovered()
        assertFalse(restarted.values["owner"] == "local-binding")
        assertFalse(restarted.values["owner"] in secrets)
        assertEquals("explicit-password", secrets["import-binding"])
    }

    @Test
    fun `legacy migration still stages secrets before publishing reachable references`() {
        val target = FakeTarget(mapOf("owner" to "old"))
        var staged = false
        ProfileGraphJournal(target).publish(mapOf("owner" to "new")) {
            assertEquals("old", target.values["owner"])
            assertTrue(ProfileGraphJournal.JOURNAL_KEY in target.values)
            staged = true
        }
        assertTrue(staged)
        assertEquals("new", target.values["owner"])
    }

    private fun credentialBackup() = SettingsBackupFile.fromProfiles(
        commandProfiles = emptyList(), usbBaudProfiles = emptyList(),
        ntripCasterProfiles = listOf(NtripCasterProfile(id = "imported", name = "Imported", secretId = "import-binding")),
        ntripCasterUploadProfiles = emptyList(), ntripMountpointProfiles = emptyList(),
        recordingPolicyProfiles = emptyList(), storageProfiles = emptyList(), settingsSets = emptyList(),
        selectedSettingsSetId = null, selectedWorkflowId = null, lastActiveNtripMountpointProfileId = null,
        passwordsBySecretId = mapOf("import-binding" to "explicit-password"),
        options = SettingsSetExportOptions(includePlaintextPasswords = true),
    )

    @Test
    fun `historically omitted upload family keeps local secrets and derived import across both publications`() {
        val local = NtripCasterUploadProfile(id = "local-upload", name = "Local", secretId = "local-upload-secret")
        val derived = local.copy(id = "derived-upload", name = "Imported", secretId = "derived-secret")
        val backup = credentialBackup().copy(ntripCasterUploadProfiles = listOf(derived),
            includedProfileFamilies = SettingsBackupProfileFamily.entries.toSet() - SettingsBackupProfileFamily.NTRIP_CASTER_UPLOAD,
            plaintextPasswordsBySecretId = mapOf("derived-secret" to "explicit-upload-password"))
        val publications = mutableListOf<SettingsBackupFile>()
        val secrets = mutableMapOf("local-upload-secret" to "local-upload-password")
        restoreProfilesThenCredentials(backup, { ids -> assertTrue(ids.none(secrets::containsKey)) }, { graph ->
            publications += graph.withRetainedImportUploads(listOf(local))
        }, { secrets.putAll(it) }, SettingsImportIdFactory { "$it-disarmed" })
        assertEquals(2, publications.size)
        publications.forEach { graph ->
            assertEquals(listOf("local-upload", "derived-upload"), graph.ntripCasterUploadProfiles.map { it.id })
            assertEquals(local, graph.ntripCasterUploadProfiles.first())
        }
        assertFalse(publications.first().ntripCasterUploadProfiles.last().secretId in secrets)
        assertEquals("explicit-upload-password", secrets[publications.last().ntripCasterUploadProfiles.last().secretId])
        assertEquals("local-upload-password", secrets[local.secretId])
    }

    @Test
    fun `empty password import publishes once without any credential write`() {
        var publications = 0
        val result = restoreProfilesThenCredentials(credentialBackup().copy(plaintextPasswordsBySecretId = emptyMap()),
            {}, { publications++ }, { error("No explicit passwords to import") })
        assertEquals(SecretStoreImportOutcome.STORED, result)
        assertEquals(1, publications)
    }

    @Test
    fun `allocated binding rejects restore before graph publication or secret writes`() {
        assertFailsWith<IllegalArgumentException> {
            restoreProfilesThenCredentials(credentialBackup(), { ids ->
                require("import-binding" !in ids) { "Allocated binding" }
            }, { error("Must not publish") }, { error("Must not overwrite") })
        }
    }

    @Test
    fun `failed initial graph restore never attempts password persistence`() {
        val target = FakeTarget(mapOf("owner" to "local-binding"), listOf(true, true, false, true))
        assertFailsWith<GraphPublicationException> {
            restoreProfilesThenCredentials(credentialBackup(), {}, { graph ->
                ProfileGraphJournal(target).publish(mapOf("owner" to graph.ntripCasterProfiles.single().secretId)) {}
            }, { error("Must not write passwords after graph failure") })
        }
        assertEquals("local-binding", target.values["owner"])
    }

    @Test
    fun `activation publication failure restores the already imported disarmed graph`() {
        val target = FakeTarget(mapOf("owner" to "local-binding"),
            listOf(true, true, true, true, true, true, false, true))
        val secrets = mutableMapOf("local-binding" to "local-password")
        val result = restoreProfilesThenCredentials(credentialBackup(), {}, { graph ->
            ProfileGraphJournal(target).publish(mapOf("owner" to graph.ntripCasterProfiles.single().secretId)) {}
        }, { secrets.putAll(it) }, SettingsImportIdFactory { "$it-disarmed" })
        assertEquals(SecretStoreImportOutcome.ACTIVATION_FAILED, result)
        assertFalse(target.values["owner"] == "local-binding")
        assertFalse(target.values["owner"] in secrets)
        assertEquals("explicit-password", secrets["import-binding"])
        assertEquals("local-password", secrets["local-binding"])
    }

    @Test
    fun `uncertain activation recovery blocks graph reads and reports partial restore truthfully`() {
        val target = FakeTarget(mapOf("owner" to "local-binding"),
            listOf(true, true, true, true, true, true, false, false, false, true))
        val failure = assertFailsWith<GraphRecoveryException> {
            restoreProfilesThenCredentials(credentialBackup(), {}, { graph ->
                ProfileGraphJournal(target).publish(mapOf("owner" to graph.ntripCasterProfiles.single().secretId)) {}
            }, {}, SettingsImportIdFactory { "$it-disarmed" })
        }
        assertTrue(failure.message.orEmpty().contains("Profiles were restored"))
        assertTrue(failure.message.orEmpty().contains("blocked"))
        assertFailsWith<GraphRecoveryException> { ProfileGraphJournal(target).requireRecovered() }
        ProfileGraphJournal(target).requireRecovered()
        assertEquals("ntrip-import-disarmed-disarmed", target.values["owner"])
    }

    @Test
    fun `activation interruption recovers disarmed graph or fully committed credential graph`() {
        (5..8).forEach { boundary ->
            val target = FakeTarget(mapOf("owner" to "local-binding"), crashAfterSuccessCommit = boundary)
            val secrets = mutableMapOf("local-binding" to "local-password")
            assertFailsWith<SimulatedProcessDeath> {
                restoreProfilesThenCredentials(credentialBackup(), {}, { graph ->
                    ProfileGraphJournal(target).publish(mapOf("owner" to graph.ntripCasterProfiles.single().secretId)) {}
                }, { secrets.putAll(it) }, SettingsImportIdFactory { "$it-disarmed" })
            }
            val restarted = FakeTarget(target.durableValues())
            ProfileGraphJournal(restarted).requireRecovered()
            assertEquals(if (boundary == 8) "import-binding" else "ntrip-import-disarmed-disarmed", restarted.values["owner"])
            assertEquals("local-password", secrets["local-binding"])
        }
    }

    @Test
    fun `batch commit failure and uncertain rollback cannot expose attempted passwords through restored bindings`() {
        listOf(true, false).forEach { rollbackSucceeds ->
            val secretValues = mutableMapOf("local-binding" to "local-password")
            var commits = 0
            val secretTarget = object : PreferenceCommitTarget {
                override fun allValues(): Map<String, *> = secretValues.toMap()
                override fun commit(changes: Map<String, StoredPreferenceValue?>): Boolean {
                    commits++
                    // Model primary commit(false) becoming visible, and a failed rollback retaining it.
                    if (commits == 1 || rollbackSucceeds) changes.forEach { (id, value) ->
                        if (value == null) secretValues.remove(id)
                        else secretValues[id] = (value as StoredPreferenceValue.StringValue).value
                    }
                    return commits == 2 && rollbackSucceeds
                }
            }
            val graph = FakeTarget(mapOf("owner" to "local-binding"))
            val result = restoreProfilesThenCredentials(credentialBackup(), { ids ->
                assertTrue(ids.none(secretValues::containsKey))
            }, { backup ->
                ProfileGraphJournal(graph).publish(mapOf("owner" to backup.ntripCasterProfiles.single().secretId)) {}
            }, { passwords ->
                commitStringChangesWithRollback(secretTarget, passwords, "Private details must not be shown")
            }, SettingsImportIdFactory { "$it-disarmed" })
            assertEquals(if (rollbackSucceeds) SecretStoreImportOutcome.PRIMARY_COMMIT_FAILED_ROLLBACK_SUCCEEDED
                else SecretStoreImportOutcome.PRIMARY_AND_ROLLBACK_FAILED, result)
            assertEquals(2, commits)
            assertEquals(!rollbackSucceeds, "import-binding" in secretValues)
            assertFalse(graph.values["owner"] in secretValues)
            assertEquals("local-password", secretValues["local-binding"])
        }
    }

    @Test
    fun `interrupted repair restores blocking graph or complete repaired graph with its history`() {
        val oldSet = RecordingSettingsSet.builtInRoverNtrip().copy(id = "editable", isProtected = false,
            ntripCasterPolicyNeedsReview = true)
        val issue = MigrationReviewIssue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, "ntripCasterPolicy",
            LegacyFieldDisposition.UNCERTAIN, MigrationReviewReason.POLICY_CONFLICT)
        val oldRecovery = LegacyMigrationRecovery(oldSet, setOf(MigrationReviewReason.POLICY_CONFLICT), listOf(issue))
        val newSet = oldSet.copy(ntripCasterPolicyNeedsReview = false)
        val newRecovery = oldRecovery.copy(reviewReasons = emptySet(), issues = listOf(
            issue.copy(resolution = MigrationIssueResolution.VALIDATED_OPERATOR_REPAIR)))
        val old = mapOf("settingsSets" to oldSet.toJson().toString(), "migrationRecovery" to oldRecovery.toJson().toString())
        val new = mapOf("settingsSets" to newSet.toJson().toString(), "migrationRecovery" to newRecovery.toJson().toString())
        (1..4).forEach { boundary ->
            val target = FakeTarget(old, crashAfterSuccessCommit = boundary)
            assertFailsWith<SimulatedProcessDeath> { ProfileGraphJournal(target).publish(new) {} }
            val restarted = FakeTarget(target.durableValues())
            ProfileGraphJournal(restarted).requireRecovered()
            val restoredSet = RecordingSettingsSet.fromJson(org.json.JSONObject(restarted.values.getValue("settingsSets") as String))
            val restoredRecovery = LegacyMigrationRecovery.fromJson(org.json.JSONObject(restarted.values.getValue("migrationRecovery") as String))
            assertEquals(if (boundary == 4) newSet else oldSet, restoredSet)
            assertEquals(if (boundary == 4) newRecovery else oldRecovery, restoredRecovery)
            assertEquals(boundary != 4, restoredRecovery.blockingIssues(restoredSet, ActiveSetupSelections(restoredSet.id)).isNotEmpty())
        }
    }
    @Test
    fun `unreadable preference storage produces actionable recovery error`() {
        val target = object : PreferenceCommitTarget {
            override fun allValues(): Map<String, *> = error("private storage failure details")
            override fun commit(changes: Map<String, StoredPreferenceValue?>): Boolean = false
        }
        val failure = assertFailsWith<GraphRecoveryException> { ProfileGraphJournal(target).requireRecovered() }
        assertFalse(failure.message.orEmpty().contains("private storage"))
    }

    @Test
    fun `new adapter with shared authority recovers after journal deletion and failed rollback`() {
        val target = FakeTarget(mapOf("settingsSets" to "old"), listOf(true, true, true, false, false, true))
        val authority = Any()
        fun adapter() = object : PreferenceCommitTarget {
            override fun allValues(): Map<String, *> = target.allValues()
            override fun commit(changes: Map<String, StoredPreferenceValue?>): Boolean = target.commit(changes)
        }
        assertFailsWith<GraphRecoveryException> {
            ProfileGraphJournal(adapter(), authority).publish(mapOf("settingsSets" to "new")) {}
        }
        assertFalse(target.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))
        ProfileGraphJournal(adapter(), authority).requireRecovered()
        assertEquals("old", target.values["settingsSets"])
    }

    @Test
    fun `secret stage fails without publishing references`() {
        val target = FakeTarget(mapOf("settingsSets" to "old"))
        val journal = ProfileGraphJournal(target)

        assertFailsWith<IllegalStateException> {
            journal.publish(mapOf("settingsSets" to "new")) { error("Secret storage unavailable") }
        }

        assertEquals("old", target.values["settingsSets"])
        assertFalse(target.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))
    }

    @Test
    fun `publication failure restores old graph despite process visible changes`() {
        val target = FakeTarget(
            mapOf("settingsSets" to "old", "selectedSettingsSetId" to "old-id"),
            outcomes = listOf(true, true, false, true),
        )
        val journal = ProfileGraphJournal(target)

        assertFailsWith<GraphPublicationException> {
            journal.publish(mapOf("settingsSets" to "new", "selectedSettingsSetId" to "new-id")) {}
        }

        assertEquals("old", target.values["settingsSets"])
        assertEquals("old-id", target.values["selectedSettingsSetId"])
        assertFalse(target.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))
    }

    @Test
    fun `failed rollback blocks new graph until a later recovery`() {
        val target = FakeTarget(mapOf("settingsSets" to "old"), listOf(true, true, false, false, false, true))
        val journal = ProfileGraphJournal(target)

        assertFailsWith<GraphRecoveryException> {
            journal.publish(mapOf("settingsSets" to "new")) {}
        }
        assertFailsWith<GraphRecoveryException> { journal.requireRecovered() }
        journal.requireRecovered()
        assertEquals("old", target.values["settingsSets"])
        assertFalse(target.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))
    }

    @Test
    fun `restart after graph commit but before journal clear restores prior graph`() {
        val target = FakeTarget(mapOf("settingsSets" to "old"), listOf(true, true, true, false, false, true))
        assertFailsWith<GraphRecoveryException> {
            ProfileGraphJournal(target).publish(mapOf("settingsSets" to "new")) {}
        }
        assertFalse(target.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))

        ProfileGraphJournal(target).requireRecovered()

        assertEquals("old", target.values["settingsSets"])
        assertFalse(target.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))
    }

    @Test
    fun `process death recovers durable journal after failed finalize and rollback`() {
        val target = FakeTarget(mapOf("settingsSets" to "old"), listOf(true, true, true, false, false))
        assertFailsWith<GraphRecoveryException> {
            ProfileGraphJournal(target).publish(mapOf("settingsSets" to "new")) {}
        }
        assertFalse(target.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))

        val restarted = FakeTarget(target.durableValues())
        ProfileGraphJournal(restarted).requireRecovered()

        assertEquals("old", restarted.values["settingsSets"])
        assertFalse(restarted.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))
    }

    @Test
    fun `failed durable preparation never stages a secret`() {
        val target = FakeTarget(mapOf("settingsSets" to "old"), listOf(false, true))
        var staged = false

        assertFailsWith<GraphPublicationException> {
            ProfileGraphJournal(target).publish(mapOf("settingsSets" to "new")) { staged = true }
        }

        assertFalse(staged)
        assertEquals("old", target.values["settingsSets"])
    }

    @Test
    fun `unreadable recovery record blocks access instead of using new graph`() {
        val target = FakeTarget(
            mapOf(
                "settingsSets" to "new",
                ProfileGraphJournal.JOURNAL_KEY to "broken-recovery-record",
            ),
        )

        assertFailsWith<GraphRecoveryException> { ProfileGraphJournal(target).requireRecovered() }
        assertEquals("new", target.values["settingsSets"])
        assertTrue(target.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))
    }

    @Test
    fun `process death at each journal boundary exposes only old or committed new graph`() {
        (1..4).forEach { crashBoundary ->
            val target = FakeTarget(
                mapOf("settingsSets" to "old", "selectedSettingsSetId" to "old-id"),
                crashAfterSuccessCommit = crashBoundary,
            )
            assertFailsWith<SimulatedProcessDeath> {
                ProfileGraphJournal(target).publish(
                    mapOf("settingsSets" to "new", "selectedSettingsSetId" to "new-id"),
                ) {}
            }

            val restarted = FakeTarget(target.durableValues())
            ProfileGraphJournal(restarted).requireRecovered()

            val expected = if (crashBoundary == 4) "new" else "old"
            val expectedId = if (crashBoundary == 4) "new-id" else "old-id"
            assertEquals(expected, restarted.values["settingsSets"])
            assertEquals(expectedId, restarted.values["selectedSettingsSetId"])
        }
    }

    private class SimulatedProcessDeath : Error()

    private class FakeTarget(
        initial: Map<String, Any>,
        outcomes: List<Boolean> = emptyList(),
        private val crashAfterSuccessCommit: Int? = null,
    ) : PreferenceCommitTarget {
        val values = initial.toMutableMap()
        private var durable = initial.toMap()
        private val results = ArrayDeque(outcomes)
        private var successfulCommits = 0

        fun durableValues(): Map<String, Any> = durable

        override fun allValues(): Map<String, *> = values.toMap()

        override fun commit(changes: Map<String, StoredPreferenceValue?>): Boolean {
            changes.forEach { (key, value) ->
                when (value) {
                    null -> values.remove(key)
                    is StoredPreferenceValue.StringValue -> values[key] = value.value
                    else -> error("Unexpected non-string graph value")
                }
            }
            val succeeded = if (results.isEmpty()) true else results.removeFirst()
            if (succeeded) {
                durable = values.toMap()
                successfulCommits++
                if (successfulCommits == crashAfterSuccessCommit) throw SimulatedProcessDeath()
            }
            return succeeded
        }
    }
}
