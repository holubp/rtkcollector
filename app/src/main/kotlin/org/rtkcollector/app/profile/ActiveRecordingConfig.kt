package org.rtkcollector.app.profile

import org.rtkcollector.core.correction.DEFAULT_NTRIP_USER_AGENT
import org.rtkcollector.core.correction.NtripSourceUploadRequest
import org.rtkcollector.core.correction.Um980RtcmBaseOutputSanity
import org.rtkcollector.core.correction.normalizeSourceUploadMountpoint
import org.rtkcollector.core.rtklib.RtklibSnapshot
import org.rtkcollector.core.solution.SolutionSourcePolicy
import org.rtkcollector.core.workflow.SessionArtifact
import org.rtkcollector.receiver.ublox.UbloxBaudCommands
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
        validateUm980OutputFrequenciesForStart(
            receiverFamily = commandReceiverFamily,
            commands = initCommands + baudSwitchCommands + modeCommands,
        )
        if (rtklib.enabled) {
            require(rtklib.validationErrors.isEmpty()) { rtklib.validationErrors.joinToString(" ") }
        }
        if (ntrip.enabled) {
            require(ntrip.host.isNotBlank()) { "NTRIP host is required for ${workflowName}." }
            require(ntrip.port in 1..65535) { "NTRIP port must be 1..65535." }
            require(ntrip.mountpoint.isNotBlank()) { "NTRIP mountpoint is required for ${workflowName}." }
        }
        if (casterUpload.enabled) {
            require(casterUpload.host.isNotBlank()) { "NTRIP caster upload host is required for ${workflowName}." }
            require(casterUpload.port in 1..65535) { "NTRIP caster upload port must be 1..65535." }
            require(casterUpload.mountpoint.isNotBlank()) { "NTRIP caster upload mountpoint is required for ${workflowName}." }
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
            localInitCommands: String? = null,
            localShutdownCommands: String? = null,
            localProfileBaud: Int? = null,
            localSerialBaud: Int? = null,
            localNtripHost: String? = null,
            localNtripPort: Int? = null,
            localNtripMountpoint: String? = null,
            localNtripUsername: String? = null,
            localNtripSecretRef: String? = null,
            modeCommands: List<String> = emptyList(),
        ): ActiveRecordingConfig {
            settingsSet.validate()
            commandProfile.validate()
            usbBaudProfile.validate()
            ntripCasterProfile?.validate()
            ntripMountpointProfile?.validate()
            ntripCasterUploadProfile?.validate()
            recordingPolicyProfile.validate()
            storageProfile.validate()
            rtklibProfile?.validate()

            val commandOverride = settingsSet.overrides.command
            val baudOverride = settingsSet.overrides.usbBaud
            val casterOverride = settingsSet.overrides.ntripCaster
            val mountOverride = settingsSet.overrides.ntripMountpoint
            val casterUploadOverride = settingsSet.overrides.ntripCasterUpload
            val recordingOverride = settingsSet.overrides.recordingOutput
            val storageOverride = settingsSet.overrides.storage

            val profileBaud = localProfileBaud ?: baudOverride?.profileBaud ?: usbBaudProfile.profileBaud
            val serialBaud = localSerialBaud ?: baudOverride?.serialBaud ?: usbBaudProfile.serialBaud
            val isUblox = commandProfile.receiverFamily.startsWith("ublox", ignoreCase = true)
            val baudSwitchCommands = if (profileBaud == serialBaud) {
                emptyList()
            } else if (isUblox) {
                listOf(UbloxBaudCommands.uart1BaudCommand(serialBaud))
            } else {
                listOf("CONFIG COM1 $serialBaud")
            }

            val profileOwnedNtripSecretRef = ntripCasterProfile
                ?.let { ntripCasterSecretId(it.id) }
                .orEmpty()
            val legacyProfileNtripSecretRef = ntripCasterProfile?.secretId.orEmpty()
            val ntripSecretRef =
                if (workflowUsesNtrip) {
                    localNtripSecretRef ?: casterOverride?.secretId ?: profileOwnedNtripSecretRef
                } else {
                    ""
                }
            val ntripPassword = ntripSecretRef
                .takeIf { workflowUsesNtrip && it.isNotBlank() }
                ?.let { secretRef ->
                    passwordLookup(secretRef)
                        ?: legacyProfileNtripSecretRef
                            .takeIf { legacyRef ->
                                secretRef == profileOwnedNtripSecretRef &&
                                    legacyRef.isNotBlank() &&
                                    legacyRef != profileOwnedNtripSecretRef
                            }
                            ?.let(passwordLookup)
                }

            val ntrip = ActiveNtripConfig(
                enabled = workflowUsesNtrip,
                host = localNtripHost ?: casterOverride?.host ?: ntripCasterProfile?.host.orEmpty(),
                port = localNtripPort ?: casterOverride?.port ?: ntripCasterProfile?.port ?: 2101,
                mountpoint = localNtripMountpoint ?: mountOverride?.mountpoint ?: ntripMountpointProfile?.mountpoint.orEmpty(),
                username = localNtripUsername ?: casterOverride?.username ?: ntripCasterProfile?.username.orEmpty(),
                secretRef = ntripSecretRef.takeIf { it.isNotBlank() },
                password = ntripPassword,
                stationId = mountOverride?.stationId,
                baseLatDeg = mountOverride?.baseLatDeg,
                baseLonDeg = mountOverride?.baseLonDeg,
            )

            val profileOwnedUploadSecretRef = ntripCasterUploadProfile
                ?.let { ntripCasterUploadSecretId(it.id) }
                .orEmpty()
            val legacyUploadSecretRef = ntripCasterUploadProfile?.secretId.orEmpty()
            val casterUploadEnabled = ntripCasterUploadProfile != null &&
                (settingsSet.baseCasterUploadEnabled || ntripCasterUploadProfile.enabledByDefault)
            val casterUploadSecretRef = if (casterUploadEnabled) {
                casterUploadOverride?.secretId ?: profileOwnedUploadSecretRef
            } else {
                ""
            }
            val casterUploadPassword = casterUploadSecretRef
                .takeIf { casterUploadEnabled && it.isNotBlank() }
                ?.let { secretRef ->
                    passwordLookup(secretRef)
                        ?: legacyUploadSecretRef
                            .takeIf { legacyRef ->
                                secretRef == profileOwnedUploadSecretRef &&
                                    legacyRef.isNotBlank() &&
                                    legacyRef != profileOwnedUploadSecretRef
                            }
                            ?.let(passwordLookup)
                }
            val casterUpload = ActiveCasterUploadConfig(
                enabled = casterUploadEnabled,
                host = casterUploadOverride?.host ?: ntripCasterUploadProfile?.host.orEmpty(),
                port = casterUploadOverride?.port ?: ntripCasterUploadProfile?.port ?: 2101,
                mountpoint = casterUploadOverride?.mountpoint ?: ntripCasterUploadProfile?.mountpoint.orEmpty(),
                username = casterUploadOverride?.username ?: ntripCasterUploadProfile?.username.orEmpty(),
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
            )
            val resolvedModeCommands = commandProfile.runtimeScript.commandLines()
                .ifEmpty { modeCommands }
            val resolvedInitCommands = (localInitCommands ?: commandOverride?.initScript ?: commandProfile.initScript)
                .commandLines()
            val resolvedShutdownCommands = (localShutdownCommands ?: commandOverride?.shutdownScript ?: commandProfile.shutdownScript)
                .commandLines()
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
                recordTxToReceiver = recordingOverride?.recordTxToReceiver ?: recordingPolicyProfile.recordTxToReceiver,
                recordNtripCorrectionInput = workflowUsesNtrip &&
                    (recordingOverride?.recordNtripCorrectionInput ?: recordingPolicyProfile.recordNtripCorrectionInput),
                exportNmea = recordingOverride?.exportNmea ?: recordingPolicyProfile.exportNmea,
                pppNmeaGgaQuality = recordingOverride?.pppNmeaGgaQuality
                    ?: recordingPolicyProfile.pppNmeaGgaQuality,
                exportJsonSolution = recordingOverride?.exportJsonSolution
                    ?: recordingPolicyProfile.exportJsonSolution,
                exportGpx = recordingOverride?.exportGpx ?: recordingPolicyProfile.exportGpx,
                recordRemoteBaseRaw = workflowUsesNtrip &&
                    (recordingOverride?.recordRemoteBaseRaw ?: recordingPolicyProfile.recordRemoteBaseRaw),
                enableMockLocation = recordingOverride?.enableMockLocation ?: recordingPolicyProfile.enableMockLocation,
                mockLocationRateHz = recordingOverride?.mockLocationRateHz
                    ?: recordingPolicyProfile.mockLocationRateHz,
            )

            val solutionPolicy = ActiveSolutionPolicyConfig(
                profileId = solutionPolicyProfile?.id,
                screenPolicy = (solutionPolicyProfile?.screenPolicy ?: SolutionSourcePolicy.AUTO_BEST)
                    .coerceWhenRtklibInactive(rtklib.enabled),
                mockPolicy = (solutionPolicyProfile?.mockPolicy ?: SolutionSourcePolicy.AUTO_BEST)
                    .coerceWhenRtklibInactive(rtklib.enabled),
            )

            val storage = ActiveStorageConfig(
                id = storageProfile.id,
                kind = storageOverride?.kind ?: storageProfile.kind,
                treeUri = storageOverride?.treeUri ?: storageProfile.treeUri,
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
)

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
) {
    val isConfigured: Boolean get() = host.isNotBlank() && mountpoint.isNotBlank()
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

private fun SolutionSourcePolicy.coerceWhenRtklibInactive(rtklibEnabled: Boolean): SolutionSourcePolicy =
    if (!rtklibEnabled && this == SolutionSourcePolicy.RTKLIB_ONLY) {
        SolutionSourcePolicy.DEVICE_INTERNAL_ONLY
    } else {
        this
    }

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
