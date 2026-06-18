package org.rtkcollector.core.solution

import kotlin.math.sqrt

data class CoordinateAverageSample(
    val latDeg: Double,
    val lonDeg: Double,
    val ellipsoidalHeightM: Double,
    val fixClass: FixClass,
    val timestampMillis: Long,
)

data class CoordinateAverageAddResult(
    val accepted: Boolean,
    val reason: String? = null,
)

data class CoordinateAverageSummary(
    val sampleCount: Int,
    val latMeanDeg: Double,
    val lonMeanDeg: Double,
    val heightMeanM: Double,
    val latStandardDeviationDeg: Double?,
    val lonStandardDeviationDeg: Double?,
    val heightStandardDeviationM: Double?,
)

/**
 * Online Welford accumulator for base-coordinate averaging.
 *
 * It retains no coordinate samples. This keeps memory constant even for long
 * temporary-base sessions and prevents averaging from becoming a recording or
 * UI pressure source.
 */
class OnlineCoordinateAverager(
    private val requiredFixClass: FixClass,
) {
    private var count = 0
    private var latMean = 0.0
    private var lonMean = 0.0
    private var heightMean = 0.0
    private var latM2 = 0.0
    private var lonM2 = 0.0
    private var heightM2 = 0.0

    fun add(sample: CoordinateAverageSample): CoordinateAverageAddResult {
        if (sample.fixClass != requiredFixClass) {
            return CoordinateAverageAddResult(false, "Fix class changed from $requiredFixClass to ${sample.fixClass}.")
        }
        count += 1
        val nextLatMean = updateMean(sample.latDeg, latMean, count)
        latM2 += (sample.latDeg - latMean) * (sample.latDeg - nextLatMean)
        latMean = nextLatMean

        val nextLonMean = updateMean(sample.lonDeg, lonMean, count)
        lonM2 += (sample.lonDeg - lonMean) * (sample.lonDeg - nextLonMean)
        lonMean = nextLonMean

        val nextHeightMean = updateMean(sample.ellipsoidalHeightM, heightMean, count)
        heightM2 += (sample.ellipsoidalHeightM - heightMean) * (sample.ellipsoidalHeightM - nextHeightMean)
        heightMean = nextHeightMean

        return CoordinateAverageAddResult(true)
    }

    fun summary(): CoordinateAverageSummary =
        CoordinateAverageSummary(
            sampleCount = count,
            latMeanDeg = latMean,
            lonMeanDeg = lonMean,
            heightMeanM = heightMean,
            latStandardDeviationDeg = sampleStandardDeviation(latM2),
            lonStandardDeviationDeg = sampleStandardDeviation(lonM2),
            heightStandardDeviationM = sampleStandardDeviation(heightM2),
        )

    fun retainedSampleCountForTest(): Int = 0

    private fun updateMean(value: Double, mean: Double, count: Int): Double =
        mean + (value - mean) / count

    private fun sampleStandardDeviation(m2: Double): Double? =
        if (count < 2) null else sqrt(m2 / (count - 1))
}
