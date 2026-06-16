package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UbloxFrameTest {
    @Test
    fun `builds ubx frame with checksum`() {
        val frame = UbloxFrame.build(messageClass = 0x06, messageId = 0x08, payload = byteArrayOf(0xE8.toByte(), 0x03, 0x01, 0x00, 0x01, 0x00))

        assertArrayEquals(
            byteArrayOf(
                0xB5.toByte(), 0x62, 0x06, 0x08, 0x06, 0x00,
                0xE8.toByte(), 0x03, 0x01, 0x00, 0x01, 0x00,
                0x01, 0x39,
            ),
            frame,
        )
        assertTrue(UbloxFrame.isValid(frame))
    }
}
