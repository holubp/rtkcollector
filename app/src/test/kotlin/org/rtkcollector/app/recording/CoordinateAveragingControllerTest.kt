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
    fun `accumulates accepted selected-solution candidates`() {
        val controller = CoordinateAveragingController()
        controller.start(requiredFixClass = FixClass.RTK_FIXED)

        val result = controller.onSelectedSolution(candidate(50.0, 14.0, 302.0, FixClass.RTK_FIXED))

        assertTrue(result.accepted)
        assertEquals(1, controller.summary()?.sampleCount)
        assertEquals(302.0, controller.summary()?.heightMeanM, 1e-12)
    }

    @Test
    fun `stops and reports reason when fix class changes`() {
        val controller = CoordinateAveragingController()
        controller.start(requiredFixClass = FixClass.RTK_FIXED)
        assertTrue(controller.onSelectedSolution(candidate(50.0, 14.0, 302.0, FixClass.RTK_FIXED)).accepted)

        val result = controller.onSelectedSolution(candidate(50.1, 14.1, 303.0, FixClass.RTK_FLOAT))

        assertFalse(result.accepted)
        assertFalse(controller.active)
        assertNotNull(controller.lastStopReason)
    }

    @Test
    fun `ntrip source changes do not stop active averaging`() {
        val controller = CoordinateAveragingController()
        controller.start(requiredFixClass = FixClass.RTK_FIXED)
        assertTrue(controller.onSelectedSolution(candidate(50.0, 14.0, 302.0, FixClass.RTK_FIXED)).accepted)

        controller.onNtripSourceChanged("caster-a", "MOUNT-A")
        controller.onNtripSourceChanged("caster-b", "MOUNT-B")

        assertTrue(controller.active)
        assertEquals(null, controller.lastStopReason)
        assertEquals(1, controller.summary()?.sampleCount)
    }

    private fun candidate(
        lat: Double,
        lon: Double,
        height: Double,
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
        )
}
