package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingServiceStateQueryTest {
    @Test
    fun `successful service state query reports started`() {
        var calls = 0

        val started = tryStartRecordingServiceStateQuery { calls++ }

        assertTrue(started)
        assertEquals(1, calls)
    }

    @Test
    fun `background service restriction does not crash activity`() {
        val started = tryStartRecordingServiceStateQuery {
            throw IllegalStateException("app is in background")
        }

        assertFalse(started)
    }

    @Test
    fun `unexpected service query failures are not hidden`() {
        assertThrows(SecurityException::class.java) {
            tryStartRecordingServiceStateQuery {
                throw SecurityException("service declaration is invalid")
            }
        }
    }
}
