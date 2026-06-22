package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UbloxNavDopParserTest {
    @Test
    fun `parses nav dop centi dop values`() {
        val payload = ByteArray(18)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 123_000)
            putShort(4, 210)
            putShort(6, 180)
            putShort(8, 90)
            putShort(10, 160)
            putShort(12, 120)
            putShort(14, 130)
            putShort(16, 140)
        }
        val frame = UbloxFrame.build(0x01, 0x04, payload)

        val telemetry = UbloxNavDopParser.parse(frame, nowMillis = 4_000L)

        assertEquals(4_000L, telemetry?.updatedAtMillis)
        assertEquals(2.10, telemetry?.gdop)
        assertEquals(1.80, telemetry?.pdop)
        assertEquals(0.90, telemetry?.tdop)
        assertEquals(1.60, telemetry?.vdop)
        assertEquals(1.20, telemetry?.hdop)
        assertEquals(1.30, telemetry?.ndop)
        assertEquals(1.40, telemetry?.edop)
    }

    @Test
    fun `rejects truncated nav dop payload`() {
        val frame = UbloxFrame.build(0x01, 0x04, ByteArray(17))

        assertNull(UbloxNavDopParser.parse(frame, nowMillis = 0L))
    }

    @Test
    fun `rejects non nav dop frame`() {
        val frame = UbloxFrame.build(0x01, 0x07, ByteArray(18))

        assertNull(UbloxNavDopParser.parse(frame, nowMillis = 0L))
    }
}
