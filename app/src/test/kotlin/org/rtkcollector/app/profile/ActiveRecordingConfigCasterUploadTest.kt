package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveRecordingConfigCasterUploadTest {
    @Test
    fun `fixed base resolves enabled caster upload profile`() {
        val config = activeConfig(
            workflowId = "fixed-base",
            hasAcceptedBaseCoordinate = true,
            uploadProfile = NtripCasterUploadProfile(
                id = "upload",
                name = "Upload",
                host = "caster.example.org",
                port = 2101,
                mountpoint = "BASEOUT",
            ),
        )

        assertTrue(config.casterUpload.enabled)
        assertEquals("caster.example.org", config.casterUpload.host)
        assertEquals("BASEOUT", config.casterUpload.mountpoint)
        assertEquals("ntrip-caster-upload-profile:upload", config.casterUpload.secretRef)
        assertEquals("password", config.casterUpload.password)
        assertTrue("BASE_CASTER_UPLOAD_RTCM3" in config.expectedSessionArtifactNames)
        config.validateForStart()
    }

    @Test
    fun `active caster upload config carries retry and safety policy`() {
        val config = activeConfig(
            workflowId = "fixed-base",
            hasAcceptedBaseCoordinate = true,
            uploadProfile = NtripCasterUploadProfile(
                id = "rtk2go",
                name = "RTK2go",
                host = "www.rtk2go.com",
                mountpoint = "BASEOUT",
                retryMode = NtripCasterUploadRetryMode.FIXED,
                fixedReconnectDelaySeconds = 15,
                adaptiveInitialDelaySeconds = 30,
                adaptiveMaxDelaySeconds = 60,
                stopAfterFailuresEnabled = false,
                stopAfterConsecutiveFailures = 9,
                safetyRulesEnabled = false,
                safetyMaxBitrateKbps = 40,
                safetyBitrateWindowSeconds = 70,
                safetyMaxSessionUploadMb = 700,
            ),
        )

        assertEquals(NtripCasterUploadRetryMode.FIXED, config.casterUpload.retryMode)
        assertEquals(15, config.casterUpload.fixedReconnectDelaySeconds)
        assertEquals(30, config.casterUpload.adaptiveInitialDelaySeconds)
        assertEquals(60, config.casterUpload.adaptiveMaxDelaySeconds)
        assertEquals(false, config.casterUpload.stopAfterFailuresEnabled)
        assertEquals(9, config.casterUpload.stopAfterConsecutiveFailures)
        assertEquals(40, config.casterUpload.safetyMaxBitrateKbps)
        assertEquals(70, config.casterUpload.safetyBitrateWindowSeconds)
        assertEquals(700, config.casterUpload.safetyMaxSessionUploadMb)
        assertTrue(config.casterUpload.effectiveSafetyRulesEnabled)
    }

    @Test
    fun `rover workflow rejects enabled caster upload before start`() {
        val config = activeConfig(
            workflowId = "rover-ntrip",
            hasAcceptedBaseCoordinate = true,
        )

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertEquals("NTRIP caster upload is only available for base workflows.", error.message)
    }

    @Test
    fun `upload requires accepted base coordinate`() {
        val config = activeConfig(
            workflowId = "fixed-base",
            hasAcceptedBaseCoordinate = false,
        )

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertEquals("NTRIP caster upload requires an accepted base coordinate.", error.message)
    }

    @Test
    fun `default-enabled upload profile remains disabled when baseCasterUploadEnabled is false`() {
        val config = activeConfig(
            workflowId = "fixed-base",
            hasAcceptedBaseCoordinate = true,
            baseCasterUploadEnabled = false,
            uploadProfile = NtripCasterUploadProfile(
                id = "upload",
                name = "Upload",
                host = "caster.example.org",
                mountpoint = "BASEOUT",
                enabledByDefault = true,
            ),
        )

        assertEquals(false, config.casterUpload.enabled)
        assertEquals(false, config.expectedSessionArtifactNames.contains("BASE_CASTER_UPLOAD_RTCM3"))
        config.validateForStart()
    }

    @Test
    fun `upload rejects command profile without base rtcm output`() {
        val config = activeConfig(
            workflowId = "fixed-base",
            hasAcceptedBaseCoordinate = true,
            runtimeScript = "MODE BASE TIME 120 2.5\nBESTNAVB COM1 1",
        )

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertTrue(error.message!!.contains("NTRIP caster upload requires base RTCM output"))
    }

    @Test
    fun `v1 source upload requires source password before start`() {
        val config = activeConfig(
            workflowId = "fixed-base",
            hasAcceptedBaseCoordinate = true,
            uploadProfile = NtripCasterUploadProfile(
                id = "upload",
                name = "Upload",
                host = "caster.example.org",
                mountpoint = "BASEOUT",
                protocolPolicy = "NTRIP_V1_ONLY",
            ),
            passwordLookup = { "" },
        )

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertEquals("NTRIP v1 source upload requires a source password.", error.message)
    }

    @Test
    fun `v2 source upload rejects malformed mountpoint before start`() {
        val config = activeConfig(
            workflowId = "fixed-base",
            hasAcceptedBaseCoordinate = true,
            uploadProfile = NtripCasterUploadProfile(
                id = "upload",
                name = "Upload",
                host = "caster.example.org",
                mountpoint = "BASE HTTP/1.1",
                protocolPolicy = "NTRIP_V2_ONLY",
            ),
            passwordLookup = { "secret" },
        )

        val error = assertThrows(IllegalArgumentException::class.java, config::validateForStart)

        assertEquals("NTRIP source upload mountpoint must not contain HTTP syntax.", error.message)
    }

    @Test
    fun `disabled upload is ignored for rover workflow`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "rover-ntrip",
                baseCasterUploadEnabled = false,
                ntripCasterUploadProfileRef = null,
            ),
            commandProfile = CommandProfile("commands", "Commands", runtimeScript = "MODE ROVER"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE"),
            ntripCasterUploadProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover + NTRIP",
            workflowUsesNtrip = true,
            hasAcceptedBaseCoordinate = false,
            passwordLookup = { null },
        )

        assertEquals(false, config.casterUpload.enabled)
        config.validateForStart()
    }

    private fun activeConfig(
        workflowId: String,
        hasAcceptedBaseCoordinate: Boolean,
        baseCasterUploadEnabled: Boolean = true,
        uploadProfile: NtripCasterUploadProfile = NtripCasterUploadProfile(
            id = "upload",
            name = "Upload",
            host = "caster.example.org",
            mountpoint = "BASEOUT",
            username = "user",
        ),
        runtimeScript: String = BASE_RTCM_SCRIPT,
        passwordLookup: (String) -> String? = { "password" },
    ): ActiveRecordingConfig =
        ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = workflowId,
                ntripCasterProfileRef = null,
                ntripMountpointProfileRef = null,
                ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
                baseCasterUploadEnabled = baseCasterUploadEnabled,
            ),
            commandProfile = CommandProfile("commands", "Commands", runtimeScript = runtimeScript),
            usbBaudProfile = UsbBaudProfile("baud", "Baud"),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            ntripCasterUploadProfile = uploadProfile,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Fixed base",
            workflowUsesNtrip = false,
            hasAcceptedBaseCoordinate = hasAcceptedBaseCoordinate,
            passwordLookup = passwordLookup,
        )

    private companion object {
        const val BASE_RTCM_SCRIPT = """
            MODE BASE TIME 120 2.5
            RTCM1006 COM1 10
            RTCM1033 COM1 10
            RTCM1074 COM1 1
            RTCM1084 COM1 1
            RTCM1094 COM1 1
            RTCM1124 COM1 1
            RTCM1230 COM1 10
        """
    }
}
