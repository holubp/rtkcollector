package org.rtkcollector.app.share

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionShareModelsTest {
    @Test
    fun `zip sharing is disabled while recording unless partial snapshot is explicit`() {
        assertFalse(SessionShareState(isRecording = true, allowPartialSnapshot = false).zipEnabled)
        assertTrue(SessionShareState(isRecording = true, allowPartialSnapshot = true).zipEnabled)
        assertTrue(SessionShareState(isRecording = false, allowPartialSnapshot = false).zipEnabled)
    }
}
