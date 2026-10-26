package org.rtkcollector.app.recording

import java.util.UUID

internal enum class RecordingStopReason {
    USER, START_FAILURE, RAW_STORAGE_FAILURE, SERVICE_DESTROYED, SHUTDOWN_COMMAND_FAILURE, WRITER_CLOSE_FAILURE,
}

internal class RecordingStopContext {
    val correlationId: String = UUID.randomUUID().toString()
    @Volatile var initiator: RecordingStopReason? = null
        private set
    private val additional = linkedSetOf<RecordingStopReason>()
    val contributors: Set<RecordingStopReason> get() = synchronized(this) { additional.toSet() }

    @Synchronized fun request(reason: RecordingStopReason): Boolean {
        if (initiator == null) { initiator = reason; return true }
        if (reason != initiator) additional.add(reason)
        return false
    }
}
