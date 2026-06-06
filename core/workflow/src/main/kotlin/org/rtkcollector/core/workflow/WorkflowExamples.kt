package org.rtkcollector.core.workflow

object ReceiverCapabilityFixtures {
    fun genericNmeaRtcm(serialTxAvailable: Boolean = true): ReceiverWorkflowCapabilities =
        ReceiverWorkflowCapabilities(
            supportsRoverMode = true,
            supportsRtcmInput = serialTxAvailable,
        )

    fun um980N4(): ReceiverWorkflowCapabilities =
        ReceiverWorkflowCapabilities(
            supportsRoverMode = true,
            supportsBaseCalibrationMode = true,
            supportsFixedBaseMode = true,
            supportsRtcmInput = true,
            supportsRtcmOutput = true,
            supportsInternalRtk = true,
            supportsRawObservations = true,
            supportsRtklibCompatibleRaw = true,
            supportsReceiverSurveyIn = true,
            supportsCustomInitCommands = true,
        )

    fun ubloxM8p0(): ReceiverWorkflowCapabilities =
        ReceiverWorkflowCapabilities(
            supportsRoverMode = true,
            supportsRtcmInput = true,
            supportsInternalRtk = true,
            supportsRawObservations = true,
        )

    fun ubloxM8p2(): ReceiverWorkflowCapabilities =
        ReceiverWorkflowCapabilities(
            supportsRoverMode = true,
            supportsBaseCalibrationMode = true,
            supportsFixedBaseMode = true,
            supportsRtcmInput = true,
            supportsRtcmOutput = true,
            supportsInternalRtk = true,
            supportsRawObservations = true,
            supportsReceiverSurveyIn = true,
        )

    fun ubloxM8t(): ReceiverWorkflowCapabilities =
        ReceiverWorkflowCapabilities(
            supportsBaseCalibrationMode = true,
            supportsRawObservations = true,
            supportsReceiverSurveyIn = true,
        )
}

object WorkflowExamples {
    fun defaultBasePosition(): BasePosition =
        BasePosition(
            latDeg = 50.0,
            lonDeg = 14.0,
            heightM = 300.0,
            ecefXM = 3970000.0,
            ecefYM = 1050000.0,
            ecefZM = 4850000.0,
            frame = "ETRF2000",
            epoch = "2026.4",
            method = BasePositionMethod.STATIC_RTK,
            durationSeconds = 86400,
            horizontalUncertaintyM = 0.02,
            verticalUncertaintyM = 0.04,
            antennaHeightM = 1.5,
            antennaReferencePoint = "ARP",
            sourceSessionId = "base-session-001",
        )

    fun defaultNtrip(): CorrectionSourceSpec.Ntrip =
        CorrectionSourceSpec.Ntrip(
            casterHost = "caster.example.org",
            port = 2101,
            mountpoint = "CORS01",
            usernameSecretRef = "ntrip/username",
            passwordSecretRef = "ntrip/password",
            sendGga = true,
            ggaSource = GgaSource.RECEIVER,
            expectedCorrectionFormat = CorrectionFormat.RTCM3,
            stationId = "CORS01",
            stationName = "Example CORS",
            approximateBasePosition = defaultBasePosition(),
        )

    fun plainRoverRecording(
        capabilities: ReceiverWorkflowCapabilities,
        receiverProfileId: String = "um980-n4",
    ): WorkflowSpec =
        WorkflowSpec(
            id = "plain-rover-recording",
            name = "Plain rover recording",
            receiverRole = ReceiverRole.ROVER,
            receiverProfileId = receiverProfileId,
            receiverCapabilities = capabilities,
            correctionSource = CorrectionSourceSpec.None,
            correctionTargets = emptySet(),
            solutionEngines = setOf(SolutionEngine.DEVICE_INTERNAL),
            observationRequirement = ObservationRequirement.RAW_IF_SUPPORTED,
            baseContext = BaseContextSpec.None,
            recording = RecordingSpec(
                recordDeviceSolution = true,
                recordRawObservationsRequested = capabilities.supportsRawObservations,
                expectedSessionArtifacts = setOf(
                    SessionArtifact.RECEIVER_RX_RAW,
                    SessionArtifact.DEVICE_SOLUTION_JSONL,
                    SessionArtifact.QUALITY_LIVE_JSONL,
                ),
            ),
            qualityMonitoring = QualityMonitoringSpec(
                monitorDeviceSolution = true,
                monitorRawObservationPresence = capabilities.supportsRawObservations,
                monitorSerialThroughput = true,
            ),
            safety = WorkflowSafetySpec(),
        )

    fun roverWithNtripToReceiver(
        capabilities: ReceiverWorkflowCapabilities,
        receiverProfileId: String = "um980-n4",
    ): WorkflowSpec =
        WorkflowSpec(
            id = "rover-ntrip-to-receiver",
            name = "Rover + NTRIP to receiver",
            receiverRole = ReceiverRole.ROVER,
            receiverProfileId = receiverProfileId,
            receiverCapabilities = capabilities,
            correctionSource = defaultNtrip(),
            correctionTargets = setOf(CorrectionTarget.RECEIVER),
            solutionEngines = setOf(SolutionEngine.DEVICE_INTERNAL),
            observationRequirement = if (capabilities.supportsRawObservations) {
                ObservationRequirement.RAW_IF_SUPPORTED
            } else {
                ObservationRequirement.NONE
            },
            baseContext = BaseContextSpec.NtripMountpoint(
                casterHost = "caster.example.org",
                mountpoint = "CORS01",
                stationId = "CORS01",
                stationName = "Example CORS",
                approximateBasePosition = defaultBasePosition(),
            ),
            recording = RecordingSpec(
                recordTxToReceiver = true,
                recordCorrectionInput = true,
                recordDeviceSolution = true,
                recordRawObservationsRequested = capabilities.supportsRawObservations,
                expectedSessionArtifacts = setOf(
                    SessionArtifact.RECEIVER_RX_RAW,
                    SessionArtifact.TX_TO_RECEIVER_RAW,
                    SessionArtifact.CORRECTION_INPUT_RAW,
                    SessionArtifact.DEVICE_SOLUTION_JSONL,
                    SessionArtifact.QUALITY_LIVE_JSONL,
                ),
            ),
            qualityMonitoring = QualityMonitoringSpec(
                monitorDeviceSolution = true,
                monitorNtripState = true,
                monitorCorrectionAge = true,
                monitorRawObservationPresence = capabilities.supportsRawObservations,
                monitorSerialThroughput = true,
                monitorRtcmFrameValidity = true,
            ),
            safety = WorkflowSafetySpec(),
        )

    fun roverWithRtklibRealtime(
        capabilities: ReceiverWorkflowCapabilities,
        receiverProfileId: String = "um980-n4",
    ): WorkflowSpec =
        WorkflowSpec(
            id = "rover-rtklib-realtime",
            name = "Rover + RTKLIB real-time",
            receiverRole = ReceiverRole.ROVER,
            receiverProfileId = receiverProfileId,
            receiverCapabilities = capabilities,
            correctionSource = defaultNtrip(),
            correctionTargets = setOf(CorrectionTarget.RTKLIB),
            solutionEngines = setOf(SolutionEngine.RTKLIB_REALTIME),
            observationRequirement = ObservationRequirement.RTKLIB_COMPATIBLE_REQUIRED,
            baseContext = BaseContextSpec.NtripMountpoint(
                casterHost = "caster.example.org",
                mountpoint = "CORS01",
                stationId = "CORS01",
                stationName = "Example CORS",
                approximateBasePosition = defaultBasePosition(),
            ),
            recording = RecordingSpec(
                recordCorrectionInput = true,
                recordRtklibSolution = true,
                recordRawObservationsRequested = true,
                expectedSessionArtifacts = setOf(
                    SessionArtifact.RECEIVER_RX_RAW,
                    SessionArtifact.CORRECTION_INPUT_RAW,
                    SessionArtifact.RTKLIB_SOLUTION_JSONL,
                    SessionArtifact.QUALITY_LIVE_JSONL,
                ),
            ),
            qualityMonitoring = QualityMonitoringSpec(
                monitorRtklibSolution = true,
                monitorNtripState = true,
                monitorCorrectionAge = true,
                monitorRawObservationPresence = true,
                monitorSerialThroughput = true,
                monitorRtcmFrameValidity = true,
            ),
            safety = WorkflowSafetySpec(),
        )

    fun roverWithNtripToReceiverAndRtklibRealtime(
        capabilities: ReceiverWorkflowCapabilities,
        receiverProfileId: String = "um980-n4",
    ): WorkflowSpec {
        val rtklibWorkflow = roverWithRtklibRealtime(capabilities, receiverProfileId)
        return rtklibWorkflow.copy(
            id = "rover-ntrip-receiver-rtklib",
            name = "Rover + NTRIP to receiver + RTKLIB real-time",
            correctionTargets = setOf(CorrectionTarget.RECEIVER, CorrectionTarget.RTKLIB),
            solutionEngines = setOf(SolutionEngine.DEVICE_INTERNAL, SolutionEngine.RTKLIB_REALTIME),
            recording = rtklibWorkflow.recording.copy(
                recordTxToReceiver = true,
                recordDeviceSolution = true,
                expectedSessionArtifacts = setOf(
                    SessionArtifact.RECEIVER_RX_RAW,
                    SessionArtifact.TX_TO_RECEIVER_RAW,
                    SessionArtifact.CORRECTION_INPUT_RAW,
                    SessionArtifact.DEVICE_SOLUTION_JSONL,
                    SessionArtifact.RTKLIB_SOLUTION_JSONL,
                    SessionArtifact.QUALITY_LIVE_JSONL,
                ),
            ),
            qualityMonitoring = rtklibWorkflow.qualityMonitoring.copy(
                monitorDeviceSolution = true,
            ),
        )
    }

    fun baseCalibrationRawOnly(
        capabilities: ReceiverWorkflowCapabilities,
        receiverProfileId: String = "ublox-m8t",
    ): WorkflowSpec =
        WorkflowSpec(
            id = "base-calibration-raw-only",
            name = "Base calibration, raw only",
            receiverRole = ReceiverRole.BASE_CALIBRATION,
            receiverProfileId = receiverProfileId,
            receiverCapabilities = capabilities,
            correctionSource = CorrectionSourceSpec.None,
            correctionTargets = emptySet(),
            solutionEngines = setOf(SolutionEngine.POSTPROCESSING_PIPELINE),
            observationRequirement = if (capabilities.supportsRawObservations) {
                ObservationRequirement.RAW_IF_SUPPORTED
            } else {
                ObservationRequirement.NONE
            },
            baseContext = BaseContextSpec.None,
            recording = RecordingSpec(
                recordDeviceSolution = true,
                recordRawObservationsRequested = capabilities.supportsRawObservations,
                expectedSessionArtifacts = setOf(
                    SessionArtifact.RECEIVER_RX_RAW,
                    SessionArtifact.DEVICE_SOLUTION_JSONL,
                    SessionArtifact.QUALITY_LIVE_JSONL,
                ),
            ),
            qualityMonitoring = QualityMonitoringSpec(
                monitorDeviceSolution = true,
                monitorRawObservationPresence = capabilities.supportsRawObservations,
                monitorSerialThroughput = true,
            ),
            safety = WorkflowSafetySpec(allowStartWithoutBasePosition = true),
        )

    fun baseCalibrationWithNtripToReceiver(
        capabilities: ReceiverWorkflowCapabilities,
        receiverProfileId: String = "um980-n4",
    ): WorkflowSpec =
        WorkflowSpec(
            id = "base-calibration-ntrip",
            name = "Base calibration with CORS/NTRIP",
            receiverRole = ReceiverRole.BASE_CALIBRATION,
            receiverProfileId = receiverProfileId,
            receiverCapabilities = capabilities,
            correctionSource = defaultNtrip(),
            correctionTargets = setOf(CorrectionTarget.RECEIVER),
            solutionEngines = setOf(SolutionEngine.DEVICE_INTERNAL, SolutionEngine.POSTPROCESSING_PIPELINE),
            observationRequirement = if (capabilities.supportsRawObservations) {
                ObservationRequirement.RAW_IF_SUPPORTED
            } else {
                ObservationRequirement.NONE
            },
            baseContext = BaseContextSpec.NtripMountpoint(
                casterHost = "caster.example.org",
                mountpoint = "CORS01",
                stationId = "CORS01",
                stationName = "Example CORS",
                approximateBasePosition = defaultBasePosition(),
            ),
            recording = RecordingSpec(
                recordTxToReceiver = true,
                recordCorrectionInput = true,
                recordDeviceSolution = true,
                recordRawObservationsRequested = capabilities.supportsRawObservations,
                expectedSessionArtifacts = setOf(
                    SessionArtifact.RECEIVER_RX_RAW,
                    SessionArtifact.TX_TO_RECEIVER_RAW,
                    SessionArtifact.CORRECTION_INPUT_RAW,
                    SessionArtifact.DEVICE_SOLUTION_JSONL,
                    SessionArtifact.QUALITY_LIVE_JSONL,
                ),
            ),
            qualityMonitoring = QualityMonitoringSpec(
                monitorDeviceSolution = true,
                monitorNtripState = true,
                monitorCorrectionAge = true,
                monitorRawObservationPresence = capabilities.supportsRawObservations,
                monitorSerialThroughput = true,
                monitorRtcmFrameValidity = true,
            ),
            safety = WorkflowSafetySpec(allowStartWithoutBasePosition = true),
        )

    fun fixedBaseFromBasePosition(
        capabilities: ReceiverWorkflowCapabilities,
        receiverProfileId: String = "ublox-m8p2",
    ): WorkflowSpec =
        WorkflowSpec(
            id = "fixed-base-from-base-position",
            name = "Fixed base from accepted base position",
            receiverRole = ReceiverRole.FIXED_BASE,
            receiverProfileId = receiverProfileId,
            receiverCapabilities = capabilities,
            correctionSource = CorrectionSourceSpec.None,
            correctionTargets = emptySet(),
            solutionEngines = setOf(SolutionEngine.DEVICE_INTERNAL),
            observationRequirement = ObservationRequirement.RAW_IF_SUPPORTED,
            baseContext = BaseContextSpec.ManualCoordinate(defaultBasePosition()),
            recording = RecordingSpec(
                recordDeviceSolution = true,
                recordRawObservationsRequested = capabilities.supportsRawObservations,
                expectedSessionArtifacts = setOf(
                    SessionArtifact.RECEIVER_RX_RAW,
                    SessionArtifact.DEVICE_SOLUTION_JSONL,
                    SessionArtifact.QUALITY_LIVE_JSONL,
                    SessionArtifact.BASE_POSITION_JSON,
                    SessionArtifact.RTCM_EXTRACTED_RTCM3,
                ),
            ),
            qualityMonitoring = QualityMonitoringSpec(
                monitorDeviceSolution = true,
                monitorRawObservationPresence = capabilities.supportsRawObservations,
                monitorSerialThroughput = true,
                monitorRtcmFrameValidity = capabilities.supportsRtcmOutput,
            ),
            safety = WorkflowSafetySpec(),
        )
}
