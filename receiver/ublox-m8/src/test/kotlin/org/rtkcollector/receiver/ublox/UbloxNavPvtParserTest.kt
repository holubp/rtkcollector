package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UbloxNavPvtParserTest {
    @Test
    fun `parses nav pvt position`() {
        val payload = ByteArray(92)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 123456)
            put(20, 0x03)
            put(21, 0x02)
            put(23, 14)
            putInt(24, (14.4212534 * 1e7).toInt())
            putInt(28, (50.0874512 * 1e7).toInt())
            putInt(32, 287_423)
            putInt(36, 243_812)
            putInt(40, 800)
            putInt(44, 1200)
        }
        val frame = UbloxFrame.build(0x01, 0x07, payload)

        val telemetry = UbloxNavPvtParser.parse(frame, nowMillis = 10_000L)

        assertNotNull(telemetry)
        assertEquals(FixClass.DGPS, telemetry!!.fixClass)
        assertEquals(50.0874512, telemetry.latDeg!!, 0.0000001)
        assertEquals(14.4212534, telemetry.lonDeg!!, 0.0000001)
        assertEquals(287.423, telemetry.ellipsoidalHeightM!!, 0.001)
        assertEquals(243.812, telemetry.mslAltitudeM!!, 0.001)
        assertEquals(0.8, telemetry.horizontalAccuracyM!!, 0.001)
        assertEquals(1.2, telemetry.verticalAccuracyM!!, 0.001)
        assertEquals(14, telemetry.satellitesUsed)
    }

    @Test
    fun `nav pvt with carrSoln 1 reports rtk float`() {
        assertEquals(FixClass.RTK_FLOAT, parseFixClass(fixType = 0x03, flags = 0x40))
    }

    @Test
    fun `nav pvt with carrSoln 2 reports rtk fixed`() {
        assertEquals(FixClass.RTK_FIXED, parseFixClass(fixType = 0x03, flags = 0x80))
    }

    private fun parseFixClass(fixType: Int, flags: Int): FixClass {
        val payload = ByteArray(92)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(20, fixType.toByte())
            put(21, flags.toByte())
        }
        val frame = UbloxFrame.build(0x01, 0x07, payload)
        return UbloxNavPvtParser.parse(frame, nowMillis = 0L)!!.fixClass!!
    }
}
