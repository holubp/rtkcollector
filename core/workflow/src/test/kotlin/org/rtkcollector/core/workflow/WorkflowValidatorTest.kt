package org.rtkcollector.core.workflow

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkflowValidatorTest {
    private val validator = WorkflowValidator()

    @Test
    fun `plain rover recording with UM980 is valid`() {
        assertValid(WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()))
    }

    @Test
    fun `rover NTRIP to receiver with UM980 is valid`() {
        assertValid(WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4()))
    }

    @Test
    fun `rover NTRIP to receiver with M8P-0 is valid`() {
        assertValid(WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.ubloxM8p0()))
    }

    @Test
    fun `fixed base with M8P-2 and base position is valid`() {
        assertValid(WorkflowExamples.fixedBaseFromBasePosition(ReceiverCapabilityFixtures.ubloxM8p2()))
    }

    @Test
    fun `base calibration raw-only with M8T is valid`() {
        assertValid(WorkflowExamples.baseCalibrationRawOnly(ReceiverCapabilityFixtures.ubloxM8t()))
    }

    @Test
    fun `temporary base preparation with UM980 records raw observations device solution and receiver PPP`() {
        val spec = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.um980N4())

        assertValid(spec)
        assertTrue(spec.recording.recordRawReceiverStream)
        assertTrue(spec.recording.recordRawObservationsRequested)
        assertTrue((spec.recording.rawObservationMinimumRateHz ?: 0.0) >= 1.0)
        assertTrue(spec.recording.recordDeviceSolution)
        assertTrue(SolutionEngine.DEVICE_INTERNAL in spec.solutionEngines)
        assertTrue(spec.recording.recordPppSolution)
        assertTrue(SolutionEngine.RECEIVER_PPP in spec.solutionEngines)
        assertTrue(SessionArtifact.RECEIVER_PPP_SOLUTION_JSONL in spec.recording.expectedSessionArtifacts)
        assertTrue(
            spec.basePositionCandidateGeneration.candidateMethods.take(4) == listOf(
                BasePositionMethod.STATIC_RTK,
                BasePositionMethod.PPP_STATIC,
                BasePositionMethod.RECEIVER_PPP,
                BasePositionMethod.LONG_AVERAGE,
            ),
        )
    }

    @Test
    fun `temporary base preparation with M8T records raw and device solution without requiring receiver PPP`() {
        val spec = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.ubloxM8t())

        assertValid(spec)
        assertTrue(spec.recording.recordRawObservationsRequested)
        assertTrue((spec.recording.rawObservationMinimumRateHz ?: 0.0) >= 1.0)
        assertTrue(spec.recording.recordDeviceSolution)
        assertFalse(spec.recording.recordPppSolution)
        assertFalse(SolutionEngine.RECEIVER_PPP in spec.solutionEngines)
    }

    @Test
    fun `version one user workflows do not expose RTKLIB real-time`() {
        val workflows = WorkflowExamples.version1UserWorkflows(ReceiverCapabilityFixtures.um980N4())

        assertTrue(workflows.isNotEmpty())
        assertTrue(workflows.none { SolutionEngine.RTKLIB_REALTIME in it.solutionEngines })
        assertTrue(workflows.none { CorrectionTarget.RTKLIB in it.correctionTargets })
    }

    @Test
    fun `version one user workflows with UM980 are valid`() {
        WorkflowExamples.version1UserWorkflows(ReceiverCapabilityFixtures.um980N4())
            .forEach(::assertValid)
    }

    @Test
    fun `replay test workflow does not require Android recording service safety`() {
        val spec = WorkflowExamples.replayTest(ReceiverCapabilityFixtures.um980N4())

        assertFalse(spec.safety.requireForegroundService)
        assertFalse(spec.safety.requireWakeLockDuringRecording)
        assertValid(spec)
    }

    @Test
    fun `rover RTKLIB real-time with compatible raw and base context is valid`() {
        assertValid(WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.um980N4()))
    }

    @Test
    fun `base calibration with NTRIP and raw observations is valid`() {
        assertValid(WorkflowExamples.baseCalibrationWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4()))
    }

    @Test
    fun `temporary base preparation with NTRIP uses primary temporary base naming`() {
        val spec = WorkflowExamples.temporaryBasePreparationWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4())

        assertValid(spec)
        assertTrue(spec.id.startsWith("temporary-base-preparation"))
        assertTrue(spec.name.startsWith("Temporary base preparation"))
    }

    @Test
    fun `RTKLIB real-time without base context is invalid`() {
        val spec = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.um980N4())
            .copy(baseContext = BaseContextSpec.None)

        assertError(spec, "RTKLIB_REQUIRES_BASE_CONTEXT")
    }

    @Test
    fun `RTKLIB real-time without RTKLIB correction target is invalid`() {
        val spec = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.um980N4())
            .copy(correctionTargets = emptySet())

        assertError(spec, "RTKLIB_REQUIRES_CORRECTION_TARGET")
    }

    @Test
    fun `NTRIP correction target without correction source is invalid`() {
        val spec = WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4())
            .copy(correctionSource = CorrectionSourceSpec.None)

        assertError(spec, "CORRECTION_TARGET_REQUIRES_SOURCE")
    }

    @Test
    fun `fixed base without base position is invalid`() {
        val spec = WorkflowExamples.fixedBaseFromBasePosition(ReceiverCapabilityFixtures.ubloxM8p2())
            .copy(baseContext = BaseContextSpec.None)

        assertError(spec, "FIXED_BASE_REQUIRES_BASE_POSITION")
    }

    @Test
    fun `fixed base with M8P-0 is invalid`() {
        val spec = WorkflowExamples.fixedBaseFromBasePosition(ReceiverCapabilityFixtures.ubloxM8p0())

        assertError(spec, "FIXED_BASE_REQUIRES_CAPABILITY")
    }

    @Test
    fun `fixed base with M8T is invalid because timing fixed position is not fixed-base streaming`() {
        val spec = WorkflowExamples.fixedBaseFromBasePosition(ReceiverCapabilityFixtures.ubloxM8t())

        assertError(spec, "FIXED_BASE_REQUIRES_CAPABILITY")
    }

    @Test
    fun `rover workflow with M8T is invalid`() {
        val spec = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.ubloxM8t())

        assertError(spec, "ROVER_REQUIRES_CAPABILITY")
    }

    @Test
    fun `receiver-internal RTK workflow with M8T is invalid`() {
        val spec = WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.ubloxM8t())

        assertError(spec, "DEVICE_INTERNAL_RTK_REQUIRES_CAPABILITY")
    }

    @Test
    fun `RTKLIB workflow with no compatible raw observations or converter is invalid`() {
        val spec = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.ubloxM8p0())

        assertError(spec, "RTKLIB_REQUIRES_COMPATIBLE_RAW")
    }

    @Test
    fun `plain rover workflow with correction target is invalid`() {
        val spec = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
            .copy(correctionTargets = setOf(CorrectionTarget.RECEIVER))

        assertError(spec, "PLAIN_ROVER_HAS_CORRECTION_TARGET")
    }

    @Test
    fun `NTRIP source without mountpoint is invalid`() {
        val spec = WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4())
            .copy(correctionSource = WorkflowExamples.defaultNtrip().copy(mountpoint = ""))

        assertError(spec, "NTRIP_REQUIRES_HOST_PORT_MOUNTPOINT")
    }

    @Test
    fun `NTRIP correction workflow without base context is invalid`() {
        val spec = WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4())
            .copy(baseContext = BaseContextSpec.None)

        assertError(spec, "NTRIP_REQUIRES_BASE_CONTEXT")
    }

    @Test
    fun `NTRIP correction workflow with unrelated manual base context is invalid`() {
        val spec = WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4())
            .copy(baseContext = BaseContextSpec.ManualCoordinate(WorkflowExamples.defaultBasePosition()))

        assertError(spec, "NTRIP_REQUIRES_MOUNTPOINT_BASE_CONTEXT")
    }

    @Test
    fun `NTRIP correction workflow with mismatched mountpoint context is invalid`() {
        val spec = WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4())
            .copy(
                baseContext = BaseContextSpec.NtripMountpoint(
                    casterHost = "caster.example.org",
                    mountpoint = "OTHER",
                ),
            )

        assertError(spec, "NTRIP_BASE_CONTEXT_MISMATCH")
    }

    @Test
    fun `RTKLIB workflow rejects unsupported correction format without converter`() {
        val spec = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.um980N4())
            .copy(correctionSource = WorkflowExamples.defaultNtrip().copy(expectedCorrectionFormat = CorrectionFormat.UNKNOWN))

        assertError(spec, "RTKLIB_REQUIRES_SUPPORTED_CORRECTION_FORMAT")
    }

    @Test
    fun `base calibration recording does not expect accepted base position artifact at start`() {
        val spec = WorkflowExamples.baseCalibrationRawOnly(ReceiverCapabilityFixtures.ubloxM8t())

        assertFalse(SessionArtifact.BASE_POSITION_JSON in spec.recording.expectedSessionArtifacts)
    }

    @Test
    fun `temporary base preparation requires device solution recording`() {
        val spec = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.um980N4())
            .copy(
                solutionEngines = setOf(SolutionEngine.POSTPROCESSING_PIPELINE, SolutionEngine.RECEIVER_PPP),
                recording = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.um980N4()).recording.copy(
                    recordDeviceSolution = false,
                ),
            )

        assertError(spec, "BASE_PREPARATION_REQUIRES_DEVICE_SOLUTION")
    }

    @Test
    fun `temporary base preparation requires at least one hertz raw observations when raw is supported`() {
        val spec = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.um980N4())
            .copy(
                recording = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.um980N4()).recording.copy(
                    rawObservationMinimumRateHz = 0.2,
                ),
            )

        assertError(spec, "BASE_PREPARATION_REQUIRES_RAW_OBSERVATION_RATE")
    }

    @Test
    fun `temporary base preparation requires receiver PPP recording when receiver PPP is supported`() {
        val spec = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.um980N4())
            .copy(
                solutionEngines = setOf(SolutionEngine.DEVICE_INTERNAL, SolutionEngine.POSTPROCESSING_PIPELINE),
                recording = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.um980N4()).recording.copy(
                    recordPppSolution = false,
                    expectedSessionArtifacts = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.um980N4())
                        .recording
                        .expectedSessionArtifacts - SessionArtifact.RECEIVER_PPP_SOLUTION_JSONL,
                ),
            )

        assertError(spec, "BASE_PREPARATION_REQUIRES_PPP_RECORDING_WHEN_SUPPORTED")
    }

    @Test
    fun `long averaging before static or PPP candidate methods emits fallback warning`() {
        val spec = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.um980N4())
            .copy(
                basePositionCandidateGeneration = BasePositionCandidateGenerationSpec(
                    candidateMethods = listOf(
                        BasePositionMethod.LONG_AVERAGE,
                        BasePositionMethod.STATIC_RTK,
                        BasePositionMethod.PPP_STATIC,
                    ),
                ),
            )

        assertWarning(spec, "LONG_AVERAGE_CANDIDATE_IS_FALLBACK")
    }

    @Test
    fun `long averaging between preferred candidate methods emits fallback warning`() {
        val spec = WorkflowExamples.temporaryBasePreparation(ReceiverCapabilityFixtures.um980N4())
            .copy(
                basePositionCandidateGeneration = BasePositionCandidateGenerationSpec(
                    candidateMethods = listOf(
                        BasePositionMethod.STATIC_RTK,
                        BasePositionMethod.LONG_AVERAGE,
                        BasePositionMethod.PPP_STATIC,
                    ),
                ),
            )

        assertWarning(spec, "LONG_AVERAGE_CANDIDATE_IS_FALLBACK")
    }

    @Test
    fun `raw required on receiver without raw support is invalid`() {
        val spec = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.genericNmeaRtcm())
            .copy(observationRequirement = ObservationRequirement.RAW_REQUIRED)

        assertError(spec, "RAW_REQUIRED_REQUIRES_CAPABILITY")
    }

    @Test
    fun `secrets in session JSON export are invalid`() {
        val spec = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
            .copy(safety = WorkflowSafetySpec(allowSecretsInSessionJson = true))

        assertError(spec, "SECRETS_IN_SESSION_JSON_FORBIDDEN")
    }

    @Test
    fun `raw receiver stream recording is required`() {
        val spec = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
            .copy(recording = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()).recording.copy(recordRawReceiverStream = false))

        assertError(spec, "RAW_RECEIVER_STREAM_REQUIRED")
    }

    @Test
    fun `receiver RX raw artifact is required`() {
        val spec = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
            .copy(recording = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()).recording.copy(expectedSessionArtifacts = setOf(SessionArtifact.EVENTS_JSONL)))

        assertError(spec, "RECEIVER_RX_ARTIFACT_REQUIRED")
    }

    @Test
    fun `event log artifact is required`() {
        val spec = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
            .copy(recording = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4()).recording.copy(expectedSessionArtifacts = setOf(SessionArtifact.RECEIVER_RX_RAW)))

        assertError(spec, "EVENTS_LOG_ARTIFACT_REQUIRED")
    }

    @Test
    fun `foreground service is required for recording workflows`() {
        val spec = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
            .copy(safety = WorkflowSafetySpec(requireForegroundService = false))

        assertError(spec, "FOREGROUND_SERVICE_REQUIRED")
    }

    @Test
    fun `wake lock is required during recording`() {
        val spec = WorkflowExamples.plainRoverRecording(ReceiverCapabilityFixtures.um980N4())
            .copy(safety = WorkflowSafetySpec(requireWakeLockDuringRecording = false))

        assertError(spec, "WAKE_LOCK_REQUIRED")
    }

    @Test
    fun `plaintext NTRIP credentials are invalid`() {
        val spec = WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.um980N4())
            .copy(correctionSource = WorkflowExamples.defaultNtrip().copy(plaintextPassword = "secret"))

        assertError(spec, "NTRIP_CREDENTIALS_MUST_BE_SECRET_REFERENCES")
    }

    @Test
    fun `fixed base cannot start from unaccepted recorded base session`() {
        val spec = WorkflowExamples.fixedBaseFromBasePosition(ReceiverCapabilityFixtures.ubloxM8p2())
            .copy(baseContext = BaseContextSpec.RecordedBaseSession(sessionId = "base-calibration-session"))

        assertError(spec, "FIXED_BASE_REQUIRES_ACCEPTED_BASE_POSITION_CANDIDATE")
    }

    @Test
    fun `base position without frame emits warning`() {
        val spec = WorkflowExamples.fixedBaseFromBasePosition(ReceiverCapabilityFixtures.ubloxM8p2())
            .copy(baseContext = BaseContextSpec.ManualCoordinate(WorkflowExamples.defaultBasePosition().copy(frame = "")))

        assertWarning(spec, "BASE_POSITION_MISSING_FRAME")
    }

    @Test
    fun `base position without antenna metadata emits warning`() {
        val spec = WorkflowExamples.fixedBaseFromBasePosition(ReceiverCapabilityFixtures.ubloxM8p2())
            .copy(
                baseContext = BaseContextSpec.ManualCoordinate(
                    WorkflowExamples.defaultBasePosition().copy(
                        antennaHeightM = null,
                        antennaReferencePoint = null,
                    ),
                ),
            )

        assertWarning(spec, "BASE_POSITION_MISSING_ANTENNA")
    }

    @Test
    fun `rover NTRIP without raw observations emits warning`() {
        val spec = WorkflowExamples.roverWithNtripToReceiver(ReceiverCapabilityFixtures.genericNmeaRtcm())
            .copy(observationRequirement = ObservationRequirement.NONE)

        assertWarning(spec, "NTRIP_ROVER_WITHOUT_RAW_LIMITS_POSTPROCESSING")
    }

    @Test
    fun `long average base position emits fallback warning`() {
        val spec = WorkflowExamples.fixedBaseFromBasePosition(ReceiverCapabilityFixtures.ubloxM8p2())
            .copy(
                baseContext = BaseContextSpec.ManualCoordinate(
                    WorkflowExamples.defaultBasePosition().copy(
                        method = BasePositionMethod.LONG_AVERAGE,
                        durationSeconds = null,
                        horizontalUncertaintyM = null,
                    ),
                ),
            )

        assertWarning(spec, "LONG_AVERAGE_BASE_POSITION_IS_FALLBACK")
    }

    private fun assertValid(spec: WorkflowSpec) {
        val result = validator.validate(spec)
        assertTrue(result.valid, "Expected valid, got errors: ${result.errors}")
    }

    private fun assertError(spec: WorkflowSpec, code: String) {
        val result = validator.validate(spec)
        assertFalse(result.valid, "Expected invalid workflow")
        assertTrue(result.errors.any { it.code == code }, "Missing error $code in ${result.errors}")
    }

    private fun assertWarning(spec: WorkflowSpec, code: String) {
        val result = validator.validate(spec)
        assertTrue(result.warnings.any { it.code == code }, "Missing warning $code in ${result.warnings}")
    }
}
