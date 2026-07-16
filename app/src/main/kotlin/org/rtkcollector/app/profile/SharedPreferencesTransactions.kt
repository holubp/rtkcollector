package org.rtkcollector.app.profile

import android.content.SharedPreferences

internal sealed interface StoredPreferenceValue {
    data class StringValue(val value: String) : StoredPreferenceValue
    data class StringSetValue(val value: Set<String>) : StoredPreferenceValue
    data class IntValue(val value: Int) : StoredPreferenceValue
    data class LongValue(val value: Long) : StoredPreferenceValue
    data class FloatValue(val value: Float) : StoredPreferenceValue
    data class BooleanValue(val value: Boolean) : StoredPreferenceValue
}

internal interface PreferenceCommitTarget {
    fun allValues(): Map<String, *>

    /** Applies changes before returning, matching Android's process-visible commit(false) behavior. */
    fun commit(changes: Map<String, StoredPreferenceValue?>): Boolean
}

internal enum class PreferenceRollbackStatus {
    RESTORED,
    FAILED,
}

internal class PreferenceCommitException(
    message: String,
    val rollbackStatus: PreferenceRollbackStatus,
) : IllegalStateException(message)

internal fun SharedPreferences.commitStringChangesWithRollback(
    changes: Map<String, String?>,
    failureMessage: String,
) {
    commitStringChangesWithRollback(
        target = SharedPreferencesCommitTarget(this),
        changes = changes,
        failureMessage = failureMessage,
    )
}

internal fun commitStringChangesWithRollback(
    target: PreferenceCommitTarget,
    changes: Map<String, String?>,
    failureMessage: String,
) {
    if (changes.isEmpty()) return
    val priorValues = target.allValues()
    val rollbackChanges = changes.keys.associateWith { key ->
        if (priorValues.containsKey(key)) {
            storedPreferenceValue(requireNotNull(priorValues[key]))
        } else {
            null
        }
    }
    val primaryChanges = changes.mapValues { (_, value) ->
        value?.let { StoredPreferenceValue.StringValue(it) }
    }
    if (commitSucceeded(target, primaryChanges)) return

    if (!commitSucceeded(target, rollbackChanges)) {
        throw PreferenceCommitException(
            "$failureMessage Rollback also failed; persisted preference state may be inconsistent.",
            PreferenceRollbackStatus.FAILED,
        )
    }
    throw PreferenceCommitException(
        "$failureMessage Previous preference values were restored synchronously.",
        PreferenceRollbackStatus.RESTORED,
    )
}

private fun commitSucceeded(
    target: PreferenceCommitTarget,
    changes: Map<String, StoredPreferenceValue?>,
): Boolean =
    try {
        target.commit(changes)
    } catch (_: Exception) {
        false
    }

private fun storedPreferenceValue(value: Any): StoredPreferenceValue =
    when (value) {
        is String -> StoredPreferenceValue.StringValue(value)
        is Set<*> -> StoredPreferenceValue.StringSetValue(
            value.map { item ->
                require(item is String) { "SharedPreferences contained an unsupported string-set value." }
                item
            }.toSet(),
        )
        is Int -> StoredPreferenceValue.IntValue(value)
        is Long -> StoredPreferenceValue.LongValue(value)
        is Float -> StoredPreferenceValue.FloatValue(value)
        is Boolean -> StoredPreferenceValue.BooleanValue(value)
        else -> error("SharedPreferences contained an unsupported value type.")
    }

private class SharedPreferencesCommitTarget(
    private val preferences: SharedPreferences,
) : PreferenceCommitTarget {
    override fun allValues(): Map<String, *> = preferences.all

    override fun commit(changes: Map<String, StoredPreferenceValue?>): Boolean {
        val editor = preferences.edit()
        changes.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is StoredPreferenceValue.StringValue -> editor.putString(key, value.value)
                is StoredPreferenceValue.StringSetValue -> editor.putStringSet(key, value.value)
                is StoredPreferenceValue.IntValue -> editor.putInt(key, value.value)
                is StoredPreferenceValue.LongValue -> editor.putLong(key, value.value)
                is StoredPreferenceValue.FloatValue -> editor.putFloat(key, value.value)
                is StoredPreferenceValue.BooleanValue -> editor.putBoolean(key, value.value)
            }
        }
        return editor.commit()
    }
}
