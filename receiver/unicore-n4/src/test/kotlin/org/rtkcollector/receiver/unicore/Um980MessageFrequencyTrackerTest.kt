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

    @Test
    fun `receiver timestamp frequency ignores delayed processing time`() {
        val tracker = Um980MessageFrequencyTracker(windowMillis = 1_000)
        repeat(20) { index ->
            tracker.record(
                kind = Um980MessageKind.BESTNAV,
                timestampMillis = 100_000L + index * 200L,
                receiverTimestampMillis = 1_000L + index * 50L,
            )
        }

        assertEquals(
            "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/-/-/-/-/- Hz",
            tracker.display(timestampMillis = 104_000L, receiverTimestampMillis = 2_000L),
        )
    }
}
