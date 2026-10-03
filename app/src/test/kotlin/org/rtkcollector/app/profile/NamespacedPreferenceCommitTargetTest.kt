package org.rtkcollector.app.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class NamespacedPreferenceCommitTargetTest {
    @Test
    fun `journal restores coordinate and profile files after partial publication`() {
        val profiles = FakeTarget(mapOf("settingsSets" to "old-set"), failAt = 3)
        val coordinates = FakeTarget(mapOf("acceptedBaseCoordinates" to "old-coordinate"))
        val target = NamespacedPreferenceCommitTarget(profiles, coordinates)

        assertFailsWith<GraphPublicationException> {
            ProfileGraphJournal(target).publish(mapOf(
                "settingsSets" to "new-set",
                "coordinate.acceptedBaseCoordinates" to "new-coordinate",
            )) {}
        }

        assertEquals("old-set", profiles.values["settingsSets"])
        assertEquals("old-coordinate", coordinates.values["acceptedBaseCoordinates"])
    }

    @Test
    fun `coordinate commit failure restores both preference files`() {
        val profiles = FakeTarget(mapOf("settingsSets" to "old-set"))
        val coordinates = FakeTarget(mapOf("acceptedBaseCoordinates" to "old-coordinate"), failAt = 1)
        val target = NamespacedPreferenceCommitTarget(profiles, coordinates)

        assertFailsWith<GraphPublicationException> {
            ProfileGraphJournal(target).publish(mapOf(
                "settingsSets" to "new-set",
                "coordinate.acceptedBaseCoordinates" to "new-coordinate",
            )) {}
        }

        assertEquals("old-set", profiles.values["settingsSets"])
        assertEquals("old-coordinate", coordinates.values["acceptedBaseCoordinates"])
        assertFalse(profiles.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))
    }

    @Test
    fun `restart recovers both files after interruption between coordinate commit and finalize`() {
        val profiles = FakeTarget(mapOf("settingsSets" to "old-set"))
        val coordinates = FakeTarget(mapOf("acceptedBaseCoordinates" to "old-coordinate"), crashAt = 1)
        assertFailsWith<SimulatedProcessDeath> {
            ProfileGraphJournal(NamespacedPreferenceCommitTarget(profiles, coordinates)).publish(mapOf(
                "settingsSets" to "new-set",
                "coordinate.acceptedBaseCoordinates" to "new-coordinate",
            )) {}
        }

        val restartedProfiles = FakeTarget(profiles.durableValues())
        val restartedCoordinates = FakeTarget(coordinates.durableValues())
        ProfileGraphJournal(NamespacedPreferenceCommitTarget(restartedProfiles, restartedCoordinates)).requireRecovered()
        assertEquals("old-set", restartedProfiles.values["settingsSets"])
        assertEquals("old-coordinate", restartedCoordinates.values["acceptedBaseCoordinates"])
        assertFalse(restartedProfiles.values.containsKey(ProfileGraphJournal.JOURNAL_KEY))
    }

    private class SimulatedProcessDeath : Error()

    private class FakeTarget(
        initial: Map<String, String>,
        private val failAt: Int = -1,
        private val crashAt: Int = -1,
    ) : PreferenceCommitTarget {
        val values = initial.toMutableMap()
        private var durable = initial.toMap()
        private var commits = 0
        fun durableValues(): Map<String, String> = durable
        override fun allValues(): Map<String, *> = values.toMap()
        override fun commit(changes: Map<String, StoredPreferenceValue?>): Boolean {
            commits++
            changes.forEach { (key, value) ->
                if (value == null) values.remove(key)
                else values[key] = (value as StoredPreferenceValue.StringValue).value
            }
            if (commits == failAt) return false
            durable = values.toMap()
            if (commits == crashAt) throw SimulatedProcessDeath()
            return true
        }
    }
}
