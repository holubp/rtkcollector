package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.mocklocation.MockLocationPublishResult
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

class BestSolutionTickLogicTest {
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
        assertTrue(out.publishAction is PublishAction.Publish)
        assertEquals(1_000L, (out.publishAction as PublishAction.Publish).snapshot.updatedAtMillis)
    }

    @Test
    fun `mock disabled emits DISABLED without publish even with fresh candidate`() {
        val candidate = candidate("UM980-BESTNAV", FixClass.RTK_FIXED, updatedAtMillis = 1_000L)

        val out = BestSolutionTickLogic.compute(
            input(candidates = listOf(candidate), nowMillis = 1_500L, mockEnabled = false),
        )

        assertEquals(MockLocationPublishResult.DISABLED, out.stateDelta.mockResult)
        assertTrue(out.publishAction is PublishAction.None)
        assertNull(out.newLastMockPublishedAt)
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
                previousMockResult = MockLocationPublishResult.PUBLISHED,
            ),
        )

        assertTrue(out.publishAction is PublishAction.None)
        assertEquals(MockLocationPublishResult.PUBLISHED, out.stateDelta.mockResult)
        assertEquals(1_000L, out.newLastMockPublishedAt)
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
    fun `selector ignores entries older than maxAgeMillis`() {
        val fresh = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 4_900L)
        val stale = candidate("UM980-BESTNAV", FixClass.RTK_FIXED, updatedAtMillis = 0L)

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
    }

    private fun input(
        candidates: Collection<SolutionCandidate>,
        nowMillis: Long = 1_000L,
        mockEnabled: Boolean,
        lastMockPublishedAt: Long? = null,
        previousMockResult: MockLocationPublishResult? = null,
        mockProviderAvailable: Boolean = true,
    ): BestSolutionTickInput = BestSolutionTickInput(
        candidates = candidates,
        nowMillis = nowMillis,
        mockEnabled = mockEnabled,
        mockProviderAvailable = mockProviderAvailable,
        lastMockPublishedAt = lastMockPublishedAt,
        previousMockResult = previousMockResult,
    )

    private fun previousState(
        candidates: Collection<SolutionCandidate>,
        nowMillis: Long,
        mockEnabled: Boolean,
        lastMockPublishedAt: Long?,
        previousMockResult: MockLocationPublishResult?,
    ): BestSolutionTickOutput = BestSolutionTickLogic.compute(
        input(candidates, nowMillis, mockEnabled, lastMockPublishedAt, previousMockResult),
    )

    private fun candidate(
        sourceId: String,
        fixClass: FixClass,
        updatedAtMillis: Long,
        horizontalAccuracyM: Double? = null,
    ): SolutionCandidate = SolutionCandidate(
        sourceId = sourceId,
        receiverFamily = "test",
        engine = SolutionEngine.DEVICE_INTERNAL,
        fixClass = fixClass,
        updatedAtMillis = updatedAtMillis,
        latDeg = 50.0,
        lonDeg = 14.0,
        ellipsoidalHeightM = 300.0,
        mslAltitudeM = 250.0,
        horizontalAccuracyM = horizontalAccuracyM,
        verticalAccuracyM = null,
        satellitesUsed = 12,
        satellitesInView = null,
    )
}
