package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NtripTransportSecurityTest {
    @Test
    fun `default transport uses system verified TLS`() {
        val request = NtripRequest(host = "caster.example", port = 2101, mountpoint = "BASE")

        assertEquals(NtripTransportMode.TLS, request.transportSecurity.mode)
        assertEquals(NtripTlsVerification.SystemTrust, request.transportSecurity.verification)
    }

    @Test
    fun `custom CA verification rejects empty certificate`() {
        assertThrows(IllegalArgumentException::class.java) {
            NtripTransportSecurity(
                verification = NtripTlsVerification.CustomCa(byteArrayOf()),
            )
        }
    }

    @Test
    fun `plaintext transport requires an explicit selection`() {
        val security = NtripTransportSecurity(mode = NtripTransportMode.PLAINTEXT)

        assertEquals(NtripTransportMode.PLAINTEXT, security.mode)
        assertEquals(NtripTlsVerification.SystemTrust, security.verification)
    }
}
