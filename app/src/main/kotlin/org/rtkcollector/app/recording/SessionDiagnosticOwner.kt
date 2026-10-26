package org.rtkcollector.app.recording

import java.io.Closeable
import org.rtkcollector.app.diagnostics.DiagnosticsStore
import org.rtkcollector.app.diagnostics.SessionDiagnostics

/** Lease release follows the close attempt, including deferred startup-failure cleanup. */
internal fun createSessionDiagnostics(sessionId: String, writers: RecordingSessionWriters,
    store: DiagnosticsStore, lease: Closeable, scheduleCleanup: (Runnable) -> Unit,
    startWorker: ((Runnable) -> Unit)? = null): SessionDiagnostics {
    val closeOwnedStream = {
        try { writers.closeEventStream().issues.isEmpty() } finally { lease.close() }
    }
    return SessionDiagnostics(sessionId, fallback = { store.appendEssentialJson(it) },
        closeEventStream = closeOwnedStream, startWorker = startWorker,
        scheduleFailedWorkerCleanup = { cleanup ->
            store.noteWriteFailure()
            runCatching {
                scheduleCleanup(cleanup)
            }.onFailure {
                // Keep protection if no safe cleanup owner could be scheduled.
                store.noteWriteFailure()
                throw it
            }
        },
        write = { json -> writers.appendEventJson(json); writers.flushEvents() })
}
