package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Um980NmeaExporterTest {
    @Test
    fun `exports GGA RMC and VTG from bestnav telemetry`() {
        val telemetry = Um980Telemetry(
            source = "BESTNAVB",
            utcTime = "2026-05-31T08:49:42Z",
            solutionStatus = "SOL_COMPUTED",
            positionType = "NARROW_FLOAT",
            latDeg = 50.087451234,
            lonDeg = 14.421253456,
            altitudeM = 243.812,
            ellipsoidalHeightM = 287.423,
            satellitesUsed = 18,
            hdop = 0.3779,
            differentialAgeS = 0.8,
            stationId = "1234",
            horizontalSpeedMps = 1.2,
            trackDeg = 123.4,
        )

        val sentences = Um980NmeaExporter.export(telemetry)

        assertEquals(3, sentences.size)
        assertTrue(sentences[0].startsWith("\$GPGGA,084942.00,5005.247074,N,01425.275207,E,5,18,0.378,243.812,M,43.611,M,0.800,1234*"))
        assertTrue(sentences[1].startsWith("\$GPRMC,084942.00,A,5005.247074,N,01425.275207,E,2.333,123.400,310526,,,A*"))
        assertTrue(sentences[2].startsWith("\$GPVTG,123.400,T,,M,2.333,N,4.320,K,A*"))
        sentences.forEach { sentence ->
            assertTrue(sentence.endsWith("\r\n"))
            assertChecksum(sentence)
        }
    }

    @Test
    fun `does not export nmea without position or utc`() {
        assertEquals(emptyList<String>(), Um980NmeaExporter.export(Um980Telemetry(source = "BESTNAVB")))
    }

    private fun assertChecksum(sentence: String) {
        val trimmed = sentence.trim()
        val body = trimmed.substringAfter('$').substringBefore('*')
        val expected = body.fold(0) { checksum, char -> checksum xor char.code }
        val actual = trimmed.substringAfter('*').toInt(16)
        assertEquals(expected, actual)
    }
}
