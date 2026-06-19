package org.rtkcollector.core.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RtklibInputRouterTest {
    private val router = RtklibInputRouter()
    private val validator = WorkflowValidator()

    @Test
    fun `u-blox raw observation profiles route directly to RTKLIB UBX decoder`() {
        val spec = WorkflowExamples.roverWithRtklibRealtime(
            capabilities = ReceiverCapabilityFixtures.ubloxM8p0(),
            receiverProfileId = "ublox-m8p-rawx-sfrbx",
        )

        val plan = router.plan(spec)

        assertEquals(RtklibSolutionDirection.FORWARD_ONLY, plan.solutionDirection)
        assertEquals(RtklibInputRouteKind.DIRECT_RTKLIB_DECODER, plan.roverInput.kind)
        assertEquals(RtklibRoverInputFormat.UBX_RXM_RAWX_SFRBX, plan.roverInput.format)
        assertEquals("input_ubx", plan.roverInput.decoderId)
        assertTrue(validator.validate(spec).valid)
    }

    @Test
    fun `M8T route model keeps UBX RAWX and SFRBX first class for RTKLIB`() {
        val spec = WorkflowExamples.roverWithRtklibRealtime(
            capabilities = ReceiverCapabilityFixtures.ubloxM8t().copy(supportsRoverMode = true),
            receiverProfileId = "ublox-m8t-rawx-sfrbx",
        )

        val plan = router.plan(spec)

        assertEquals(RtklibInputRouteKind.DIRECT_RTKLIB_DECODER, plan.roverInput.kind)
        assertEquals(RtklibRoverInputFormat.UBX_RXM_RAWX_SFRBX, plan.roverInput.format)
        assertEquals("input_ubx", plan.roverInput.decoderId)
    }

    @Test
    fun `UM980 direct RTKLIB route uses OBSVMB when that profile is selected`() {
        val spec = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.um980N4())
            .copy(rtklibPreferredRoverInputFormat = RtklibRoverInputFormat.UNICORE_OBSVMB)

        val plan = router.plan(spec)

        assertEquals(RtklibInputRouteKind.DIRECT_RTKLIB_DECODER, plan.roverInput.kind)
        assertEquals(RtklibRoverInputFormat.UNICORE_OBSVMB, plan.roverInput.format)
        assertEquals("input_unicore", plan.roverInput.decoderId)
        assertTrue(validator.validate(spec).valid)
    }

    @Test
    fun `UM980 compact OBSVMCMPB requires converter until RTKLIB EX direct support is declared`() {
        val spec = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.um980N4())
            .copy(rtklibPreferredRoverInputFormat = RtklibRoverInputFormat.UNICORE_OBSVMCMPB)

        val plan = router.plan(spec)

        assertEquals(RtklibInputRouteKind.UNSUPPORTED, plan.roverInput.kind)
        assertEquals(RtklibRoverInputFormat.UNICORE_OBSVMCMPB, plan.roverInput.format)
        assertTrue(validator.validate(spec).errors.any { it.code == "RTKLIB_REQUIRES_COMPATIBLE_RAW" })
    }

    @Test
    fun `UM980 compact OBSVMCMPB can be routed through explicit converter`() {
        val spec = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.um980N4())
            .copy(
                rtklibPreferredRoverInputFormat = RtklibRoverInputFormat.UNICORE_OBSVMCMPB,
                rtklibRawConverterId = "unicore-obsvmcmpb-to-rtklib-observation-v1",
            )

        val plan = router.plan(spec)

        assertEquals(RtklibInputRouteKind.CONVERTER, plan.roverInput.kind)
        assertEquals(RtklibRoverInputFormat.UNICORE_OBSVMCMPB, plan.roverInput.format)
        assertEquals("unicore-obsvmcmpb-to-rtklib-observation-v1", plan.roverInput.converterId)
        assertTrue(validator.validate(spec).valid)
    }

    @Test
    fun `NTRIP RTCM3 correction stream routes directly to RTKLIB RTCM3 decoder`() {
        val spec = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.ubloxM8p0())

        val plan = router.plan(spec)

        assertEquals(RtklibInputRouteKind.DIRECT_RTKLIB_DECODER, plan.correctionInput.kind)
        assertEquals(CorrectionFormat.RTCM3, plan.correctionInput.format)
        assertEquals("input_rtcm3", plan.correctionInput.decoderId)
    }

    @Test
    fun `not configured routes are inactive and not supported`() {
        val spec = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())

        val plan = router.plan(spec)

        assertEquals(RtklibInputRouteKind.NOT_CONFIGURED, plan.roverInput.kind)
        assertEquals(RtklibInputRouteKind.NOT_CONFIGURED, plan.correctionInput.kind)
        assertFalse(plan.roverInput.supported)
        assertFalse(plan.correctionInput.supported)
    }
}
