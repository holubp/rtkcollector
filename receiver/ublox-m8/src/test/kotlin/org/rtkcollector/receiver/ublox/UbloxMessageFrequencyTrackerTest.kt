package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UbloxMessageFrequencyTrackerTest {
    @Test
    fun `formats compact frequency line for one second window`() {
        val tracker = UbloxMessageFrequencyTracker()
        tracker.record(UbloxMessageKind.RAWX, 2_100L)
        tracker.record(UbloxMessageKind.RAWX, 2_600L)
        tracker.record(UbloxMessageKind.NAV_PVT, 2_500L)

        assertEquals(
            "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA 2/-/-/1/- Hz",
            tracker.display(nowMillis = 3_000L),
        )
    }

    @Test
    fun `header is built from UbloxMessageKind labels`() {
        // Adding GGA support means the header is derived from enum labels.
        val expectedHeader = UbloxMessageKind.entries.joinToString("/") { it.label }
        val tracker = UbloxMessageFrequencyTracker()

        val line = tracker.display(nowMillis = 0L)

        assertEquals("Frequency $expectedHeader -/-/-/-/- Hz", line)
    }

    @Test
    fun `excludes samples at or beyond window boundary`() {
        val tracker = UbloxMessageFrequencyTracker()
        tracker.record(UbloxMessageKind.GGA, 1_000L)
        tracker.record(UbloxMessageKind.GGA, 2_000L)

        // window=1000; at nowMillis=3000 the only included GGA sample is the one
        // at 2000 (3000 - 2000 = 1000 is NOT < 1000). So GGA count is zero.
        assertEquals(
            "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA -/-/-/-/- Hz",
            tracker.display(nowMillis = 3_000L),
        )
    }
}
