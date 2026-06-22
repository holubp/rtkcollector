package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UbloxMessageFrequencyTrackerTest {
    @Test
    fun `formats compact smoothed frequency line`() {
        val tracker = UbloxMessageFrequencyTracker()
        tracker.record(UbloxMessageKind.RAWX, 2_100L)
        tracker.record(UbloxMessageKind.RAWX, 2_600L)
        tracker.record(UbloxMessageKind.NAV_PVT, 2_500L)

        assertEquals(
            "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA 2/-/-/-/-/-/- Hz",
            tracker.display(nowMillis = 3_000L),
        )
    }

    @Test
    fun `header is built from UbloxMessageKind labels`() {
        // Adding GGA support means the header is derived from enum labels.
        val expectedHeader = UbloxMessageKind.entries.joinToString("/") { it.label }
        val tracker = UbloxMessageFrequencyTracker()

        val line = tracker.display(nowMillis = 0L)

        assertEquals("Frequency $expectedHeader -/-/-/-/-/-/- Hz", line)
    }

    @Test
    fun `smooths bursty message rates over a longer window`() {
        val tracker = UbloxMessageFrequencyTracker()
        repeat(21) { index ->
            tracker.record(UbloxMessageKind.SFRBX, 1_000L + index * 200L)
        }

        assertEquals(
            "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA -/5/-/-/-/-/- Hz",
            tracker.display(nowMillis = 5_000L),
        )
    }

    @Test
    fun `excludes samples outside smoothing window`() {
        val tracker = UbloxMessageFrequencyTracker()
        tracker.record(UbloxMessageKind.GGA, 1_000L)
        tracker.record(UbloxMessageKind.GGA, 2_000L)

        // window=5000; at nowMillis=7001 both samples are outside the window.
        assertEquals(
            "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA -/-/-/-/-/-/- Hz",
            tracker.display(nowMillis = 7_001L),
        )
    }
}
