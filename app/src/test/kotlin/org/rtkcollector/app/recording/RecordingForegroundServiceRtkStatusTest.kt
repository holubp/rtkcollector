package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordingForegroundServiceRtkStatusTest {
    @Test
    fun `rtkstatus diagnostic failure takes precedence over float position type`() {
        val status = classifyReceiverRtkStatus(
            positionType = "NARROW_FLOAT",
            solutionStatus = null,
            calculateStatus = 0,
            differentialAgeS = null,
            recentRtcmDecoded = true,
        )

        assertEquals("No RTCM", status)
    }

    @Test
    fun `rtkstatus high latency takes precedence over fixed position type`() {
        val status = classifyReceiverRtkStatus(
            positionType = "NARROW_INT",
            solutionStatus = null,
            calculateStatus = 2,
            differentialAgeS = null,
            recentRtcmDecoded = true,
        )

        assertEquals("RTK stale", status)
    }

    @Test
    fun `missing current evidence clears stale rtk state to unavailable`() {
        val status = classifyReceiverRtkStatus(
            positionType = null,
            solutionStatus = null,
            calculateStatus = null,
            differentialAgeS = null,
            recentRtcmDecoded = false,
        )

        assertEquals("n/a", status)
    }

    @Test
    fun `computed adrnav fixed remains rtk fixed`() {
        val status = classifyReceiverRtkStatus(
            positionType = "NARROW_INT",
            solutionStatus = "SOL_COMPUTED",
            calculateStatus = null,
            differentialAgeS = 1.0,
            recentRtcmDecoded = true,
        )

        assertEquals("RTK fixed", status)
    }

    @Test
    fun `rtcm decoded update does not preserve stale fixed status`() {
        val status = receiverRtkStatusAfterRtcmDecoded(
            previousStatus = "RTK fixed",
            lastReceiverRtkEvidenceAtMillis = 1_000L,
            nowMillis = 20_000L,
        )

        assertEquals("RTCM decoded", status)
    }
}
