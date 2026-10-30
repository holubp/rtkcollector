package org.rtkcollector.app.recording

import org.rtkcollector.core.solution.CoordinateAverageAddResult
import org.rtkcollector.core.solution.CoordinateAverageSample
import org.rtkcollector.core.solution.CoordinateAverageSummary
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.OnlineCoordinateAverager
import org.rtkcollector.core.solution.SolutionCandidate

internal class CoordinateAveragingController {
    private var averager: OnlineCoordinateAverager? = null
    private var sessionStartedAtMillis: Long? = null
    private var latestPrimaryCandidate: SolutionCandidate? = null

    var active: Boolean = false
        private set

    var lastStopReason: String? = null
        private set

    @Synchronized
    fun beginSession(nowMillis: Long) {
        endSession()
        sessionStartedAtMillis = nowMillis
    }

    @Synchronized
    fun endSession() {
        sessionStartedAtMillis = null
        latestPrimaryCandidate = null
        averager = null
        active = false
        lastStopReason = null
    }

    @Synchronized
    fun start(nowMillis: Long): CoordinateAverageAddResult {
        val candidate = latestPrimaryCandidate
        if (candidate == null || !candidate.isValidForSession(nowMillis)) {
            val result = CoordinateAverageAddResult(false, "Cannot start averaging: no fresh valid primary solution in this recording.")
            stop(result.reason)
            return result
        }
        averager = OnlineCoordinateAverager(candidate.fixClass)
        active = true
        lastStopReason = null
        return onSelectedSolution(candidate)
    }

    @Synchronized
    fun stop(reason: String? = null) {
        active = false
        lastStopReason = reason
    }

    @Synchronized
    fun summary(): CoordinateAverageSummary? = averager?.summary()

    @Synchronized
    fun onPrimarySolution(candidate: SolutionCandidate?, nowMillis: Long): CoordinateAverageAddResult? {
        if (sessionStartedAtMillis == null) return null
        // Keep an invalid update too: a previous valid fix must not be reused after fix loss.
        latestPrimaryCandidate = candidate
        if (!active) return null
        if (candidate == null || !candidate.isValidForSession(nowMillis)) {
            val result = CoordinateAverageAddResult(false, "Selected primary solution is stale or invalid.")
            stop(result.reason)
            return result
        }
        return onSelectedSolution(candidate)
    }

    private fun SolutionCandidate.isValidForSession(nowMillis: Long): Boolean {
        val startedAt = sessionStartedAtMillis ?: return false
        return updatedAtMillis >= startedAt && updatedAtMillis <= nowMillis &&
            nowMillis - updatedAtMillis <= org.rtkcollector.core.solution.BestSolutionSelector.DEFAULT_MAX_AGE_MILLIS &&
            fixClass != FixClass.NONE && latDeg.isFinite() && latDeg in -90.0..90.0 &&
            lonDeg.isFinite() && lonDeg in -180.0..180.0 &&
            ellipsoidalHeightM?.isFinite() == true && mslAltitudeM?.isFinite() != false
    }

    fun onNtripSourceChanged(
        casterLabel: String?,
        mountpointLabel: String?,
    ) {
        // Averaging is tied to the stationary local receiver and selected
        // solution fix class. Changing the upstream correction source is not
        // a stop condition.
        casterLabel.hashCode()
        mountpointLabel.hashCode()
    }

    private fun onSelectedSolution(candidate: SolutionCandidate): CoordinateAverageAddResult {
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
                mslAltitudeM = candidate.mslAltitudeM,
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
