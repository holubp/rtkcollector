package org.rtkcollector.app.profile

import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileGraphJournalTest {
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
