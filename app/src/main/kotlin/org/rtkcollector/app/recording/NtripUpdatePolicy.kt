package org.rtkcollector.app.recording

object NtripUpdatePolicy {
    data class Result(
        val allowed: Boolean,
        val category: RecordingErrorCategory,
        val severity: RecordingErrorSeverity,
        val message: String?,
    )

    fun validateUpdate(
        activeRecordingRunning: Boolean,
        activeWorkflowUsesNtrip: Boolean,
    ): Result =
        when {
            !activeRecordingRunning -> rejected("Cannot update NTRIP: no active recording.")
            !activeWorkflowUsesNtrip -> rejected("Cannot update NTRIP: active workflow does not use NTRIP.")
            else -> Result(
                allowed = true,
                category = RecordingErrorCategory.NONE,
                severity = RecordingErrorSeverity.NONE,
                message = null,
            )
        }

    private fun rejected(message: String): Result =
        Result(
            allowed = false,
            category = RecordingErrorCategory.NTRIP,
            severity = RecordingErrorSeverity.DEGRADED,
            message = message,
        )
}
