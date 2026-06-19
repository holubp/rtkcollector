package org.rtkcollector.core.workflow

enum class ReceiverRole {
    ROVER,
    BASE_CALIBRATION,
    FIXED_BASE,
    REPLAY_TEST,
}

enum class CorrectionTarget {
    RECEIVER,
    RTKLIB,
}

enum class SolutionEngine {
    DEVICE_INTERNAL,
    RECEIVER_PPP,
    RTKLIB_REALTIME,
    POSTPROCESSING_PIPELINE,
}

enum class ObservationRequirement {
    NONE,
    RAW_IF_SUPPORTED,
    RAW_REQUIRED,
    RTKLIB_COMPATIBLE_REQUIRED,
}

enum class BasePositionMethod {
    MANUAL_KNOWN_POINT,
    STATIC_RTK,
    PPP_STATIC,
    RECEIVER_PPP,
    LONG_AVERAGE,
    RECEIVER_SURVEY_IN,
    EXTERNAL_BASE_POSITION_JSON,
    UNKNOWN,
}

enum class GgaSource {
    NONE,
    RECEIVER,
    MANUAL,
    LAST_DEVICE_POSITION,
}

enum class CorrectionFormat {
    RTCM3,
    RTCM_OBSERVATIONS,
    RECEIVER_NATIVE_RAW,
    FILE_REPLAY,
    UNKNOWN,
}

enum class RtklibRoverInputFormat {
    UBX_RXM_RAWX_SFRBX,
    UNICORE_OBSVMB,
    UNICORE_OBSVMCMPB,
    RTCM3_OBSERVATIONS,
    CONVERTED_OBSERVATION_EPOCHS,
    UNKNOWN,
}

enum class RtklibInputRouteKind {
    NOT_CONFIGURED,
    DIRECT_RTKLIB_DECODER,
    CONVERTER,
    UNSUPPORTED,
}

data class RtklibRoverInputCapability(
    val format: RtklibRoverInputFormat,
    val description: String,
    val preferred: Boolean = false,
    val converterId: String? = null,
)

data class RtklibEngineCapabilities(
    val id: String,
    val directRoverInputFormats: Set<RtklibRoverInputFormat>,
    val directCorrectionFormats: Set<CorrectionFormat>,
)

data class RtklibInputRoute(
    val kind: RtklibInputRouteKind,
    val format: RtklibRoverInputFormat? = null,
    val decoderId: String? = null,
    val converterId: String? = null,
    val reason: String,
) {
    val supported: Boolean
        get() = kind == RtklibInputRouteKind.DIRECT_RTKLIB_DECODER ||
            kind == RtklibInputRouteKind.CONVERTER
}

data class RtklibCorrectionInputRoute(
    val kind: RtklibInputRouteKind,
    val format: CorrectionFormat? = null,
    val decoderId: String? = null,
    val converterId: String? = null,
    val reason: String,
) {
    val supported: Boolean
        get() = kind == RtklibInputRouteKind.DIRECT_RTKLIB_DECODER ||
            kind == RtklibInputRouteKind.CONVERTER
}

data class RtklibInputRoutePlan(
    val roverInput: RtklibInputRoute,
    val correctionInput: RtklibCorrectionInputRoute,
    val solutionDirection: RtklibSolutionDirection = RtklibSolutionDirection.FORWARD_ONLY,
)

enum class RtklibSolutionDirection {
    FORWARD_ONLY,
}

object RtklibExCapabilities {
    fun current(): RtklibEngineCapabilities =
        RtklibEngineCapabilities(
            id = "rtklib-ex-2.5.0",
            directRoverInputFormats = setOf(
                RtklibRoverInputFormat.UBX_RXM_RAWX_SFRBX,
                RtklibRoverInputFormat.UNICORE_OBSVMB,
                RtklibRoverInputFormat.RTCM3_OBSERVATIONS,
            ),
            directCorrectionFormats = setOf(
                CorrectionFormat.RTCM3,
                CorrectionFormat.RTCM_OBSERVATIONS,
            ),
        )
}

data class BasePosition(
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
    val heightM: Double? = null,
    val ecefXM: Double? = null,
    val ecefYM: Double? = null,
    val ecefZM: Double? = null,
    val frame: String? = null,
    val epoch: String? = null,
    val method: BasePositionMethod = BasePositionMethod.UNKNOWN,
    val durationSeconds: Long? = null,
    val horizontalUncertaintyM: Double? = null,
    val verticalUncertaintyM: Double? = null,
    val antennaHeightM: Double? = null,
    val antennaReferencePoint: String? = null,
    val sourceSessionId: String? = null,
)

data class AntennaMetadata(
    val antennaType: String? = null,
    val antennaSerialNumber: String? = null,
    val antennaHeightM: Double? = null,
    val antennaReferencePoint: String? = null,
)

sealed class CorrectionSourceSpec {
    data object None : CorrectionSourceSpec()

    data class Ntrip(
        val casterHost: String,
        val port: Int,
        val mountpoint: String,
        val usernameSecretRef: String? = null,
        val passwordSecretRef: String? = null,
        val plaintextUsername: String? = null,
        val plaintextPassword: String? = null,
        val sendGga: Boolean = false,
        val ggaSource: GgaSource = GgaSource.NONE,
        val expectedCorrectionFormat: CorrectionFormat = CorrectionFormat.RTCM3,
        val stationId: String? = null,
        val stationName: String? = null,
        val approximateBasePosition: BasePosition? = null,
    ) : CorrectionSourceSpec()

    data class LocalBaseStream(
        val id: String,
        val expectedCorrectionFormat: CorrectionFormat = CorrectionFormat.RTCM3,
        val approximateBasePosition: BasePosition? = null,
    ) : CorrectionSourceSpec()

    data class FileReplay(
        val path: String,
        val expectedCorrectionFormat: CorrectionFormat = CorrectionFormat.FILE_REPLAY,
    ) : CorrectionSourceSpec()

    data class ExternalSerialOrTcp(
        val endpoint: String,
        val expectedCorrectionFormat: CorrectionFormat = CorrectionFormat.RTCM3,
    ) : CorrectionSourceSpec()
}

sealed class BaseContextSpec {
    data object None : BaseContextSpec()

    data class KnownStation(
        val stationId: String? = null,
        val stationName: String? = null,
        val coordinateSource: String? = null,
        val basePosition: BasePosition? = null,
        val referenceFrame: String? = null,
        val epoch: String? = null,
        val antennaMetadata: AntennaMetadata? = null,
        val sourceReference: String? = null,
    ) : BaseContextSpec()

    data class BasePositionFile(
        val path: String,
        val basePosition: BasePosition? = null,
        val sourceReference: String? = path,
    ) : BaseContextSpec()

    data class ManualCoordinate(
        val basePosition: BasePosition,
    ) : BaseContextSpec()

    data class RecordedBaseSession(
        val sessionId: String,
        val acceptedBasePosition: BasePosition? = null,
    ) : BaseContextSpec()

    data class NtripMountpoint(
        val casterHost: String,
        val mountpoint: String,
        val stationId: String? = null,
        val stationName: String? = null,
        val approximateBasePosition: BasePosition? = null,
        val referenceFrame: String? = null,
        val epoch: String? = null,
        val antennaMetadata: AntennaMetadata? = null,
    ) : BaseContextSpec()

    data class LocalBaseStream(
        val streamId: String,
        val stationId: String? = null,
        val stationName: String? = null,
        val approximateBasePosition: BasePosition? = null,
        val referenceFrame: String? = null,
        val epoch: String? = null,
        val antennaMetadata: AntennaMetadata? = null,
    ) : BaseContextSpec()
}

data class ReceiverWorkflowCapabilities(
    val supportsRoverMode: Boolean = false,
    val supportsBaseCalibrationMode: Boolean = false,
    val supportsFixedBaseMode: Boolean = false,
    val supportsRtcmInput: Boolean = false,
    val supportsRtcmOutput: Boolean = false,
    val supportsInternalRtk: Boolean = false,
    val supportsRawObservations: Boolean = false,
    val supportsRtklibCompatibleRaw: Boolean = false,
    val supportsReceiverPppSolution: Boolean = false,
    val supportsReceiverSurveyIn: Boolean = false,
    val supportsCustomInitCommands: Boolean = false,
    val supportsRtklibRawConverter: Boolean = false,
    val rtklibRoverInputCapabilities: List<RtklibRoverInputCapability> = emptyList(),
)

enum class SessionArtifact {
    RECEIVER_RX_RAW,
    TX_TO_RECEIVER_RAW,
    CORRECTION_INPUT_RAW,
    CORRECTION_INPUT_RTCM3,
    BASE_CASTER_UPLOAD_RTCM3,
    EVENTS_JSONL,
    DEVICE_SOLUTION_JSONL,
    RECEIVER_PPP_SOLUTION_JSONL,
    RTKLIB_SOLUTION_NMEA,
    RTKLIB_SOLUTION_POS,
    RTKLIB_STATUS_JSONL,
    QUALITY_LIVE_JSONL,
    BASE_POSITION_JSON,
    RTCM_EXTRACTED_RTCM3,
}

data class RecordingSpec(
    val recordRawReceiverStream: Boolean = true,
    val recordTxToReceiver: Boolean = false,
    val recordCorrectionInput: Boolean = false,
    val recordDeviceSolution: Boolean = false,
    val recordPppSolution: Boolean = false,
    val recordRtklibSolution: Boolean = false,
    val recordQualityEvents: Boolean = true,
    val recordRawObservationsRequested: Boolean = false,
    val rawObservationMinimumRateHz: Double? = null,
    val expectedSessionArtifacts: Set<SessionArtifact> = setOf(
        SessionArtifact.RECEIVER_RX_RAW,
        SessionArtifact.EVENTS_JSONL,
        SessionArtifact.QUALITY_LIVE_JSONL,
    ),
)

data class QualityMonitoringSpec(
    val monitorDeviceSolution: Boolean = false,
    val monitorPppSolution: Boolean = false,
    val monitorRtklibSolution: Boolean = false,
    val monitorNtripState: Boolean = false,
    val monitorCorrectionAge: Boolean = false,
    val monitorRawObservationPresence: Boolean = false,
    val monitorSerialThroughput: Boolean = true,
    val monitorRtcmFrameValidity: Boolean = false,
)

data class WorkflowSafetySpec(
    val requireForegroundService: Boolean = true,
    val requireWakeLockDuringRecording: Boolean = true,
    val allowStartWithoutRawObservations: Boolean = true,
    val allowStartWithoutBasePosition: Boolean = false,
    val allowUnvalidatedReceiverCommands: Boolean = false,
    val allowSecretsInSessionJson: Boolean = false,
)

data class BasePositionCandidateGenerationSpec(
    val candidateMethods: List<BasePositionMethod> = emptyList(),
)

data class WorkflowSpec(
    val id: String,
    val name: String,
    val receiverRole: ReceiverRole,
    val receiverProfileId: String,
    val receiverCapabilities: ReceiverWorkflowCapabilities,
    val correctionSource: CorrectionSourceSpec,
    val correctionTargets: Set<CorrectionTarget>,
    val solutionEngines: Set<SolutionEngine>,
    val observationRequirement: ObservationRequirement,
    val baseContext: BaseContextSpec,
    val recording: RecordingSpec,
    val qualityMonitoring: QualityMonitoringSpec,
    val safety: WorkflowSafetySpec,
    val basePositionCandidateGeneration: BasePositionCandidateGenerationSpec = BasePositionCandidateGenerationSpec(),
    val workflowSpecVersion: Int = 1,
    val rtklibRawConverterId: String? = null,
    val rtklibPreferredRoverInputFormat: RtklibRoverInputFormat? = null,
    val customInitCommandsRequested: Boolean = false,
)

data class WorkflowValidationResult(
    val valid: Boolean,
    val errors: List<WorkflowValidationMessage>,
    val warnings: List<WorkflowValidationMessage>,
)

data class WorkflowValidationMessage(
    val code: String,
    val message: String,
)
