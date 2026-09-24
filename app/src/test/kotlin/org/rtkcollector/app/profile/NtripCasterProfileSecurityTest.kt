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
    fun `new caster defaults to TLS and legacy JSON remains plaintext`() {
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
        assertFailsWith<IllegalArgumentException> { legacy.toCore(allowInsecure = false) }
        assertEquals(NtripTransportMode.PLAINTEXT, legacy.toCore(allowInsecure = true).transport)
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
    fun `copy clears unsafe acknowledgement`() {
        val profile = NtripCasterProfile(
            id = "caster", name = "Caster", host = "caster.example",
            tlsVerification = NtripTlsVerification.Unsafe, unsafeTlsAcknowledged = true,
        )
        assertFalse(profile.copyProfile("copy", "Copy").unsafeTlsAcknowledged)
    }

    @Test
    fun `service policy decoding rejects invalid and unacknowledged modes`() {
        assertEquals(NtripTransportMode.TLS,
            ntripSecurityPolicyFromStorage("caster.example", 2101, null, null, false, false).transport)
        assertEquals(NtripTransportMode.PLAINTEXT,
            ntripSecurityPolicyFromStorage("caster.example", 2101, "PLAINTEXT", "SYSTEM_TRUST", false, true).transport)
        assertFailsWith<IllegalArgumentException> {
            ntripSecurityPolicyFromStorage("caster.example", 2101, "PLAINTEXT", "SYSTEM_TRUST", false, false)
        }
        assertFailsWith<IllegalArgumentException> {
            ntripSecurityPolicyFromStorage("caster.example", 2101, "TLS", "UNSAFE", false, true)
        }
        assertFailsWith<IllegalArgumentException> {
            ntripSecurityPolicyFromStorage("caster.example", 2101, "BOGUS", "SYSTEM_TRUST", false, true)
        }
    }
}
