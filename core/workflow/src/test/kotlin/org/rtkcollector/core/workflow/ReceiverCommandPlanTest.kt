package org.rtkcollector.core.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiverCommandPlanTest {
    private val validator = ReceiverCommandPlanValidator()

    @Test
    fun `startup commands concatenate init then selected mode sequence`() {
        val plan = ReceiverCommandPlanExamples.um980RoverPlan()

        assertEquals(
            listOf(
                "# UM980 init commands intentionally omitted",
                "# UM980 rover commands intentionally omitted",
            ),
            plan.startupCommands(),
        )
    }

    @Test
    fun `shutdown commands are not part of startup commands`() {
        val plan = ReceiverCommandPlanExamples.um980RoverPlan(
            shutdownSequence = ReceiverCommandSequence(
                id = "um980-safe-shutdown",
                name = "UM980 safe shutdown reference",
                phase = ReceiverCommandPhase.SHUTDOWN,
                commands = listOf("# UM980 shutdown commands intentionally omitted"),
            ),
        )

        assertEquals(
            listOf(
                "# UM980 init commands intentionally omitted",
                "# UM980 rover commands intentionally omitted",
            ),
            plan.startupCommands(),
        )
        assertEquals(listOf("# UM980 shutdown commands intentionally omitted"), plan.shutdownCommands())
    }

    @Test
    fun `rover workflow accepts init plus rover sequence`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.um980RoverPlan()

        val result = validator.validate(workflow, plan)

        assertTrue(result.valid, result.errors.toString())
    }

    @Test
    fun `rover workflow rejects fixed-base mode command sequence`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.um980FixedBasePlan()

        val result = validator.validate(workflow, plan)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.code == "COMMAND_MODE_DOES_NOT_MATCH_WORKFLOW" })
    }

    @Test
    fun `profile mismatch produces warning`() {
        val workflow = WorkflowExamples.plainRoverRecording(
            capabilities = ReceiverCapabilityFixtures.um980N4(),
            receiverProfileId = "other-profile",
        )
        val plan = ReceiverCommandPlanExamples.um980RoverPlan()

        val result = validator.validate(workflow, plan)

        assertTrue(result.valid, result.errors.toString())
        assertTrue(result.warnings.any { it.code == "COMMAND_PROFILE_DIFFERS_FROM_WORKFLOW_PROFILE" })
    }

    @Test
    fun `plan rejects non-init init sequence phase`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.um980RoverPlan().copy(
            initSequence = ReceiverCommandSequence(
                id = "bad-init",
                name = "Bad init",
                phase = ReceiverCommandPhase.ROVER,
                commands = listOf("# wrong phase"),
            ),
        )

        val result = validator.validate(workflow, plan)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.code == "INIT_SEQUENCE_REQUIRES_INIT_PHASE" })
    }

    @Test
    fun `invalid shutdown sequence phase is rejected`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.um980RoverPlan(
            shutdownSequence = ReceiverCommandSequence(
                id = "bad-shutdown",
                name = "Bad shutdown",
                phase = ReceiverCommandPhase.ROVER,
                commands = listOf("# wrong shutdown phase"),
            ),
        )

        val result = validator.validate(workflow, plan)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.code == "SHUTDOWN_SEQUENCE_REQUIRES_SHUTDOWN_PHASE" })
    }

    @Test
    fun `custom command plan requires custom command capability`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.genericNmeaRtcm())
        val plan = ReceiverCommandPlanExamples.genericRoverPlan().copy(customCommandsRequested = true)

        val result = validator.validate(workflow, plan)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.code == "CUSTOM_COMMANDS_REQUIRE_CAPABILITY" })
    }

    @Test
    fun `empty shutdown sequence is valid`() {
        val workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.um980RoverPlan(shutdownSequence = ReceiverCommandSequence.emptyShutdown())

        val result = validator.validate(workflow, plan)

        assertTrue(result.valid, result.errors.toString())
        assertEquals(emptyList<String>(), plan.shutdownCommands())
    }

    @Test
    fun `replay workflow accepts empty replay command plan`() {
        val workflow = WorkflowExamples.replayTest(ReceiverCapabilityFixtures.um980N4())
        val plan = ReceiverCommandPlanExamples.replayPlan(workflow.receiverProfileId)

        val result = validator.validate(workflow, plan)

        assertTrue(result.valid, result.errors.toString())
        assertEquals(emptyList<String>(), plan.startupCommands())
    }

    @Test
    fun `safe reference plan uses selected receiver profile id`() {
        val plan = ReceiverCommandPlanExamples.safeReferencePlan(
            receiverProfileId = "ublox-m8p2",
            receiverRole = ReceiverRole.FIXED_BASE,
        )

        assertEquals("ublox-m8p2", plan.receiverProfileId)
        assertEquals(ReceiverCommandPhase.FIXED_BASE, plan.modeSequence.phase)
        assertEquals(
            listOf(
                "# ublox-m8p2 init commands intentionally omitted",
                "# ublox-m8p2 fixed-base commands intentionally omitted",
            ),
            plan.startupCommands(),
        )
    }
}
