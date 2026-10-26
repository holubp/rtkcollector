package org.rtkcollector.app.diagnostics

import org.rtkcollector.app.sessions.SessionCompletionAssessment

/** Bounded per-process deduplication; never modifies a historical session. */
class RecoveryDiagnostics(private val emit: (RuntimeDiagnosticRecord) -> Unit) {
    private val seen = linkedSetOf<String>()

    fun record(assessments: List<SessionCompletionAssessment>) {
        assessments.filter { it.state in setOf("INCOMPLETE", "CLOSE_FAILED") &&
            it.sessionId != null && "${it.sessionId}:${it.state}" !in seen }.take(16).forEach { assessment ->
            val id = assessment.sessionId ?: return@forEach
            if (!seen.add("$id:${assessment.state}")) return@forEach
            while (seen.size > 512) seen.remove(seen.first())
            runCatching {
                emit(RuntimeDiagnosticRecord(System.currentTimeMillis(), DiagnosticCategory.SERVICE, "NOTICE",
                    "Session recovery assessment; no unobserved stop cause inferred.",
                    mapOf("sessionId" to id, "reason" to assessment.state), code = "session-recovery-assessed"))
            }
        }
    }
}
