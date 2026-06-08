package org.rtkcollector.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionWriterLifecycleTest {
    @Test
    fun `report with only sidecar issue is degraded but raw safe`() {
        val report = SessionWriterCloseReport(
            issues = listOf(
                SessionWriterIssue(
                    artifact = SessionArtifactFile.RECEIVER_SOLUTION_JSONL.fileName,
                    category = SessionWriterIssueCategory.LINE_SIDECAR,
                    severity = SessionWriterIssueSeverity.DEGRADED,
                    message = "solution sidecar close failed",
                ),
            ),
        )

        assertTrue(report.rawRecordingSafe)
        assertFalse(report.hasFatalIssue)
    }

    @Test
    fun `report with raw issue is fatal and raw unsafe`() {
        val report = SessionWriterCloseReport(
            issues = listOf(
                SessionWriterIssue(
                    artifact = SessionArtifactFile.RECEIVER_RX_RAW.fileName,
                    category = SessionWriterIssueCategory.RAW_RX,
                    severity = SessionWriterIssueSeverity.FATAL,
                    message = "raw flush failed",
                ),
            ),
        )

        assertFalse(report.rawRecordingSafe)
        assertTrue(report.hasFatalIssue)
        assertEquals("raw flush failed", report.userMessage)
    }
}
