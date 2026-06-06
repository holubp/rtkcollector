package org.rtkcollector.core.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkflowDryRunSessionTest {
    @Test
    fun `valid workflow and command plan can start dry-run recording`() {
        val session = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4()),
            commandPlan = ReceiverCommandPlanExamples.um980RoverPlan(),
        )

        val recording = session.start()

        assertEquals(DryRunRecordingState.RECORDING, recording.state)
        assertTrue(recording.canStop)
        assertFalse(recording.canStart)
        assertEquals(2, recording.startupCommands.size)
        assertTrue(recording.observables.rawObservationStatus.contains("1.0 Hz"))
        assertTrue(recording.observables.ntripState.contains("dry-run configured"))
    }

    @Test
    fun `invalid workflow cannot start dry-run recording and surfaces fixed-base capability error`() {
        val session = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.fixedBaseFromBasePosition(ReceiverCapabilityFixtures.ubloxM8p0()),
            commandPlan = ReceiverCommandPlanExamples.um980FixedBasePlan(),
        )

        val result = session.start()

        assertEquals(DryRunRecordingState.BLOCKED, result.state)
        assertFalse(result.validation.valid)
        assertTrue(result.validation.errors.any { it.code == "FIXED_BASE_REQUIRES_CAPABILITY" })
    }

    @Test
    fun `invalid command plan cannot start dry-run recording and surfaces command mode mismatch`() {
        val session = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()),
            commandPlan = ReceiverCommandPlanExamples.um980FixedBasePlan(),
        )

        val result = session.start()

        assertEquals(DryRunRecordingState.BLOCKED, result.state)
        assertFalse(result.validation.valid)
        assertTrue(result.validation.errors.any { it.code == "COMMAND_MODE_DOES_NOT_MATCH_WORKFLOW" })
    }

    @Test
    fun `workflow warnings and command warnings are both surfaced`() {
        val baseWorkflow = WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4())
        val workflow = baseWorkflow.copy(
            receiverProfileId = "um980-n4",
            observationRequirement = ObservationRequirement.NONE,
            recording = baseWorkflow.recording.copy(
                recordRawObservationsRequested = false,
            ),
        )
        val commandPlan = ReceiverCommandPlanExamples.um980RoverPlan()
            .copy(receiverProfileId = "different-profile")

        val session = WorkflowDryRunSession.create(workflow, commandPlan)

        assertTrue(session.validation.valid, session.validation.errors.toString())
        assertTrue(session.validation.warnings.any { it.code == "NTRIP_ROVER_WITHOUT_RAW_LIMITS_POSTPROCESSING" })
        assertTrue(
            session.validation.warnings.any {
                it.code == "COMMAND_PROFILE_DIFFERS_FROM_WORKFLOW_PROFILE"
            },
        )
    }

    @Test
    fun `stop without shutdown commands records shutdown not configured`() {
        val recording = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()),
            commandPlan = ReceiverCommandPlanExamples.um980RoverPlan(),
        ).start()

        val stopped = recording.stop(transportAvailable = true)

        assertEquals(DryRunRecordingState.STOPPED, stopped.state)
        assertEquals(ShutdownCommandStatus.NOT_CONFIGURED, stopped.shutdownStatus)
        assertEquals(emptyList<String>(), stopped.shutdownCommands)
        assertFalse(stopped.canStop)
    }

    @Test
    fun `stop with configured shutdown commands records sent status and command list`() {
        val shutdownCommands = listOf("# shutdown commands intentionally omitted")
        val recording = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()),
            commandPlan = ReceiverCommandPlanExamples.um980RoverPlan(
                shutdownSequence = ReceiverCommandSequence(
                    id = "shutdown",
                    name = "Shutdown",
                    phase = ReceiverCommandPhase.SHUTDOWN,
                    commands = shutdownCommands,
                ),
            ),
        ).start()

        val stopped = recording.stop(transportAvailable = true)

        assertEquals(DryRunRecordingState.STOPPED, stopped.state)
        assertEquals(ShutdownCommandStatus.SENT, stopped.shutdownStatus)
        assertEquals(shutdownCommands, stopped.shutdownCommands)
    }

    @Test
    fun `stop with configured shutdown commands and missing transport records skipped status`() {
        val recording = WorkflowDryRunSession.create(
            workflow = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()),
            commandPlan = ReceiverCommandPlanExamples.um980RoverPlan(
                shutdownSequence = ReceiverCommandSequence(
                    id = "shutdown",
                    name = "Shutdown",
                    phase = ReceiverCommandPhase.SHUTDOWN,
                    commands = listOf("# shutdown commands intentionally omitted"),
                ),
            ),
        ).start()

        val stopped = recording.stop(transportAvailable = false)

        assertEquals(DryRunRecordingState.STOPPED, stopped.state)
        assertEquals(ShutdownCommandStatus.SKIPPED_TRANSPORT_UNAVAILABLE, stopped.shutdownStatus)
    }
}
