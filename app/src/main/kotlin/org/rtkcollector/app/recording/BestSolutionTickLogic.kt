package org.rtkcollector.app.recording

import org.rtkcollector.app.mocklocation.MockLocationPublishResult
import org.rtkcollector.core.solution.BestSolutionSelector
import org.rtkcollector.core.solution.BestSolutionSnapshot
import org.rtkcollector.core.solution.SolutionCandidate

data class BestSolutionTickInput(
    val candidates: Collection<SolutionCandidate>,
    val nowMillis: Long,
    val mockEnabled: Boolean,
    val mockProviderAvailable: Boolean = true,
    val lastMockPublishedAt: Long? = null,
    val lastMockPublishedIdentity: String? = null,
    val lastMockPublishWallClockAtMillis: Long? = null,
    val previousMockResult: MockLocationPublishResult? = null,
    val maxAgeMillis: Long = BestSolutionSelector.DEFAULT_MAX_AGE_MILLIS,
)

data class BestSolutionStateDelta(
    val bestSolutionSource: String,
    val bestSolutionFix: String,
    val bestSolutionAgeMs: Long?,
    val latDeg: Double?,
    val lonDeg: Double?,
    val ellipsoidalHeightM: Double?,
    val mslAltitudeM: Double?,
    val horizontalAccuracyM: Double?,
    val verticalAccuracyM: Double?,
    val satellitesUsed: Int?,
    val satellitesInView: Int?,
    val mockResult: MockLocationPublishResult,
)

sealed class PublishAction {
    data object None : PublishAction()
    data class Publish(val snapshot: BestSolutionSnapshot) : PublishAction()
}

data class BestSolutionTickOutput(
    val stateDelta: BestSolutionStateDelta,
    val publishAction: PublishAction,
    val newLastMockPublishedAt: Long?,
    val newLastMockPublishedIdentity: String?,
    val newLastMockPublishWallClockAtMillis: Long?,
    val newPreviousMockResult: MockLocationPublishResult?,
    val previousMockResult: MockLocationPublishResult?,
)

data class PublishResultApplication(
    val mockResult: MockLocationPublishResult,
    val setLastError: Boolean,
    val newLastMockPublishedAt: Long?,
    val newLastMockPublishedIdentity: String?,
    val newLastMockPublishWallClockAtMillis: Long?,
    val lastMockPublishIntervalMillis: Long?,
)

object BestSolutionTickLogic {
    fun compute(input: BestSolutionTickInput): BestSolutionTickOutput {
        val best = BestSolutionSelector.select(
            input.candidates,
            input.nowMillis,
            input.maxAgeMillis,
        )
        val bestIdentity = best?.publishIdentity()
        val samePublishedCandidate =
            best != null &&
                best.updatedAtMillis == input.lastMockPublishedAt &&
                bestIdentity == input.lastMockPublishedIdentity

        val mockResult = when {
            !input.mockEnabled -> MockLocationPublishResult.DISABLED
            !input.mockProviderAvailable -> MockLocationPublishResult.NOT_PERMITTED
            best == null -> MockLocationPublishResult.STALE
            samePublishedCandidate ->
                input.previousMockResult ?: MockLocationPublishResult.PUBLISHED
            else -> MockLocationPublishResult.PUBLISHED // tentative; caller updates via applyPublishResult
        }

        val publishAction: PublishAction = if (
            input.mockEnabled &&
            input.mockProviderAvailable &&
            best != null &&
            !samePublishedCandidate
        ) {
            PublishAction.Publish(best)
        } else {
            PublishAction.None
        }

        return BestSolutionTickOutput(
            stateDelta = stateDeltaForSnapshot(best, mockResult),
            publishAction = publishAction,
            newLastMockPublishedAt = input.lastMockPublishedAt,
            newLastMockPublishedIdentity = input.lastMockPublishedIdentity,
            newLastMockPublishWallClockAtMillis = input.lastMockPublishWallClockAtMillis,
            newPreviousMockResult = mockResult,
            previousMockResult = input.previousMockResult,
        )
    }

    fun stateDeltaForCandidate(
        candidate: SolutionCandidate,
        nowMillis: Long,
        mockResult: MockLocationPublishResult,
    ): BestSolutionStateDelta =
        BestSolutionStateDelta(
            bestSolutionSource = candidate.sourceId,
            bestSolutionFix = candidate.fixClass.name,
            bestSolutionAgeMs = (nowMillis - candidate.updatedAtMillis).coerceAtLeast(0L),
            latDeg = candidate.latDeg,
            lonDeg = candidate.lonDeg,
            ellipsoidalHeightM = candidate.ellipsoidalHeightM,
            mslAltitudeM = candidate.mslAltitudeM,
            horizontalAccuracyM = candidate.horizontalAccuracyM,
            verticalAccuracyM = candidate.verticalAccuracyM,
            satellitesUsed = candidate.satellitesUsed,
            satellitesInView = candidate.satellitesInView,
            mockResult = mockResult,
        )

    private fun stateDeltaForSnapshot(
        snapshot: BestSolutionSnapshot?,
        mockResult: MockLocationPublishResult,
    ): BestSolutionStateDelta =
        BestSolutionStateDelta(
            bestSolutionSource = snapshot?.sourceId ?: "n/a",
            bestSolutionFix = snapshot?.fixClass?.name ?: "n/a",
            bestSolutionAgeMs = snapshot?.ageMillis,
            latDeg = snapshot?.latDeg,
            lonDeg = snapshot?.lonDeg,
            ellipsoidalHeightM = snapshot?.ellipsoidalHeightM,
            mslAltitudeM = snapshot?.mslAltitudeM,
            horizontalAccuracyM = snapshot?.horizontalAccuracyM,
            verticalAccuracyM = snapshot?.verticalAccuracyM,
            satellitesUsed = snapshot?.satellitesUsed,
            satellitesInView = snapshot?.satellitesInView,
            mockResult = mockResult,
        )

    /**
     * Folds a publish result back into the tick output. Caller invokes the sink
     * with the snapshot from `publishAction`, then calls this with the result.
     */
    fun applyPublishResult(
        previous: BestSolutionTickOutput,
        publishedResult: MockLocationPublishResult,
        publishedAtMillis: Long?,
        publishedWallClockAtMillis: Long? = publishedAtMillis,
    ): PublishResultApplication {
        val transitionedIntoFailed =
            publishedResult == MockLocationPublishResult.FAILED &&
                previous.previousMockResult != MockLocationPublishResult.FAILED
        val publishedSnapshot = (previous.publishAction as? PublishAction.Publish)?.snapshot
        val newLastPublishedAt = when (publishedResult) {
            MockLocationPublishResult.PUBLISHED -> publishedAtMillis
            else -> previous.newLastMockPublishedAt
        }
        val newLastPublishedIdentity = when (publishedResult) {
            MockLocationPublishResult.PUBLISHED -> publishedSnapshot?.publishIdentity()
            else -> previous.newLastMockPublishedIdentity
        }
        val lastInterval = if (
            publishedResult == MockLocationPublishResult.PUBLISHED &&
            publishedWallClockAtMillis != null &&
            previous.newLastMockPublishWallClockAtMillis != null
        ) {
            (publishedWallClockAtMillis - previous.newLastMockPublishWallClockAtMillis).coerceAtLeast(0L)
        } else {
            null
        }
        val newLastWallClockPublishedAt = when (publishedResult) {
            MockLocationPublishResult.PUBLISHED -> publishedWallClockAtMillis
            else -> previous.newLastMockPublishWallClockAtMillis
        }
        return PublishResultApplication(
            mockResult = publishedResult,
            setLastError = transitionedIntoFailed,
            newLastMockPublishedAt = newLastPublishedAt,
            newLastMockPublishedIdentity = newLastPublishedIdentity,
            newLastMockPublishWallClockAtMillis = newLastWallClockPublishedAt,
            lastMockPublishIntervalMillis = lastInterval,
        )
    }

    private fun BestSolutionSnapshot.publishIdentity(): String =
        "$sourceId|$receiverFamily|$engine"
}
