package org.rtkcollector.app.sessions

import org.json.JSONObject

data class SessionCompletionAssessment(val state: String, val label: String, val sessionId: String?)

internal fun assessSessionCompletion(metadata: JSONObject?, active: Boolean, location: String = ""): SessionCompletionAssessment {
    val identity = metadata?.optString("sessionUuid")?.takeIf { UUID_PATTERN.matches(it) }
        ?: UUID_PATTERN.find(location)?.value
    val stopped = metadata != null && !metadata.isNull("stoppedAt") && metadata.optString("stoppedAt").isNotBlank()
    val state = when {
        active -> "ACTIVE"
        metadata == null -> "INCOMPLETE"
        metadata.optString("stopOutcome") == "FINALIZED" && stopped -> "FINALIZED"
        metadata.optString("stopOutcome") == "CLOSE_FAILED" -> "CLOSE_FAILED"
        metadata.isNull("stopOutcome") && stopped -> "LEGACY_STOPPED"
        else -> "INCOMPLETE"
    }
    val label = when (state) {
        "ACTIVE" -> "Recording"
        "FINALIZED" -> "Stopped: ${metadata?.optString("stopReason", "unknown")}"
        "CLOSE_FAILED" -> "Stopped; writer close failed"
        "LEGACY_STOPPED" -> "Stopped (legacy evidence)"
        else -> "Incomplete; cause unknown"
    }
    return SessionCompletionAssessment(state, label, identity)
}

internal fun sessionCompletionLabel(metadata: JSONObject?, active: Boolean): String = assessSessionCompletion(metadata, active).label

private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
