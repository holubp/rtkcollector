package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteMonitorEngine
import org.rtkcollector.core.quality.SatelliteMonitorInputBatch
import org.rtkcollector.core.quality.SatelliteMonitorSource
import org.rtkcollector.core.quality.SatelliteSignalKey
import org.rtkcollector.core.quality.SatelliteSignalObservation

class SatelliteMonitorControllerTest {
    @Test
    fun `compact payload serializes boxed bar group counts for active in-device engine`() {
        val controller = SatelliteMonitorController()
        controller.offer(
            batch(
                source = SatelliteMonitorSource.ROVER,
                observations = listOf(
                    observation(SatelliteMonitorSource.ROVER, svid = 12, band = "L1"),
                    observation(SatelliteMonitorSource.ROVER, svid = 12, band = "L5"),
                ),
            ),
        )
        controller.offer(
            batch(
                source = SatelliteMonitorSource.BASE,
                observations = listOf(
                    observation(SatelliteMonitorSource.BASE, svid = 12, band = "L1"),
                    observation(SatelliteMonitorSource.BASE, svid = 20, band = "L1"),
                ),
            ),
        )
        controller.offer(
            batch(
                source = SatelliteMonitorSource.SOLUTION,
                observations = listOf(
                    observation(SatelliteMonitorSource.SOLUTION, svid = 12, band = "L1", used = true),
                ),
            ),
        )

        val payload = controller.compactPayload(
            engine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            nowEpochMillis = 10_000L,
        )

        assertEquals("In-device RTK", payload.engineLabel)
        assertEquals("R:FRESH;B:FRESH;S:FRESH", payload.sources)
        assertEquals("GPS|L1|1|1|1|2;GPS|L5|0|1|0|0", payload.groups)
        assertEquals("Live satellite monitor", payload.message)
    }

    @Test
    fun `compact payload follows selected engine and ignores other engine batches`() {
        val controller = SatelliteMonitorController()
        controller.offer(
            batch(
                engine = SatelliteMonitorEngine.IN_DEVICE_RTK,
                source = SatelliteMonitorSource.ROVER,
                observations = listOf(observation(SatelliteMonitorSource.ROVER, svid = 3, band = "L1")),
            ),
        )
        controller.offer(
            batch(
                engine = SatelliteMonitorEngine.RTKLIB,
                source = SatelliteMonitorSource.ROVER,
                observations = listOf(observation(SatelliteMonitorSource.ROVER, svid = 4, band = "L2")),
            ),
        )

        val payload = controller.compactPayload(
            engine = SatelliteMonitorEngine.RTKLIB,
            nowEpochMillis = 10_000L,
        )

        assertEquals("RTKLIB", payload.engineLabel)
        assertEquals("GPS|L2|0|1|0|0", payload.groups)
    }

    @Test
    fun `stale sources remain visible in source wire without blocking fresh rover bars`() {
        val controller = SatelliteMonitorController()
        controller.offer(
            batch(
                source = SatelliteMonitorSource.ROVER,
                receivedAt = 9_000L,
                observations = listOf(observation(SatelliteMonitorSource.ROVER, svid = 5, band = "L1")),
            ),
        )
        controller.offer(
            batch(
                source = SatelliteMonitorSource.BASE,
                receivedAt = 2_000L,
                observations = listOf(observation(SatelliteMonitorSource.BASE, svid = 5, band = "L1")),
            ),
        )

        val payload = controller.compactPayload(
            engine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            nowEpochMillis = 10_000L,
        )

        assertEquals("R:FRESH;B:STALE;S:UNAVAILABLE", payload.sources)
        assertEquals("GPS|L1|0|1|0|1", payload.groups)
    }

    @Test
    fun `compact payload discloses inferred per-frequency usage`() {
        val controller = SatelliteMonitorController()
        controller.offer(
            batch(
                source = SatelliteMonitorSource.ROVER,
                observations = listOf(
                    observation(SatelliteMonitorSource.ROVER, svid = 12, band = "L1"),
                    observation(SatelliteMonitorSource.ROVER, svid = 12, band = "L5"),
                ),
            ),
        )
        controller.offer(
            batch(
                source = SatelliteMonitorSource.SOLUTION,
                observations = listOf(
                    observation(
                        SatelliteMonitorSource.SOLUTION,
                        svid = 12,
                        band = SatelliteSignalKey.BAND_ANY,
                        used = true,
                    ),
                ),
            ),
        )

        val payload = controller.compactPayload(
            engine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            nowEpochMillis = 10_000L,
        )

        assertEquals("Per-frequency used counts inferred", payload.message)
    }

    @Test
    fun `clear removes retained satellite monitor batches`() {
        val controller = SatelliteMonitorController()
        controller.offer(
            batch(
                source = SatelliteMonitorSource.ROVER,
                observations = listOf(observation(SatelliteMonitorSource.ROVER, svid = 1, band = "L1")),
            ),
        )

        controller.clear()

        val payload = controller.compactPayload(
            engine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            nowEpochMillis = 10_000L,
        )

        assertEquals("R:UNAVAILABLE;B:UNAVAILABLE;S:UNAVAILABLE", payload.sources)
        assertEquals("", payload.groups)
    }

    private fun batch(
        engine: SatelliteMonitorEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
        source: SatelliteMonitorSource,
        receivedAt: Long = 9_900L,
        observations: List<SatelliteSignalObservation>,
    ): SatelliteMonitorInputBatch =
        SatelliteMonitorInputBatch(
            engine = engine,
            source = source,
            receivedAtEpochMillis = receivedAt,
            observations = observations,
        )

    private fun observation(
        source: SatelliteMonitorSource,
        svid: Int,
        band: String,
        used: Boolean = false,
    ): SatelliteSignalObservation =
        SatelliteSignalObservation(
            key = SatelliteSignalKey(
                constellation = SatelliteConstellation.GPS,
                svid = svid,
                band = band,
            ),
            source = source,
            observedAtEpochMillis = 9_900L,
            used = used,
        )
}
