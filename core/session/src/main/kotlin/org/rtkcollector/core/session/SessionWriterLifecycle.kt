package org.rtkcollector.core.session

class SessionWriterOpenException(val artifact: String, val operation: String, cause: Throwable) :
    java.io.IOException(cause.message ?: "Failed to $operation $artifact", cause)

class SessionWriterCleanupException(val artifact: String, val operation: String, cause: Throwable) :
    java.io.IOException("Failed to $operation $artifact during partial session-writer cleanup.", cause)

enum class SessionWriterIssueCategory {
    RAW_RX,
    BINARY_SIDECAR,
    LINE_SIDECAR,
    STRUCTURED_SIDECAR,
    METADATA,
}

enum class SessionWriterIssueSeverity {
    DEGRADED,
    FATAL,
}

data class SessionWriterIssue(
    val artifact: String,
    val category: SessionWriterIssueCategory,
    val severity: SessionWriterIssueSeverity,
    val message: String,
    val operation: String? = null,
    val exceptionClass: String? = null,
    val causeClasses: List<String> = emptyList(),
) {
    companion object {
        fun fromFailure(artifact: String, category: SessionWriterIssueCategory,
            severity: SessionWriterIssueSeverity, operation: String, failure: Throwable): SessionWriterIssue {
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
            seen.add(failure)
            val causes = mutableListOf<String>()
            var cause = failure.cause
            while (cause != null && causes.size < 4 && seen.add(cause)) {
                causes += cause.javaClass.name.take(256)
                cause = cause.cause
            }
            return SessionWriterIssue(artifact, category, severity,
                failure.message ?: "Failed to $operation $artifact", operation,
                failure.javaClass.name.take(256), causes)
        }
    }
}

data class SessionWriterCloseReport(
    val issues: List<SessionWriterIssue> = emptyList(),
) {
    val rawRecordingSafe: Boolean =
        issues.none {
            it.category == SessionWriterIssueCategory.RAW_RX ||
                it.severity == SessionWriterIssueSeverity.FATAL
        }

    val hasFatalIssue: Boolean =
        issues.any { it.severity == SessionWriterIssueSeverity.FATAL }

    val userMessage: String? =
        issues.firstOrNull { it.severity == SessionWriterIssueSeverity.FATAL }?.message
            ?: issues.firstOrNull()?.message
}
