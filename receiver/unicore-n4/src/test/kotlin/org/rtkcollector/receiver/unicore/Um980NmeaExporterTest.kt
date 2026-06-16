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
        assertTrue(sentences[0].startsWith("\$GPGGA,084942.000,5005.247074,N,01425.275207,E,5,18,0.378,243.812,M,43.611,M,0.800,1234*"))
        assertTrue(sentences[1].startsWith("\$GPRMC,084942.000,A,5005.247074,N,01425.275207,E,2.333,123.400,310526,,,A*"))
        assertTrue(sentences[2].startsWith("\$GPVTG,123.400,T,,M,2.333,N,4.320,K,A*"))
        sentences.forEach { sentence ->
            assertTrue(sentence.endsWith("\r\n"))
            assertChecksum(sentence)
        }
    }

    @Test
    fun `exports sub second utc time for multi hertz telemetry`() {
        val telemetry = Um980Telemetry(
            source = "BESTNAVB",
            utcTime = "2026-06-14T15:14:19.550Z",
            solutionStatus = "SOL_COMPUTED",
            positionType = "PSRDIFF",
            latDeg = 49.24349032,
            lonDeg = 16.58035958,
            altitudeM = 276.834,
            ellipsoidalHeightM = 321.083,
            satellitesUsed = 10,
            differentialAgeS = 2.5,
            stationId = "1022",
            horizontalSpeedMps = 0.205,
            trackDeg = 107.203,
        )

        val sentences = Um980NmeaExporter.export(telemetry)

        assertTrue(
            sentences[0].startsWith("\$GPGGA,151419.550,4914.609419,N,01634.821575,E,2,10,"),
            sentences.joinToString(),
        )
        assertTrue(
            sentences[1].startsWith("\$GPRMC,151419.550,A,4914.609419,N,01634.821575,E,0.398,107.203,140626,,,A*"),
            sentences.joinToString(),
        )
        sentences.forEach(::assertChecksum)
    }

    @Test
    fun `exports sub second utc without rounding into invalid next minute`() {
        val telemetry = Um980Telemetry(
            source = "BESTNAVB",
            utcTime = "2026-06-14T15:14:59.999600Z",
            solutionStatus = "SOL_COMPUTED",
            positionType = "PSRDIFF",
            latDeg = 49.24349032,
            lonDeg = 16.58035958,
        )

        val sentences = Um980NmeaExporter.export(telemetry)

        assertTrue(sentences[0].startsWith("\$GPGGA,151459.999,"))
        assertTrue(sentences[1].startsWith("\$GPRMC,151459.999,"))
        sentences.forEach(::assertChecksum)
    }

    @Test
    fun `exports converged ppp as default dgps quality instead of estimated`() {
        val telemetry = Um980Telemetry(
            source = "BESTNAVB",
            utcTime = "2026-06-14T19:29:52Z",
            solutionStatus = "SOL_COMPUTED",
            positionType = "PPP",
            latDeg = 49.25167342112689,
            lonDeg = 16.556459890287186,
            altitudeM = 329.273,
            satellitesUsed = 16,
            differentialAgeS = 2.0,
            stationId = "9901",
        )

        val sentences = Um980NmeaExporter.export(telemetry)

        assertTrue(
            sentences[0].startsWith("\$GPGGA,192952.000,4915.100405,N,01633.387593,E,2,16,"),
            sentences.joinToString(),
        )
        sentences.forEach(::assertChecksum)
    }

    @Test
    fun `exports converged ppp with selectable quality`() {
        val telemetry = Um980Telemetry(
            source = "BESTNAVB",
            utcTime = "2026-06-14T19:29:52Z",
            solutionStatus = "SOL_COMPUTED",
            positionType = "PPP",
            latDeg = 49.25167342112689,
            lonDeg = 16.556459890287186,
            altitudeM = 329.273,
            satellitesUsed = 16,
        )

        val qualityFive = Um980NmeaExporter.export(
            telemetry,
            options = Um980NmeaExportOptions(pppGgaQuality = 5),
        )
        val qualityNine = Um980NmeaExporter.export(
            telemetry,
            options = Um980NmeaExportOptions(pppGgaQuality = 9),
        )

        assertTrue(qualityFive[0].startsWith("\$GPGGA,192952.000,4915.100405,N,01633.387593,E,5,16,"))
        assertTrue(qualityNine[0].startsWith("\$GPGGA,192952.000,4915.100405,N,01633.387593,E,9,16,"))
        qualityFive.forEach(::assertChecksum)
        qualityNine.forEach(::assertChecksum)
    }

    @Test
    fun `exports ppp converging as single quality instead of estimated`() {
        val telemetry = Um980Telemetry(
            source = "BESTNAVB",
            utcTime = "2026-06-14T19:29:52Z",
            solutionStatus = "SOL_COMPUTED",
            positionType = "PPP_CONVERGING",
            latDeg = 49.25167342112689,
            lonDeg = 16.556459890287186,
        )

        val sentences = Um980NmeaExporter.export(telemetry)

        assertTrue(
            sentences[0].startsWith("\$GPGGA,192952.000,4915.100405,N,01633.387593,E,1,00,"),
            sentences.joinToString(),
        )
        sentences.forEach(::assertChecksum)
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
