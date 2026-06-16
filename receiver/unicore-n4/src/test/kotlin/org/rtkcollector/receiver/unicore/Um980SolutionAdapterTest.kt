package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionEngine

class Um980SolutionAdapterTest {
    @Test
    fun `narrow int maps to RTK_FIXED on DEVICE_INTERNAL engine`() {
        val telemetry = bestnavTelemetry(positionType = "NARROW_INT")

        val candidate = telemetry.toBestnavCandidate(nowMillis = 7_000L)

        assertEquals(FixClass.RTK_FIXED, candidate?.fixClass)
        assertEquals(SolutionEngine.DEVICE_INTERNAL, candidate?.engine)
        assertEquals("UM980-BESTNAV", candidate?.sourceId)
        assertEquals("um980", candidate?.receiverFamily)
        assertEquals(7_000L, candidate?.updatedAtMillis)
    }

    @Test
    fun `narrow float maps to RTK_FLOAT`() {
        val candidate = bestnavTelemetry(positionType = "NARROW_FLOAT").toBestnavCandidate(0L)
        assertEquals(FixClass.RTK_FLOAT, candidate?.fixClass)
    }

    @Test
    fun `psrdiff maps to DGPS`() {
        val candidate = bestnavTelemetry(positionType = "PSRDIFF").toBestnavCandidate(0L)
        assertEquals(FixClass.DGPS, candidate?.fixClass)
    }

    @Test
    fun `single maps to SINGLE`() {
        val candidate = bestnavTelemetry(positionType = "SINGLE").toBestnavCandidate(0L)
        assertEquals(FixClass.SINGLE, candidate?.fixClass)
    }

    @Test
    fun `none position type returns null`() {
        val candidate = bestnavTelemetry(positionType = "NONE").toBestnavCandidate(0L)
        assertNull(candidate)
    }

    @Test
    fun `blank position type returns null`() {
        val candidate = bestnavTelemetry(positionType = null).toBestnavCandidate(0L)
        assertNull(candidate)
    }

    @Test
    fun `missing lat or lon returns null`() {
        val candidate = bestnavTelemetry(positionType = "SINGLE", latDeg = null)
            .toBestnavCandidate(0L)
        assertNull(candidate)
    }

    @Test
    fun `pppnav with PPP maps to PPP_CONVERGED on RECEIVER_PPP engine`() {
        val candidate = pppTelemetry(positionType = "PPP").toPppCandidate(0L)
        assertEquals(FixClass.PPP_CONVERGED, candidate?.fixClass)
        assertEquals(SolutionEngine.RECEIVER_PPP, candidate?.engine)
        assertEquals("UM980-PPP", candidate?.sourceId)
    }

    @Test
    fun `pppnav with PPP_CONVERGING maps to PPP_CONVERGING`() {
        val candidate = pppTelemetry(positionType = "PPP_CONVERGING").toPppCandidate(0L)
        assertEquals(FixClass.PPP_CONVERGING, candidate?.fixClass)
    }

    @Test
    fun `pppnav with non ppp type returns null`() {
        val candidate = pppTelemetry(positionType = "SINGLE").toPppCandidate(0L)
        assertNull(candidate)
    }

    @Test
    fun `ascii BESTNAV solution maps via positionType`() {
        val solution = Um980AsciiSolution(
            logName = "BESTNAVA",
            solutionStatus = "SOL_COMPUTED",
            positionType = "WIDE_INT",
            latDeg = 50.0,
            lonDeg = 14.0,
            heightM = 300.0,
        )

        val candidate = solution.toBestnavCandidate(0L)

        assertEquals(FixClass.RTK_FIXED, candidate?.fixClass)
        assertEquals(SolutionEngine.DEVICE_INTERNAL, candidate?.engine)
        assertEquals(300.0, candidate?.ellipsoidalHeightM)
    }

    private fun bestnavTelemetry(
        positionType: String?,
        latDeg: Double? = 50.0,
        lonDeg: Double? = 14.0,
    ): Um980Telemetry = Um980Telemetry(
        source = "BESTNAVB",
        positionType = positionType,
        latDeg = latDeg,
        lonDeg = lonDeg,
        altitudeM = 243.812,
        ellipsoidalHeightM = 287.423,
        latErrorM = 0.008,
        lonErrorM = 0.010,
        verticalAccuracyM = 0.015,
        satellitesUsed = 14,
        satellitesInView = 22,
    )

    private fun pppTelemetry(positionType: String?): Um980Telemetry = Um980Telemetry(
        source = "PPPNAVB",
        positionType = positionType,
        latDeg = 50.0,
        lonDeg = 14.0,
        altitudeM = 240.0,
        ellipsoidalHeightM = 290.0,
        latErrorM = 0.05,
        lonErrorM = 0.05,
        verticalAccuracyM = 0.1,
        satellitesUsed = 18,
    )
}
