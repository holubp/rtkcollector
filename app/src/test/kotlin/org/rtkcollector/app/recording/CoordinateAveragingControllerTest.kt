package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

class CoordinateAveragingControllerTest {
    @Test
    fun `invalid fix cannot seed an average`() {
        val controller = CoordinateAveragingController()
        controller.beginSession(0)
        controller.onPrimarySolution(candidate(50.0, 14.0, 302.0, fixClass = FixClass.NONE), 1_000)
        val result = controller.start(1_000)
        assertFalse(result.accepted)
        assertFalse(controller.active)
    }

    @Test
    fun `accumulates accepted selected-solution candidates`() {
        val controller = CoordinateAveragingController()
        controller.beginSession(0)

        controller.onPrimarySolution(
            candidate(
                lat = 50.0,
                lon = 14.0,
                height = 302.0,
                mslAltitude = 257.0,
                fixClass = FixClass.RTK_FIXED,
            ),
            1_000,
        )
        val result = controller.start(1_000)

        assertTrue(result.accepted)
        val summary = requireNotNull(controller.summary())
        assertEquals(1, summary.sampleCount)
        assertEquals(302.0, summary.heightMeanM, 1e-12)
        assertEquals(257.0, requireNotNull(summary.mslAltitudeMeanM), 1e-12)
    }

    @Test
    fun `stops and reports reason when fix class changes`() {
        val controller = CoordinateAveragingController()
        controller.beginSession(0)
        controller.onPrimarySolution(candidate(50.0, 14.0, 302.0, fixClass = FixClass.RTK_FIXED), 1_000)
        assertTrue(controller.start(1_000).accepted)

        val result = requireNotNull(controller.onPrimarySolution(candidate(50.1, 14.1, 303.0, fixClass = FixClass.RTK_FLOAT), 1_000))

        assertFalse(result.accepted)
        assertFalse(controller.active)
        assertNotNull(controller.lastStopReason)
    }

    @Test
    fun `ntrip source changes do not stop active averaging`() {
        val controller = CoordinateAveragingController()
        controller.beginSession(0)
        controller.onPrimarySolution(candidate(50.0, 14.0, 302.0, fixClass = FixClass.RTK_FIXED), 1_000)
        assertTrue(controller.start(1_000).accepted)

        controller.onNtripSourceChanged("caster-a", "MOUNT-A")
        controller.onNtripSourceChanged("caster-b", "MOUNT-B")

        assertTrue(controller.active)
        assertEquals(null, controller.lastStopReason)
        assertEquals(1, controller.summary()?.sampleCount)
        assertTrue(requireNotNull(controller.onPrimarySolution(
            candidate(50.1, 14.1, 303.0, fixClass = FixClass.RTK_FIXED).copy(updatedAtMillis = 2_000), 2_000,
        )).accepted)
        assertEquals(2, controller.summary()?.sampleCount)
    }

    @Test
    fun `stop and new session clear all averages reason and seed`() {
        val controller = CoordinateAveragingController()
        controller.beginSession(0)
        controller.onPrimarySolution(candidate(50.0, 14.0, 302.0, fixClass = FixClass.RTK_FIXED), 1_000)
        assertTrue(controller.start(1_000).accepted)
        controller.stop("Manual stop")
        assertEquals(1, controller.summary()?.sampleCount, "Manual Avg stop retains selectable summary")
        controller.endSession()
        assertFalse(controller.active)
        assertEquals(null, controller.summary())
        assertEquals(null, controller.lastStopReason)
        controller.onPrimarySolution(candidate(50.1, 14.1, 303.0, fixClass = FixClass.RTK_FIXED), 1_100)
        assertFalse(controller.start(1_100).accepted, "Late stopped-session telemetry cannot seed")
        controller.beginSession(2_000)
        assertEquals(null, controller.lastStopReason)
        assertFalse(controller.start(2_000).accepted, "New session must not reuse old primary seed")
        controller.onPrimarySolution(candidate(50.1, 14.1, 303.0, fixClass = FixClass.RTK_FIXED), 2_000)
        assertFalse(controller.start(2_000).accepted, "Pre-session timestamp cannot seed")
        controller.onPrimarySolution(candidate(51.0, 15.0, 310.0, fixClass = FixClass.SINGLE).copy(updatedAtMillis = 2_000), 2_000)
        assertTrue(controller.start(2_000).accepted)
        assertEquals(1, controller.summary()?.sampleCount)
        assertEquals(51.0, controller.summary()?.latMeanDeg)
        controller.beginSession(3_000)
        assertFalse(controller.active)
        assertEquals(null, controller.summary())
        assertEquals(null, controller.lastStopReason)
        assertFalse(controller.start(3_000).accepted)
    }

    @Test
    fun `seed uses established five second window including boundary and rejects future time`() {
        val controller = CoordinateAveragingController()
        controller.beginSession(0)
        controller.onPrimarySolution(candidate(50.0, 14.0, 302.0, fixClass = FixClass.RTK_FIXED), 1_000)
        assertTrue(controller.start(6_000).accepted)
        controller.stop()
        assertFalse(controller.start(6_001).accepted)
        controller.onPrimarySolution(candidate(50.0, 14.0, 302.0, fixClass = FixClass.RTK_FIXED).copy(updatedAtMillis = 8_000), 7_000)
        assertFalse(controller.start(7_000).accepted)
    }

    @Test
    fun `invalid primary supersedes valid cached seed and cannot contaminate average`() {
        val valid = candidate(50.0, 14.0, 302.0, fixClass = FixClass.RTK_FIXED)
        val invalid = listOf(
            valid.copy(fixClass = FixClass.NONE), valid.copy(latDeg = Double.NaN),
            valid.copy(lonDeg = Double.POSITIVE_INFINITY), valid.copy(latDeg = 91.0),
            valid.copy(lonDeg = -181.0), valid.copy(ellipsoidalHeightM = null),
            valid.copy(ellipsoidalHeightM = Double.NEGATIVE_INFINITY), valid.copy(mslAltitudeM = Double.NaN),
        )
        for (bad in invalid) {
            val controller = CoordinateAveragingController()
            controller.beginSession(0)
            controller.onPrimarySolution(valid, 1_000)
            controller.onPrimarySolution(bad, 1_000)
            assertFalse(controller.start(1_000).accepted)
            assertFalse(controller.active)
            assertEquals(null, controller.summary())
            controller.onPrimarySolution(valid, 1_000)
            assertTrue(controller.start(1_000).accepted)
            assertFalse(requireNotNull(controller.onPrimarySolution(bad, 1_000)).accepted)
            assertFalse(controller.active)
            assertEquals(1, controller.summary()?.sampleCount)
        }
    }

    @Test
    fun `adapter null fix loss clears seed and stops active average without losing manual summary`() {
        val controller = CoordinateAveragingController()
        val valid = candidate(50.0, 14.0, 302.0, fixClass = FixClass.RTK_FIXED)
        controller.beginSession(0)
        controller.onPrimarySolution(valid, 1_000)
        controller.onPrimarySolution(null, 1_001)
        assertFalse(controller.start(1_001).accepted)
        controller.onPrimarySolution(valid, 1_002)
        assertTrue(controller.start(1_002).accepted)
        assertFalse(requireNotNull(controller.onPrimarySolution(null, 1_003)).accepted)
        assertFalse(controller.active)
        assertEquals(1, controller.summary()?.sampleCount)
        assertFalse(controller.start(1_004).accepted)
    }

    private fun candidate(
        lat: Double,
        lon: Double,
        height: Double,
        mslAltitude: Double? = null,
        fixClass: FixClass,
    ): SolutionCandidate =
        SolutionCandidate(
            sourceId = "UM980-BESTNAV",
            receiverFamily = "um980",
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = fixClass,
            updatedAtMillis = 1_000L,
            latDeg = lat,
            lonDeg = lon,
            ellipsoidalHeightM = height,
            mslAltitudeM = mslAltitude,
        )
}
