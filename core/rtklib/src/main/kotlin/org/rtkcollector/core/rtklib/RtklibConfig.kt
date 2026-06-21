package org.rtkcollector.core.rtklib

import org.rtkcollector.core.workflow.RtklibInputRouteKind
import org.rtkcollector.core.workflow.RtklibInputRoutePlan
import org.rtkcollector.core.workflow.RtklibSolutionDirection

enum class RtklibPreset {
    ROVER_KINEMATIC_RTK,
    TEMPORARY_BASE_STATIC_RTK,
}

data class RtklibConfig(
    val routePlan: RtklibInputRoutePlan,
    val preset: RtklibPreset,
    val receiverProfileId: String,
    val baseContextSummary: String,
    val outputNmea: Boolean = true,
    val outputPos: Boolean = true,
    val maxRoverQueueBytes: Int = DEFAULT_MAX_ROVER_QUEUE_BYTES,
    val maxCorrectionQueueBytes: Int = DEFAULT_MAX_CORRECTION_QUEUE_BYTES,
    val staleThresholdMillis: Long = DEFAULT_STALE_THRESHOLD_MILLIS,
    val backlogWarningBytes: Int = DEFAULT_BACKLOG_WARNING_BYTES,
    val frequencyCount: Int = DEFAULT_FREQUENCY_COUNT,
    val serverCycleMillis: Int = DEFAULT_SERVER_CYCLE_MILLIS,
    val serverBufferBytes: Int = DEFAULT_SERVER_BUFFER_BYTES,
    val solutionBufferBytes: Int = DEFAULT_SOLUTION_BUFFER_BYTES,
) {
    fun validate(): RtklibConfigValidationResult {
        val errors = mutableListOf<String>()
        if (receiverProfileId.isBlank()) errors += "receiverProfileId is required"
        if (baseContextSummary.isBlank()) errors += "baseContextSummary is required"
        if (!outputNmea && !outputPos) errors += "At least one RTKLIB output must be enabled"
        if (maxRoverQueueBytes <= 0) errors += "maxRoverQueueBytes must be positive"
        if (maxCorrectionQueueBytes <= 0) errors += "maxCorrectionQueueBytes must be positive"
        if (staleThresholdMillis <= 0) errors += "staleThresholdMillis must be positive"
        if (backlogWarningBytes <= 0) errors += "backlogWarningBytes must be positive"
        if (frequencyCount !in 1..3) errors += "frequencyCount must be 1, 2 or 3"
        if (serverCycleMillis <= 0) errors += "serverCycleMillis must be positive"
        if (serverBufferBytes <= 0) errors += "serverBufferBytes must be positive"
        if (solutionBufferBytes <= 0) errors += "solutionBufferBytes must be positive"
        if (routePlan.solutionDirection != RtklibSolutionDirection.FORWARD_ONLY) {
            errors += "Only forward-only RTKLIB live processing is supported"
        }
        if (routePlan.roverInput.kind == RtklibInputRouteKind.UNSUPPORTED) {
            errors += "Unsupported RTKLIB rover input route: ${routePlan.roverInput.reason}"
        }
        if (routePlan.roverInput.kind == RtklibInputRouteKind.NOT_CONFIGURED) {
            errors += "RTKLIB rover input route is not configured"
        }
        if (routePlan.correctionInput.kind == RtklibInputRouteKind.UNSUPPORTED) {
            errors += "Unsupported RTKLIB correction input route: ${routePlan.correctionInput.reason}"
        }
        if (routePlan.correctionInput.kind == RtklibInputRouteKind.NOT_CONFIGURED) {
            errors += "RTKLIB correction input route is not configured"
        }
        return RtklibConfigValidationResult(errors = errors)
    }

    companion object {
        const val DEFAULT_MAX_ROVER_QUEUE_BYTES: Int = 4 * 1024 * 1024
        const val DEFAULT_MAX_CORRECTION_QUEUE_BYTES: Int = 1024 * 1024
        const val DEFAULT_STALE_THRESHOLD_MILLIS: Long = 3_000L
        const val DEFAULT_BACKLOG_WARNING_BYTES: Int = 512 * 1024
        const val DEFAULT_FREQUENCY_COUNT: Int = 1
        const val DEFAULT_SERVER_CYCLE_MILLIS: Int = 50
        const val DEFAULT_SERVER_BUFFER_BYTES: Int = 65_536
        const val DEFAULT_SOLUTION_BUFFER_BYTES: Int = 65_536
    }
}

data class RtklibConfigValidationResult(
    val errors: List<String>,
) {
    val valid: Boolean
        get() = errors.isEmpty()
}
