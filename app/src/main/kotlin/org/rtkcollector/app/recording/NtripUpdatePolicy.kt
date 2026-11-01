package org.rtkcollector.app.recording

/** One pending status and one serial-consumer turn; delivery never holds the producer lock. */
internal class LatestNtripStatusDelivery<T : Any>(
    private val schedule: (Runnable) -> Unit,
    private val deliver: (T) -> Unit,
) {
    private val lock = Any()
    private var pending: T? = null
    private var scheduled = false

    fun offer(value: T) {
        val enqueue = synchronized(lock) {
            pending = value
            if (scheduled) false else { scheduled = true; true }
        }
        if (enqueue) schedule(Runnable(::drain))
    }

    private fun drain() {
        val value = synchronized(lock) { pending.also { pending = null } }
        try { value?.let(deliver) }
        finally {
            val again = synchronized(lock) {
                if (pending == null) { scheduled = false; false } else true
            }
            if (again) schedule(Runnable(::drain))
        }
    }
}

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
