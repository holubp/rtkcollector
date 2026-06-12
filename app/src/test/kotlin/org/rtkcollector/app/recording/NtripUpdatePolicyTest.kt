package org.rtkcollector.app.recording

import kotlin.test.Test
import kotlin.test.assertEquals

class NtripUpdatePolicyTest {
    @Test
    fun rejectsLiveUpdateWhenActiveWorkflowDoesNotUseNtrip() {
        val result = NtripUpdatePolicy.validateUpdate(
            activeRecordingRunning = true,
            activeWorkflowUsesNtrip = false,
        )

        assertEquals(false, result.allowed)
        assertEquals(RecordingErrorCategory.NTRIP, result.category)
    }

    @Test
    fun allowsLiveUpdateWhenActiveWorkflowUsesNtrip() {
        val result = NtripUpdatePolicy.validateUpdate(
            activeRecordingRunning = true,
            activeWorkflowUsesNtrip = true,
        )

        assertEquals(true, result.allowed)
        assertEquals(RecordingErrorCategory.NONE, result.category)
    }
}
