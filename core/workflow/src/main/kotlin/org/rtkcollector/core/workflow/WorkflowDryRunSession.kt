package org.rtkcollector.core.workflow

enum class DryRunRecordingState {
    READY,
    RECORDING,
    BLOCKED,
    STOPPED,
}

enum class ShutdownCommandStatus {
    NOT_ATTEMPTED,
    NOT_CONFIGURED,
    SENT,
    SKIPPED_TRANSPORT_UNAVAILABLE,
}

data class RecordingObservables(
    val elapsedSeconds: Long = 0,
    val receiverRxBytes: Long = 0,
    val txToReceiverBytes: Long = 0,
    val correctionInputBytes: Long = 0,
    val serialThroughputBytesPerSecond: Long = 0,
    val latestDeviceSolution: String = "No device solution yet",
    val ntripState: String = "Not configured",
    val correctionAgeSeconds: Long? = null,
    val rawObservationStatus: String = "Not observed yet",
    val parserStatus: String = "No parser failures",
    val recordingHealth: String = "Raw recorder dry-run ready",
)

data class WorkflowDryRunSession(
    val workflow: WorkflowSpec,
    val commandPlan: ReceiverCommandPlan,
    val validation: WorkflowValidationResult,
    val state: DryRunRecordingState,
    val observables: RecordingObservables = RecordingObservables(),
    val startupCommands: List<String> = emptyList(),
    val shutdownCommands: List<String> = emptyList(),
    val shutdownStatus: ShutdownCommandStatus = ShutdownCommandStatus.NOT_ATTEMPTED,
) {
    val canStart: Boolean get() = state == DryRunRecordingState.READY && validation.valid

    val canStop: Boolean get() = state == DryRunRecordingState.RECORDING

    fun start(): WorkflowDryRunSession =
        if (!validation.valid) {
            copy(state = DryRunRecordingState.BLOCKED)
        } else {
            copy(
                state = DryRunRecordingState.RECORDING,
                startupCommands = commandPlan.startupCommands(),
                observables = observables.copy(
                    recordingHealth = "Raw recorder dry-run active",
                    rawObservationStatus = rawObservationStatus(),
                    ntripState = ntripState(),
                ),
            )
        }

    fun stop(transportAvailable: Boolean): WorkflowDryRunSession {
        val shutdown = commandPlan.shutdownCommands()
        val status = when {
            shutdown.isEmpty() -> ShutdownCommandStatus.NOT_CONFIGURED
            transportAvailable -> ShutdownCommandStatus.SENT
            else -> ShutdownCommandStatus.SKIPPED_TRANSPORT_UNAVAILABLE
        }

        return copy(
            state = DryRunRecordingState.STOPPED,
            shutdownCommands = shutdown,
            shutdownStatus = status,
            observables = observables.copy(recordingHealth = "Raw recorder dry-run stopped"),
        )
    }

    private fun rawObservationStatus(): String =
        if (workflow.recording.recordRawObservationsRequested) {
            "Raw observations requested at ${workflow.recording.rawObservationMinimumRateHz ?: 1.0} Hz or higher"
        } else {
            "Raw observations not requested for this receiver/profile"
        }

    private fun ntripState(): String =
        if (workflow.correctionSource is CorrectionSourceSpec.Ntrip) {
            "NTRIP dry-run configured"
        } else {
            "Not configured"
        }

    companion object {
        fun create(
            workflow: WorkflowSpec,
            commandPlan: ReceiverCommandPlan,
        ): WorkflowDryRunSession {
            val workflowValidation = WorkflowValidator().validate(workflow)
            val commandValidation = ReceiverCommandPlanValidator().validate(workflow, commandPlan)
            val combined = WorkflowValidationResult(
                valid = workflowValidation.valid && commandValidation.valid,
                errors = workflowValidation.errors + commandValidation.errors,
                warnings = workflowValidation.warnings + commandValidation.warnings,
            )

            return WorkflowDryRunSession(
                workflow = workflow,
                commandPlan = commandPlan,
                validation = combined,
                state = if (combined.valid) DryRunRecordingState.READY else DryRunRecordingState.BLOCKED,
            )
        }
    }
}
