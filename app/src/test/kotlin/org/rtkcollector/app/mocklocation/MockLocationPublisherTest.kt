package org.rtkcollector.app.mocklocation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.BestSolutionSnapshot
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionEngine

class MockLocationPublisherTest {
    @Test
    fun `publishes fresh snapshot when enabled`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)
        val result = publisher.publish(snapshot(), enabled = true)

        assertEquals(MockLocationPublishResult.PUBLISHED, result)
        assertEquals(1, sink.locations.size)
    }

    @Test
    fun `does not publish when disabled`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)
        val result = publisher.publish(snapshot(), enabled = false)

        assertEquals(MockLocationPublishResult.DISABLED, result)
        assertEquals(0, sink.locations.size)
    }

    private fun snapshot(): BestSolutionSnapshot =
        BestSolutionSnapshot(
            sourceId = "UBX-NAV-PVT",
            receiverFamily = "ublox-m8",
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = FixClass.SINGLE,
            updatedAtMillis = 1_000L,
            utcTime = null,
            latDeg = 50.0,
            lonDeg = 14.0,
            ellipsoidalHeightM = 300.0,
            mslAltitudeM = 250.0,
            horizontalAccuracyM = 1.2,
            verticalAccuracyM = 2.4,
            satellitesUsed = 12,
            satellitesInView = null,
            ageMillis = 500L,
        )
}
