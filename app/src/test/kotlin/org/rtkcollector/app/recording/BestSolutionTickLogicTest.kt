package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.mocklocation.MockLocationPublishResult
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine
import org.rtkcollector.core.solution.SolutionSourcePolicy

class BestSolutionTickLogicTest {
    @Test
    fun `default mock location publish period is one hertz`() {
        assertEquals(1_000L, RecordingForegroundService.DEFAULT_MOCK_LOCATION_PUBLISH_PERIOD_MILLIS)
    }

    @Test
    fun `mock location publish period follows supported rate and sanitizes unsupported rates`() {
        assertEquals(1_000L, mockLocationPublishPeriodMillis(1))
        assertEquals(500L, mockLocationPublishPeriodMillis(2))
        assertEquals(200L, mockLocationPublishPeriodMillis(5))
        assertEquals(100L, mockLocationPublishPeriodMillis(10))
        assertEquals(1_000L, mockLocationPublishPeriodMillis(3))
    }

    @Test
    fun `empty candidate set yields STALE and nothing to publish`() {
        val out = BestSolutionTickLogic.compute(
            input(candidates = emptyList(), mockEnabled = true),
        )

        assertEquals("n/a", out.stateDelta.bestSolutionSource)
        assertEquals("n/a", out.stateDelta.bestSolutionFix)
        assertNull(out.stateDelta.bestSolutionAgeMs)
        assertEquals(MockLocationPublishResult.STALE, out.stateDelta.mockResult)
        assertTrue(out.publishAction is PublishAction.None)
        assertNull(out.newLastMockPublishedAt)
        assertEquals(MockLocationPublishResult.STALE, out.newPreviousMockResult)
    }

    @Test
    fun `fresh candidate yields publish action when mock enabled`() {
        val candidate = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 1_000L)

        val out = BestSolutionTickLogic.compute(
            input(candidates = listOf(candidate), nowMillis = 1_500L, mockEnabled = true),
        )

        assertEquals("UBX-NAV-PVT", out.stateDelta.bestSolutionSource)
        assertEquals("SINGLE", out.stateDelta.bestSolutionFix)
        assertEquals(500L, out.stateDelta.bestSolutionAgeMs)
        assertEquals(50.0, out.stateDelta.latDeg)
        assertEquals(14.0, out.stateDelta.lonDeg)
        assertEquals(300.0, out.stateDelta.ellipsoidalHeightM)
        assertEquals(250.0, out.stateDelta.mslAltitudeM)
        assertEquals(12, out.stateDelta.satellitesUsed)
        assertTrue(out.publishAction is PublishAction.Publish)
        assertEquals(1_000L, (out.publishAction as PublishAction.Publish).snapshot.updatedAtMillis)
    }

    @Test
    fun `screen and mock policy can select different solution sources`() {
        val device = candidate("UM980-BESTNAV", FixClass.DGPS, updatedAtMillis = 1_000L)
        val rtklib = candidate(
            sourceId = "RTKLIB",
            fixClass = FixClass.RTK_FIXED,
            updatedAtMillis = 1_000L,
            engine = SolutionEngine.RTKLIB_REALTIME,
        )

        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(device, rtklib),
                nowMillis = 1_500L,
                mockEnabled = true,
            ).copy(
                screenPolicy = SolutionSourcePolicy.DEVICE_INTERNAL_ONLY,
                mockPolicy = SolutionSourcePolicy.RTKLIB_ONLY,
            ),
        )

        assertEquals("UM980-BESTNAV", out.stateDelta.bestSolutionSource)
        assertEquals("RTKLIB", (out.publishAction as PublishAction.Publish).snapshot.sourceId)
    }

    @Test
    fun `rtklib only mock policy publishes fresh rtklib candidate`() {
        val device = candidate("UBX-NAV-PVT", FixClass.DGPS, updatedAtMillis = 1_000L)
        val rtklib = candidate(
            sourceId = "RTKLIB",
            fixClass = FixClass.RTK_FLOAT,
            updatedAtMillis = 1_200L,
            engine = SolutionEngine.RTKLIB_REALTIME,
        )

        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(device, rtklib),
                nowMillis = 1_500L,
                mockEnabled = true,
            ).copy(mockPolicy = SolutionSourcePolicy.RTKLIB_ONLY),
        )

        val publish = out.publishAction as PublishAction.Publish
        assertEquals("RTKLIB", publish.snapshot.sourceId)
        assertEquals(SolutionEngine.RTKLIB_REALTIME, publish.snapshot.engine)
        assertEquals(FixClass.RTK_FLOAT, publish.snapshot.fixClass)
    }

    @Test
    fun `rtklib only mock policy does not fall back to device candidate`() {
        val device = candidate("UBX-NAV-PVT", FixClass.DGPS, updatedAtMillis = 1_000L)

        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(device),
                nowMillis = 1_500L,
                mockEnabled = true,
            ).copy(mockPolicy = SolutionSourcePolicy.RTKLIB_ONLY),
        )

        assertEquals(MockLocationPublishResult.STALE, out.stateDelta.mockResult)
        assertTrue(out.publishAction is PublishAction.None)
    }

    @Test
    fun `candidate display delta carries position and quality for direct monitoring`() {
        val candidate = candidate(
            sourceId = "UBX-NAV-PVT",
            fixClass = FixClass.SINGLE,
            updatedAtMillis = 1_000L,
            horizontalAccuracyM = 0.9,
            satellitesInView = 14,
        )

        val delta = BestSolutionTickLogic.stateDeltaForCandidate(
            candidate = candidate,
            nowMillis = 1_250L,
            mockResult = MockLocationPublishResult.DISABLED,
        )

        assertEquals("UBX-NAV-PVT", delta.bestSolutionSource)
        assertEquals("SINGLE", delta.bestSolutionFix)
        assertEquals(250L, delta.bestSolutionAgeMs)
        assertEquals(50.0, delta.latDeg)
        assertEquals(14.0, delta.lonDeg)
        assertEquals(300.0, delta.ellipsoidalHeightM)
        assertEquals(0.9, delta.horizontalAccuracyM)
        assertEquals(12, delta.satellitesUsed)
        assertEquals(14, delta.satellitesInView)
    }

    @Test
    fun `mock disabled computes no publish action and no required screen update`() {
        val candidate = candidate("UM980-BESTNAV", FixClass.RTK_FIXED, updatedAtMillis = 1_000L)

        val out = BestSolutionTickLogic.compute(
            input(candidates = listOf(candidate), nowMillis = 1_500L, mockEnabled = false),
        )

        assertEquals(MockLocationPublishResult.DISABLED, out.stateDelta.mockResult)
        assertTrue(out.publishAction is PublishAction.None)
        assertNull(out.newLastMockPublishedAt)
    }

    @Test
    fun `stale best solution clears coordinate delta`() {
        val out = BestSolutionTickLogic.compute(
            input(candidates = emptyList(), nowMillis = 1_500L, mockEnabled = true),
        )

        assertEquals("n/a", out.stateDelta.bestSolutionSource)
        assertEquals("n/a", out.stateDelta.bestSolutionFix)
        assertNull(out.stateDelta.bestSolutionAgeMs)
        assertNull(out.stateDelta.latDeg)
        assertNull(out.stateDelta.lonDeg)
        assertNull(out.stateDelta.ellipsoidalHeightM)
        assertNull(out.stateDelta.mslAltitudeM)
        assertNull(out.stateDelta.horizontalAccuracyM)
        assertNull(out.stateDelta.verticalAccuracyM)
        assertNull(out.stateDelta.satellitesUsed)
    }

    @Test
    fun `same updatedAt as lastPublished does not republish`() {
        val candidate = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 1_000L)

        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(candidate),
                nowMillis = 1_500L,
                mockEnabled = true,
                lastMockPublishedAt = 1_000L,
                lastMockPublishedIdentity = "UBX-NAV-PVT|test|DEVICE_INTERNAL",
                previousMockResult = MockLocationPublishResult.PUBLISHED,
            ),
        )

        assertTrue(out.publishAction is PublishAction.None)
        assertEquals(MockLocationPublishResult.PUBLISHED, out.stateDelta.mockResult)
        assertEquals(1_000L, out.newLastMockPublishedAt)
        assertEquals("UBX-NAV-PVT|test|DEVICE_INTERNAL", out.newLastMockPublishedIdentity)
    }

    @Test
    fun `same updatedAt with different selected candidate republishes`() {
        val candidate = candidate("UM980-BESTNAV", FixClass.RTK_FIXED, updatedAtMillis = 1_000L)

        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(candidate),
                nowMillis = 1_500L,
                mockEnabled = true,
                lastMockPublishedAt = 1_000L,
                lastMockPublishedIdentity = "UBX-NAV-PVT|test|DEVICE_INTERNAL",
                previousMockResult = MockLocationPublishResult.PUBLISHED,
            ),
        )

        assertTrue(out.publishAction is PublishAction.Publish)
        val applied = BestSolutionTickLogic.applyPublishResult(
            previous = out,
            publishedResult = MockLocationPublishResult.PUBLISHED,
            publishedAtMillis = (out.publishAction as PublishAction.Publish).snapshot.updatedAtMillis,
        )
        assertEquals("UM980-BESTNAV|test|DEVICE_INTERNAL", applied.newLastMockPublishedIdentity)
    }

    @Test
    fun `successful publish reports wall clock interval between last two mock updates`() {
        val candidate = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 2_000L)
        val previous = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(candidate),
                nowMillis = 2_100L,
                mockEnabled = true,
                lastMockPublishWallClockAtMillis = 5_000L,
            ),
        )

        val applied = BestSolutionTickLogic.applyPublishResult(
            previous = previous,
            publishedResult = MockLocationPublishResult.PUBLISHED,
            publishedAtMillis = 2_000L,
            publishedWallClockAtMillis = 5_250L,
        )

        assertEquals(250L, applied.lastMockPublishIntervalMillis)
        assertEquals(5_250L, applied.newLastMockPublishWallClockAtMillis)
    }

    @Test
    fun `transition into FAILED sets lastError once`() {
        val candidate = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 1_500L)

        val out = BestSolutionTickLogic.applyPublishResult(
            previous = previousState(
                candidates = listOf(candidate),
                nowMillis = 2_000L,
                mockEnabled = true,
                lastMockPublishedAt = 1_000L,
                previousMockResult = MockLocationPublishResult.PUBLISHED,
            ),
            publishedResult = MockLocationPublishResult.FAILED,
            publishedAtMillis = null,
        )

        assertEquals(MockLocationPublishResult.FAILED, out.mockResult)
        assertEquals(true, out.setLastError)
        assertEquals(1_000L, out.newLastMockPublishedAt)
        assertNull(out.newLastMockPublishedIdentity)
    }

    @Test
    fun `failed publish does not mark candidate as published so next tick retries`() {
        val candidate = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 1_500L)
        val previous = previousState(
            candidates = listOf(candidate),
            nowMillis = 2_000L,
            mockEnabled = true,
            lastMockPublishedAt = 1_000L,
            lastMockPublishedIdentity = "OLD|test|DEVICE_INTERNAL",
            previousMockResult = MockLocationPublishResult.PUBLISHED,
        )
        val failed = BestSolutionTickLogic.applyPublishResult(
            previous = previous,
            publishedResult = MockLocationPublishResult.FAILED,
            publishedAtMillis = null,
        )

        val retry = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(candidate),
                nowMillis = 2_500L,
                mockEnabled = true,
                lastMockPublishedAt = failed.newLastMockPublishedAt,
                lastMockPublishedIdentity = failed.newLastMockPublishedIdentity,
                previousMockResult = MockLocationPublishResult.FAILED,
            ),
        )

        assertTrue(retry.publishAction is PublishAction.Publish)
    }

    @Test
    fun `repeated FAILED does not re-set lastError`() {
        val out = BestSolutionTickLogic.applyPublishResult(
            previous = previousState(
                candidates = emptyList(),
                nowMillis = 0L,
                mockEnabled = true,
                lastMockPublishedAt = null,
                previousMockResult = MockLocationPublishResult.FAILED,
            ),
            publishedResult = MockLocationPublishResult.FAILED,
            publishedAtMillis = null,
        )

        assertEquals(false, out.setLastError)
    }

    @Test
    fun `NOT_PERMITTED preserves enable state and skips publish`() {
        val candidate = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 1_000L)
        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(candidate),
                nowMillis = 1_500L,
                mockEnabled = true,
                previousMockResult = MockLocationPublishResult.NOT_PERMITTED,
                mockProviderAvailable = false,
            ),
        )

        assertTrue(out.publishAction is PublishAction.None)
        assertEquals(MockLocationPublishResult.NOT_PERMITTED, out.stateDelta.mockResult)
    }

    @Test
    fun `selector ignores stale best state while accepting fresh replacement`() {
        val fresh = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 5_100L, satellitesInView = 14)
        val stale = candidate("UM980-BESTNAV", FixClass.RTK_FIXED, updatedAtMillis = 4_999L, satellitesInView = 30)

        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(fresh, stale),
                nowMillis = 10_000L,
                mockEnabled = false,
            ),
        )

        // BestSolutionSelector filters stale candidates; the fresh one wins
        // even though it ranks lower than the stale RTK_FIXED.
        assertEquals("UBX-NAV-PVT", out.stateDelta.bestSolutionSource)
        assertEquals(4_900L, out.stateDelta.bestSolutionAgeMs)
        assertEquals(14, out.stateDelta.satellitesInView)
    }

    private fun input(
        candidates: Collection<SolutionCandidate>,
        nowMillis: Long = 1_000L,
        mockEnabled: Boolean,
        lastMockPublishedAt: Long? = null,
        lastMockPublishedIdentity: String? = null,
        lastMockPublishWallClockAtMillis: Long? = null,
        previousMockResult: MockLocationPublishResult? = null,
        mockProviderAvailable: Boolean = true,
    ): BestSolutionTickInput = BestSolutionTickInput(
        candidates = candidates,
        nowMillis = nowMillis,
        mockEnabled = mockEnabled,
        mockProviderAvailable = mockProviderAvailable,
        lastMockPublishedAt = lastMockPublishedAt,
        lastMockPublishedIdentity = lastMockPublishedIdentity,
        lastMockPublishWallClockAtMillis = lastMockPublishWallClockAtMillis,
        previousMockResult = previousMockResult,
    )

    private fun previousState(
        candidates: Collection<SolutionCandidate>,
        nowMillis: Long,
        mockEnabled: Boolean,
        lastMockPublishedAt: Long?,
        lastMockPublishedIdentity: String? = null,
        previousMockResult: MockLocationPublishResult?,
    ): BestSolutionTickOutput = BestSolutionTickLogic.compute(
        input(
            candidates = candidates,
            nowMillis = nowMillis,
            mockEnabled = mockEnabled,
            lastMockPublishedAt = lastMockPublishedAt,
            lastMockPublishedIdentity = lastMockPublishedIdentity,
            previousMockResult = previousMockResult,
        ),
    )

    private fun candidate(
        sourceId: String,
        fixClass: FixClass,
        updatedAtMillis: Long,
        horizontalAccuracyM: Double? = null,
        satellitesInView: Int? = null,
        engine: SolutionEngine = SolutionEngine.DEVICE_INTERNAL,
    ): SolutionCandidate = SolutionCandidate(
        sourceId = sourceId,
        receiverFamily = "test",
        engine = engine,
        fixClass = fixClass,
        updatedAtMillis = updatedAtMillis,
        latDeg = 50.0,
        lonDeg = 14.0,
        ellipsoidalHeightM = 300.0,
        mslAltitudeM = 250.0,
        horizontalAccuracyM = horizontalAccuracyM,
        verticalAccuracyM = null,
        satellitesUsed = 12,
        satellitesInView = satellitesInView,
    )
}
