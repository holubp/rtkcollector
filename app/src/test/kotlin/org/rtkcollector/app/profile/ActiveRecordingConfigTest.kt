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
}
