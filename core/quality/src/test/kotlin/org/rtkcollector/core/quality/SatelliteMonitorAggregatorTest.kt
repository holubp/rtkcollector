package org.rtkcollector.core.quality

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SatelliteMonitorAggregatorTest {
    private val aggregator = SatelliteMonitorAggregator(
        freshSourceMillis = 2_500L,
        staleSourceMillis = 10_000L,
    )

    @Test
    fun `summarizes explicit used and visible signals by constellation and band`() {
        val snapshot = aggregator.summarize(
            engine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            nowEpochMillis = 10_000L,
            batches = listOf(
                batch(
                    source = SatelliteMonitorSource.ROVER,
                    observations = listOf(
                        rover(svid = 12, band = "L1", cn0 = 42.0),
                        rover(svid = 12, band = "L5", cn0 = 38.0),
                    ),
                ),
                batch(
                    source = SatelliteMonitorSource.BASE,
                    observations = listOf(
                        base(svid = 12, band = "L1", cn0 = 39.0),
                        base(svid = 12, band = "L5", cn0 = 35.0),
                    ),
                ),
                batch(
                    source = SatelliteMonitorSource.SOLUTION,
                    observations = listOf(solution(svid = 12, band = "L1")),
                ),
            ),
        )

        val l1 = snapshot.summary(SatelliteConstellation.GPS, "L1")
        assertEquals(1, l1.roverVisible)
        assertEquals(1, l1.roverUsed)
        assertEquals(1, l1.baseVisible)
        assertEquals(1, l1.baseUsed)
        assertEquals(42.0, l1.roverAverageCn0DbHz)
        assertEquals(39.0, l1.baseAverageCn0DbHz)

        val l5 = snapshot.summary(SatelliteConstellation.GPS, "L5")
        assertEquals(1, l5.roverVisible)
        assertEquals(0, l5.roverUsed)
        assertEquals(1, l5.baseVisible)
        assertEquals(0, l5.baseUsed)
        assertTrue(snapshot.diagnostics.isEmpty())
    }

    @Test
    fun `counts duplicate satellite band observations once`() {
        val snapshot = aggregator.summarize(
            engine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            nowEpochMillis = 10_000L,
            batches = listOf(
                batch(
                    source = SatelliteMonitorSource.ROVER,
                    observations = listOf(
                        rover(svid = 5, band = "L1", cn0 = 35.0, observedAt = 9_900L),
                        rover(svid = 5, band = "L1", cn0 = 37.0, observedAt = 9_950L),
                    ),
                ),
            ),
        )

        val l1 = snapshot.summary(SatelliteConstellation.GPS, "L1")
        assertEquals(1, l1.roverVisible)
        assertEquals(37.0, l1.roverAverageCn0DbHz)
    }

    @Test
    fun `expires stale base data independently from rover data`() {
        val snapshot = aggregator.summarize(
            engine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            nowEpochMillis = 30_000L,
            batches = listOf(
                batch(
                    source = SatelliteMonitorSource.ROVER,
                    receivedAt = 29_000L,
                    observations = listOf(rover(svid = 7, band = "L1")),
                ),
                batch(
                    source = SatelliteMonitorSource.BASE,
                    receivedAt = 10_000L,
                    observations = listOf(base(svid = 7, band = "L1")),
                ),
            ),
        )

        val l1 = snapshot.summary(SatelliteConstellation.GPS, "L1")
        assertEquals(1, l1.roverVisible)
        assertEquals(0, l1.baseVisible)
        assertEquals(SatelliteMonitorSourceState.FRESH, snapshot.sourceFreshness.getValue(SatelliteMonitorSource.ROVER).state)
        assertEquals(SatelliteMonitorSourceState.UNAVAILABLE, snapshot.sourceFreshness.getValue(SatelliteMonitorSource.BASE).state)
    }

    @Test
    fun `reports zero used when solution source is unavailable`() {
        val snapshot = aggregator.summarize(
            engine = SatelliteMonitorEngine.RTKLIB,
            nowEpochMillis = 10_000L,
            batches = listOf(
                batch(
                    engine = SatelliteMonitorEngine.RTKLIB,
                    source = SatelliteMonitorSource.ROVER,
                    observations = listOf(rover(svid = 9, band = "L1", engine = SatelliteMonitorEngine.RTKLIB)),
                ),
            ),
        )

        val l1 = snapshot.summary(SatelliteConstellation.GPS, "L1")
        assertEquals(1, l1.roverVisible)
        assertEquals(0, l1.roverUsed)
        assertEquals(SatelliteMonitorSourceState.UNAVAILABLE, snapshot.sourceFreshness.getValue(SatelliteMonitorSource.SOLUTION).state)
        assertNull(snapshot.message)
    }

    @Test
    fun `satellite level solution usage marks observed bands and emits diagnostic`() {
        val snapshot = aggregator.summarize(
            engine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            nowEpochMillis = 10_000L,
            batches = listOf(
                batch(
                    source = SatelliteMonitorSource.ROVER,
                    observations = listOf(
                        rover(svid = 12, band = "L1"),
                        rover(svid = 12, band = "L5"),
                        rover(svid = 13, band = "L1"),
                    ),
                ),
                batch(
                    source = SatelliteMonitorSource.SOLUTION,
                    observations = listOf(solution(svid = 12, band = SatelliteSignalKey.BAND_ANY)),
                ),
            ),
        )

        assertEquals(1, snapshot.summary(SatelliteConstellation.GPS, "L1").roverUsed)
        assertEquals(1, snapshot.summary(SatelliteConstellation.GPS, "L5").roverUsed)
        assertTrue(snapshot.diagnostics.contains(SatelliteMonitorAggregator.PARTIAL_USAGE_FREQUENCY_INFERRED))
    }

    @Test
    fun `matches constellation specific receiver labels against generic solution bands`() {
        val snapshot = aggregator.summarize(
            engine = SatelliteMonitorEngine.RTKLIB,
            nowEpochMillis = 10_000L,
            batches = listOf(
                batch(
                    engine = SatelliteMonitorEngine.RTKLIB,
                    source = SatelliteMonitorSource.ROVER,
                    observations = listOf(
                        signal(
                            engine = SatelliteMonitorEngine.RTKLIB,
                            source = SatelliteMonitorSource.ROVER,
                            constellation = SatelliteConstellation.GALILEO,
                            svid = 11,
                            band = "E5A",
                            used = false,
                        ),
                        signal(
                            engine = SatelliteMonitorEngine.RTKLIB,
                            source = SatelliteMonitorSource.ROVER,
                            constellation = SatelliteConstellation.BEIDOU,
                            svid = 23,
                            band = "B2",
                            signalCode = "B2",
                            used = false,
                        ),
                    ),
                ),
                batch(
                    engine = SatelliteMonitorEngine.RTKLIB,
                    source = SatelliteMonitorSource.SOLUTION,
                    observations = listOf(
                        signal(
                            engine = SatelliteMonitorEngine.RTKLIB,
                            source = SatelliteMonitorSource.SOLUTION,
                            constellation = SatelliteConstellation.GALILEO,
                            svid = 11,
                            band = "L5",
                            signalCode = "5X",
                            used = true,
                        ),
                        signal(
                            engine = SatelliteMonitorEngine.RTKLIB,
                            source = SatelliteMonitorSource.SOLUTION,
                            constellation = SatelliteConstellation.BEIDOU,
                            svid = 23,
                            band = "L7",
                            signalCode = "7I",
                            used = true,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, snapshot.summary(SatelliteConstellation.GALILEO, "L5").roverUsed)
        assertEquals(1, snapshot.summary(SatelliteConstellation.BEIDOU, "L7").roverUsed)
    }

    private fun SatelliteMonitorSnapshot.summary(
        constellation: SatelliteConstellation,
        band: String,
    ): SatelliteFrequencySummary =
        summaries.single { it.constellation == constellation && it.band == band }

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

    private fun rover(
        svid: Int,
        band: String,
        cn0: Double? = null,
        observedAt: Long = 9_900L,
        engine: SatelliteMonitorEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
    ): SatelliteSignalObservation =
        signal(
            engine = engine,
            source = SatelliteMonitorSource.ROVER,
            svid = svid,
            band = band,
            cn0 = cn0,
            observedAt = observedAt,
            used = false,
        )

    private fun base(
        svid: Int,
        band: String,
        cn0: Double? = null,
    ): SatelliteSignalObservation =
        signal(
            source = SatelliteMonitorSource.BASE,
            svid = svid,
            band = band,
            cn0 = cn0,
            used = false,
        )

    private fun solution(
        svid: Int,
        band: String,
    ): SatelliteSignalObservation =
        signal(
            source = SatelliteMonitorSource.SOLUTION,
            svid = svid,
            band = band,
            used = true,
        )

    @Suppress("UNUSED_PARAMETER")
    private fun signal(
        engine: SatelliteMonitorEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
        source: SatelliteMonitorSource,
        constellation: SatelliteConstellation = SatelliteConstellation.GPS,
        svid: Int,
        band: String,
        signalCode: String? = null,
        cn0: Double? = null,
        observedAt: Long = 9_900L,
        used: Boolean,
    ): SatelliteSignalObservation =
        SatelliteSignalObservation(
            key = SatelliteSignalKey(
                constellation = constellation,
                svid = svid,
                band = band,
                signalCode = signalCode,
            ),
            source = source,
            observedAtEpochMillis = observedAt,
            cn0DbHz = cn0,
            used = used,
        )
}
