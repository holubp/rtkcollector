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
    val previousMockResult: MockLocationPublishResult? = null,
    val maxAgeMillis: Long = BestSolutionSelector.DEFAULT_MAX_AGE_MILLIS,
)

data class BestSolutionStateDelta(
    val bestSolutionSource: String,
    val bestSolutionFix: String,
    val bestSolutionAgeMs: Long?,
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
    val newPreviousMockResult: MockLocationPublishResult?,
)

data class PublishResultApplication(
    val mockResult: MockLocationPublishResult,
    val setLastError: Boolean,
    val newLastMockPublishedAt: Long?,
)

object BestSolutionTickLogic {
    fun compute(input: BestSolutionTickInput): BestSolutionTickOutput {
        val best = BestSolutionSelector.select(
            input.candidates,
            input.nowMillis,
            input.maxAgeMillis,
        )

        val mockResult = when {
            !input.mockEnabled -> MockLocationPublishResult.DISABLED
            !input.mockProviderAvailable -> MockLocationPublishResult.NOT_PERMITTED
            best == null -> MockLocationPublishResult.STALE
            best.updatedAtMillis == input.lastMockPublishedAt ->
                input.previousMockResult ?: MockLocationPublishResult.PUBLISHED
            else -> MockLocationPublishResult.PUBLISHED // tentative; caller updates via applyPublishResult
        }

        val publishAction: PublishAction = if (
            input.mockEnabled &&
            input.mockProviderAvailable &&
            best != null &&
            best.updatedAtMillis != input.lastMockPublishedAt
        ) {
            PublishAction.Publish(best)
        } else {
            PublishAction.None
        }

        val newLastPublishedAt = when (publishAction) {
            is PublishAction.Publish -> publishAction.snapshot.updatedAtMillis
            PublishAction.None -> input.lastMockPublishedAt
        }

        return BestSolutionTickOutput(
            stateDelta = BestSolutionStateDelta(
                bestSolutionSource = best?.sourceId ?: "n/a",
                bestSolutionFix = best?.fixClass?.name ?: "n/a",
                bestSolutionAgeMs = best?.ageMillis,
                mockResult = mockResult,
            ),
            publishAction = publishAction,
            newLastMockPublishedAt = newLastPublishedAt,
            newPreviousMockResult = mockResult,
        )
    }

    /**
     * Folds a publish result back into the tick output. Caller invokes the sink
     * with the snapshot from `publishAction`, then calls this with the result.
     */
    fun applyPublishResult(
        previous: BestSolutionTickOutput,
        publishedResult: MockLocationPublishResult,
        publishedAtMillis: Long?,
    ): PublishResultApplication {
        val transitionedIntoFailed =
            publishedResult == MockLocationPublishResult.FAILED &&
                previous.newPreviousMockResult != MockLocationPublishResult.FAILED
        val newLastPublishedAt = when (publishedResult) {
            MockLocationPublishResult.PUBLISHED -> publishedAtMillis
            else -> previous.newLastMockPublishedAt
        }
        return PublishResultApplication(
            mockResult = publishedResult,
            setLastError = transitionedIntoFailed,
            newLastMockPublishedAt = newLastPublishedAt,
        )
    }
}
