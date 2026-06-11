package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Um980MessageFrequencyTrackerTest {
    @Test
    fun `reports observed rates and missing sources`() {
        val tracker = Um980MessageFrequencyTracker(windowMillis = 1_000)
        repeat(20) { tracker.record(Um980MessageKind.GGA, timestampMillis = 1_000L + it * 50L) }
        tracker.record(Um980MessageKind.BESTNAV, timestampMillis = 1_100L)

        val display = tracker.display(timestampMillis = 2_000L)

        assertEquals("Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 1/20/-/-/-/- Hz", display)
    }

    @Test
    fun `old samples age out`() {
        val tracker = Um980MessageFrequencyTracker(windowMillis = 1_000)
        tracker.record(Um980MessageKind.GGA, timestampMillis = 0L)

        assertEquals("Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM -/-/-/-/-/- Hz", tracker.display(2_000L))
    }
}
