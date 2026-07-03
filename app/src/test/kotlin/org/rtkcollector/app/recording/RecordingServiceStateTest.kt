package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

class RecordingServiceStateTest {
    @Test
    fun `clearBestSolutionFields removes stale position and best-solution display state`() {
        val cleared = RecordingServiceState(
            latDeg = 50.0,
            lonDeg = 14.0,
            latLon = "50.000000000, 14.000000000",
            ellipsoidalHeight = "300.000 m",
            altitude = "250.000 m",
            baseCandidateLatDeg = 50.0,
            baseCandidateLonDeg = 14.0,
            baseCandidateEllipsoidalHeightM = 300.0,
            baseCandidateMslAltitudeM = 250.0,
            horizontalAccuracy = "1.200 m",
            verticalAccuracy = "2.400 m",
            satellites = "12 / 18",
            satellitesUsed = 12,
            satellitesInView = 18,
            bestSolutionSource = "UBX-NAV-PVT",
            bestSolutionFix = "SINGLE",
            bestSolutionAgeMs = 500L,
            mockLocationState = "PUBLISHED",
            ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA 1/1/1/1/1/-/1 Hz",
        ).clearBestSolutionFields(
            mockLocationState = "Disabled",
            ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA -/-/-/-/-/-/- Hz",
        )

        assertNull(cleared.latDeg)
        assertNull(cleared.lonDeg)
        assertEquals("n/a", cleared.latLon)
        assertEquals("n/a", cleared.ellipsoidalHeight)
        assertEquals("n/a", cleared.altitude)
        assertNull(cleared.baseCandidateLatDeg)
        assertNull(cleared.baseCandidateLonDeg)
        assertNull(cleared.baseCandidateEllipsoidalHeightM)
        assertNull(cleared.baseCandidateMslAltitudeM)
        assertEquals("n/a", cleared.horizontalAccuracy)
        assertEquals("n/a", cleared.verticalAccuracy)
        assertEquals("n/a", cleared.satellites)
        assertNull(cleared.satellitesUsed)
        assertNull(cleared.satellitesInView)
        assertEquals("n/a", cleared.bestSolutionSource)
        assertEquals("n/a", cleared.bestSolutionFix)
        assertNull(cleared.bestSolutionAgeMs)
        assertEquals("Disabled", cleared.mockLocationState)
        assertEquals("Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA -/-/-/-/-/-/- Hz", cleared.ubloxFrequency)
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
    fun `best solution tick clears base candidate MSL when selected solution lacks MSL`() {
        val previous = RecordingServiceState(
            latDeg = 50.0,
            lonDeg = 14.0,
            latLon = "50.000000000, 14.000000000",
            ellipsoidalHeight = "300.000 m",
            altitude = "250.000 m",
            baseCandidateLatDeg = 50.0,
            baseCandidateLonDeg = 14.0,
            baseCandidateEllipsoidalHeightM = 300.0,
            baseCandidateMslAltitudeM = 250.0,
        )

        val updated = previous.applyBestSolutionDisplayDelta(
            delta = BestSolutionStateDelta(
                bestSolutionSource = "UM980-BESTNAV",
                bestSolutionFix = "RTK_FIXED",
                bestSolutionAgeMs = 20L,
                latDeg = 50.1,
                lonDeg = 14.1,
                ellipsoidalHeightM = 301.0,
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

        assertEquals("250.000 m", updated.altitude)
        assertEquals(50.1, updated.baseCandidateLatDeg)
        assertEquals(14.1, updated.baseCandidateLonDeg)
        assertEquals(301.0, updated.baseCandidateEllipsoidalHeightM)
        assertNull(updated.baseCandidateMslAltitudeM)
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
        assertNull(updated.baseCandidateLatDeg)
        assertNull(updated.baseCandidateLonDeg)
        assertNull(updated.baseCandidateEllipsoidalHeightM)
        assertNull(updated.baseCandidateMslAltitudeM)
        assertEquals("0.012 m", updated.horizontalAccuracy)
        assertEquals("0.030 m", updated.verticalAccuracy)
        assertEquals(4, updated.satellitesUsed)
        assertEquals(19, updated.satellitesInView)
        assertEquals("4 / 19", updated.satellites)
    }

    @Test
    fun `best solution delta propagates utc time`() {
        val state = RecordingServiceState()

        val updated = state.applyBestSolutionDisplayDelta(
            delta = BestSolutionStateDelta(
                bestSolutionSource = "UBX-NAV-PVT",
                bestSolutionFix = "DGPS",
                bestSolutionAgeMs = 10,
                latDeg = 50.0874512,
                lonDeg = 14.4212534,
                ellipsoidalHeightM = 287.423,
                mslAltitudeM = 243.812,
                horizontalAccuracyM = 0.8,
                verticalAccuracyM = 1.2,
                satellitesUsed = 14,
                satellitesInView = 18,
                utcTime = "2026-06-19T21:00:48Z",
                mockResult = org.rtkcollector.app.mocklocation.MockLocationPublishResult.PUBLISHED,
            ),
            ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA -/-/-/-/-/-/- Hz",
            formatLatLon = { lat, lon -> "$lat, $lon" },
            formatMeters = { "$it m" },
            formatSatellites = { used, view -> "$used/$view" },
        )

        assertEquals("2026-06-19T21:00:48Z", updated.utcTime)
    }

    @Test
    fun `selected solution updates summary without replacing richer direct telemetry`() {
        val previous = RecordingServiceState(
            latLon = "50.000000000, 14.000000000",
            horizontalAccuracy = "0.012 m",
            satellites = "19 / 32",
            satellitesUsed = 19,
            satellitesInView = 32,
        )
        val candidate = SolutionCandidate(
            sourceId = "UM980-BESTNAV",
            receiverFamily = "um980",
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = FixClass.RTK_FIXED,
            updatedAtMillis = 1_000L,
            latDeg = 50.1,
            lonDeg = 14.1,
            horizontalAccuracyM = null,
            satellitesUsed = null,
            satellitesInView = null,
        )

        val updated = previous.withSelectedSolution(candidate, nowMillis = 1_250L)

        assertEquals("UM980-BESTNAV", updated.bestSolutionSource)
        assertEquals("RTK_FIXED", updated.bestSolutionFix)
        assertEquals(250L, updated.bestSolutionAgeMs)
        assertEquals(50.1, updated.baseCandidateLatDeg)
        assertEquals(14.1, updated.baseCandidateLonDeg)
        assertNull(updated.baseCandidateEllipsoidalHeightM)
        assertNull(updated.baseCandidateMslAltitudeM)
        assertEquals("50.000000000, 14.000000000", updated.latLon)
        assertEquals("0.012 m", updated.horizontalAccuracy)
        assertEquals("19 / 32", updated.satellites)
    }
}
