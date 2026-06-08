package org.rtkcollector.core.session

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
)

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
