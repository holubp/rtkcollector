package org.rtkcollector.app.recording

import org.rtkcollector.core.solution.CoordinateAverageAddResult
import org.rtkcollector.core.solution.CoordinateAverageSample
import org.rtkcollector.core.solution.CoordinateAverageSummary
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.OnlineCoordinateAverager
import org.rtkcollector.core.solution.SolutionCandidate

internal class CoordinateAveragingController {
    private var averager: OnlineCoordinateAverager? = null

    var active: Boolean = false
        private set

    var lastStopReason: String? = null
        private set

    fun start(requiredFixClass: FixClass) {
        averager = OnlineCoordinateAverager(requiredFixClass)
        active = true
        lastStopReason = null
    }

    fun stop(reason: String? = null) {
        active = false
        lastStopReason = reason
    }

    fun summary(): CoordinateAverageSummary? = averager?.summary()

    fun onSelectedSolution(candidate: SolutionCandidate): CoordinateAverageAddResult {
        if (!active) return CoordinateAverageAddResult(false, "Averaging is not active.")
        val lat = candidate.latDeg
        val lon = candidate.lonDeg
        val height = candidate.ellipsoidalHeightM
            ?: return CoordinateAverageAddResult(false, "Selected solution has no ellipsoidal height.")
        val result = averager?.add(
            CoordinateAverageSample(
                latDeg = lat,
                lonDeg = lon,
                ellipsoidalHeightM = height,
                fixClass = candidate.fixClass,
                timestampMillis = candidate.updatedAtMillis,
            ),
        ) ?: CoordinateAverageAddResult(false, "Averaging is not configured.")
        if (!result.accepted) {
            stop(result.reason)
        }
        return result
    }
}
