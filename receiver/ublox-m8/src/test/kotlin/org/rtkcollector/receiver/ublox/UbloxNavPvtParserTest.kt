package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
            putShort(4, 2026)
            put(6, 6)
            put(7, 19)
            put(8, 21)
            put(9, 0)
            put(10, 48)
            put(11, 0x03)
            put(20, 0x03)
            put(21, 0x03)
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
        assertEquals("2026-06-19T21:00:48Z", telemetry.utcTime)
    }

    @Test
    fun `does not export utc time when nav pvt date time validity flags are unset`() {
        val payload = ByteArray(92)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(4, 2026)
            put(6, 6)
            put(7, 19)
            put(8, 21)
            put(9, 0)
            put(10, 48)
            put(11, 0x00)
        }
        val frame = UbloxFrame.build(0x01, 0x07, payload)

        val telemetry = UbloxNavPvtParser.parse(frame, nowMillis = 10_000L)

        assertNotNull(telemetry)
        assertNull(telemetry!!.utcTime)
    }

    @Test
    fun `time only fix never becomes a position despite solution flags`() {
        listOf(0x01, 0x03, 0x41, 0x81).forEach { flags ->
            val telemetry = parseNavPvt(fixType = 0x05, flags = flags)

            assertEquals(FixClass.NONE, telemetry.fixClass, "flags=0x${flags.toString(16)}")
            assertEquals(FixClass.NONE, telemetry.toSolutionCandidate()?.fixClass, "flags=0x${flags.toString(16)}")
        }
    }

    @Test
    fun `three dimensional differential position classification is unchanged`() {
        val telemetry = parseNavPvt(fixType = 0x03, flags = 0x03)

        assertEquals(FixClass.DGPS, telemetry.fixClass)
        assertNotNull(telemetry.toSolutionCandidate())
        assertEquals(50.0874512, telemetry.latDeg!!, 0.0000001)
        assertEquals(FixClass.SINGLE, parseNavPvt(fixType = 0x04, flags = 0x01).fixClass)
    }

    @Test
    fun `signed nav pvt nano is normalized into utc and exported as milliseconds`() {
        val positive = parseNavPvt(nano = 250_000_000)
        val negative = parseNavPvt(nano = -250_000_000)

        assertEquals("2026-06-19T21:00:48.25Z", positive.utcTime)
        assertEquals("2026-06-19T21:00:47.75Z", negative.utcTime)
        assertTrue(UbloxNmeaExporter.exportGga(positive)!!.startsWith("\$GNGGA,210048.250,"))
        assertTrue(UbloxNmeaExporter.exportGga(negative)!!.startsWith("\$GNGGA,210047.750,"))
    }

    @Test
    fun `signed nano rolls utc across midnight without changing validity`() {
        val positive = parseNavPvt(
            nano = 1_000_000_000,
            hour = 23,
            minute = 59,
            second = 59,
        )
        val negative = parseNavPvt(
            nano = -250_000_000,
            hour = 0,
            minute = 0,
            second = 0,
        )

        assertEquals("2026-06-20T00:00:00Z", positive.utcTime)
        assertEquals("2026-06-18T23:59:59.75Z", negative.utcTime)
        assertTrue(UbloxNmeaExporter.exportGga(positive)!!.startsWith("\$GNGGA,000000.000,"))
        assertTrue(UbloxNmeaExporter.exportGga(negative)!!.startsWith("\$GNGGA,235959.750,"))

        val invalidTime = parseNavPvt(nano = 250_000_000, validFlags = 0)
        assertNull(invalidTime.utcTime)
    }

    @Test
    fun `preserves valid leap second when nano is zero`() {
        val telemetry = parseNavPvt(second = 60)

        assertEquals("2026-06-19T21:00:60Z", telemetry.utcTime)
        assertTrue(UbloxNmeaExporter.exportGga(telemetry)!!.startsWith("\$GNGGA,210060.000,"))
    }

    @Test
    fun `nav pvt with carrSoln 1 reports rtk float`() {
        assertEquals(FixClass.RTK_FLOAT, parseFixClass(fixType = 0x03, flags = 0x41))
    }

    @Test
    fun `nav pvt with carrSoln 2 reports rtk fixed`() {
        assertEquals(FixClass.RTK_FIXED, parseFixClass(fixType = 0x03, flags = 0x81))
    }

    @Test
    fun `nav pvt without gnssFixOK never reports a usable position`() {
        listOf(0x00, 0x02, 0x40, 0x80).forEach { flags ->
            listOf(0x03, 0x04).forEach { fixType ->
                assertEquals(FixClass.NONE, parseFixClass(fixType, flags))
            }
        }
    }

    @Test
    fun `nav pvt with carrSoln and two dimensional fix does not report rtk`() {
        assertEquals(FixClass.NONE, parseFixClass(fixType = 0x02, flags = 0x41))
    }

    private fun parseFixClass(fixType: Int, flags: Int): FixClass {
        return parseNavPvt(fixType = fixType, flags = flags).fixClass!!
    }

    private fun parseNavPvt(
        fixType: Int = 0x03,
        flags: Int = 0x03,
        nano: Int = 0,
        validFlags: Int = 0x03,
        hour: Int = 21,
        minute: Int = 0,
        second: Int = 48,
    ): UbloxTelemetry {
        val payload = ByteArray(92)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 123456)
            putShort(4, 2026)
            put(6, 6)
            put(7, 19)
            put(8, hour.toByte())
            put(9, minute.toByte())
            put(10, second.toByte())
            put(11, validFlags.toByte())
            putInt(16, nano)
            put(20, fixType.toByte())
            put(21, flags.toByte())
            put(23, 14)
            putInt(24, (14.4212534 * 1e7).toInt())
            putInt(28, (50.0874512 * 1e7).toInt())
            putInt(32, 287_423)
            putInt(36, 243_812)
            putInt(40, 800)
            putInt(44, 1200)
        }
        val frame = UbloxFrame.build(0x01, 0x07, payload)
        return UbloxNavPvtParser.parse(frame, nowMillis = 0L)!!
    }
}
