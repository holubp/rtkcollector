package org.rtkcollector.app.mocklocation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.rtkcollector.core.solution.BestSolutionSnapshot
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionEngine
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MockLocationPublisherTest {
    @Test
    fun `publishes fresh snapshot when enabled`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)

        val result = publisher.publish(snapshot(), enabled = true)

        assertEquals(MockLocationPublishResult.PUBLISHED, result)
        assertEquals(1, sink.locations.size)
        val update = sink.locations.single()
        assertEquals(50.0, update.latDeg)
        assertEquals(14.0, update.lonDeg)
        assertEquals(300.0, update.altitudeM)
        assertEquals(250.0, update.mslAltitudeM)
        assertEquals(1.2f, update.horizontalAccuracyM)
        assertEquals(2.4f, update.verticalAccuracyM)
        assertEquals(1_000L, update.timeMillis)
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
    fun `security setup failure message points to mock app selection`() {
        val message = mockLocationSetupFailureMessage(SecurityException("denied"))

        assertEquals(
            "RtkCollector is not the selected mock location app. Enable it in Developer Options.",
            message,
        )
    }

    @Test
    fun `generic setup failure message preserves provider cause`() {
        val message = mockLocationSetupFailureMessage(IllegalStateException("provider exists"))

        assertEquals("Android mock-location provider setup failed: provider exists", message)
    }

    @Test
    fun `publishes ellipsoidal height as Android location altitude`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)

        publisher.publish(snapshot(), enabled = true)

        assertEquals(300.0, sink.locations.single().altitudeM)
    }

    @Test
    fun `omits altitude when ellipsoidalHeightM is null`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)
        val noEllipsoid = snapshot().copy(mslAltitudeM = 250.0, ellipsoidalHeightM = null)

        publisher.publish(noEllipsoid, enabled = true)

        assertNull(sink.locations.single().altitudeM)
    }

    @Test
    fun `carries satellite counts as best effort mock location extras`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)

        publisher.publish(snapshot().copy(satellitesUsed = 12, satellitesInView = 18), enabled = true)

        val update = sink.locations.single()
        assertEquals(12, update.satellitesUsed)
        assertEquals(18, update.satellitesInView)
    }

    @Test
    fun `omits optional vertical and msl fields when unavailable`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)
        val partial = snapshot().copy(mslAltitudeM = null, verticalAccuracyM = null)

        publisher.publish(partial, enabled = true)

        val update = sink.locations.single()
        assertNull(update.mslAltitudeM)
        assertNull(update.verticalAccuracyM)
    }

    @Test
    fun `mock location extras include common satellite count aliases`() {
        val extras = mockLocationExtras(
            MockLocationUpdate(
                latDeg = 50.0,
                lonDeg = 14.0,
                altitudeM = 300.0,
                mslAltitudeM = 250.0,
                horizontalAccuracyM = 1.2f,
                verticalAccuracyM = 2.4f,
                timeMillis = 1_000L,
                satellitesUsed = 12,
                satellitesInView = 18,
            ),
        )

        assertNotNull(extras)
        assertEquals(12, extras!!.getInt("satellites"))
        assertEquals(12, extras.getInt("satellitesUsed"))
        assertEquals(12, extras.getInt("satellitesInUse"))
        assertEquals(18, extras.getInt("satellitesInView"))
        assertEquals(18, extras.getInt("satellitesVisible"))
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
