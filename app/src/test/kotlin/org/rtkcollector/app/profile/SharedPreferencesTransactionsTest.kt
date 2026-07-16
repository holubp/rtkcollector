package org.rtkcollector.app.profile

import java.util.ArrayDeque
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SharedPreferencesTransactionsTest {
    @Test
    fun `failed primary commit restores exact prior snapshot`() {
        val original = mapOf<String, Any>(
            "existing" to "old-value",
            "typed-int" to 7,
            "typed-set" to setOf("a", "b"),
            "untouched" to true,
        )
        val target = MutatingCommitTarget(original, ArrayDeque(listOf(false, true)))

        val failure = assertThrows(PreferenceCommitException::class.java) {
            commitStringChangesWithRollback(
                target = target,
                changes = mapOf(
                    "existing" to "new-value",
                    "typed-int" to "replacement",
                    "typed-set" to null,
                    "new-key" to "plaintext-secret-must-not-leak",
                ),
                failureMessage = "Primary preference write failed.",
            )
        }

        assertEquals(original, target.values)
        assertEquals(2, target.commitCount)
        assertEquals(PreferenceRollbackStatus.RESTORED, failure.rollbackStatus)
        assertTrue(failure.message.orEmpty().contains("restored synchronously"))
        assertFalse(failure.message.orEmpty().contains("plaintext-secret-must-not-leak"))
    }

    @Test
    fun `rollback commit failure reports compound failure without values`() {
        val original = mapOf<String, Any>("existing" to "old-value")
        val target = MutatingCommitTarget(original, ArrayDeque(listOf(false, false)))

        val failure = assertThrows(PreferenceCommitException::class.java) {
            commitStringChangesWithRollback(
                target = target,
                changes = mapOf("existing" to "plaintext-secret-must-not-leak", "new-key" to "new-value"),
                failureMessage = "Primary preference write failed.",
            )
        }

        assertEquals(original, target.values)
        assertEquals(2, target.commitCount)
        assertEquals(PreferenceRollbackStatus.FAILED, failure.rollbackStatus)
        assertTrue(failure.message.orEmpty().contains("Rollback also failed"))
        assertFalse(failure.message.orEmpty().contains("plaintext-secret-must-not-leak"))
    }

    private class MutatingCommitTarget(
        initialValues: Map<String, Any>,
        private val commitResults: ArrayDeque<Boolean>,
    ) : PreferenceCommitTarget {
        val values = initialValues.toMutableMap()
        var commitCount: Int = 0
            private set

        override fun allValues(): Map<String, *> = values.toMap()

        override fun commit(changes: Map<String, StoredPreferenceValue?>): Boolean {
            commitCount++
            changes.forEach { (key, value) ->
                if (value == null) {
                    values.remove(key)
                } else {
                    values[key] = value.rawValue()
                }
            }
            return commitResults.removeFirst()
        }
    }

    private fun StoredPreferenceValue.rawValue(): Any =
        when (this) {
            is StoredPreferenceValue.StringValue -> value
            is StoredPreferenceValue.StringSetValue -> value.toSet()
            is StoredPreferenceValue.IntValue -> value
            is StoredPreferenceValue.LongValue -> value
            is StoredPreferenceValue.FloatValue -> value
            is StoredPreferenceValue.BooleanValue -> value
        }
}
