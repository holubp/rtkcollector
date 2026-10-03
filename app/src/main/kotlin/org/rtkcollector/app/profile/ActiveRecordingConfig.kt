package org.rtkcollector.app.profile

import org.rtkcollector.core.correction.DEFAULT_NTRIP_USER_AGENT
import org.rtkcollector.app.base.AcceptedBaseCoordinate
import org.rtkcollector.core.correction.NtripEndpointSecurityPolicy
import org.rtkcollector.core.correction.NtripSourceUploadRequest
import org.rtkcollector.core.correction.NtripTlsVerification
import org.rtkcollector.core.correction.NtripTransportMode
import org.rtkcollector.core.correction.Um980RtcmBaseOutputSanity
import org.rtkcollector.core.correction.normalizeSourceUploadMountpoint
import org.rtkcollector.core.rtklib.RtklibSnapshot
import org.rtkcollector.core.solution.SolutionSourcePolicy
import org.rtkcollector.core.workflow.SessionArtifact
import org.rtkcollector.receiver.unicore.Um980OutputFrequencyValidator

data class ActiveRecordingConfig(
    val workflowId: String,
    val workflowName: String,
    val receiverProfileId: String,
    val commandProfileId: String,
    val commandReceiverFamily: String,
    val satelliteTelemetry: SatelliteTelemetryCapability,
    val usbBaudProfileId: String,
    val profileBaud: Int,
    val serialBaud: Int,
    val initCommands: List<String>,
    val baudSwitchCommands: List<String>,
    val modeCommands: List<String>,
    val shutdownCommands: List<String>,
    val ntrip: ActiveNtripConfig,
    val casterUpload: ActiveCasterUploadConfig,
    val rtklib: ActiveRtklibConfig,
    val solutionPolicy: ActiveSolutionPolicyConfig,
    val recording: ActiveRecordingOutputConfig,
    val storage: ActiveStorageConfig,
) {
    val expectedSessionArtifactNames: List<String> by lazy {
        buildSet {
            addAll(recording.expectedSessionArtifacts)
            if (casterUpload.enabled) {
                add(SessionArtifact.BASE_CASTER_UPLOAD_RTCM3)
            }
            if (rtklib.enabled && rtklib.outputNmea) {
                add(SessionArtifact.RTKLIB_SOLUTION_NMEA)
            }
            if (rtklib.enabled && rtklib.outputPos) {
                add(SessionArtifact.RTKLIB_SOLUTION_POS)
            }
            if (rtklib.enabled) {
                add(SessionArtifact.RTKLIB_STATUS_JSONL)
            }
        }.map(SessionArtifact::name).sorted()
    }

    fun validateForStart() {
        require(rtklib.enabled || solutionPolicy.screenPolicy != SolutionSourcePolicy.RTKLIB_ONLY) {
            "Screen RTKLIB_ONLY requires an active RTKLIB workflow."
        }
        require(!recording.enableMockLocation || rtklib.enabled || solutionPolicy.mockPolicy != SolutionSourcePolicy.RTKLIB_ONLY) {
            "Mock RTKLIB_ONLY requires an active RTKLIB workflow."
        }
        validateUm980OutputFrequenciesForStart(
            receiverFamily = commandReceiverFamily,
            commands = initCommands + baudSwitchCommands + modeCommands,
        )
        if (rtklib.enabled) {
            require(rtklib.validationErrors.isEmpty()) { rtklib.validationErrors.joinToString(" ") }
        }
        if (ntrip.enabled) {
            validateCorrectionProtocolPolicy(ntrip.protocolPolicy)
            require(!ntrip.requiresTlsVerificationChoice) { "Choose system-trusted TLS or explicit plaintext in profile settings before connecting." }
            require(ntrip.host.isNotBlank()) { "NTRIP host is required for ${workflowName}." }
            require(ntrip.port in 1..65535) { "NTRIP port must be 1..65535." }
            require(ntrip.mountpoint.isNotBlank()) { "NTRIP mountpoint is required for ${workflowName}." }
            ntrip.toCore(false)
        }
        if (storage.kind == "SAF_TREE") {
            require(!storage.treeUri.isNullOrBlank()) {
                "Select the Android recording folder again before starting."
            }
        }
        if (casterUpload.enabled) {
            require(!casterUpload.requiresTlsVerificationChoice) { "Choose system-trusted TLS or explicit plaintext in profile settings before connecting." }
            require(casterUpload.host.isNotBlank()) { "NTRIP caster upload host is required for ${workflowName}." }
            require(casterUpload.port in 1..65535) { "NTRIP caster upload port must be 1..65535." }
            require(casterUpload.mountpoint.isNotBlank()) { "NTRIP caster upload mountpoint is required for ${workflowName}." }
            casterUpload.toCore(false)
            normalizeSourceUploadMountpoint(casterUpload.mountpoint)
            if (casterUpload.protocolPolicy == "NTRIP_V1_ONLY") {
                NtripSourceUploadRequest(
                    mountpoint = casterUpload.mountpoint,
                    password = casterUpload.password.orEmpty(),
                    sourceAgent = DEFAULT_NTRIP_USER_AGENT,
                )
            }
            require(workflowId == WORKFLOW_FIXED_BASE || workflowId == WORKFLOW_BASE_CALIBRATION) {
                "NTRIP caster upload is only available for base workflows."
            }
            require(casterUpload.hasAcceptedBaseCoordinate) {
                "NTRIP caster upload requires an accepted base coordinate."
            }
            val sanity = Um980RtcmBaseOutputSanity.validateCommands(initCommands + baudSwitchCommands + modeCommands)
            require(sanity.canUpload) {
                "NTRIP caster upload requires base RTCM output: ${sanity.errors.joinToString(" ")}"
            }
        }
        if (workflowId == WORKFLOW_PLAIN_ROVER ||
            workflowId == WORKFLOW_ROVER_NTRIP ||
            workflowId == WORKFLOW_ROVER_RTKLIB ||
            workflowId == WORKFLOW_ROVER_NTRIP_RTKLIB ||
            workflowId == WORKFLOW_FIXED_BASE
        ) {
            validateWorkflowModeCommandsForStart(workflowId, initCommands + baudSwitchCommands + modeCommands)
        }
    }

    companion object {
        fun resolve(
            settingsSet: RecordingSettingsSet,
            selections: ActiveSetupSelections,
            profileGraph: ActiveSetupProfileGraph,
            workflowName: String,
            passwordLookup: (String) -> String?,
            currentWorkflowId: String? = null,
        ): ActiveRecordingConfig {
            require(!settingsSet.overrides.hasChanges) {
                "Settings set contains legacy overlays; migrate them to owning profiles before starting."
            }
            val setup = ActiveSetupResolver.resolve(settingsSet, selections, currentWorkflowId,
                profileGraph = profileGraph)
            require(setup.canStart) { setup.messages.joinToString(" ") { it.message } }
            val profiles = checkNotNull(setup.resolvedProfiles)
            val projected = setup.projectSettingsSet(settingsSet, profileGraph::referenceFor)
            return fromResolvedProfiles(
                settingsSet = projected,
                commandProfile = checkNotNull(profiles.command),
                usbBaudProfile = checkNotNull(profiles.usbBaud),
                ntripCasterProfile = profiles.caster,
                ntripMountpointProfile = profiles.source,
                ntripCasterUploadProfile = profiles.upload,
                recordingPolicyProfile = checkNotNull(profiles.output),
                storageProfile = checkNotNull(profiles.storage),
                rtklibProfile = profiles.rtklib,
                solutionPolicyProfile = profiles.solution,
                workflowName = workflowName,
                workflowUsesNtrip = setup.option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).applicable,
                hasAcceptedBaseCoordinate = profiles.baseCoordinate != null,
                passwordLookup = passwordLookup,
            )
        }

        fun resolve(
            settingsSet: RecordingSettingsSet,
            commandProfile: CommandProfile,
            usbBaudProfile: UsbBaudProfile,
            ntripCasterProfile: NtripCasterProfile?,
            ntripMountpointProfile: NtripMountpointProfile?,
            ntripCasterUploadProfile: NtripCasterUploadProfile? = null,
            recordingPolicyProfile: RecordingPolicyProfile,
            storageProfile: StorageProfile,
            rtklibProfile: RtklibProfile? = null,
            solutionPolicyProfile: SolutionPolicyProfile? = null,
            workflowName: String,
            workflowUsesNtrip: Boolean,
            hasAcceptedBaseCoordinate: Boolean = false,
            passwordLookup: (String) -> String?,
            baseCoordinates: List<AcceptedBaseCoordinate> = emptyList(),
        ): ActiveRecordingConfig {
            val expectedNtrip = settingsSet.workflowId in setOf(WORKFLOW_ROVER_NTRIP, WORKFLOW_ROVER_RTKLIB,
                WORKFLOW_ROVER_NTRIP_RTKLIB, WORKFLOW_BASE_CALIBRATION)
            require(workflowUsesNtrip == expectedNtrip) { "Correction applicability does not match selected workflow." }
            // The legacy boolean cannot supply coordinate identity or prove MODE BASE agreement.
            require(!hasAcceptedBaseCoordinate || baseCoordinates.isNotEmpty() || settingsSet.basePositionProfileRef == null) {
                "Supply selected accepted base coordinates, not only a presence flag."
            }
            return resolve(settingsSet, ActiveSetupSelections(settingsSet.id), ActiveSetupProfileGraph(
                commandProfiles = listOf(commandProfile), usbBaudProfiles = listOf(usbBaudProfile),
                ntripCasterProfiles = listOfNotNull(ntripCasterProfile), ntripMountpointProfiles = listOfNotNull(ntripMountpointProfile),
                ntripCasterUploadProfiles = listOfNotNull(ntripCasterUploadProfile), rtklibProfiles = listOfNotNull(rtklibProfile),
                solutionPolicyProfiles = listOfNotNull(solutionPolicyProfile), recordingOutputProfiles = listOf(recordingPolicyProfile),
                storageProfiles = listOf(storageProfile), baseCoordinates = baseCoordinates,
            ), workflowName, passwordLookup, currentWorkflowId = settingsSet.workflowId)
        }

        private fun fromResolvedProfiles(
            settingsSet: RecordingSettingsSet,
            commandProfile: CommandProfile,
            usbBaudProfile: UsbBaudProfile,
            ntripCasterProfile: NtripCasterProfile?,
            ntripMountpointProfile: NtripMountpointProfile?,
            ntripCasterUploadProfile: NtripCasterUploadProfile?,
            recordingPolicyProfile: RecordingPolicyProfile,
            storageProfile: StorageProfile,
            rtklibProfile: RtklibProfile?,
            solutionPolicyProfile: SolutionPolicyProfile?,
            workflowName: String,
            workflowUsesNtrip: Boolean,
            hasAcceptedBaseCoordinate: Boolean,
            passwordLookup: (String) -> String?,
        ): ActiveRecordingConfig {
            val profileBaud = usbBaudProfile.profileBaud
            val serialBaud = usbBaudProfile.serialBaud
            val baudSwitchCommands = receiverBaudTransitionCommands(commandProfile.receiverFamily, profileBaud, serialBaud)

            val ntrip = if (workflowUsesNtrip) {
                ActiveNtripConfig.fromProfiles(checkNotNull(ntripCasterProfile), checkNotNull(ntripMountpointProfile), passwordLookup)
            } else {
                ActiveNtripConfig(false, "", 2101, "", "", null, null, null, null, null)
            }

            val casterUploadEnabled = settingsSet.baseCasterUploadEnabled
            val casterUploadSecretRef = if (casterUploadEnabled) {
                ntripCasterUploadProfile?.secretId.orEmpty()
            } else {
                ""
            }
            val casterUploadPassword = casterUploadSecretRef
                .takeIf { casterUploadEnabled && it.isNotBlank() }
                ?.let { readOwnerPassword(it, passwordLookup) }
            val uploadHost = ntripCasterUploadProfile?.host.orEmpty()
            val uploadPort = ntripCasterUploadProfile?.port ?: 2101
            val casterUpload = ActiveCasterUploadConfig(
                enabled = casterUploadEnabled,
                host = uploadHost,
                port = uploadPort,
                mountpoint = ntripCasterUploadProfile?.mountpoint.orEmpty(),
                username = ntripCasterUploadProfile?.username.orEmpty(),
                secretRef = casterUploadSecretRef.takeIf(String::isNotBlank),
                password = casterUploadPassword,
                protocolPolicy = ntripCasterUploadProfile?.protocolPolicy ?: "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY",
                retryMode = ntripCasterUploadProfile?.retryMode ?: NtripCasterUploadRetryMode.ADAPTIVE,
                fixedReconnectDelaySeconds = ntripCasterUploadProfile?.fixedReconnectDelaySeconds ?: 10,
                adaptiveInitialDelaySeconds = ntripCasterUploadProfile?.adaptiveInitialDelaySeconds ?: 10,
                adaptiveMaxDelaySeconds = ntripCasterUploadProfile?.adaptiveMaxDelaySeconds ?: 300,
                stopAfterFailuresEnabled = ntripCasterUploadProfile?.stopAfterFailuresEnabled ?: true,
                stopAfterConsecutiveFailures = ntripCasterUploadProfile?.stopAfterConsecutiveFailures ?: 5,
                safetyRulesEnabled = ntripCasterUploadProfile?.safetyRulesEnabled ?: false,
                safetyMaxBitrateKbps = ntripCasterUploadProfile?.safetyMaxBitrateKbps ?: 35,
                safetyBitrateWindowSeconds = ntripCasterUploadProfile?.safetyBitrateWindowSeconds ?: 60,
                safetyMaxSessionUploadMb = ntripCasterUploadProfile?.safetyMaxSessionUploadMb ?: 500,
                effectiveSafetyRulesEnabled = ntripCasterUploadProfile?.effectiveSafetyRulesEnabled ?: false,
                hasAcceptedBaseCoordinate = hasAcceptedBaseCoordinate,
                transportMode = ntripCasterUploadProfile?.transportMode ?: NtripTransportMode.TLS,
                tlsVerification = ntripCasterUploadProfile?.tlsVerification ?: NtripTlsVerification.SystemTrust,
                unsafeTlsAcknowledged = ntripCasterUploadProfile?.let {
                    it.unsafeTlsAcknowledged && uploadHost == it.host && uploadPort == it.port
                } == true,
                requiresTlsVerificationChoice = ntripCasterUploadProfile?.requiresTlsVerificationChoice == true,
            )
            val resolvedModeCommands = commandProfile.runtimeScript.commandLines()
            val resolvedInitCommands = commandProfile.initScript.commandLines()
            val resolvedShutdownCommands = commandProfile.shutdownScript.commandLines()
            val effectiveRtklibProfile = rtklibProfile.takeIf { settingsSet.workflowId.workflowUsesRtklibForStart() }
            val rtklibEnabled = effectiveRtklibProfile?.enabled == true

            val rtklibValidation = RtklibStartValidator.validate(
                enabled = rtklibEnabled,
                receiverProfileId = settingsSet.receiverProfileId,
                commands = resolvedInitCommands + baudSwitchCommands + resolvedModeCommands,
                ntripEnabled = workflowUsesNtrip,
                ntripConfigured = ntrip.isConfigured,
                outputNmea = effectiveRtklibProfile?.outputNmea ?: false,
                outputPos = effectiveRtklibProfile?.outputPos ?: false,
            )
            val rtklib = ActiveRtklibConfig(
                enabled = rtklibEnabled,
                profileId = effectiveRtklibProfile?.id,
                preset = effectiveRtklibProfile?.preset ?: RtklibProfile.PRESET_ROVER_KINEMATIC_RTK,
                snapshotId = RtklibSnapshot.ID,
                routePlan = rtklibValidation.routePlan,
                validationSummary = rtklibValidation.validationSummary,
                validationErrors = rtklibValidation.errors,
                outputNmea = effectiveRtklibProfile?.outputNmea ?: false,
                outputPos = effectiveRtklibProfile?.outputPos ?: false,
                maxRoverQueueBytes = effectiveRtklibProfile?.maxRoverQueueBytes ?: RtklibProfile.DEFAULT_MAX_ROVER_QUEUE_BYTES,
                maxCorrectionQueueBytes = effectiveRtklibProfile?.maxCorrectionQueueBytes
                    ?: RtklibProfile.DEFAULT_MAX_CORRECTION_QUEUE_BYTES,
                frequencyCount = effectiveRtklibProfile?.frequencyCount ?: RtklibProfile.DEFAULT_FREQUENCY_COUNT,
                serverCycleMillis = effectiveRtklibProfile?.serverCycleMillis ?: RtklibProfile.DEFAULT_SERVER_CYCLE_MILLIS,
                serverBufferBytes = effectiveRtklibProfile?.serverBufferBytes ?: RtklibProfile.DEFAULT_SERVER_BUFFER_BYTES,
                solutionBufferBytes = effectiveRtklibProfile?.solutionBufferBytes
                    ?: RtklibProfile.DEFAULT_SOLUTION_BUFFER_BYTES,
            )

            val recordingOutput = ActiveRecordingOutputConfig(
                recordTxToReceiver = recordingPolicyProfile.recordTxToReceiver,
                recordNtripCorrectionInput = workflowUsesNtrip &&
                    recordingPolicyProfile.recordNtripCorrectionInput,
                exportNmea = recordingPolicyProfile.exportNmea,
                pppNmeaGgaQuality = recordingPolicyProfile.pppNmeaGgaQuality,
                exportJsonSolution = recordingPolicyProfile.exportJsonSolution,
                exportGpx = recordingPolicyProfile.exportGpx,
                recordRemoteBaseRaw = workflowUsesNtrip &&
                    recordingPolicyProfile.recordRemoteBaseRaw,
                enableMockLocation = recordingPolicyProfile.enableMockLocation,
                mockLocationRateHz = recordingPolicyProfile.mockLocationRateHz,
            )

            val solutionPolicy = ActiveSolutionPolicyConfig(
                profileId = solutionPolicyProfile?.id,
                screenPolicy = solutionPolicyProfile?.screenPolicy ?: SolutionSourcePolicy.AUTO_BEST,
                mockPolicy = solutionPolicyProfile?.mockPolicy ?: SolutionSourcePolicy.AUTO_BEST,
            )

            val storage = ActiveStorageConfig(
                id = storageProfile.id,
                kind = storageProfile.kind,
                treeUri = storageProfile.treeUri,
            )

            return ActiveRecordingConfig(
                workflowId = settingsSet.workflowId,
                workflowName = workflowName,
                receiverProfileId = settingsSet.receiverProfileId,
                commandProfileId = commandProfile.id,
                commandReceiverFamily = commandProfile.receiverFamily,
                satelliteTelemetry = commandProfile.satelliteTelemetry,
                usbBaudProfileId = usbBaudProfile.id,
                profileBaud = profileBaud,
                serialBaud = serialBaud,
                initCommands = resolvedInitCommands,
                baudSwitchCommands = baudSwitchCommands,
                modeCommands = resolvedModeCommands,
                shutdownCommands = resolvedShutdownCommands,
                ntrip = ntrip,
                casterUpload = casterUpload,
                rtklib = rtklib,
                solutionPolicy = solutionPolicy,
                recording = recordingOutput,
                storage = storage,
            )
        }
    }
}

data class ActiveSolutionPolicyConfig(
    val profileId: String?,
    val screenPolicy: SolutionSourcePolicy,
    val mockPolicy: SolutionSourcePolicy,
)

data class ActiveRtklibConfig(
    val enabled: Boolean,
    val profileId: String?,
    val preset: String,
    val snapshotId: String?,
    val routePlan: String?,
    val validationSummary: String?,
    val validationErrors: List<String>,
    val outputNmea: Boolean,
    val outputPos: Boolean,
    val maxRoverQueueBytes: Int,
    val maxCorrectionQueueBytes: Int,
    val frequencyCount: Int,
    val serverCycleMillis: Int,
    val serverBufferBytes: Int,
    val solutionBufferBytes: Int,
)

data class ActiveCasterUploadConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val mountpoint: String,
    val username: String,
    val secretRef: String?,
    val password: String?,
    val protocolPolicy: String,
    val retryMode: NtripCasterUploadRetryMode,
    val fixedReconnectDelaySeconds: Int,
    val adaptiveInitialDelaySeconds: Int,
    val adaptiveMaxDelaySeconds: Int,
    val stopAfterFailuresEnabled: Boolean,
    val stopAfterConsecutiveFailures: Int,
    val safetyRulesEnabled: Boolean,
    val safetyMaxBitrateKbps: Int,
    val safetyBitrateWindowSeconds: Int,
    val safetyMaxSessionUploadMb: Int,
    val effectiveSafetyRulesEnabled: Boolean,
    val hasAcceptedBaseCoordinate: Boolean,
    val transportMode: NtripTransportMode = NtripTransportMode.TLS,
    val tlsVerification: NtripTlsVerification = NtripTlsVerification.SystemTrust,
    val unsafeTlsAcknowledged: Boolean = false,
    val requiresTlsVerificationChoice: Boolean = false,
) {
    fun toCore(allowInsecure: Boolean): NtripEndpointSecurityPolicy =
        ntripSecurityPolicy(host, port, transportMode, tlsVerification, unsafeTlsAcknowledged, allowInsecure)
            .also { require(!requiresTlsVerificationChoice) { "Choose system-trusted TLS or explicit plaintext in profile settings before connecting." } }
}

data class ActiveNtripConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val mountpoint: String,
    val username: String,
    val secretRef: String?,
    val password: String?,
    val stationId: String?,
    val baseLatDeg: Double?,
    val baseLonDeg: Double?,
    val transportMode: NtripTransportMode = NtripTransportMode.TLS,
    val tlsVerification: NtripTlsVerification = NtripTlsVerification.SystemTrust,
    val unsafeTlsAcknowledged: Boolean = false,
    val requiresTlsVerificationChoice: Boolean = false,
    val casterProfileId: String? = null,
    val sourceProfileId: String? = null,
    val ggaUploadPolicy: String = "",
    val protocolPolicy: String = "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY",
) {
    val isConfigured: Boolean get() = host.isNotBlank() && mountpoint.isNotBlank()

    fun toCore(allowInsecure: Boolean): NtripEndpointSecurityPolicy =
        ntripSecurityPolicy(host, port, transportMode, tlsVerification, unsafeTlsAcknowledged, allowInsecure)
            .also { require(!requiresTlsVerificationChoice) { "Choose system-trusted TLS or explicit plaintext in profile settings before connecting." } }

    companion object {
        fun fromProfiles(
            caster: NtripCasterProfile,
            mountpoint: NtripMountpointProfile,
            passwordLookup: (String) -> String?,
        ): ActiveNtripConfig {
            caster.validate()
            validateCorrectionProtocolPolicy(caster.protocolPolicy)
            mountpoint.validate()
            require(mountpoint.casterProfileId == caster.id) { "Selected source belongs to another caster profile." }
            require(caster.host.isNotBlank()) { "NTRIP host is required." }
            require(mountpoint.mountpoint.isNotBlank()) { "NTRIP mountpoint is required." }
            caster.toCore(false)
            val secret = caster.secretId.takeIf(String::isNotBlank)
            return ActiveNtripConfig(
                enabled = true, host = caster.host, port = caster.port, mountpoint = mountpoint.mountpoint,
                username = caster.username, secretRef = secret, password = secret?.let { readOwnerPassword(it, passwordLookup) },
                stationId = mountpoint.stationId, baseLatDeg = mountpoint.baseLatDeg, baseLonDeg = mountpoint.baseLonDeg,
                transportMode = caster.transportMode, tlsVerification = caster.tlsVerification,
                unsafeTlsAcknowledged = caster.unsafeTlsAcknowledged,
                requiresTlsVerificationChoice = caster.requiresTlsVerificationChoice,
                casterProfileId = caster.id, sourceProfileId = mountpoint.id, ggaUploadPolicy = mountpoint.ggaUploadPolicy,
                protocolPolicy = caster.protocolPolicy,
            )
        }
    }
}

data class ActiveRecordingOutputConfig(
    val recordTxToReceiver: Boolean,
    val recordNtripCorrectionInput: Boolean,
    val exportNmea: Boolean,
    val pppNmeaGgaQuality: Int,
    val exportJsonSolution: Boolean,
    val exportGpx: Boolean,
    val recordRemoteBaseRaw: Boolean,
    val enableMockLocation: Boolean,
    val mockLocationRateHz: Int,
    val expectedSessionArtifacts: Set<SessionArtifact> = buildSessionArtifacts(
        recordTxToReceiver,
        recordNtripCorrectionInput,
    ),
)

data class ActiveStorageConfig(
    val id: String,
    val kind: String,
    val treeUri: String?,
)

internal fun validateCorrectionProtocolPolicy(policy: String) {
    require(policy in setOf("NTRIP_V1_ONLY", "NTRIP_V2_ONLY", "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY")) {
        "NTRIP correction protocol policy is invalid."
    }
}

private fun readOwnerPassword(secretId: String, lookup: (String) -> String?): String? =
    try { lookup(secretId) } catch (_: RuntimeException) {
        throw IllegalArgumentException("Selected profile credentials could not be read.")
    }

private fun buildSessionArtifacts(
    recordTxToReceiver: Boolean,
    recordNtripCorrectionInput: Boolean,
): Set<SessionArtifact> {
    val artifacts = mutableSetOf(
        SessionArtifact.RECEIVER_RX_RAW,
        SessionArtifact.EVENTS_JSONL,
        SessionArtifact.QUALITY_LIVE_JSONL,
    )

    if (recordTxToReceiver) {
        artifacts += SessionArtifact.TX_TO_RECEIVER_RAW
    }
    if (recordNtripCorrectionInput) {
        artifacts += SessionArtifact.CORRECTION_INPUT_RAW
        artifacts += SessionArtifact.CORRECTION_INPUT_RTCM3
    }
    return artifacts
}

private fun String.commandLines(): List<String> =
    lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toList()

private fun List<String>.containsModeCommand(mode: String): Boolean =
    any { command ->
        val parts = command.trim().split(Regex("\\s+"))
        parts.size >= 2 &&
            parts[0].equals("MODE", ignoreCase = true) &&
            parts[1].equals(mode, ignoreCase = true)
    }

private fun String.workflowUsesRtklibForStart(): Boolean =
    this == WORKFLOW_ROVER_RTKLIB || this == WORKFLOW_ROVER_NTRIP_RTKLIB

internal fun validateWorkflowModeCommandsForStart(workflowId: String?, modeCommands: List<String>) {
    if (workflowId == WORKFLOW_PLAIN_ROVER ||
        workflowId == WORKFLOW_ROVER_NTRIP ||
        workflowId == WORKFLOW_ROVER_RTKLIB ||
        workflowId == WORKFLOW_ROVER_NTRIP_RTKLIB
    ) {
        require(!modeCommands.containsModeCommand("BASE")) {
            "Rover workflow cannot start with a command profile that sets MODE BASE."
        }
    }
    if (workflowId == WORKFLOW_FIXED_BASE) {
        require(!modeCommands.containsModeCommand("ROVER")) {
            "Fixed base workflow cannot start with a command profile that sets MODE ROVER."
        }
    }
}

internal fun validateUm980OutputFrequenciesForStart(receiverFamily: String?, commands: List<String>) {
    if (!receiverFamily.orEmpty().isUm980ReceiverFamily()) return
    Um980OutputFrequencyValidator.validateCommands(commands)?.let { error ->
        throw IllegalArgumentException(error)
    }
}

private fun String.isUm980ReceiverFamily(): Boolean =
    contains("um980", ignoreCase = true) ||
        contains("unicore", ignoreCase = true) ||
        contains("n4", ignoreCase = true)

private const val WORKFLOW_PLAIN_ROVER = "plain-rover"
private const val WORKFLOW_ROVER_NTRIP = "rover-ntrip"
private const val WORKFLOW_ROVER_RTKLIB = "rover-rtklib"
private const val WORKFLOW_ROVER_NTRIP_RTKLIB = "rover-ntrip-rtklib"
private const val WORKFLOW_BASE_CALIBRATION = "base-calibration"
private const val WORKFLOW_FIXED_BASE = "fixed-base"
