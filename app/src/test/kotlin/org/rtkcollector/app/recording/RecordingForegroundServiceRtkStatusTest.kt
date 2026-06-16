package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordingForegroundServiceRtkStatusTest {
    @Test
    fun `rtkstatus no differential data remains no rtcm when no correction evidence exists`() {
        val status = classifyReceiverRtkStatus(
            positionType = "NARROW_FLOAT",
            solutionStatus = null,
            calculateStatus = 0,
            differentialAgeS = null,
            recentCorrectionInput = false,
            recentReceiverRtcmDecoded = false,
        )

        assertEquals("No RTCM", status)
    }

    @Test
    fun `rtkstatus no differential data with ntrip frames reports receiver pending`() {
        val status = classifyReceiverRtkStatus(
            positionType = "NONE",
            solutionStatus = null,
            calculateStatus = 0,
            differentialAgeS = null,
            recentCorrectionInput = true,
            recentReceiverRtcmDecoded = false,
        )

        assertEquals("RTCM input, receiver pending", status)
    }

    @Test
    fun `rtkstatus no differential data with receiver decode reports decoded pending`() {
        val status = classifyReceiverRtkStatus(
            positionType = "NONE",
            solutionStatus = null,
            calculateStatus = 0,
            differentialAgeS = null,
            recentCorrectionInput = true,
            recentReceiverRtcmDecoded = true,
        )

        assertEquals("RTCM decoded, receiver pending", status)
    }

    @Test
    fun `computed float solution is not hidden by stale no differential diagnostic`() {
        val status = classifyReceiverRtkStatus(
            positionType = "NARROW_FLOAT",
            solutionStatus = "SOL_COMPUTED",
            calculateStatus = 0,
            differentialAgeS = null,
            recentCorrectionInput = true,
            recentReceiverRtcmDecoded = true,
        )

        assertEquals("RTK float", status)
    }

    @Test
    fun `rtkstatus high latency takes precedence over fixed position type`() {
        val status = classifyReceiverRtkStatus(
            positionType = "NARROW_INT",
            solutionStatus = null,
            calculateStatus = 2,
            differentialAgeS = null,
            recentCorrectionInput = true,
            recentReceiverRtcmDecoded = true,
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
            recentCorrectionInput = false,
            recentReceiverRtcmDecoded = false,
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
            recentCorrectionInput = true,
            recentReceiverRtcmDecoded = true,
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

    @Test
    fun `rtcm decoded update preserves receiver pending status while evidence is recent`() {
        val status = receiverRtkStatusAfterRtcmDecoded(
            previousStatus = "RTCM input, receiver pending",
            lastReceiverRtkEvidenceAtMillis = 1_000L,
            nowMillis = 2_000L,
        )

        assertEquals("RTCM input, receiver pending", status)
    }
}
