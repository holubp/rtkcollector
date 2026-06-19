package org.rtkcollector.core.rtklib

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rtkcollector.core.workflow.ReceiverCapabilityFixtures
import org.rtkcollector.core.workflow.RtklibInputRouter
import org.rtkcollector.core.workflow.RtklibRoverInputFormat
import org.rtkcollector.core.workflow.WorkflowExamples

class RtklibConfigTest {
    @Test
    fun `valid u-blox NTRIP config accepts direct routes`() {
        val workflow = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.ubloxM8t().copy(supportsRoverMode = true))
        val config = RtklibConfig(
            routePlan = RtklibInputRouter().plan(workflow),
            preset = RtklibPreset.ROVER_KINEMATIC_RTK,
            receiverProfileId = "ublox-m8t-rawx-sfrbx",
            baseContextSummary = "NTRIP CORS01",
        )

        assertTrue(config.validate().valid)
    }

    @Test
    fun `unsupported UM980 compact route is rejected without converter`() {
        val workflow = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.um980N4())
            .copy(rtklibPreferredRoverInputFormat = RtklibRoverInputFormat.UNICORE_OBSVMCMPB)
        val config = RtklibConfig(
            routePlan = RtklibInputRouter().plan(workflow),
            preset = RtklibPreset.ROVER_KINEMATIC_RTK,
            receiverProfileId = "um980-compact",
            baseContextSummary = "NTRIP CORS01",
        )

        val result = config.validate()

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Unsupported RTKLIB rover input route") })
    }

    @Test
    fun `UM980 compact route is accepted with explicit RtkCollector OBSVMCMPB shim`() {
        val workflow = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.um980N4())
            .copy(
                rtklibPreferredRoverInputFormat = RtklibRoverInputFormat.UNICORE_OBSVMCMPB,
                rtklibRawConverterId = "rtkcollector-obsvmcmp-shim",
            )
        val config = RtklibConfig(
            routePlan = RtklibInputRouter().plan(workflow),
            preset = RtklibPreset.ROVER_KINEMATIC_RTK,
            receiverProfileId = "um980-compact",
            baseContextSummary = "NTRIP CORS01",
        )

        assertTrue(config.validate().valid)
    }

    @Test
    fun `at least one RTKLIB output is required`() {
        val workflow = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.ubloxM8p0())
        val config = RtklibConfig(
            routePlan = RtklibInputRouter().plan(workflow),
            preset = RtklibPreset.ROVER_KINEMATIC_RTK,
            receiverProfileId = "ublox-m8p",
            baseContextSummary = "NTRIP CORS01",
            outputNmea = false,
            outputPos = false,
        )

        assertFalse(config.validate().valid)
    }

    @Test
    fun `not configured route is rejected before native backend can start`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
        val config = RtklibConfig(
            routePlan = RtklibInputRouter().plan(workflow),
            preset = RtklibPreset.ROVER_KINEMATIC_RTK,
            receiverProfileId = "um980",
            baseContextSummary = "none",
        )

        val result = config.validate()

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("not configured") })
    }
}
