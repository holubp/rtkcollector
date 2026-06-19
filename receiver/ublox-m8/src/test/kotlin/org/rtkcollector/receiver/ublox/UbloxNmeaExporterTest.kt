package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass

class UbloxNmeaExporterTest {
    @Test
    fun `exports nav pvt telemetry as gga`() {
        val sentence = UbloxNmeaExporter.exportGga(
            UbloxTelemetry(
                source = "UBX-NAV-PVT",
                updatedAtMillis = 10_000L,
                fixClass = FixClass.DGPS,
                latDeg = 50.0874512,
                lonDeg = 14.4212534,
                ellipsoidalHeightM = 287.423,
                mslAltitudeM = 243.812,
                satellitesUsed = 14,
                utcTime = "2026-06-19T21:00:48Z",
            ),
        )

        assertNotNull(sentence)
        assertTrue(sentence!!.startsWith("\$GNGGA,210048.000,5005.2470720,N,01425.2752040,E,2,14,"))
        assertTrue(sentence.contains(",243.812,M,43.611,M,"))
        assertChecksum(sentence)
    }

    @Test
    fun `carries coordinate rounding instead of emitting sixty minutes`() {
        val sentence = UbloxNmeaExporter.exportGga(
            UbloxTelemetry(
                source = "UBX-NAV-PVT",
                updatedAtMillis = 10_000L,
                fixClass = FixClass.SINGLE,
                latDeg = 12.9999999999,
                lonDeg = 179.9999999999,
                mslAltitudeM = 1.0,
                satellitesUsed = 1,
            ),
        )

        assertNotNull(sentence)
        assertTrue(sentence!!.contains(",1300.0000000,N,18000.0000000,E,"))
        assertTrue(!sentence.contains("60.0000000"))
        assertChecksum(sentence)
    }

    private fun assertChecksum(sentence: String) {
        val trimmed = sentence.trim()
        val body = trimmed.substringAfter('$').substringBefore('*')
        val expected = body.fold(0) { checksum, character -> checksum xor character.code }
        val actual = trimmed.substringAfter('*').toInt(16)
        assertEquals(expected, actual)
    }
}
