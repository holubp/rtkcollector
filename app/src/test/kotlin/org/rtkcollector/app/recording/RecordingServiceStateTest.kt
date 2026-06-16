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

    @Test
    fun `best solution tick with sparse candidate preserves richer live telemetry fields`() {
        val previous = RecordingServiceState(
            latDeg = 50.0,
            lonDeg = 14.0,
            latLon = "50.000000000, 14.000000000",
            ellipsoidalHeight = "300.000 m",
            altitude = "250.000 m",
            horizontalAccuracy = "0.012 m",
            verticalAccuracy = "0.030 m",
            satellites = "4 / 19",
            satellitesUsed = 4,
            satellitesInView = 19,
        )

        val updated = previous.applyBestSolutionDisplayDelta(
            delta = BestSolutionStateDelta(
                bestSolutionSource = "NMEA-GGA",
                bestSolutionFix = "DGPS",
                bestSolutionAgeMs = 250L,
                latDeg = 50.1,
                lonDeg = 14.1,
                ellipsoidalHeightM = 301.0,
                mslAltitudeM = 251.0,
                horizontalAccuracyM = null,
                verticalAccuracyM = null,
                satellitesUsed = 16,
                satellitesInView = null,
                mockResult = org.rtkcollector.app.mocklocation.MockLocationPublishResult.DISABLED,
            ),
            ubloxFrequency = previous.ubloxFrequency,
            formatLatLon = { lat, lon -> "%.1f, %.1f".format(java.util.Locale.US, lat, lon) },
            formatMeters = { "%.3f m".format(java.util.Locale.US, it) },
            formatSatellites = { used, inView ->
                when {
                    used == null && inView == null -> "n/a"
                    inView == null -> used.toString()
                    used == null -> "n/a / $inView"
                    else -> "$used / $inView"
                }
            },
        )

        assertEquals("NMEA-GGA", updated.bestSolutionSource)
        assertEquals("DGPS", updated.bestSolutionFix)
        assertEquals("50.1, 14.1", updated.latLon)
        assertEquals("0.012 m", updated.horizontalAccuracy)
        assertEquals("0.030 m", updated.verticalAccuracy)
        assertEquals(16, updated.satellitesUsed)
        assertEquals(19, updated.satellitesInView)
        assertEquals("16 / 19", updated.satellites)
    }

    @Test
    fun `best solution tick without candidate preserves live telemetry fields`() {
        val previous = RecordingServiceState(
            latDeg = 50.0,
            lonDeg = 14.0,
            latLon = "50.000000000, 14.000000000",
            ellipsoidalHeight = "300.000 m",
            altitude = "250.000 m",
            horizontalAccuracy = "0.012 m",
            verticalAccuracy = "0.030 m",
            satellites = "4 / 19",
            satellitesUsed = 4,
            satellitesInView = 19,
            bestSolutionSource = "UM980-BESTNAV",
            bestSolutionFix = "RTK_FLOAT",
            bestSolutionAgeMs = 500L,
        )

        val updated = previous.applyBestSolutionDisplayDelta(
            delta = BestSolutionStateDelta(
                bestSolutionSource = "n/a",
                bestSolutionFix = "n/a",
                bestSolutionAgeMs = null,
                latDeg = null,
                lonDeg = null,
                ellipsoidalHeightM = null,
                mslAltitudeM = null,
                horizontalAccuracyM = null,
                verticalAccuracyM = null,
                satellitesUsed = null,
                satellitesInView = null,
                mockResult = org.rtkcollector.app.mocklocation.MockLocationPublishResult.DISABLED,
            ),
            ubloxFrequency = previous.ubloxFrequency,
            formatLatLon = { lat, lon -> "%.1f, %.1f".format(java.util.Locale.US, lat, lon) },
            formatMeters = { "%.3f m".format(java.util.Locale.US, it) },
            formatSatellites = { used, inView ->
                when {
                    used == null && inView == null -> "n/a"
                    inView == null -> used.toString()
                    used == null -> "n/a / $inView"
                    else -> "$used / $inView"
                }
            },
        )

        assertEquals("n/a", updated.bestSolutionSource)
        assertEquals("n/a", updated.bestSolutionFix)
        assertNull(updated.bestSolutionAgeMs)
        assertEquals(50.0, updated.latDeg)
        assertEquals(14.0, updated.lonDeg)
        assertEquals("50.000000000, 14.000000000", updated.latLon)
        assertEquals("300.000 m", updated.ellipsoidalHeight)
        assertEquals("250.000 m", updated.altitude)
        assertEquals("0.012 m", updated.horizontalAccuracy)
        assertEquals("0.030 m", updated.verticalAccuracy)
        assertEquals(4, updated.satellitesUsed)
        assertEquals(19, updated.satellitesInView)
        assertEquals("4 / 19", updated.satellites)
    }
}
