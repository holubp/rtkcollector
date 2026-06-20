package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UbloxNavSatParserTest {
    @Test
    fun `parses visible and used satellites from nav sat`() {
        val payload = ByteArray(8 + 3 * 12)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 123_000)
            put(4, 1)
            put(5, 3)
            putInt(8 + 0 * 12 + 8, 0x08)
            putInt(8 + 1 * 12 + 8, 0x00)
            putInt(8 + 2 * 12 + 8, 0x08)
        }
        val frame = UbloxFrame.build(0x01, 0x35, payload)

        val telemetry = UbloxNavSatParser.parse(frame, nowMillis = 4_000L)

        assertEquals(3, telemetry?.satellitesInView)
        assertEquals(2, telemetry?.satellitesUsed)
        assertEquals(4_000L, telemetry?.updatedAtMillis)
    }

    @Test
    fun `rejects truncated nav sat payload`() {
        val payload = ByteArray(8)
        payload[5] = 2
        val frame = UbloxFrame.build(0x01, 0x35, payload)

        assertNull(UbloxNavSatParser.parse(frame, nowMillis = 0L))
    }
}
