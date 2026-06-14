package org.rtkcollector.app.mocklocation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
        assertNull(publisher.lastFailure)
    }

    @Test
    fun `does not publish when disabled`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)

        val result = publisher.publish(snapshot(), enabled = false)

        assertEquals(MockLocationPublishResult.DISABLED, result)
        assertEquals(0, sink.locations.size)
    }

    @Test
    fun `returns STALE when snapshot is null`() {
        val publisher = MockLocationPublisher(FakeMockLocationSink())

        assertEquals(MockLocationPublishResult.STALE, publisher.publish(null, enabled = true))
    }

    @Test
    fun `returns STALE when snapshot is not fresh`() {
        val publisher = MockLocationPublisher(FakeMockLocationSink())
        val stale = snapshot().copy(ageMillis = 10_000L)

        assertEquals(MockLocationPublishResult.STALE, publisher.publish(stale, enabled = true))
    }

    @Test
    fun `records lastFailure when sink throws`() {
        val publisher = MockLocationPublisher(ThrowingMockLocationSink(IllegalStateException("boom")))

        val result = publisher.publish(snapshot(), enabled = true)

        assertEquals(MockLocationPublishResult.FAILED, result)
        assertNotNull(publisher.lastFailure)
        assertEquals("boom", publisher.lastFailure?.message)
    }

    @Test
    fun `omits altitude when mslAltitudeM is null`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)
        val noMsl = snapshot().copy(mslAltitudeM = null, ellipsoidalHeightM = 300.0)

        publisher.publish(noMsl, enabled = true)

        assertNull(sink.locations.single().altitudeM)
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

    private class ThrowingMockLocationSink(private val error: Throwable) : MockLocationSink {
        override fun publish(update: MockLocationUpdate) {
            throw error
        }
    }
}
