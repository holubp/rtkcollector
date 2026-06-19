package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.workflow.SessionArtifact

class ActiveRecordingConfigTest {
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
                rtklibProfileRef = ProfileReference("rtklib-rover", "RTKLIB rover"),
                receiverProfileId = "um980-n4",
            ),
            commandProfile = CommandProfile("commands", "Commands", runtimeScript = "MODE ROVER\nOBSVMB COM1 0.25"),
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
    fun `enabled rtklib rejects UM980 compact OBSVMCMPB without converter`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                rtklibProfileRef = ProfileReference("rtklib-rover", "RTKLIB rover"),
                receiverProfileId = "um980-n4",
            ),
            commandProfile = CommandProfile("commands", "Commands", runtimeScript = "MODE ROVER\nOBSVMCMPB COM1 0.25"),
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

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertTrue(error.message.orEmpty().contains("OBSVMCMPB requires a converter"))
        assertTrue(config.rtklib.routePlan.orEmpty().contains("unsupported(UNICORE_OBSVMCMPB)"))
    }

    @Test
    fun `enabled rtklib profile requires ntrip corrections for this mvp`() {
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

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertTrue(error.message.orEmpty().contains("RTKLIB real-time MVP requires NTRIP RTCM3 corrections."))
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
}
