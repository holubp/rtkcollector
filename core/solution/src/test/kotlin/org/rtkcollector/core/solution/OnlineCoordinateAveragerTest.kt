package org.rtkcollector.core.solution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnlineCoordinateAveragerTest {
    @Test
    fun `accumulates mean and sample standard deviation without retaining samples`() {
        val averager = OnlineCoordinateAverager(requiredFixClass = FixClass.RTK_FIXED)
        val first = sample(50.0, 14.0, 300.0)
        val second = sample(52.0, 16.0, 304.0)

        assertTrue(averager.add(first).accepted)
        assertTrue(averager.add(second).accepted)

        val summary = averager.summary()
        assertEquals(2, summary.sampleCount)
        assertEquals(51.0, summary.latMeanDeg, 1e-12)
        assertEquals(15.0, summary.lonMeanDeg, 1e-12)
        assertEquals(302.0, summary.heightMeanM, 1e-12)
        assertEquals(2.8284271247461903, summary.heightStandardDeviationM!!, 1e-12)
        assertEquals(0, averager.retainedSampleCountForTest())
    }

    @Test
    fun `rejects fix class change`() {
        val averager = OnlineCoordinateAverager(requiredFixClass = FixClass.RTK_FIXED)

        assertFalse(averager.add(sample(50.0, 14.0, 300.0, FixClass.RTK_FLOAT)).accepted)
    }

    private fun sample(
        lat: Double,
        lon: Double,
        height: Double,
        fix: FixClass = FixClass.RTK_FIXED,
    ): CoordinateAverageSample =
        CoordinateAverageSample(
            latDeg = lat,
            lonDeg = lon,
            ellipsoidalHeightM = height,
            fixClass = fix,
            timestampMillis = 1_000L,
        )
}
