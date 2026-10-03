package org.rtkcollector.app.profile

import org.json.JSONObject
import java.util.IdentityHashMap

internal class GraphPublicationException(message: String) : IllegalStateException(message)

internal class GraphRecoveryException(message: String) : IllegalStateException(message)

/** A graph commit spans one preference file and a separately staged secret store. */
internal class ProfileGraphJournal(
    private val target: PreferenceCommitTarget,
    private val authorityKey: Any = target,
) {

    fun requireRecovered(): Boolean = synchronized(LOCK) {
        val held = heldPrior[authorityKey]
        val prior = held ?: run {
            val raw = try {
                target.allValues()[JOURNAL_KEY]
            } catch (_: Exception) {
                throw GraphRecoveryException("Settings recovery storage is unreadable.")
            } ?: return@synchronized false
            try {
                val record = JSONObject(raw as String)
                require(record.getInt("version") == 1) { "Unsupported settings recovery record." }
                require(record.getString("phase") in setOf("PREPARED", "SECRETS_STAGED")) {
                    "Unknown settings recovery phase."
                }
                val snapshot = record.getJSONObject("prior")
                val keys = snapshot.keys()
                linkedMapOf<String, StoredPreferenceValue?>().apply {
                    while (keys.hasNext()) {
                        val key = keys.next()
                        require(key != JOURNAL_KEY) { "Invalid settings recovery record." }
                        val value = snapshot.get(key)
                        require(value == JSONObject.NULL || value is String) { "Invalid settings recovery value." }
                        put(key, if (value == JSONObject.NULL) null else StoredPreferenceValue.StringValue(value as String))
                    }
                    require(isNotEmpty()) { "Empty settings recovery record." }
                }
            } catch (_: Exception) {
                throw GraphRecoveryException("Settings recovery record is unreadable.")
            }
        }
        val restore = prior + (JOURNAL_KEY to null)
        if (!commit(restore)) {
            heldPrior[authorityKey] = prior
            throw GraphRecoveryException("Unable to durably recover previous settings.")
        }
        heldPrior.remove(authorityKey)
        true
    }

    fun publish(changes: Map<String, String?>, stageSecrets: () -> Unit) = synchronized(LOCK) {
        require(changes.isNotEmpty()) { "A graph publication must change settings." }
        require(JOURNAL_KEY !in changes) { "The settings journal key is reserved." }
        requireRecovered()

        val existing = target.allValues()
        val snapshot = JSONObject()
        val held = linkedMapOf<String, StoredPreferenceValue?>()
        (existing.keys + changes.keys).filter { it != JOURNAL_KEY }.forEach { key ->
            val value = existing[key]
            require(value == null || value is String) { "Graph preference '$key' is not a string." }
            snapshot.put(key, value ?: JSONObject.NULL)
            held[key] = (value as? String)?.let(StoredPreferenceValue::StringValue)
        }
        heldPrior[authorityKey] = held
        val journal = JSONObject().put("version", 1).put("phase", "PREPARED").put("prior", snapshot)
        if (!commit(mapOf(JOURNAL_KEY to StoredPreferenceValue.StringValue(journal.toString())))) {
            recoverAfterFailure()
            throw GraphPublicationException("Unable to prepare settings publication.")
        }

        try {
            stageSecrets()
        } catch (failure: Exception) {
            recoverAfterFailure()
            throw failure
        }

        journal.put("phase", "SECRETS_STAGED")
        if (!commit(mapOf(JOURNAL_KEY to StoredPreferenceValue.StringValue(journal.toString())))) {
            recoverAfterFailure()
            throw GraphPublicationException("Unable to record staged settings secrets.")
        }
        val publication = changes.mapValues { (_, value) -> value?.let(StoredPreferenceValue::StringValue) }
        if (!commit(publication)) {
            recoverAfterFailure()
            throw GraphPublicationException("Unable to publish settings graph.")
        }
        if (!commit(mapOf(JOURNAL_KEY to null))) {
            recoverAfterFailure()
            throw GraphPublicationException("Unable to finalize settings publication.")
        }
        heldPrior.remove(authorityKey)
    }

    private fun recoverAfterFailure() {
        requireRecovered()
    }

    private fun commit(changes: Map<String, StoredPreferenceValue?>): Boolean =
        try {
            target.commit(changes)
        } catch (_: Exception) {
            false
        }

    companion object {
        const val JOURNAL_KEY = "profileGraphPublicationJournal"
        private val LOCK = Any()
        private val heldPrior = IdentityHashMap<Any, Map<String, StoredPreferenceValue?>>()
    }
}
