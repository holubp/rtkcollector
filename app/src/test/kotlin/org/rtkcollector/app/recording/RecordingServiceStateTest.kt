package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RecordingServiceStateTest {
    @Test
    fun `clearBestSolutionFields removes stale position and best-solution display state`() {
        val cleared = RecordingServiceState(
            latDeg = 50.0,
            lonDeg = 14.0,
            latLon = "50.000000000, 14.000000000",
            ellipsoidalHeight = "300.000 m",
            altitude = "250.000 m",
            horizontalAccuracy = "1.200 m",
            verticalAccuracy = "2.400 m",
            satellites = "12 / 18",
            satellitesUsed = 12,
            satellitesInView = 18,
            bestSolutionSource = "UBX-NAV-PVT",
            bestSolutionFix = "SINGLE",
            bestSolutionAgeMs = 500L,
            mockLocationState = "PUBLISHED",
            ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA 1/1/1/1/1 Hz",
        ).clearBestSolutionFields(
            mockLocationState = "Disabled",
            ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA -/-/-/-/- Hz",
        )

        assertNull(cleared.latDeg)
        assertNull(cleared.lonDeg)
        assertEquals("n/a", cleared.latLon)
        assertEquals("n/a", cleared.ellipsoidalHeight)
        assertEquals("n/a", cleared.altitude)
        assertEquals("n/a", cleared.horizontalAccuracy)
        assertEquals("n/a", cleared.verticalAccuracy)
        assertEquals("n/a", cleared.satellites)
        assertNull(cleared.satellitesUsed)
        assertNull(cleared.satellitesInView)
        assertEquals("n/a", cleared.bestSolutionSource)
        assertEquals("n/a", cleared.bestSolutionFix)
        assertNull(cleared.bestSolutionAgeMs)
        assertEquals("Disabled", cleared.mockLocationState)
        assertEquals("Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA -/-/-/-/- Hz", cleared.ubloxFrequency)
    }
}
