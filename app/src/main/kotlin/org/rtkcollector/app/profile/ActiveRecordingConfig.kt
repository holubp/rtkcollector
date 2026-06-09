package org.rtkcollector.app.profile

import org.rtkcollector.core.workflow.SessionArtifact

data class ActiveRecordingConfig(
    val workflowId: String,
    val workflowName: String,
    val receiverProfileId: String,
    val commandProfileId: String,
    val usbBaudProfileId: String,
    val profileBaud: Int,
    val serialBaud: Int,
    val initCommands: List<String>,
    val baudSwitchCommands: List<String>,
    val modeCommands: List<String>,
    val shutdownCommands: List<String>,
    val ntrip: ActiveNtripConfig,
    val recording: ActiveRecordingOutputConfig,
    val storage: ActiveStorageConfig,
) {
    val expectedSessionArtifactNames: List<String> by lazy {
        recording.expectedSessionArtifacts.map(SessionArtifact::name).sorted()
    }

    fun validateForStart() {
        if (ntrip.enabled) {
            require(ntrip.host.isNotBlank()) { "NTRIP host is required for ${workflowName}." }
            require(ntrip.port in 1..65535) { "NTRIP port must be 1..65535." }
            require(ntrip.mountpoint.isNotBlank()) { "NTRIP mountpoint is required for ${workflowName}." }
        }
    }

    companion object {
        fun resolve(
            settingsSet: RecordingSettingsSet,
            commandProfile: CommandProfile,
            usbBaudProfile: UsbBaudProfile,
            ntripCasterProfile: NtripCasterProfile?,
            ntripMountpointProfile: NtripMountpointProfile?,
            recordingPolicyProfile: RecordingPolicyProfile,
            storageProfile: StorageProfile,
            workflowName: String,
            workflowUsesNtrip: Boolean,
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
            recordingPolicyProfile.validate()
            storageProfile.validate()

            val commandOverride = settingsSet.overrides.command
            val baudOverride = settingsSet.overrides.usbBaud
            val casterOverride = settingsSet.overrides.ntripCaster
            val mountOverride = settingsSet.overrides.ntripMountpoint
            val recordingOverride = settingsSet.overrides.recordingOutput
            val storageOverride = settingsSet.overrides.storage

            val profileBaud = localProfileBaud ?: baudOverride?.profileBaud ?: usbBaudProfile.profileBaud
            val serialBaud = localSerialBaud ?: baudOverride?.serialBaud ?: usbBaudProfile.serialBaud
            val baudSwitchCommands = if (profileBaud == serialBaud) {
                emptyList()
            } else {
                listOf("CONFIG COM1 $serialBaud")
            }

            val ntripSecretRef =
                if (workflowUsesNtrip) {
                    localNtripSecretRef ?: casterOverride?.secretId ?: ntripCasterProfile?.secretId.orEmpty()
                } else {
                    ""
                }

            val ntrip = ActiveNtripConfig(
                enabled = workflowUsesNtrip,
                host = localNtripHost ?: casterOverride?.host ?: ntripCasterProfile?.host.orEmpty(),
                port = localNtripPort ?: casterOverride?.port ?: ntripCasterProfile?.port ?: 2101,
                mountpoint = localNtripMountpoint ?: mountOverride?.mountpoint ?: ntripMountpointProfile?.mountpoint.orEmpty(),
                username = localNtripUsername ?: casterOverride?.username ?: ntripCasterProfile?.username.orEmpty(),
                secretRef = ntripSecretRef.takeIf { it.isNotBlank() },
                password = ntripSecretRef.takeIf { workflowUsesNtrip && it.isNotBlank() }?.let(passwordLookup),
                stationId = mountOverride?.stationId,
                baseLatDeg = mountOverride?.baseLatDeg,
                baseLonDeg = mountOverride?.baseLonDeg,
            )

            val recordingOutput = ActiveRecordingOutputConfig(
                recordTxToReceiver = recordingOverride?.recordTxToReceiver ?: recordingPolicyProfile.recordTxToReceiver,
                recordNtripCorrectionInput = workflowUsesNtrip &&
                    (recordingOverride?.recordNtripCorrectionInput ?: recordingPolicyProfile.recordNtripCorrectionInput),
                exportNmea = recordingOverride?.exportNmea ?: recordingPolicyProfile.exportNmea,
                exportJsonSolution = recordingOverride?.exportJsonSolution
                    ?: recordingPolicyProfile.exportJsonSolution,
                exportGpx = recordingOverride?.exportGpx ?: recordingPolicyProfile.exportGpx,
                recordRemoteBaseRaw = workflowUsesNtrip &&
                    (recordingOverride?.recordRemoteBaseRaw ?: recordingPolicyProfile.recordRemoteBaseRaw),
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
                usbBaudProfileId = usbBaudProfile.id,
                profileBaud = profileBaud,
                serialBaud = serialBaud,
                initCommands = (localInitCommands ?: commandOverride?.initScript ?: commandProfile.initScript).commandLines(),
                baudSwitchCommands = baudSwitchCommands,
                modeCommands = modeCommands,
                shutdownCommands = (localShutdownCommands ?: commandOverride?.shutdownScript ?: commandProfile.shutdownScript).commandLines(),
                ntrip = ntrip,
                recording = recordingOutput,
                storage = storage,
            )
        }
    }
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
) {
    val isConfigured: Boolean get() = host.isNotBlank() && mountpoint.isNotBlank()
}

data class ActiveRecordingOutputConfig(
    val recordTxToReceiver: Boolean,
    val recordNtripCorrectionInput: Boolean,
    val exportNmea: Boolean,
    val exportJsonSolution: Boolean,
    val exportGpx: Boolean,
    val recordRemoteBaseRaw: Boolean,
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
    }
    return artifacts
}

private fun String.commandLines(): List<String> =
    lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toList()
