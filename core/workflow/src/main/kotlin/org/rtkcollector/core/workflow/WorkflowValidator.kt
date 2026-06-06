package org.rtkcollector.core.workflow

class WorkflowValidator {
    fun validate(spec: WorkflowSpec): WorkflowValidationResult {
        val errors = mutableListOf<WorkflowValidationMessage>()
        val warnings = mutableListOf<WorkflowValidationMessage>()

        validateCorrectionTopology(spec, errors)
        validateReceiverRoleCapability(spec, errors)
        validateRtklib(spec, errors, warnings)
        validateFixedBase(spec, errors)
        validateRecordingSpec(spec, errors)
        validateTemporaryBasePreparation(spec, errors, warnings)
        validateRawObservationRequirements(spec, errors, warnings)
        validateInternalRtk(spec, errors)
        validateReceiverPpp(spec, errors, warnings)
        validateNtrip(spec, errors, warnings)
        validateBasePositionCandidatePolicy(spec, errors, warnings)
        validateBasePositionWarnings(spec, warnings)
        validateSafety(spec, errors, warnings)

        return WorkflowValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    private fun validateReceiverRoleCapability(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
    ) {
        when (spec.receiverRole) {
            ReceiverRole.ROVER -> if (!spec.receiverCapabilities.supportsRoverMode) {
                errors += error(
                    "ROVER_REQUIRES_CAPABILITY",
                    "Rover workflow requires receiver rover capability.",
                )
            }

            ReceiverRole.BASE_CALIBRATION -> if (!spec.receiverCapabilities.supportsBaseCalibrationMode) {
                errors += error(
                    "BASE_CALIBRATION_REQUIRES_CAPABILITY",
                    "Base-calibration workflow requires receiver base-calibration capability.",
                )
            }

            ReceiverRole.FIXED_BASE,
            ReceiverRole.REPLAY_TEST,
            -> Unit
        }
    }

    private fun validateCorrectionTopology(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
    ) {
        if (isPlainRover(spec) && spec.correctionTargets.isNotEmpty()) {
            errors += error(
                "PLAIN_ROVER_HAS_CORRECTION_TARGET",
                "Plain rover recording must not have correction targets.",
            )
        }

        if (spec.receiverRole == ReceiverRole.ROVER &&
            spec.correctionSource is CorrectionSourceSpec.Ntrip &&
            spec.correctionTargets.isEmpty() &&
            SolutionEngine.RTKLIB_REALTIME !in spec.solutionEngines
        ) {
            errors += error(
                "PLAIN_ROVER_HAS_NTRIP_SOURCE",
                "Plain rover recording must not have NTRIP correction source.",
            )
        }

        if (spec.correctionTargets.isNotEmpty() && spec.correctionSource is CorrectionSourceSpec.None) {
            errors += error(
                "CORRECTION_TARGET_REQUIRES_SOURCE",
                "Correction targets require a non-empty correction source.",
            )
        }

        if (CorrectionTarget.RECEIVER in spec.correctionTargets &&
            spec.correctionSource.isRtcmCorrectionSource() &&
            !spec.receiverCapabilities.supportsRtcmInput
        ) {
            errors += error(
                "RECEIVER_TARGET_REQUIRES_RTCM_INPUT",
                "Receiver correction target requires receiver RTCM input support.",
            )
        }
    }

    private fun validateRtklib(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
        warnings: MutableList<WorkflowValidationMessage>,
    ) {
        if (SolutionEngine.RTKLIB_REALTIME !in spec.solutionEngines) {
            return
        }

        if (CorrectionTarget.RTKLIB !in spec.correctionTargets) {
            errors += error(
                "RTKLIB_REQUIRES_CORRECTION_TARGET",
                "RTKLIB real-time solution requires RTKLIB as an explicit correction target.",
            )
        }

        if (spec.baseContext is BaseContextSpec.None) {
            errors += error(
                "RTKLIB_REQUIRES_BASE_CONTEXT",
                "RTKLIB real-time solution requires base context.",
            )
        }

        if (spec.correctionSource is CorrectionSourceSpec.None && !spec.baseContext.hasLocalOrRecordedBaseObservation()) {
            errors += error(
                "RTKLIB_REQUIRES_CORRECTION_OR_BASE_OBSERVATION_SOURCE",
                "RTKLIB real-time solution requires correction source or local/recorded base observation source.",
            )
        }

        if (!spec.correctionSource.supportsRtklibCorrectionInput(spec.rtklibRawConverterId)) {
            errors += error(
                "RTKLIB_REQUIRES_SUPPORTED_CORRECTION_FORMAT",
                "RTKLIB real-time requires RTCM observation corrections or an explicit converter for the correction source format.",
            )
        }

        if (!spec.hasRtklibCompatibleRawOrConverter()) {
            errors += error(
                "RTKLIB_REQUIRES_COMPATIBLE_RAW",
                "RTKLIB real-time requires RTKLIB-compatible raw observations or an explicit converter.",
            )
        }

        if (ObservationRequirement.RTKLIB_COMPATIBLE_REQUIRED == spec.observationRequirement &&
            !spec.hasRtklibCompatibleRawOrConverter()
        ) {
            errors += error(
                "RTKLIB_COMPATIBLE_REQUIRED_REQUIRES_CAPABILITY",
                "RTKLIB-compatible observation requirement requires compatible raw output or converter configuration.",
            )
        }

        if (SolutionEngine.DEVICE_INTERNAL in spec.solutionEngines) {
            warnings += warning(
                "RTKLIB_AND_DEVICE_SOLUTIONS_ARE_SEPARATE",
                "RTKLIB real-time and receiver-internal solutions are separate engines and must be displayed and logged independently.",
            )
        }
    }

    private fun validateFixedBase(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
    ) {
        if (spec.receiverRole != ReceiverRole.FIXED_BASE) {
            return
        }

        if (!spec.baseContext.hasAcceptedBasePosition()) {
            errors += error(
                "FIXED_BASE_REQUIRES_BASE_POSITION",
                "Fixed-base operation requires an accepted base position, base-position file or manual coordinate.",
            )
        }

        if (!spec.receiverCapabilities.supportsFixedBaseMode) {
            errors += error(
                "FIXED_BASE_REQUIRES_CAPABILITY",
                "Fixed-base operation requires receiver fixed-base capability.",
            )
        }

        if (spec.baseContext is BaseContextSpec.RecordedBaseSession && spec.baseContext.acceptedBasePosition == null) {
            errors += error(
                "FIXED_BASE_REQUIRES_ACCEPTED_BASE_POSITION_CANDIDATE",
                "Fixed-base streaming must not start from a base-calibration session without an accepted base-position candidate.",
            )
        }
    }

    private fun validateRawObservationRequirements(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
        warnings: MutableList<WorkflowValidationMessage>,
    ) {
        if (spec.observationRequirement == ObservationRequirement.RAW_REQUIRED &&
            !spec.receiverCapabilities.supportsRawObservations
        ) {
            errors += error(
                "RAW_REQUIRED_REQUIRES_CAPABILITY",
                "Raw observation requirement requires receiver raw-observation support.",
            )
        }

        if (spec.receiverRole == ReceiverRole.BASE_CALIBRATION &&
            spec.receiverCapabilities.supportsRawObservations &&
            (!spec.recording.recordRawObservationsRequested || spec.observationRequirement == ObservationRequirement.NONE)
        ) {
            warnings += warning(
                "BASE_CALIBRATION_SHOULD_REQUEST_RAW",
                "Base calibration should request raw observations when the receiver supports them.",
            )
        }

        if (spec.receiverRole == ReceiverRole.ROVER &&
            spec.correctionSource is CorrectionSourceSpec.Ntrip &&
            CorrectionTarget.RECEIVER in spec.correctionTargets &&
            (!spec.recording.recordRawObservationsRequested || spec.observationRequirement == ObservationRequirement.NONE)
        ) {
            warnings += warning(
                "NTRIP_ROVER_WITHOUT_RAW_LIMITS_POSTPROCESSING",
                "Rover + NTRIP without raw observations limits later post-processing validation.",
            )
        }
    }

    private fun validateRecordingSpec(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
    ) {
        if (!spec.recording.recordRawReceiverStream) {
            errors += error(
                "RAW_RECEIVER_STREAM_REQUIRED",
                "Workflow recording must keep the raw receiver RX stream authoritative.",
            )
        }

        if (SessionArtifact.RECEIVER_RX_RAW !in spec.recording.expectedSessionArtifacts) {
            errors += error(
                "RECEIVER_RX_ARTIFACT_REQUIRED",
                "Workflow session artifacts must include receiver-rx.raw.",
            )
        }

        if (SessionArtifact.EVENTS_JSONL !in spec.recording.expectedSessionArtifacts) {
            errors += error(
                "EVENTS_LOG_ARTIFACT_REQUIRED",
                "Workflow session artifacts must include events.jsonl.",
            )
        }

        if (spec.recording.recordTxToReceiver &&
            SessionArtifact.TX_TO_RECEIVER_RAW !in spec.recording.expectedSessionArtifacts
        ) {
            errors += error(
                "TX_ARTIFACT_REQUIRED",
                "Receiver TX recording requires tx-to-receiver.raw as an expected artifact.",
            )
        }

        if (spec.recording.recordCorrectionInput &&
            SessionArtifact.CORRECTION_INPUT_RAW !in spec.recording.expectedSessionArtifacts
        ) {
            errors += error(
                "CORRECTION_INPUT_ARTIFACT_REQUIRED",
                "Correction input recording requires a correction input sidecar as an expected artifact.",
            )
        }

        if (spec.recording.recordQualityEvents &&
            SessionArtifact.QUALITY_LIVE_JSONL !in spec.recording.expectedSessionArtifacts
        ) {
            errors += error(
                "QUALITY_ARTIFACT_REQUIRED",
                "Quality event recording requires quality-live.jsonl as an expected artifact.",
            )
        }

        if (spec.recording.recordPppSolution &&
            SessionArtifact.RECEIVER_PPP_SOLUTION_JSONL !in spec.recording.expectedSessionArtifacts
        ) {
            errors += error(
                "PPP_ARTIFACT_REQUIRED",
                "Receiver PPP solution recording requires receiver-ppp-solution.jsonl as an expected artifact.",
            )
        }
    }

    private fun validateTemporaryBasePreparation(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
        warnings: MutableList<WorkflowValidationMessage>,
    ) {
        if (spec.receiverRole != ReceiverRole.BASE_CALIBRATION) {
            return
        }

        if (SolutionEngine.DEVICE_INTERNAL !in spec.solutionEngines || !spec.recording.recordDeviceSolution) {
            errors += error(
                "BASE_PREPARATION_REQUIRES_DEVICE_SOLUTION",
                "Temporary-base preparation must record the receiver's normal in-device solution.",
            )
        }

        if (spec.receiverCapabilities.supportsRawObservations) {
            if (!spec.recording.recordRawObservationsRequested ||
                spec.observationRequirement == ObservationRequirement.NONE ||
                (spec.recording.rawObservationMinimumRateHz ?: 0.0) < 1.0
            ) {
                errors += error(
                    "BASE_PREPARATION_REQUIRES_RAW_OBSERVATION_RATE",
                    "Temporary-base preparation with a raw-capable receiver must request raw observations at 1 Hz or higher.",
                )
            }
        } else {
            warnings += warning(
                "BASE_PREPARATION_RAW_OBSERVATIONS_UNAVAILABLE",
                "Temporary-base preparation without raw observations cannot support static RTK or RTCM3 observation conversion.",
            )
        }

        if (spec.receiverCapabilities.supportsReceiverPppSolution &&
            (!spec.recording.recordPppSolution || SolutionEngine.RECEIVER_PPP !in spec.solutionEngines)
        ) {
            errors += error(
                "BASE_PREPARATION_REQUIRES_PPP_RECORDING_WHEN_SUPPORTED",
                "Temporary-base preparation must request receiver PPP solution recording when the receiver profile supports it.",
            )
        }

        if (spec.basePositionCandidateGeneration.candidateMethods.isEmpty()) {
            errors += error(
                "BASE_PREPARATION_REQUIRES_CANDIDATE_METHODS",
                "Temporary-base preparation must declare base-position candidate generation methods.",
            )
        }
    }

    private fun validateInternalRtk(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
    ) {
        val expectsReceiverRtk = spec.receiverRole == ReceiverRole.ROVER &&
            SolutionEngine.DEVICE_INTERNAL in spec.solutionEngines &&
            CorrectionTarget.RECEIVER in spec.correctionTargets &&
            spec.correctionSource.isRtcmCorrectionSource()

        if (expectsReceiverRtk && !spec.receiverCapabilities.supportsInternalRtk) {
            errors += error(
                "DEVICE_INTERNAL_RTK_REQUIRES_CAPABILITY",
                "Receiver-internal RTK workflow requires receiver internal RTK capability.",
            )
        }
    }

    private fun validateReceiverPpp(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
        warnings: MutableList<WorkflowValidationMessage>,
    ) {
        val pppEngineEnabled = SolutionEngine.RECEIVER_PPP in spec.solutionEngines

        if ((pppEngineEnabled || spec.recording.recordPppSolution) &&
            !spec.receiverCapabilities.supportsReceiverPppSolution
        ) {
            warnings += warning(
                "RECEIVER_PPP_UNSUPPORTED_BY_PROFILE",
                "Receiver PPP solution recording was requested for a profile that does not declare receiver PPP support.",
            )
        }

        if (pppEngineEnabled && !spec.recording.recordPppSolution) {
            errors += error(
                "RECEIVER_PPP_ENGINE_REQUIRES_RECORDING",
                "Receiver PPP solution engine requires receiver PPP solution recording.",
            )
        }
    }

    private fun validateNtrip(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
        warnings: MutableList<WorkflowValidationMessage>,
    ) {
        val ntrip = spec.correctionSource as? CorrectionSourceSpec.Ntrip ?: return

        if (ntrip.casterHost.isBlank() || ntrip.port <= 0 || ntrip.mountpoint.isBlank()) {
            errors += error(
                "NTRIP_REQUIRES_HOST_PORT_MOUNTPOINT",
                "NTRIP source requires host, positive port and mountpoint.",
            )
        }

        if (ntrip.plaintextUsername != null || ntrip.plaintextPassword != null) {
            errors += error(
                "NTRIP_CREDENTIALS_MUST_BE_SECRET_REFERENCES",
                "NTRIP credentials must be secret references, not plaintext export values.",
            )
        }

        if (spec.baseContext is BaseContextSpec.None &&
            (spec.correctionTargets.isNotEmpty() || SolutionEngine.RTKLIB_REALTIME in spec.solutionEngines)
        ) {
            errors += error(
                "NTRIP_REQUIRES_BASE_CONTEXT",
                "NTRIP correction workflows require explicit base or mountpoint context.",
            )
        }

        val ntripContext = spec.baseContext as? BaseContextSpec.NtripMountpoint
        if (spec.baseContext !is BaseContextSpec.None && ntripContext == null &&
            (spec.correctionTargets.isNotEmpty() || SolutionEngine.RTKLIB_REALTIME in spec.solutionEngines)
        ) {
            errors += error(
                "NTRIP_REQUIRES_MOUNTPOINT_BASE_CONTEXT",
                "NTRIP correction workflows require base context that represents the NTRIP/CORS mountpoint.",
            )
        }

        if (ntripContext != null &&
            (!ntripContext.casterHost.equals(ntrip.casterHost, ignoreCase = true) ||
                ntripContext.mountpoint != ntrip.mountpoint)
        ) {
            errors += error(
                "NTRIP_BASE_CONTEXT_MISMATCH",
                "NTRIP base context must match the correction source caster host and mountpoint.",
            )
        }

        if (ntrip.stationId.isNullOrBlank() &&
            ntrip.stationName.isNullOrBlank() &&
            ntrip.approximateBasePosition == null
        ) {
            warnings += warning(
                "NTRIP_MOUNTPOINT_METADATA_WEAK",
                "NTRIP mountpoint without approximate station/base metadata weakens reproducibility.",
            )
        }
    }

    private fun validateBasePositionWarnings(
        spec: WorkflowSpec,
        warnings: MutableList<WorkflowValidationMessage>,
    ) {
        val basePosition = spec.baseContext.basePosition() ?: return

        if (basePosition.frame.isNullOrBlank()) {
            warnings += warning(
                "BASE_POSITION_MISSING_FRAME",
                "Base position should include reference frame or datum.",
            )
        }

        if (basePosition.antennaHeightM == null || basePosition.antennaReferencePoint.isNullOrBlank()) {
            warnings += warning(
                "BASE_POSITION_MISSING_ANTENNA",
                "Base position should include antenna height and antenna reference point.",
            )
        }

        if (basePosition.method == BasePositionMethod.LONG_AVERAGE &&
            (basePosition.durationSeconds == null ||
                basePosition.horizontalUncertaintyM == null ||
                basePosition.verticalUncertaintyM == null)
        ) {
            warnings += warning(
                "LONG_AVERAGE_BASE_POSITION_IS_FALLBACK",
                "Long-average base positions are fallback/lower-grade unless uncertainty and duration metadata are present.",
            )
        }
    }

    private fun validateBasePositionCandidatePolicy(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
        warnings: MutableList<WorkflowValidationMessage>,
    ) {
        val methods = spec.basePositionCandidateGeneration.candidateMethods

        if (methods.distinct().size != methods.size) {
            errors += error(
                "BASE_POSITION_CANDIDATE_METHODS_MUST_BE_UNIQUE",
                "Base-position candidate generation methods must not contain duplicates.",
            )
        }

        val longAverageIndex = methods.indexOf(BasePositionMethod.LONG_AVERAGE)
        if (longAverageIndex >= 0) {
            val preferredMethodIndexes = methods.mapIndexedNotNull { index, method ->
                if (method == BasePositionMethod.STATIC_RTK ||
                    method == BasePositionMethod.PPP_STATIC ||
                    method == BasePositionMethod.RECEIVER_PPP
                ) {
                    index
                } else {
                    null
                }
            }
            if (preferredMethodIndexes.isEmpty() || preferredMethodIndexes.any { it > longAverageIndex }) {
                warnings += warning(
                    "LONG_AVERAGE_CANDIDATE_IS_FALLBACK",
                    "Long-average base-position candidates are fallback/lower-grade and should rank after static RTK, PPP/static or receiver PPP candidates.",
                )
            }
        }
    }

    private fun validateSafety(
        spec: WorkflowSpec,
        errors: MutableList<WorkflowValidationMessage>,
        warnings: MutableList<WorkflowValidationMessage>,
    ) {
        if (spec.safety.allowSecretsInSessionJson) {
            errors += error(
                "SECRETS_IN_SESSION_JSON_FORBIDDEN",
                "Workflow safety must forbid secrets in session.json.",
            )
        }

        if (spec.receiverRole != ReceiverRole.REPLAY_TEST && !spec.safety.requireForegroundService) {
            errors += error(
                "FOREGROUND_SERVICE_REQUIRED",
                "Android recording workflows must require foreground-service execution.",
            )
        }

        if (spec.receiverRole != ReceiverRole.REPLAY_TEST && !spec.safety.requireWakeLockDuringRecording) {
            errors += error(
                "WAKE_LOCK_REQUIRED",
                "Android recording workflows must require a wake lock while recording.",
            )
        }

        if (spec.customInitCommandsRequested && !spec.receiverCapabilities.supportsCustomInitCommands) {
            warnings += warning(
                "CUSTOM_INIT_UNSUPPORTED_BY_PROFILE",
                "Custom init commands were requested for a profile that does not support custom init commands.",
            )
        }
    }

    private fun isPlainRover(spec: WorkflowSpec): Boolean =
        spec.receiverRole == ReceiverRole.ROVER &&
            spec.correctionSource is CorrectionSourceSpec.None &&
            SolutionEngine.RTKLIB_REALTIME !in spec.solutionEngines

    private fun WorkflowSpec.hasRtklibCompatibleRawOrConverter(): Boolean =
        receiverCapabilities.supportsRtklibCompatibleRaw ||
            receiverCapabilities.supportsRtklibRawConverter ||
            rtklibRawConverterId != null

    private fun CorrectionSourceSpec.isRtcmCorrectionSource(): Boolean =
        when (this) {
            is CorrectionSourceSpec.Ntrip -> true
            is CorrectionSourceSpec.LocalBaseStream -> true
            is CorrectionSourceSpec.ExternalSerialOrTcp -> true
            is CorrectionSourceSpec.FileReplay -> expectedCorrectionFormat == CorrectionFormat.RTCM3 ||
                expectedCorrectionFormat == CorrectionFormat.RTCM_OBSERVATIONS
            CorrectionSourceSpec.None -> false
        }

    private fun CorrectionSourceSpec.supportsRtklibCorrectionInput(rtklibRawConverterId: String?): Boolean =
        when (this) {
            is CorrectionSourceSpec.Ntrip -> expectedCorrectionFormat.isRtklibInputCompatible(rtklibRawConverterId)
            is CorrectionSourceSpec.LocalBaseStream -> expectedCorrectionFormat.isRtklibInputCompatible(rtklibRawConverterId)
            is CorrectionSourceSpec.ExternalSerialOrTcp -> expectedCorrectionFormat.isRtklibInputCompatible(rtklibRawConverterId)
            is CorrectionSourceSpec.FileReplay -> expectedCorrectionFormat.isRtklibInputCompatible(rtklibRawConverterId)
            CorrectionSourceSpec.None -> false
        }

    private fun CorrectionFormat.isRtklibInputCompatible(rtklibRawConverterId: String?): Boolean =
        this == CorrectionFormat.RTCM3 ||
            this == CorrectionFormat.RTCM_OBSERVATIONS ||
            (this == CorrectionFormat.RECEIVER_NATIVE_RAW && rtklibRawConverterId != null)

    private fun BaseContextSpec.hasLocalOrRecordedBaseObservation(): Boolean =
        this is BaseContextSpec.LocalBaseStream || this is BaseContextSpec.RecordedBaseSession

    private fun BaseContextSpec.hasAcceptedBasePosition(): Boolean =
        when (this) {
            is BaseContextSpec.BasePositionFile -> true
            is BaseContextSpec.KnownStation -> basePosition != null
            is BaseContextSpec.ManualCoordinate -> true
            is BaseContextSpec.RecordedBaseSession -> acceptedBasePosition != null
            is BaseContextSpec.LocalBaseStream -> approximateBasePosition != null
            is BaseContextSpec.NtripMountpoint -> approximateBasePosition != null
            BaseContextSpec.None -> false
        }

    private fun BaseContextSpec.basePosition(): BasePosition? =
        when (this) {
            is BaseContextSpec.BasePositionFile -> basePosition
            is BaseContextSpec.KnownStation -> basePosition
            is BaseContextSpec.ManualCoordinate -> basePosition
            is BaseContextSpec.RecordedBaseSession -> acceptedBasePosition
            is BaseContextSpec.LocalBaseStream -> approximateBasePosition
            is BaseContextSpec.NtripMountpoint -> approximateBasePosition
            BaseContextSpec.None -> null
        }

    private fun error(code: String, message: String): WorkflowValidationMessage =
        WorkflowValidationMessage(code = code, message = message)

    private fun warning(code: String, message: String): WorkflowValidationMessage =
        WorkflowValidationMessage(code = code, message = message)
}
