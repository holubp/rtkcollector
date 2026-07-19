package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.SolutionSourcePolicy
import org.rtkcollector.core.workflow.SessionArtifact

class ActiveRecordingConfigTest {
    @Test
    fun `SAF profile awaiting folder reselection cannot start`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "plain-rover",
                ntripCasterProfileRef = null,
                ntripMountpointProfileRef = null,
            ),
            commandProfile = CommandProfile("commands", "Commands", initScript = "UNLOG COM1"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud", profileBaud = 230400, serialBaud = 230400),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile(
                id = "storage",
                name = "Imported SAF folder",
                kind = "SAF_TREE",
                requiresTreeReselection = true,
            ),
            workflowName = "Plain rover recording",
            workflowUsesNtrip = false,
            passwordLookup = { null },
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            config.validateForStart()
        }

        assertTrue(failure.message.orEmpty().contains("Select the Android recording folder again"))
    }

    @Test
    fun `SAF override marker uses folder selected later on referenced profile`() {
        val selectedTreeUri = "content://documents/tree/selected-after-import"
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "plain-rover",
                ntripCasterProfileRef = null,
                ntripMountpointProfileRef = null,
                overrides = SettingsSetOverrides(
                    storage = StorageProfileOverride(
                        kind = "SAF_TREE",
                        treeUri = null,
                        requiresTreeReselection = true,
                    ),
                ),
            ),
            commandProfile = CommandProfile("commands", "Commands", initScript = "UNLOG COM1"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile(
                id = "storage",
                name = "Selected SAF folder",
                kind = "SAF_TREE",
                treeUri = selectedTreeUri,
                requiresTreeReselection = false,
            ),
            workflowName = "Plain rover recording",
            workflowUsesNtrip = false,
            passwordLookup = { null },
        )

        assertEquals(selectedTreeUri, config.storage.treeUri)
        config.validateForStart()
    }

    @Test
    fun `plain rover config disables ntrip`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "plain-rover",
                ntripCasterProfileRef = null,
                ntripMountpointProfileRef = null,
            ),
            commandProfile = CommandProfile("commands", "Commands", initScript = "UNLOG COM1"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud", profileBaud = 230400, serialBaud = 921600),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster"),
            ntripMountpointProfile = NtripMountpointProfile(
                "mount",
                "Mount",
                casterProfileId = "caster",
            ),
            recordingPolicyProfile = RecordingPolicyProfile(
                "record",
                "Record",
                recordTxToReceiver = false,
                recordNtripCorrectionInput = false,
                exportNmea = false,
                exportJsonSolution = false,
            ),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Plain rover recording",
            workflowUsesNtrip = false,
            passwordLookup = { error("Password must not be requested for non-NTRIP workflow") },
        )

        assertEquals("plain-rover", config.workflowId)
        assertFalse(config.ntrip.enabled)
        assertFalse(config.recording.recordNtripCorrectionInput)
        assertEquals(listOf(SessionArtifact.RECEIVER_RX_RAW, SessionArtifact.EVENTS_JSONL, SessionArtifact.QUALITY_LIVE_JSONL), config.recording.expectedSessionArtifacts.sorted())
        assertTrue(config.baudSwitchCommands.isNotEmpty())
    }

    @Test
    fun `command receiver family follows selected command profile`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "plain-rover",
                receiverProfileId = "um980-n4",
                ntripCasterProfileRef = null,
                ntripMountpointProfileRef = null,
            ),
            commandProfile = CommandProfile(
                id = "ublox-m8t-raw-safe",
                name = "u-blox M8T raw 1Hz safe",
                receiverFamily = "ublox-m8t",
                runtimeScript = "!UBX CFG-RATE 1000 1 1",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud", profileBaud = 230400, serialBaud = 230400),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Plain rover recording",
            workflowUsesNtrip = false,
            passwordLookup = { error("Password must not be requested for non-NTRIP workflow") },
        )

        assertEquals("ublox-m8t", config.commandReceiverFamily)
        assertEquals(listOf("!UBX CFG-RATE 1000 1 1"), config.modeCommands)
        config.validateForStart()
    }

    @Test
    fun `ublox command profile generates ubx baud switch when initial and target baud differ`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "plain-rover",
                receiverProfileId = "um980-n4",
                ntripCasterProfileRef = null,
                ntripMountpointProfileRef = null,
            ),
            commandProfile = CommandProfile(
                id = "ublox-m8t-raw-safe",
                name = "u-blox M8T raw 1Hz safe",
                receiverFamily = "ublox-m8t",
                runtimeScript = "!UBX CFG-RATE 1000 1 1",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud", profileBaud = 9600, serialBaud = 230400),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Plain rover recording",
            workflowUsesNtrip = false,
            passwordLookup = { error("Password must not be requested for non-NTRIP workflow") },
        )

        assertEquals(listOf("!UBX CFG-PRT 1 0 0 2256 230400 7 3 0 0"), config.baudSwitchCommands)
        config.validateForStart()
    }

    @Test
    fun `ntrip config uses profile and local overrides and secret lookup`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "rover-ntrip",
                overrides = SettingsSetOverrides(
                    ntripCaster = NtripCasterOverride(
                        host = "override-host",
                        port = 2222,
                        username = "override-user",
                        secretId = "secret-override",
                    ),
                    ntripMountpoint = NtripMountpointOverride(mountpoint = "OVERRIDE"),
                    command = CommandProfileOverride(initScript = "UNLOG COM1", shutdownScript = ""),
                    usbBaud = UsbBaudProfileOverride(profileBaud = 230400, serialBaud = 115200),
                    recordingOutput = RecordingOutputOverride(
                        recordTxToReceiver = false,
                        recordNtripCorrectionInput = false,
                        exportGpx = true,
                    ),
                ),
            ),
            commandProfile = CommandProfile("commands", "Commands", initScript = "#comment\nUNLOG COM1", shutdownScript = "UNLOG COM1"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud", profileBaud = 230400, serialBaud = 230400),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "legacy", port = 2101, username = "legacy"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "OLD"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { "password-for-$it" },
            localInitCommands = "CONFIG COM1 230400",
            localProfileBaud = 921600,
            localSerialBaud = 115200,
            localNtripHost = "runtime-host",
            localNtripPort = 2101,
            localNtripMountpoint = "TUBO00CZE0",
            localNtripUsername = "runtime-user",
        )

        assertTrue(config.ntrip.enabled)
        assertEquals("runtime-host", config.ntrip.host)
        assertEquals(2101, config.ntrip.port)
        assertEquals("TUBO00CZE0", config.ntrip.mountpoint)
        assertEquals("runtime-user", config.ntrip.username)
        assertEquals("secret-override", config.ntrip.secretRef)
        assertEquals("password-for-secret-override", config.ntrip.password)
        assertEquals("CONFIG COM1 230400", config.initCommands.single())
        assertEquals(921600, config.profileBaud)
        assertEquals(115200, config.serialBaud)
        assertTrue(config.baudSwitchCommands.contains("CONFIG COM1 115200"))
        assertEquals(false, config.recording.expectedSessionArtifacts.contains(SessionArtifact.TX_TO_RECEIVER_RAW))
        assertEquals(false, config.recording.expectedSessionArtifacts.contains(SessionArtifact.CORRECTION_INPUT_RAW))
        assertEquals(false, config.recording.expectedSessionArtifacts.contains(SessionArtifact.CORRECTION_INPUT_RTCM3))
    }

    @Test
    fun `recording artifacts include tx and correction when enabled`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip(),
            commandProfile = CommandProfile("commands", "Commands"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "OLD"),
            recordingPolicyProfile = RecordingPolicyProfile(
                "record",
                "Record",
                recordTxToReceiver = true,
                recordNtripCorrectionInput = true,
            ),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { "password-for-$it" },
        )

        assertTrue(config.recording.expectedSessionArtifacts.contains(SessionArtifact.TX_TO_RECEIVER_RAW))
        assertTrue(config.recording.expectedSessionArtifacts.contains(SessionArtifact.CORRECTION_INPUT_RAW))
        assertTrue(config.recording.expectedSessionArtifacts.contains(SessionArtifact.CORRECTION_INPUT_RTCM3))
    }

    @Test
    fun `rtklib disabled by default adds no rtklib artifacts`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip(),
            commandProfile = CommandProfile("commands", "Commands"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "OLD"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        assertFalse(config.rtklib.enabled)
        assertFalse(config.expectedSessionArtifactNames.contains(SessionArtifact.RTKLIB_SOLUTION_NMEA.name))
        assertFalse(config.expectedSessionArtifactNames.contains(SessionArtifact.RTKLIB_SOLUTION_POS.name))
    }

    @Test
    fun `enabled rtklib profile adds configured output artifacts`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "rover-ntrip-rtklib",
                rtklibProfileRef = ProfileReference("rtklib-rover", "RTKLIB rover"),
                receiverProfileId = "um980-n4",
            ),
            commandProfile = CommandProfile("commands", "Commands", runtimeScript = "MODE ROVER\nOBSVMB COM1 0.2"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            rtklibProfile = RtklibProfile(
                id = "rtklib-rover",
                name = "RTKLIB rover",
                enabled = true,
                outputNmea = true,
                outputPos = false,
            ),
            workflowName = "Rover + RTKLIB",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        assertTrue(config.rtklib.enabled)
        assertEquals("ROVER_KINEMATIC_RTK", config.rtklib.preset)
        assertEquals("rtklib-ex-2.5.0@8dfabc9a106b2e74c069bc80f0d7743f314e6ab4", config.rtklib.snapshotId)
        assertEquals("valid; snapshot=rtklib-ex-2.5.0@8dfabc9a106b2e74c069bc80f0d7743f314e6ab4", config.rtklib.validationSummary)
        assertTrue(config.rtklib.routePlan.orEmpty().contains("input_unicore(UNICORE_OBSVMB)"))
        assertTrue(config.expectedSessionArtifactNames.contains(SessionArtifact.RTKLIB_SOLUTION_NMEA.name))
        assertFalse(config.expectedSessionArtifactNames.contains(SessionArtifact.RTKLIB_SOLUTION_POS.name))
    }

    @Test
    fun `enabled rtklib accepts UM980 compact OBSVMCMPB through decoder shim`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "rover-ntrip-rtklib",
                rtklibProfileRef = ProfileReference("rtklib-rover", "RTKLIB rover"),
                receiverProfileId = "um980-n4",
            ),
            commandProfile = CommandProfile("commands", "Commands", runtimeScript = "MODE ROVER\nOBSVMCMPB COM1 0.2"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            rtklibProfile = RtklibProfile(
                id = "rtklib-rover",
                name = "RTKLIB rover",
                enabled = true,
                outputNmea = true,
                outputPos = true,
            ),
            workflowName = "Rover + RTKLIB",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        config.validateForStart()

        assertTrue(config.rtklib.validationErrors.isEmpty())
        assertTrue(config.rtklib.routePlan.orEmpty().contains("rtkcollector-obsvmcmp-shim(UNICORE_OBSVMCMPB)"))
    }

    @Test
    fun `plain rover workflow ignores remembered enabled rtklib profile`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "plain-rover",
                rtklibProfileRef = ProfileReference("rtklib-rover", "RTKLIB rover"),
            ),
            commandProfile = CommandProfile("commands", "Commands"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            rtklibProfile = RtklibProfile(
                id = "rtklib-rover",
                name = "RTKLIB rover",
                enabled = true,
            ),
            workflowName = "Plain rover",
            workflowUsesNtrip = false,
            passwordLookup = { null },
        )

        config.validateForStart()

        assertFalse(config.rtklib.enabled)
        assertFalse(config.expectedSessionArtifactNames.contains(SessionArtifact.RTKLIB_STATUS_JSONL.name))
    }

    @Test
    fun `ublox rtklib rover workflow accepts configured ntrip corrections`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "rover-rtklib",
                receiverProfileId = "ublox-m8t",
                rtklibProfileRef = ProfileReference("rtklib-rover", "RTKLIB rover"),
            ),
            commandProfile = CommandProfile(
                id = "commands",
                name = "Commands",
                receiverFamily = "ublox-m8t",
                runtimeScript = "!UBX CFG-MSG 2 21 0 0 0 1 0 0",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            rtklibProfile = RtklibProfile(
                id = "rtklib-rover",
                name = "RTKLIB rover",
                enabled = true,
            ),
            workflowName = "Rover + RTKLIB",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        config.validateForStart()

        assertTrue(config.rtklib.validationErrors.isEmpty())
        assertTrue(config.rtklib.routePlan.orEmpty().contains("input_ubx(UBX_RXM_RAWX_SFRBX)"))
    }

    @Test
    fun `active config carries rtklib server runtime parameters`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "rover-rtklib",
                receiverProfileId = "ublox-m8t",
                rtklibProfileRef = ProfileReference("rtklib-rover", "RTKLIB rover"),
            ),
            commandProfile = CommandProfile(
                id = "commands",
                name = "Commands",
                receiverFamily = "ublox-m8t",
                runtimeScript = "!UBX CFG-MSG 2 21 0 0 0 1 0 0",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            rtklibProfile = RtklibProfile(
                id = "rtklib-rover",
                name = "RTKLIB rover",
                enabled = true,
                frequencyCount = 1,
                serverCycleMillis = 50,
                serverBufferBytes = 65536,
                solutionBufferBytes = 65536,
            ),
            workflowName = "Rover + RTKLIB",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        assertEquals(1, config.rtklib.frequencyCount)
        assertEquals(50, config.rtklib.serverCycleMillis)
        assertEquals(65536, config.rtklib.serverBufferBytes)
        assertEquals(65536, config.rtklib.solutionBufferBytes)
    }

    @Test
    fun `rtklib rover workflow rejects base mode command profiles`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "rover-rtklib",
                receiverProfileId = "ublox-m8t",
                rtklibProfileRef = ProfileReference("rtklib-rover", "RTKLIB rover"),
            ),
            commandProfile = CommandProfile(
                id = "commands",
                name = "Commands",
                receiverFamily = "ublox-m8t",
                runtimeScript = "MODE BASE",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            rtklibProfile = RtklibProfile(
                id = "rtklib-rover",
                name = "RTKLIB rover",
                enabled = true,
            ),
            workflowName = "Rover + RTKLIB",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertTrue(error.message.orEmpty().contains("Rover workflow cannot start with a command profile that sets MODE BASE."))
    }

    @Test
    fun `ntrip runtime uses profile-bound secret and falls back to legacy stored password`() {
        val lookedUpSecrets = mutableListOf<String>()
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip(),
            commandProfile = CommandProfile("commands", "Commands"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile(
                id = "caster",
                name = "Caster",
                host = "caster.example.org",
                username = "user",
                secretId = "legacy-shared-secret",
            ),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "OLD"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { secretId ->
                lookedUpSecrets += secretId
                if (secretId == "legacy-shared-secret") "legacy-password" else null
            },
        )

        assertEquals("ntrip-caster-profile:caster", config.ntrip.secretRef)
        assertEquals("legacy-password", config.ntrip.password)
        assertEquals(listOf("ntrip-caster-profile:caster", "legacy-shared-secret"), lookedUpSecrets)
    }

    @Test
    fun `temporary base config enables ntrip when workflow supports corrections`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "base-calibration"),
            commandProfile = CommandProfile("commands", "Commands", initScript = "UNLOG COM1", runtimeScript = "MODE ROVER"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud", profileBaud = 230400, serialBaud = 921600),
            ntripCasterProfile = NtripCasterProfile(
                id = "caster",
                name = "Caster",
                host = "caster.example.org",
                port = 2101,
                username = "user",
                secretId = "secret",
            ),
            ntripMountpointProfile = NtripMountpointProfile(
                id = "mount",
                name = "Mount",
                casterProfileId = "caster",
                mountpoint = "CORS01",
            ),
            recordingPolicyProfile = RecordingPolicyProfile(
                id = "record",
                name = "Record",
                recordTxToReceiver = true,
                recordNtripCorrectionInput = true,
            ),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Temporary base",
            workflowUsesNtrip = true,
            passwordLookup = { "password-for-$it" },
        )

        assertTrue(config.ntrip.enabled)
        assertEquals("caster.example.org", config.ntrip.host)
        assertEquals("CORS01", config.ntrip.mountpoint)
        assertTrue(config.recording.expectedSessionArtifacts.contains(SessionArtifact.CORRECTION_INPUT_RAW))
        assertTrue(config.recording.expectedSessionArtifacts.contains(SessionArtifact.CORRECTION_INPUT_RTCM3))
    }

    @Test
    fun `recording config uses mock location policy and settings override`() {
        val policyConfig = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip(),
            commandProfile = CommandProfile("commands", "Commands"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster"),
            recordingPolicyProfile = RecordingPolicyProfile(
                "record",
                "Record",
                enableMockLocation = true,
                mockLocationRateHz = 5,
            ),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )
        val overrideConfig = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                overrides = SettingsSetOverrides(
                    recordingOutput = RecordingOutputOverride(enableMockLocation = false, mockLocationRateHz = 10),
                ),
            ),
            commandProfile = CommandProfile("commands", "Commands"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster"),
            recordingPolicyProfile = RecordingPolicyProfile(
                "record",
                "Record",
                enableMockLocation = true,
                mockLocationRateHz = 5,
            ),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        assertEquals(true, policyConfig.recording.enableMockLocation)
        assertEquals(5, policyConfig.recording.mockLocationRateHz)
        assertEquals(false, overrideConfig.recording.enableMockLocation)
        assertEquals(10, overrideConfig.recording.mockLocationRateHz)
    }

    @Test
    fun `recording config resolves solution policy profile`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "rover-ntrip-rtklib",
                receiverProfileId = "um980-n4",
                rtklibProfileRef = ProfileReference("rtklib-rover", "RTKLIB rover"),
            ),
            commandProfile = CommandProfile(
                "commands",
                "Commands",
                runtimeScript = "MODE ROVER\nOBSVMB COM1 0.2",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            rtklibProfile = RtklibProfile(
                id = "rtklib-rover",
                name = "RTKLIB rover",
                enabled = true,
            ),
            solutionPolicyProfile = SolutionPolicyProfile(
                id = "solution-rtklib",
                name = "RTKLIB solution",
                screenPolicy = SolutionSourcePolicy.DEVICE_INTERNAL_ONLY,
                mockPolicy = SolutionSourcePolicy.RTKLIB_ONLY,
            ),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        assertEquals("solution-rtklib", config.solutionPolicy.profileId)
        assertEquals(SolutionSourcePolicy.DEVICE_INTERNAL_ONLY, config.solutionPolicy.screenPolicy)
        assertEquals(SolutionSourcePolicy.RTKLIB_ONLY, config.solutionPolicy.mockPolicy)
    }

    @Test
    fun `plain rover workflow coerces rtklib-only solution policy to device internal`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "plain-rover"),
            commandProfile = CommandProfile("commands", "Commands"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            solutionPolicyProfile = SolutionPolicyProfile(
                id = "solution-rtklib",
                name = "RTKLIB solution",
                screenPolicy = SolutionSourcePolicy.RTKLIB_ONLY,
                mockPolicy = SolutionSourcePolicy.RTKLIB_ONLY,
            ),
            workflowName = "Plain rover",
            workflowUsesNtrip = false,
            passwordLookup = { null },
        )

        assertEquals(SolutionSourcePolicy.DEVICE_INTERNAL_ONLY, config.solutionPolicy.screenPolicy)
        assertEquals(SolutionSourcePolicy.DEVICE_INTERNAL_ONLY, config.solutionPolicy.mockPolicy)
    }

    @Test
    fun `command profile runtime script becomes mode commands`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "plain-rover"),
            commandProfile = CommandProfile(
                id = "commands",
                name = "Commands",
                runtimeScript = "MODE ROVER\n\nBESTNAVB COM1 0.1",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Plain rover",
            workflowUsesNtrip = false,
            passwordLookup = { null },
            modeCommands = listOf("FALLBACK"),
        )

        assertEquals(listOf("MODE ROVER", "BESTNAVB COM1 0.1"), config.modeCommands)
    }

    @Test
    fun `ntrip workflow requires host and mountpoint before start`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip(),
            commandProfile = CommandProfile("commands", "Commands"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = ""),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = ""),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        assertThrows(IllegalArgumentException::class.java, config::validateForStart)
    }

    @Test
    fun `configured ntrip workflow passes start validation`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip(),
            commandProfile = CommandProfile("commands", "Commands"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        config.validateForStart()
    }

    @Test
    fun `rover workflow rejects base mode command profile before start`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "rover-ntrip"),
            commandProfile = CommandProfile(
                id = "commands",
                name = "Commands",
                runtimeScript = "MODE BASE TIME 120 2.5\nGNGGA 1",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertEquals("Rover workflow cannot start with a command profile that sets MODE BASE.", error.message)
    }

    @Test
    fun `rover workflow rejects base mode init command before start`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "rover-ntrip"),
            commandProfile = CommandProfile(
                id = "commands",
                name = "Commands",
                initScript = "UNLOG COM1\nMODE BASE TIME 120 2.5",
                runtimeScript = "GNGGA 1",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertEquals("Rover workflow cannot start with a command profile that sets MODE BASE.", error.message)
    }

    @Test
    fun `um980 workflow rejects unsupported periodic output frequency before start`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "rover-ntrip"),
            commandProfile = CommandProfile(
                id = "commands",
                name = "Commands",
                receiverFamily = "um980",
                runtimeScript = """
                    MODE ROVER SURVEY
                    BESTNAVB COM1 0.25
                    OBSVMCMPB COM1 0.2
                    GPSEPHB COM1 300
                    RTCMSTATUSB COM1 ONCHANGED
                """.trimIndent(),
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertEquals(
            "Unsupported UM980 output frequency in `BESTNAVB COM1 0.25`: use 1, 2, 5, 10, 20, or 50 Hz.",
            error.message,
        )
    }

    @Test
    fun `um980 workflow accepts supported output frequencies and non-periodic outputs before start`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "rover-ntrip"),
            commandProfile = CommandProfile(
                id = "commands",
                name = "Commands",
                receiverFamily = "um980",
                runtimeScript = """
                    MODE ROVER SURVEY
                    BESTNAVB COM1 0.05
                    OBSVMCMPB COM1 0.2
                    ADRNAVB COM1 1
                    GNGGA 0.2
                    GPSEPHB COM1 300
                    GALIONB ONCHANGED
                """.trimIndent(),
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )

        config.validateForStart()
    }

    @Test
    fun `fixed base workflow rejects rover mode command profile before start`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "fixed-base"),
            commandProfile = CommandProfile(
                id = "commands",
                name = "Commands",
                runtimeScript = "MODE ROVER SURVEY\nBESTNAVB COM1 1",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Fixed base",
            workflowUsesNtrip = false,
            passwordLookup = { null },
        )

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertEquals("Fixed base workflow cannot start with a command profile that sets MODE ROVER.", error.message)
    }

    @Test
    fun `fixed base mode commands are not mutated by start config`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "fixed-base"),
            commandProfile = CommandProfile(
                id = "fixed",
                name = "Fixed",
                runtimeScript = "UNLOG COM1\nMODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
            ),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            ntripCasterUploadProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Fixed base",
            workflowUsesNtrip = false,
            hasAcceptedBaseCoordinate = true,
            passwordLookup = { null },
        )

        assertEquals(
            listOf(
                "UNLOG COM1",
                "MODE BASE 49.4637593130 15.4512544790 707.8000",
                "GNGGA 1",
            ),
            config.modeCommands,
        )
    }
}
