package org.rtkcollector.app.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.json.JSONObject
import org.rtkcollector.core.correction.NtripTlsVerification
import org.rtkcollector.core.correction.NtripTransportMode

class NtripCasterProfileSecurityTest {
    @Test
    fun `new caster defaults to TLS and unmarked legacy plaintext requires selection`() {
        val fresh = NtripCasterProfile(id = "caster", name = "Caster", host = "caster.example")
        assertEquals(NtripTransportMode.TLS, fresh.transportMode)
        assertEquals(NtripTlsVerification.SystemTrust, fresh.tlsVerification)
        assertFalse(fresh.unsafeTlsAcknowledged)
        assertEquals(fresh, NtripCasterProfile.fromJson(fresh.toJson()))

        val legacy = NtripCasterProfile.fromJson(
            JSONObject().put("id", "caster").put("name", "Caster").put("host", "caster.example"),
        )
        assertEquals(NtripTransportMode.PLAINTEXT, legacy.transportMode)
        assertEquals("PLAINTEXT", legacy.toJson().getString("transportMode"))
        assertEquals(true, legacy.requiresTlsVerificationChoice)
        assertFailsWith<IllegalArgumentException> { legacy.toCore(allowInsecure = false) }
        assertFailsWith<IllegalArgumentException> { legacy.toCore(allowInsecure = true) }
        assertEquals(NtripTransportMode.PLAINTEXT,
            NtripCasterProfile.fromJson(legacy.toJson()).copy(requiresTlsVerificationChoice = false).toCore(false).transport)
    }

    @Test
    fun `invalid serialized security values fail closed`() {
        val json = JSONObject().put("id", "caster").put("name", "Caster")
        assertFailsWith<IllegalArgumentException> {
            NtripCasterProfile.fromJson(JSONObject(json.toString()).put("transportMode", "BOGUS"))
        }
        assertFailsWith<IllegalArgumentException> {
            NtripCasterProfile.fromJson(JSONObject(json.toString()).put("tlsVerification", "BOGUS"))
        }
    }

    @Test
    fun `explicit stored plaintext selection remains usable`() {
        val profile = NtripCasterProfile.fromJson(JSONObject()
            .put("id", "plain")
            .put("name", "Plain caster")
            .put("host", "caster.example")
            .put("transportMode", "PLAINTEXT"))
        assertFalse(profile.requiresTlsVerificationChoice)
        assertEquals(NtripTransportMode.PLAINTEXT, profile.toCore(allowInsecure = false).transport)
    }

    @Test
    fun `copy clears unsafe acknowledgement`() {
        val profile = NtripCasterProfile(
            id = "caster", name = "Caster", host = "caster.example",
            tlsVerification = NtripTlsVerification.Unsafe, unsafeTlsAcknowledged = true,
        )
        assertFalse(profile.copyProfile("copy", "Copy").unsafeTlsAcknowledged)
    }

    @Test
    fun `legacy custom CA profile is disabled without retaining certificate bytes`() {
        val profile = NtripCasterProfile.fromJson(
            JSONObject()
                .put("id", "caster")
                .put("name", "Caster")
                .put("host", "private.example")
                .put("port", 2201)
                .put("tlsVerification", "CUSTOM_CA")
                .put("customCaCertificateDer", "private-certificate-bytes")
                .put("unsafeTlsAcknowledged", true),
        )

        assertEquals("private.example", profile.host)
        assertEquals(2201, profile.port)
        assertEquals(NtripTlsVerification.Unsafe, profile.tlsVerification)
        assertFalse(profile.unsafeTlsAcknowledged)
        assertFailsWith<IllegalArgumentException> { profile.toCore(allowInsecure = true) }
        val persisted = profile.toJson()
        assertEquals("UNSAFE", persisted.getString("tlsVerification"))
        assertFalse(persisted.has("customCaCertificateDer"))
        assertFalse(persisted.toString().contains("private-certificate-bytes"))
    }

    @Test
    fun `refresh uses unsaved editor security and rejects unresolved custom CA`() {
        val profile = NtripCasterProfile(id = "caster", name = "Caster", host = "old.example")
        val editor = mapOf("host" to "new.example", "port" to "443", "transportMode" to "TLS",
            "tlsVerification" to "SYSTEM_TRUST", "unsafeTlsAcknowledged" to "false")
        assertEquals("new.example", profile.securityForEditorRefresh(editor, false).endpoint.host)
        assertEquals(443, profile.securityForEditorRefresh(editor, false).endpoint.port)
        assertEquals(NtripTransportMode.TLS, profile.securityForEditorRefresh(editor, false).transport)

        val migrated = profile.copy(tlsVerification = NtripTlsVerification.Unsafe,
            requiresTlsVerificationChoice = true)
        assertFailsWith<IllegalArgumentException> {
            migrated.securityForEditorRefresh(editor, true)
        }
        assertEquals(NtripTlsVerification.SystemTrust,
            migrated.securityForEditorRefresh(editor + ("requiresTlsVerificationChoice" to "false"), true).verification)
    }

    @Test
    fun `active configs retain migrated verification guard`() {
        assertFailsWith<IllegalArgumentException> {
            ActiveNtripConfig(true, "caster.example", 2101, "MOUNT", "", null, null, null, null, null,
                requiresTlsVerificationChoice = true).toCore(true)
        }
    }

    @Test
    fun `service policy decoding rejects invalid and unacknowledged modes`() {
        assertEquals(NtripTransportMode.TLS,
            ntripSecurityPolicyFromStorage("caster.example", 2101, null, null, false, false).transport)
        assertEquals(NtripTransportMode.PLAINTEXT,
            ntripSecurityPolicyFromStorage("caster.example", 2101, "PLAINTEXT", "SYSTEM_TRUST", false, true).transport)
        assertEquals(NtripTransportMode.PLAINTEXT,
            ntripSecurityPolicyFromStorage("caster.example", 2101, "PLAINTEXT", "SYSTEM_TRUST", false, false).transport)
        assertFailsWith<IllegalArgumentException> {
            ntripSecurityPolicyFromStorage("caster.example", 2101, "TLS", "UNSAFE", false, true)
        }
        assertFailsWith<IllegalArgumentException> {
            ntripSecurityPolicyFromStorage("caster.example", 2101, "BOGUS", "SYSTEM_TRUST", false, true)
        }
    }
}
