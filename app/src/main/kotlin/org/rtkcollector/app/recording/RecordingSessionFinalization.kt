package org.rtkcollector.app.recording

import org.rtkcollector.app.diagnostics.SessionDiagnostics
import org.rtkcollector.core.session.SessionWriterCloseReport
import org.rtkcollector.core.session.SessionWriterIssue
import org.rtkcollector.core.session.SessionWriterIssueCategory
import org.rtkcollector.core.session.SessionWriterIssueSeverity

internal data class RecordingSessionFinalization(val outcome: String, val closeReport: SessionWriterCloseReport)

/** Raw/other artifacts close independently; an event owner has only a bounded grace period. */
internal fun closeRecordingSessionWriters(writers: RecordingSessionWriters?, essential: SessionDiagnostics?,
    timeoutMillis: Long = 150): RecordingSessionFinalization {
    val report = runCatching {
        val nonEvents = writers?.closeNonEventStreams() ?: SessionWriterCloseReport()
        val unownedEvents = if (essential == null) writers?.closeEventStream() ?: SessionWriterCloseReport() else SessionWriterCloseReport()
        SessionWriterCloseReport(nonEvents.issues + unownedEvents.issues)
    }.getOrElse {
        SessionWriterCloseReport(listOf(SessionWriterIssue("session-writers", SessionWriterIssueCategory.RAW_RX,
            SessionWriterIssueSeverity.FATAL, "Unable to finalize recording streams.")))
    }
    val outcome = when {
        report.issues.isNotEmpty() -> "CLOSE_FAILED"
        writers != null && essential != null && essential.awaitClosed(timeoutMillis) &&
            essential.writeFailures == 0L && !essential.hasCriticalLoss -> "FINALIZED"
        else -> "EVIDENCE_INCOMPLETE"
    }
    return RecordingSessionFinalization(outcome, report)
}
