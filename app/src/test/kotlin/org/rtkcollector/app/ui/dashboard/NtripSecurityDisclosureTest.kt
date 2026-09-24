package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.correction.NtripEndpoint
import org.rtkcollector.core.correction.NtripEndpointSecurityPolicy
import org.rtkcollector.core.correction.NtripTlsVerification
import org.rtkcollector.core.correction.NtripTransportMode

class NtripSecurityDisclosureTest {
    @Test
    fun `active policies disclose distribution and each insecure route`() {
        val unsafe = NtripEndpointSecurityPolicy(NtripEndpoint.parse("caster.example", 2101),
            NtripTransportMode.TLS, NtripTlsVerification.Unsafe, true, true)
        val plain = NtripEndpointSecurityPolicy(NtripEndpoint.parse("upload.example", 2101),
            NtripTransportMode.PLAINTEXT, NtripTlsVerification.SystemTrust, true, false)

        val disclosure = ntripSecurityDisclosure(unsafe, plain, true).orEmpty()

        assertTrue(disclosure.contains("Sideload build"))
        assertTrue(disclosure.contains("Correction download: unsafe TLS"))
        assertTrue(disclosure.contains("Source upload: plaintext (unencrypted)"))
        assertEquals(null, ntripSecurityDisclosure(null, null, true))
    }
}
