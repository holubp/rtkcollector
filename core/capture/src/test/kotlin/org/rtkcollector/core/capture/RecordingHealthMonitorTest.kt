package org.rtkcollector.core.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingHealthMonitorTest {
    @Test
    fun `receiver stall is reported after threshold and repeated at bounded cadence`() {
        val monitor = RecordingHealthMonitor(
            receiverStallMillis = 10_000,
            correctionStallMillis = 20_000,
            repeatMillis = 5_000,
        )
        monitor.reset(nowMillis = 1_000)

        assertTrue(monitor.recordReceiverRead(byteCount = 0, nowMillis = 10_999).isEmpty())

        val first = monitor.recordReceiverRead(byteCount = 0, nowMillis = 11_000)
        assertEquals(listOf(RecordingHealthEvent.ReceiverRxStalled(10_000)), first)

        assertTrue(monitor.recordReceiverRead(byteCount = 0, nowMillis = 14_999).isEmpty())

        val repeated = monitor.recordReceiverRead(byteCount = 0, nowMillis = 16_000)
        assertEquals(listOf(RecordingHealthEvent.ReceiverRxStalled(15_000)), repeated)
    }

    @Test
    fun `receiver recovery is reported after a stall once bytes arrive`() {
        val monitor = RecordingHealthMonitor(receiverStallMillis = 1_000, repeatMillis = 1_000)
        monitor.reset(nowMillis = 0)

        monitor.recordReceiverRead(byteCount = 0, nowMillis = 1_000)
        val events = monitor.recordReceiverRead(byteCount = 3, nowMillis = 1_100)

        assertEquals(listOf(RecordingHealthEvent.ReceiverRxRecovered), events)
        assertTrue(monitor.recordReceiverRead(byteCount = 0, nowMillis = 1_500).isEmpty())
    }

    @Test
    fun `correction stall is only reported when corrections are expected`() {
        val monitor = RecordingHealthMonitor(correctionStallMillis = 10_000, repeatMillis = 5_000)
        monitor.reset(nowMillis = 5_000)

        assertTrue(monitor.checkCorrections(nowMillis = 20_000, correctionsExpected = false).isEmpty())

        val events = monitor.checkCorrections(nowMillis = 20_000, correctionsExpected = true)

        assertEquals(listOf(RecordingHealthEvent.CorrectionsStalled(15_000)), events)
    }

    @Test
    fun `correction recovery is reported after stalled corrections resume`() {
        val monitor = RecordingHealthMonitor(correctionStallMillis = 1_000, repeatMillis = 1_000)
        monitor.reset(nowMillis = 0)

        monitor.checkCorrections(nowMillis = 1_000, correctionsExpected = true)
        val events = monitor.recordCorrectionBytes(byteCount = 10, nowMillis = 1_100)

        assertEquals(listOf(RecordingHealthEvent.CorrectionsRecovered), events)
    }
}
