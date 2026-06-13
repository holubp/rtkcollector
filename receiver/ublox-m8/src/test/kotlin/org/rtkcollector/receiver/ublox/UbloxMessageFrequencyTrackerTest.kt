package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UbloxMessageFrequencyTrackerTest {
    @Test
    fun `formats compact frequency line`() {
        val tracker = UbloxMessageFrequencyTracker()
        tracker.record(UbloxMessageKind.RAWX, 1_000L)
        tracker.record(UbloxMessageKind.RAWX, 2_000L)
        tracker.record(UbloxMessageKind.NAV_PVT, 1_500L)
        tracker.record(UbloxMessageKind.NAV_PVT, 2_500L)

        assertEquals("Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA 1/-/-/1/- Hz", tracker.display(3_000L))
    }
}
