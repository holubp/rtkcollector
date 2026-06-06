package org.rtkcollector.core.workflow

enum class ReceiverCommandPhase {
    INIT,
    ROVER,
    BASE_CALIBRATION,
    FIXED_BASE,
    SHUTDOWN,
}

data class ReceiverCommandSequence(
    val id: String,
    val name: String,
    val phase: ReceiverCommandPhase,
    val commands: List<String> = emptyList(),
    val source: ReceiverCommandSource = ReceiverCommandSource.BUILT_IN_SAFE_REFERENCE,
) {
    companion object {
        fun emptyShutdown(): ReceiverCommandSequence =
            ReceiverCommandSequence(
                id = "empty-shutdown",
                name = "No shutdown commands",
                phase = ReceiverCommandPhase.SHUTDOWN,
                commands = emptyList(),
            )
    }
}

enum class ReceiverCommandSource {
    BUILT_IN_SAFE_REFERENCE,
    USER_SCRIPT_REFERENCE,
    PROFILE_REFERENCE,
}

data class ReceiverCommandPlan(
    val receiverProfileId: String,
    val initSequence: ReceiverCommandSequence,
    val modeSequence: ReceiverCommandSequence,
    val shutdownSequence: ReceiverCommandSequence = ReceiverCommandSequence.emptyShutdown(),
    val customCommandsRequested: Boolean = false,
) {
    fun startupSequences(): List<ReceiverCommandSequence> = listOf(initSequence, modeSequence)

    fun startupCommands(): List<String> = startupSequences().flatMap { it.commands }

    fun shutdownCommands(): List<String> = shutdownSequence.commands
}

object ReceiverCommandPlanExamples {
    fun genericRoverPlan(): ReceiverCommandPlan =
        ReceiverCommandPlan(
            receiverProfileId = "generic-nmea-rtcm",
            initSequence = ReceiverCommandSequence(
                id = "generic-init",
                name = "Generic NMEA/RTCM init reference",
                phase = ReceiverCommandPhase.INIT,
                commands = listOf("# Generic receiver init commands intentionally omitted"),
            ),
            modeSequence = ReceiverCommandSequence(
                id = "generic-rover",
                name = "Generic rover mode reference",
                phase = ReceiverCommandPhase.ROVER,
                commands = listOf("# Generic rover commands intentionally omitted"),
            ),
        )

    fun um980RoverPlan(
        shutdownSequence: ReceiverCommandSequence = ReceiverCommandSequence.emptyShutdown(),
    ): ReceiverCommandPlan =
        ReceiverCommandPlan(
            receiverProfileId = "um980-n4",
            initSequence = ReceiverCommandSequence(
                id = "um980-init-reference",
                name = "UM980/N4 init reference",
                phase = ReceiverCommandPhase.INIT,
                commands = listOf("# UM980 init commands intentionally omitted"),
            ),
            modeSequence = ReceiverCommandSequence(
                id = "um980-rover-reference",
                name = "UM980/N4 rover reference",
                phase = ReceiverCommandPhase.ROVER,
                commands = listOf("# UM980 rover commands intentionally omitted"),
            ),
            shutdownSequence = shutdownSequence,
        )

    fun um980BaseCalibrationPlan(): ReceiverCommandPlan =
        ReceiverCommandPlan(
            receiverProfileId = "um980-n4",
            initSequence = ReceiverCommandSequence(
                id = "um980-init-reference",
                name = "UM980/N4 init reference",
                phase = ReceiverCommandPhase.INIT,
                commands = listOf("# UM980 init commands intentionally omitted"),
            ),
            modeSequence = ReceiverCommandSequence(
                id = "um980-base-calibration-reference",
                name = "UM980/N4 base calibration reference",
                phase = ReceiverCommandPhase.BASE_CALIBRATION,
                commands = listOf("# UM980 base-calibration commands intentionally omitted"),
            ),
        )

    fun um980FixedBasePlan(): ReceiverCommandPlan =
        ReceiverCommandPlan(
            receiverProfileId = "um980-n4",
            initSequence = ReceiverCommandSequence(
                id = "um980-init-reference",
                name = "UM980/N4 init reference",
                phase = ReceiverCommandPhase.INIT,
                commands = listOf("# UM980 init commands intentionally omitted"),
            ),
            modeSequence = ReceiverCommandSequence(
                id = "um980-fixed-base-reference",
                name = "UM980/N4 fixed-base reference",
                phase = ReceiverCommandPhase.FIXED_BASE,
                commands = listOf("# UM980 fixed-base commands intentionally omitted"),
            ),
        )

    fun replayPlan(receiverProfileId: String = "file-replay"): ReceiverCommandPlan =
        ReceiverCommandPlan(
            receiverProfileId = receiverProfileId,
            initSequence = ReceiverCommandSequence(
                id = "replay-no-init",
                name = "Replay has no receiver init commands",
                phase = ReceiverCommandPhase.INIT,
                commands = emptyList(),
            ),
            modeSequence = ReceiverCommandSequence(
                id = "replay-no-mode",
                name = "Replay has no receiver mode commands",
                phase = ReceiverCommandPhase.INIT,
                commands = emptyList(),
            ),
        )

    fun safeReferencePlan(
        receiverProfileId: String,
        receiverRole: ReceiverRole,
    ): ReceiverCommandPlan {
        val phase = when (receiverRole) {
            ReceiverRole.ROVER -> ReceiverCommandPhase.ROVER
            ReceiverRole.BASE_CALIBRATION -> ReceiverCommandPhase.BASE_CALIBRATION
            ReceiverRole.FIXED_BASE -> ReceiverCommandPhase.FIXED_BASE
            ReceiverRole.REPLAY_TEST -> ReceiverCommandPhase.INIT
        }
        val modeLabel = when (phase) {
            ReceiverCommandPhase.INIT -> "replay"
            ReceiverCommandPhase.ROVER -> "rover"
            ReceiverCommandPhase.BASE_CALIBRATION -> "base-calibration"
            ReceiverCommandPhase.FIXED_BASE -> "fixed-base"
            ReceiverCommandPhase.SHUTDOWN -> "shutdown"
        }

        return ReceiverCommandPlan(
            receiverProfileId = receiverProfileId,
            initSequence = ReceiverCommandSequence(
                id = "$receiverProfileId-init-reference",
                name = "$receiverProfileId init reference",
                phase = ReceiverCommandPhase.INIT,
                commands = if (receiverRole == ReceiverRole.REPLAY_TEST) {
                    emptyList()
                } else {
                    listOf("# $receiverProfileId init commands intentionally omitted")
                },
            ),
            modeSequence = ReceiverCommandSequence(
                id = "$receiverProfileId-$modeLabel-reference",
                name = "$receiverProfileId $modeLabel reference",
                phase = phase,
                commands = if (receiverRole == ReceiverRole.REPLAY_TEST) {
                    emptyList()
                } else {
                    listOf("# $receiverProfileId $modeLabel commands intentionally omitted")
                },
            ),
        )
    }
}

class ReceiverCommandPlanValidator {
    fun validate(
        workflow: WorkflowSpec,
        commandPlan: ReceiverCommandPlan,
    ): WorkflowValidationResult {
        val errors = mutableListOf<WorkflowValidationMessage>()
        val warnings = mutableListOf<WorkflowValidationMessage>()

        if (commandPlan.receiverProfileId != workflow.receiverProfileId) {
            warnings += WorkflowValidationMessage(
                code = "COMMAND_PROFILE_DIFFERS_FROM_WORKFLOW_PROFILE",
                message = "Receiver command plan profile differs from the workflow receiver profile.",
            )
        }

        if (commandPlan.initSequence.phase != ReceiverCommandPhase.INIT) {
            errors += WorkflowValidationMessage(
                code = "INIT_SEQUENCE_REQUIRES_INIT_PHASE",
                message = "The receiver init sequence must use the INIT command phase.",
            )
        }

        val expectedMode = workflow.expectedCommandPhase()
        if (commandPlan.modeSequence.phase != expectedMode) {
            errors += WorkflowValidationMessage(
                code = "COMMAND_MODE_DOES_NOT_MATCH_WORKFLOW",
                message = "The selected receiver mode command sequence must match the workflow receiver role.",
            )
        }

        if (commandPlan.shutdownSequence.phase != ReceiverCommandPhase.SHUTDOWN) {
            errors += WorkflowValidationMessage(
                code = "SHUTDOWN_SEQUENCE_REQUIRES_SHUTDOWN_PHASE",
                message = "The receiver shutdown sequence must use the SHUTDOWN command phase.",
            )
        }

        if (commandPlan.customCommandsRequested && !workflow.receiverCapabilities.supportsCustomInitCommands) {
            errors += WorkflowValidationMessage(
                code = "CUSTOM_COMMANDS_REQUIRE_CAPABILITY",
                message = "Custom receiver command scripts require receiver custom-init capability.",
            )
        }

        return WorkflowValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    private fun WorkflowSpec.expectedCommandPhase(): ReceiverCommandPhase =
        when (receiverRole) {
            ReceiverRole.ROVER -> ReceiverCommandPhase.ROVER
            ReceiverRole.BASE_CALIBRATION -> ReceiverCommandPhase.BASE_CALIBRATION
            ReceiverRole.FIXED_BASE -> ReceiverCommandPhase.FIXED_BASE
            ReceiverRole.REPLAY_TEST -> ReceiverCommandPhase.INIT
        }
}
