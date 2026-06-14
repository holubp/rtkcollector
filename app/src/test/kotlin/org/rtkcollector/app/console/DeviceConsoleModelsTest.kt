package org.rtkcollector.app.console

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceConsoleModelsTest {
    @Test
    fun `line endings render bytes`() {
        assertArrayEquals(byteArrayOf(13, 10), DeviceConsoleLineEnding.CRLF.bytes)
        assertArrayEquals(byteArrayOf(10), DeviceConsoleLineEnding.LF.bytes)
        assertArrayEquals(byteArrayOf(13), DeviceConsoleLineEnding.CR.bytes)
        assertArrayEquals(byteArrayOf(), DeviceConsoleLineEnding.NONE.bytes)
    }

    @Test
    fun `formatter preserves text and marks binary bytes`() {
        val rendered = DeviceConsoleOutputFormatter.render(byteArrayOf(0x41, 0x0A, 0x00, 0x7F, 0x42))
        assertEquals("A\n<00><7F>B", rendered)
    }

    @Test
    fun `rolling buffer keeps newest content`() {
        val buffer = DeviceConsoleRollingBuffer(maxChars = 8)
            .append("abc")
            .append("def")
            .append("ghi")

        assertEquals("bcdefghi", buffer.text)
    }

    @Test
    fun `recording active disables console connect`() {
        val idle = DeviceConsoleAvailability.fromRecordingState(recordingActive = false)
        val recording = DeviceConsoleAvailability.fromRecordingState(recordingActive = true)

        assertTrue(idle.canConnect)
        assertFalse(recording.canConnect)
        assertEquals("Stop recording before opening the device console.", recording.message)
    }
}
