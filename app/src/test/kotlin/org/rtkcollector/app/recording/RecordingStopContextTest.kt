package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecordingStopContextTest {
    @Test fun `first stop cause survives duplicate and contributing failures`() {
        val context = RecordingStopContext()
        assertTrue(context.request(RecordingStopReason.USER))
        val id = context.correlationId
        assertFalse(context.request(RecordingStopReason.SERVICE_DESTROYED))
        assertFalse(context.request(RecordingStopReason.RAW_STORAGE_FAILURE))
        assertEquals(RecordingStopReason.USER, context.initiator)
        assertEquals(id, context.correlationId)
        assertEquals(setOf(RecordingStopReason.SERVICE_DESTROYED, RecordingStopReason.RAW_STORAGE_FAILURE), context.contributors)
    }
}
