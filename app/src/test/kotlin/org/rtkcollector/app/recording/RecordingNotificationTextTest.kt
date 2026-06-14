package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordingNotificationTextTest {
    @Test
    fun `inactive notification text is explicit`() {
        assertEquals(
            "Recording inactive",
            recordingNotificationText(running = false, receiverRxBytes = 0, correctionInputBytes = 0),
        )
    }

    @Test
    fun `starting notification text is only used before data arrives`() {
        assertEquals(
            "Starting recording",
            recordingNotificationText(running = true, receiverRxBytes = 0, correctionInputBytes = 0),
        )
    }

    @Test
    fun `active notification text includes raw and ntrip byte counts`() {
        assertEquals(
            "Recording in progress · RAW 12.3 kB · NTRIP 4.6 kB",
            recordingNotificationText(running = true, receiverRxBytes = 12_345, correctionInputBytes = 4_567),
        )
    }
}
