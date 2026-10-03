package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.rtkcollector.core.correction.NtripTransportMode
import org.rtkcollector.core.correction.NtripProtocolVersion

class RecordingForegroundServiceTest {
    @Test
    fun `correction protocol choice is explicit and rejects unknown values`() {
        assertEquals(NtripProtocolVersion.NTRIP_V1, correctionProtocolVersion("NTRIP_V1_ONLY"))
        assertEquals(NtripProtocolVersion.NTRIP_V2, correctionProtocolVersion("NTRIP_V2_ONLY"))
        assertEquals(NtripProtocolVersion.NTRIP_V2, correctionProtocolVersion("NTRIP_V2_PREFERRED_WITH_COMPATIBILITY"))
        assertEquals(NtripProtocolVersion.NTRIP_V2, correctionProtocolVersion("AUTO"))
        assertThrows(IllegalArgumentException::class.java) { correctionProtocolVersion("unknown") }
    }
    @Test
    fun `invalid security extras cannot construct a request`() {
        val invalid = listOf(
            Triple(null, "SYSTEM_TRUST", false),
            Triple("BOGUS", "SYSTEM_TRUST", false),
            Triple("TLS", "BOGUS", false),
            Triple("TLS", "SYSTEM_TRUST", null),
            Triple("TLS", "UNSAFE", true),
        )
        for ((mode, verification, acknowledgement) in invalid) {
            var constructed = false
            assertThrows(IllegalArgumentException::class.java) {
                withValidatedServiceNtripPolicy(
                    "caster.example", 2101, mode, verification, acknowledgement,
                    allowInsecure = false,
                ) {
                    constructed = true
                }
            }
            assertFalse(constructed)
        }
    }

    @Test
    fun `sideload permits plaintext but not unacknowledged unsafe TLS`() {
        val mode = withValidatedServiceNtripPolicy(
            "caster.example", 2101, "PLAINTEXT", "SYSTEM_TRUST", false,
            allowInsecure = true,
        ) { it.transport }
        assertEquals(NtripTransportMode.PLAINTEXT, mode)
        assertThrows(IllegalArgumentException::class.java) {
            withValidatedServiceNtripPolicy(
                "caster.example", 2101, "TLS", "UNSAFE", false,
                allowInsecure = true,
            ) { error("request must not be constructed") }
        }
    }
}
