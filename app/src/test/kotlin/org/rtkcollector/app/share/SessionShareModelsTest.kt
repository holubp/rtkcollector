package org.rtkcollector.app.share

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionShareModelsTest {
    @Test
    fun `zip sharing stays disabled for active sessions even with a legacy partial flag`() {
        assertFalse(SessionShareState(isRecording = true, allowPartialSnapshot = false).zipEnabled)
        assertFalse(SessionShareState(isRecording = true, allowPartialSnapshot = true).zipEnabled)
        assertTrue(SessionShareState(isRecording = false, allowPartialSnapshot = false).zipEnabled)
        assertTrue(SessionShareState(isRecording = false, allowPartialSnapshot = true).zipEnabled)
    }
}
