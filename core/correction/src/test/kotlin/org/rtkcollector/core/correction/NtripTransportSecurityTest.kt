package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NtripTransportSecurityTest {
    @Test
    fun `system trust TLS is valid without insecure capability`() {
        val policy = policy("caster.example", NtripTransportMode.TLS, NtripTlsVerification.SystemTrust)
        assertEquals("caster.example", policy.endpoint.host)
        assertEquals("caster.example:2101", policy.endpoint.hostHeader)
        assertEquals("caster.example", policy.endpoint.sniName)
    }

    @Test
    fun `plaintext needs sideload capability and system trust marker`() {
        assertThrows(IllegalArgumentException::class.java) {
            policy("caster.example", NtripTransportMode.PLAINTEXT, NtripTlsVerification.SystemTrust)
        }
        assertEquals(NtripTransportMode.PLAINTEXT,
            policy("caster.example", NtripTransportMode.PLAINTEXT, NtripTlsVerification.SystemTrust,
                allowInsecure = true).transport)
        assertThrows(IllegalArgumentException::class.java) {
            policy("caster.example", NtripTransportMode.PLAINTEXT, NtripTlsVerification.Unsafe,
                allowInsecure = true, unsafeAcknowledged = true)
        }
    }

    @Test
    fun `unsafe TLS needs sideload capability and local acknowledgement`() {
        assertThrows(IllegalArgumentException::class.java) {
            policy("caster.example", NtripTransportMode.TLS, NtripTlsVerification.Unsafe,
                unsafeAcknowledged = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy("caster.example", NtripTransportMode.TLS, NtripTlsVerification.Unsafe,
                allowInsecure = true)
        }
        assertEquals(NtripTlsVerification.Unsafe,
            policy("caster.example", NtripTransportMode.TLS, NtripTlsVerification.Unsafe,
                allowInsecure = true, unsafeAcknowledged = true).verification)
    }

    @Test
    fun `DNS is normalized with IDNA and default port is included in Host`() {
        val endpoint = NtripEndpoint.parse("BÜCHER.example", 443)
        assertEquals("xn--bcher-kva.example", endpoint.host)
        assertEquals("xn--bcher-kva.example:443", endpoint.hostHeader)
    }

    @Test
    fun `IP literals are canonical and omit SNI`() {
        val ipv4 = NtripEndpoint.parse("192.0.2.1", 2101)
        assertEquals("192.0.2.1:2101", ipv4.hostHeader)
        assertNull(ipv4.sniName)
        val ipv6 = NtripEndpoint.parse("[2001:db8::1]", 2101)
        assertEquals("[2001:db8:0:0:0:0:0:1]:2101", ipv6.hostHeader)
        assertNull(ipv6.sniName)
    }

    @Test
    fun `host syntax rejects URLs ports controls and ambiguous IP`() {
        listOf("", "a b", "a\nb", "user@host", "https://host", "host/path", "host?x", "host#x",
            "host:2101", "2001:db8::1", "[2001:db8::1]:2101", "127.1", "192.168.001.1",
            "999.1.1.1", "[fe80::1%eth0]", "bad..host", "xn--", "xn--invalid-.example").forEach { host ->
            assertThrows(IllegalArgumentException::class.java, {
                NtripEndpoint.parse(host, 2101)
            }, "host=$host")
        }
    }

    @Test
    fun `custom CA verification type is absent`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("org.rtkcollector.core.correction.NtripTlsVerification\$CustomCa")
        }
    }

    @Test
    fun `connector exposes no unqualified host and port connection`() {
        assertEquals(listOf(NtripEndpointSecurityPolicy::class.java),
            NtripSocketConnector::class.java.methods.filter { it.name == "connect" }
                .map { it.parameterTypes.toList() }.single())
    }

    @Test
    fun `all three requests retain validated policy and use canonical Host`() {
        val endpoint = NtripEndpoint.parse("BÜCHER.example", 2101)
        val policy = NtripEndpointSecurityPolicy(endpoint, NtripTransportMode.TLS,
            NtripTlsVerification.SystemTrust, allowInsecure = false, unsafeAcknowledged = false)
        assertEquals("Host: xn--bcher-kva.example:2101", NtripRequest(
            policy = policy, mountpoint = "BASE").render().lineSequence().first { it.startsWith("Host:") })
        assertEquals("Host: xn--bcher-kva.example:2101", NtripSourcetableRequest(
            policy = policy).render().lineSequence().first { it.startsWith("Host:") })
        assertEquals("Host: xn--bcher-kva.example:2101", NtripCasterUploadRequest(
            policy = policy, mountpoint = "BASE", credentials = null).render().lineSequence().first { it.startsWith("Host:") })
        assertEquals(policy, NtripRequest(policy = policy, mountpoint = "BASE")
            .withProtocolVersion(NtripProtocolVersion.NTRIP_V1).policy)
    }

    private fun policy(host: String, transport: NtripTransportMode, verification: NtripTlsVerification,
        allowInsecure: Boolean = false, unsafeAcknowledged: Boolean = false): NtripEndpointSecurityPolicy =
        NtripEndpointSecurityPolicy(NtripEndpoint.parse(host, 2101), transport, verification,
            allowInsecure, unsafeAcknowledged)
}
