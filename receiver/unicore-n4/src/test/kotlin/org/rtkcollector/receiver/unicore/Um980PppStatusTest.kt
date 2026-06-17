package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Um980PppStatusTest {
    @Test
    fun `pppnav insufficient observations means ppp has not started`() {
        assertEquals(
            "PPP not started",
            um980PppStatusLabel(solutionStatus = "INSUFFICIENT_OBS", positionType = "NONE"),
        )
    }

    @Test
    fun `pppnav no convergence is explicit`() {
        assertEquals(
            "PPP no convergence",
            um980PppStatusLabel(solutionStatus = "NO_CONVERGENCE", positionType = "NONE"),
        )
    }

    @Test
    fun `pppnav converging and converged remain distinct`() {
        assertEquals(
            "PPP converging",
            um980PppStatusLabel(solutionStatus = "SOL_COMPUTED", positionType = "PPP_CONVERGING"),
        )
        assertEquals(
            "PPP converged",
            um980PppStatusLabel(solutionStatus = "SOL_COMPUTED", positionType = "PPP"),
        )
    }

    @Test
    fun `missing pppnav remains unavailable`() {
        assertNull(um980PppStatusLabel(solutionStatus = null, positionType = null))
    }
}
