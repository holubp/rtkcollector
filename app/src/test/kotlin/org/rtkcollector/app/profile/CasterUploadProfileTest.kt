package org.rtkcollector.app.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.json.JSONObject
import org.rtkcollector.core.correction.NtripTransportMode
import org.rtkcollector.core.correction.NtripTlsVerification

class CasterUploadProfileTest {
    @Test
    fun `new upload profiles serialize safe TLS selection`() {
        val profile = NtripCasterUploadProfile(id = "upload", name = "Upload")
        val json = profile.toJson()
        assertEquals("TLS", json.getString("transportMode"))
        assertEquals("SYSTEM_TRUST", json.getString("tlsVerification"))
        assertFalse(json.getBoolean("unsafeTlsAcknowledged"))
    }

    @Test
    fun `unmarked legacy upload JSON is blocked until explicit transport selection`() {
        val profile = NtripCasterUploadProfile.fromJson(JSONObject().put("id", "upload").put("name", "Upload"))
        assertEquals("PLAINTEXT", profile.toJson().getString("transportMode"))
        assertTrue(profile.requiresTlsVerificationChoice)
        assertFailsWith<IllegalArgumentException> { profile.toCore(allowInsecure = false) }
    }

    @Test
    fun `explicit stored plaintext upload remains usable`() {
        val profile = NtripCasterUploadProfile.fromJson(JSONObject()
            .put("id", "plain")
            .put("name", "Plain upload")
            .put("host", "caster.example")
            .put("transportMode", "PLAINTEXT"))
        assertFalse(profile.requiresTlsVerificationChoice)
        assertEquals(NtripTransportMode.PLAINTEXT, profile.toCore(allowInsecure = false).transport)
    }

    @Test
    fun `upload security selection round trips and validates in core`() {
        val profile = NtripCasterUploadProfile(
            id = "upload", name = "Upload", host = "caster.example",
            transportMode = NtripTransportMode.TLS,
            tlsVerification = NtripTlsVerification.Unsafe,
            unsafeTlsAcknowledged = true,
        )
        val restored = NtripCasterUploadProfile.fromJson(profile.toJson())
        assertEquals(profile.copy(unsafeTlsAcknowledged = false, needsSecurityPersistenceMigration = true, requiresTlsVerificationChoice = true), restored)
        assertFailsWith<IllegalArgumentException> { restored.toCore(allowInsecure = false) }
        assertFailsWith<IllegalArgumentException> { restored.toCore(allowInsecure = true) }
        assertFailsWith<IllegalArgumentException> {
            restored.copy(unsafeTlsAcknowledged = false).toCore(allowInsecure = true)
        }
    }

    @Test
    fun `legacy custom CA upload is disabled and endpoint derivation clears acknowledgement`() {
        val profile = NtripCasterUploadProfile.fromJson(
            JSONObject()
                .put("id", "upload")
                .put("name", "Upload")
                .put("host", "private.example")
                .put("tlsVerification", "CUSTOM_CA")
                .put("customCaCertificateDer", "private-certificate-bytes")
                .put("unsafeTlsAcknowledged", true),
        )

        assertEquals(NtripTlsVerification.Unsafe, profile.tlsVerification)
        assertFalse(profile.unsafeTlsAcknowledged)
        assertFailsWith<IllegalArgumentException> { profile.toCore(allowInsecure = true) }
        assertEquals("UNSAFE", profile.toJson().getString("tlsVerification"))
        assertFalse(profile.toJson().has("customCaCertificateDer"))
    }

    @Test
    fun `caster upload profile defaults to adaptive retry and disabled manual safety`() {
        val profile = NtripCasterUploadProfile(id = "upload", name = "Upload")

        assertEquals(NtripCasterUploadRetryMode.ADAPTIVE, profile.retryMode)
        assertEquals(10, profile.fixedReconnectDelaySeconds)
        assertEquals(10, profile.adaptiveInitialDelaySeconds)
        assertEquals(300, profile.adaptiveMaxDelaySeconds)
        assertTrue(profile.stopAfterFailuresEnabled)
        assertEquals(5, profile.stopAfterConsecutiveFailures)
        assertFalse(profile.safetyRulesEnabled)
        assertEquals(35, profile.safetyMaxBitrateKbps)
        assertEquals(60, profile.safetyBitrateWindowSeconds)
        assertEquals(500, profile.safetyMaxSessionUploadMb)
        assertFalse(profile.effectiveSafetyRulesEnabled)
    }

    @Test
    fun `caster upload profile migrates missing retry and safety fields`() {
        val profile = NtripCasterUploadProfile.fromJson(
            JSONObject()
                .put("id", "upload")
                .put("name", "Upload")
                .put("host", "rtk2go.com")
                .put("port", 2101)
                .put("mountpoint", "TEST"),
        )

        assertEquals(NtripCasterUploadRetryMode.ADAPTIVE, profile.retryMode)
        assertEquals(10, profile.fixedReconnectDelaySeconds)
        assertEquals(10, profile.adaptiveInitialDelaySeconds)
        assertEquals(300, profile.adaptiveMaxDelaySeconds)
        assertTrue(profile.stopAfterFailuresEnabled)
        assertEquals(5, profile.stopAfterConsecutiveFailures)
        assertFalse(profile.safetyRulesEnabled)
        assertEquals(35, profile.safetyMaxBitrateKbps)
        assertEquals(60, profile.safetyBitrateWindowSeconds)
        assertEquals(500, profile.safetyMaxSessionUploadMb)
        assertTrue(profile.effectiveSafetyRulesEnabled)
        assertTrue(profile.isRtk2goHost)
    }

    @Test
    fun `unknown retry mode is treated as adaptive`() {
        val profile = NtripCasterUploadProfile.fromJson(
            JSONObject()
                .put("id", "upload")
                .put("name", "Upload")
                .put("retryMode", "BOGUS"),
        )

        assertEquals(NtripCasterUploadRetryMode.ADAPTIVE, profile.retryMode)
    }

    @Test
    fun `profile validates editable drafts but requires host and mountpoint for start`() {
        val draft = NtripCasterUploadProfile(id = "upload", name = "Upload")

        draft.validate()
        assertFailsWith<IllegalArgumentException> { draft.validateForStart() }
    }

    @Test
    fun `profile json round trips`() {
        val profile = NtripCasterUploadProfile(
            id = "upload",
            name = "Upload",
            host = "caster.example.org",
            port = 2101,
            mountpoint = "BASE",
            username = "user",
            secretId = "secret",
            protocolPolicy = "NTRIP_V2_ONLY",
            enabledByDefault = true,
            retryMode = NtripCasterUploadRetryMode.FIXED,
            fixedReconnectDelaySeconds = 15,
            adaptiveInitialDelaySeconds = 12,
            adaptiveMaxDelaySeconds = 360,
            stopAfterFailuresEnabled = false,
            stopAfterConsecutiveFailures = 11,
            safetyRulesEnabled = true,
            safetyMaxBitrateKbps = 99,
            safetyBitrateWindowSeconds = 120,
            safetyMaxSessionUploadMb = 600,
        )

        val parsed = NtripCasterUploadProfile.fromJson(profile.toJson())

        assertEquals(profile, parsed)
    }

    @Test
    fun `copy assigns upload-specific secret id`() {
        val copy = NtripCasterUploadProfile(
            id = "source",
            name = "Source",
            secretId = "old-secret",
            tlsVerification = NtripTlsVerification.Unsafe,
            unsafeTlsAcknowledged = true,
        ).copyProfile(id = "copy", name = "Copy")

        assertEquals("copy", copy.id)
        assertEquals("Copy", copy.name)
        assertEquals("ntrip-caster-upload-profile:copy", copy.secretId)
        assertFalse(copy.unsafeTlsAcknowledged)
    }

    @Test
    fun `fixed reconnect delay below ten seconds is invalid`() {
        val error = assertFailsWith<IllegalArgumentException> {
            NtripCasterUploadProfile(
                id = "upload",
                name = "Upload",
                retryMode = NtripCasterUploadRetryMode.FIXED,
                fixedReconnectDelaySeconds = 9,
            ).validate()
        }

        assertTrue(error.message!!.contains("Fixed reconnect delay must be at least 10 seconds."))
    }

    @Test
    fun `adaptive reconnect delay and safety thresholds must be valid`() {
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                NtripCasterUploadProfile(
                    id = "upload",
                    name = "Upload",
                    adaptiveInitialDelaySeconds = 9,
                ).validate()
            }.message!!.contains("Adaptive initial reconnect delay must be at least 10 seconds."),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                NtripCasterUploadProfile(
                    id = "upload",
                    name = "Upload",
                    adaptiveInitialDelaySeconds = 20,
                    adaptiveMaxDelaySeconds = 19,
                ).validate()
            }.message!!.contains("Adaptive maximum reconnect delay must be greater than or equal to the initial delay."),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                NtripCasterUploadProfile(
                    id = "upload",
                    name = "Upload",
                    stopAfterConsecutiveFailures = 0,
                ).validate()
            }.message!!.contains("Stop-after-failures count must be at least 1."),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                NtripCasterUploadProfile(
                    id = "upload",
                    name = "Upload",
                    safetyMaxBitrateKbps = 0,
                ).validate()
            }.message!!.contains("Safety bitrate threshold must be positive."),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                NtripCasterUploadProfile(
                    id = "upload",
                    name = "Upload",
                    safetyBitrateWindowSeconds = 0,
                ).validate()
            }.message!!.contains("Safety bitrate window must be positive."),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                NtripCasterUploadProfile(
                    id = "upload",
                    name = "Upload",
                    safetyMaxSessionUploadMb = 0,
                ).validate()
            }.message!!.contains("Safety session upload limit must be positive."),
        )
    }

    @Test
    fun `empty upload password is preserved when explicitly exported`() {
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(),
            usbBaudProfiles = emptyList(),
            ntripCasterProfiles = emptyList(),
            ntripCasterUploadProfiles = listOf(
                NtripCasterUploadProfile(id = "upload", name = "Upload", secretId = "upload-secret"),
            ),
            ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(),
            settingsSets = emptyList(),
            selectedSettingsSetId = null,
            selectedWorkflowId = null,
            lastActiveNtripMountpointProfileId = null,
            passwordsBySecretId = mapOf("upload-secret" to ""),
            options = SettingsSetExportOptions(includePlaintextPasswords = true),
        )

        val parsed = SettingsBackupFile.fromJson(backup.toJson())

        assertEquals("", parsed.plaintextPasswordsBySecretId["upload-secret"])
    }

    @Test
    fun `invalid protocol policy is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            NtripCasterUploadProfile(
                id = "upload",
                name = "Upload",
                protocolPolicy = "NTRIP_V3_ONLY",
            ).validate()
        }
    }
}
