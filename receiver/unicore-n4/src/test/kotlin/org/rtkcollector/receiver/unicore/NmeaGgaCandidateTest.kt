package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionEngine

class NmeaGgaCandidateTest {
    @Test
    fun `quality 0 returns null`() {
        assertNull(gga(quality = 0).toCandidate(receiverFamily = "um980", nowMillis = 0L))
    }

    @Test
    fun `quality 1 maps to SINGLE`() {
        assertEquals(FixClass.SINGLE, gga(quality = 1).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `quality 2 maps to DGPS`() {
        assertEquals(FixClass.DGPS, gga(quality = 2).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `quality 4 maps to RTK_FIXED`() {
        assertEquals(FixClass.RTK_FIXED, gga(quality = 4).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `quality 5 maps to RTK_FLOAT`() {
        assertEquals(FixClass.RTK_FLOAT, gga(quality = 5).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `quality 6 maps to PPP_CONVERGED`() {
        assertEquals(FixClass.PPP_CONVERGED, gga(quality = 6).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `unknown quality maps to SINGLE`() {
        assertEquals(FixClass.SINGLE, gga(quality = 9).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `missing lat or lon returns null`() {
        val fix = gga(quality = 1).copy(latDeg = null)
        assertNull(fix.toCandidate("um980", 0L))
    }

    @Test
    fun `engine is GENERIC_NMEA and source label is NMEA-GGA`() {
        val candidate = gga(quality = 1).toCandidate("ublox", 99L)

        assertEquals(SolutionEngine.GENERIC_NMEA, candidate?.engine)
        assertEquals("NMEA-GGA", candidate?.sourceId)
        assertEquals("ublox", candidate?.receiverFamily)
        assertEquals(99L, candidate?.updatedAtMillis)
    }

    private fun gga(quality: Int): NmeaGgaFix = NmeaGgaFix(
        talker = "GN",
        utcTime = "120000",
        latDeg = 50.0,
        lonDeg = 14.0,
        fixQuality = quality,
        satelliteCount = 12,
        hdop = 0.8,
        altitudeM = 300.0,
        geoidSeparationM = -42.0,
        differentialAgeS = null,
        stationId = null,
    )
}
