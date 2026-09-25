package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.core.correction.NtripTransportMode

class NtripSecurityDisclosureTest {
    @Test
    fun `plain tag is metadata only for selected plaintext`() {
        assertEquals("\u26A0 PLAINTEXT", NtripTransportMode.PLAINTEXT.plaintextTag())
        assertEquals(null, NtripTransportMode.TLS.plaintextTag())
        assertEquals(null, (null as NtripTransportMode?).plaintextTag())
    }
}
