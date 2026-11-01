package org.rtkcollector.app.diagnostics

import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

internal fun failureDiagnosticAttributes(error: Throwable, artifact: String? = null,
    operation: String? = null): Map<String, String> = buildMap {
    put("exceptionClass", error.javaClass.name.take(256))
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    seen.add(error)
    val causes = mutableListOf<String>()
    var cause = error.cause
    while (cause != null && causes.size < 4 && seen.add(cause)) {
        causes += cause.javaClass.name
        cause = cause.cause
    }
    if (causes.isNotEmpty()) put("causeClasses", causes.joinToString(" -> ").take(256))
    val storage = error as? org.rtkcollector.core.capture.RawRecordingStorageException
    (artifact ?: storage?.artifact)?.let { put("artifact", redactDiagnosticText(it).take(256)) }
    (operation ?: storage?.operation)?.let { put("operation", redactDiagnosticText(it).take(256)) }
    val cleanup = failureCleanupIssues(error)
    if (cleanup.isNotEmpty()) put("cleanupFailures", cleanup.joinToString("; ") {
        "${it.artifact}:${it.operation}:${it.exceptionClass}"
    }.take(256))
}

internal fun failureCleanupIssues(error: Throwable): List<org.rtkcollector.core.session.SessionWriterIssue> = buildList {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = error
    while (current != null && seen.size < 4 && seen.add(current) && size < 4) {
        for (suppressed in current.suppressed.take(4)) {
            if (suppressed !is org.rtkcollector.core.session.SessionWriterCleanupException) continue
            val raw = suppressed.artifact == "receiver-rx.raw"
            add(org.rtkcollector.core.session.SessionWriterIssue.fromFailure(suppressed.artifact,
                if (raw) org.rtkcollector.core.session.SessionWriterIssueCategory.RAW_RX
                    else org.rtkcollector.core.session.SessionWriterIssueCategory.BINARY_SIDECAR,
                if (raw) org.rtkcollector.core.session.SessionWriterIssueSeverity.FATAL
                    else org.rtkcollector.core.session.SessionWriterIssueSeverity.DEGRADED,
                suppressed.operation, suppressed.cause ?: suppressed))
            if (size == 4) break
        }
        current = current.cause
    }
}

internal object ProcessDiagnosticIdentity {
    private val instance = UUID.randomUUID().toString()
    fun attributes(processId: Int): Map<String, String> =
        mapOf("processId" to processId.toString(), "processInstanceId" to instance)
}

internal val FAILURE_DIAGNOSTIC_KEYS = setOf("exceptionClass", "causeClasses", "artifact", "operation",
    "stage", "stageElapsedMillis", "startupGeneration", "processId", "processInstanceId", "cleanupFailures")
