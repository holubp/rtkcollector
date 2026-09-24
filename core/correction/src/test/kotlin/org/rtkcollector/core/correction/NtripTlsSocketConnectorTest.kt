package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.SNIHostName

class NtripTlsSocketConnectorTest {
    private val trustedConnector get() = JavaNtripSocketConnector(NtripTlsFixture.trustedFactory())

    @Test
    fun `failed raw TCP setup closes socket for either transport`() {
        for (transport in NtripTransportMode.entries) {
            for (failOnTimeout in listOf(false, true)) {
                var closed = false
                val rawSocket = object : Socket() {
                    override fun connect(endpoint: SocketAddress?, timeout: Int) {
                        if (!failOnTimeout) throw SocketException("connect failed")
                    }

                    override fun setSoTimeout(timeout: Int) {
                        if (failOnTimeout) throw SocketException("timeout setup failed")
                    }

                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
                val connector = JavaNtripSocketConnector(NtripTlsFixture.trustedFactory()) { rawSocket }
                val policy = if (transport == NtripTransportMode.TLS) {
                    NtripEndpointSecurityPolicy.systemTrust("127.0.0.1", 2101)
                } else {
                    NtripEndpointSecurityPolicy(
                        NtripEndpoint.parse("127.0.0.1", 2101), transport,
                        NtripTlsVerification.SystemTrust, allowInsecure = true, unsafeAcknowledged = false,
                    )
                }
                assertThrows(SocketException::class.java) { connector.connect(policy) }
                assertTrue(closed, "$transport failOnTimeout=$failOnTimeout")
            }
        }
    }

    @Test
    fun `TLS handshake fails before an NTRIP request is written to a plaintext peer`() {
        ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).use { server ->
            val received = CompletableFuture<ByteArray>()
            val peer = Thread {
                server.accept().use { socket ->
                    socket.soTimeout = 2_000
                    received.complete(socket.getInputStream().readNBytes(64))
                }
            }.apply { start() }

            assertThrows(Exception::class.java) {
                JavaNtripSocketConnector().connect(
                    NtripEndpointSecurityPolicy.systemTrust("127.0.0.1", server.localPort),
                )
            }

            val bytes = received.get(3, TimeUnit.SECONDS).toString(Charsets.US_ASCII)
            assertFalse(bytes.contains("GET /"))
            assertFalse(bytes.contains("POST /"))
            assertFalse(bytes.contains("SOURCE "))
            peer.join(2_000)
        }
    }

    @Test
    fun `trusted DNS and IP SANs negotiate TLS 1_2 or newer`() {
        for (host in listOf("localhost", "127.0.0.1")) {
            NtripTlsFixture.Server(bindHost = host) { peer ->
                peer.startHandshake()
                assertTrue(peer.session.protocol in setOf("TLSv1.2", "TLSv1.3"))
                val names = (peer.session as ExtendedSSLSession).requestedServerNames
                if (host == "localhost") {
                    assertEquals("localhost", (names.single() as SNIHostName).asciiName)
                } else {
                    assertTrue(names.isEmpty())
                }
            }.use { server ->
                trustedConnector.connect(NtripEndpointSecurityPolicy.systemTrust(host, server.port)).use { }
                server.await()
            }
        }
    }

    @Test
    fun `trusted certificate with wrong IP SAN is rejected`() {
        NtripTlsFixture.Server(bindHost = "127.0.0.2") { peer -> runCatching { peer.startHandshake() } }.use { server ->
            assertThrows(Exception::class.java) {
                trustedConnector.connect(NtripEndpointSecurityPolicy.systemTrust("127.0.0.2", server.port))
            }
            server.await()
        }
    }

    @Test
    fun `unsafe TLS requires local consent but still handshakes before returning`() {
        NtripTlsFixture.Server(bindHost = "127.0.0.2") { peer -> peer.startHandshake() }.use { server ->
            val endpoint = NtripEndpoint.parse("127.0.0.2", server.port)
            assertThrows(IllegalArgumentException::class.java) {
                NtripEndpointSecurityPolicy(
                    endpoint, NtripTransportMode.TLS, NtripTlsVerification.Unsafe,
                    allowInsecure = true, unsafeAcknowledged = false,
                )
            }
            val authorized = NtripEndpointSecurityPolicy(
                endpoint, NtripTransportMode.TLS, NtripTlsVerification.Unsafe,
                allowInsecure = true, unsafeAcknowledged = true,
            )
            JavaNtripSocketConnector().connect(authorized).use { }
            server.await()
        }
    }

    @Test
    fun `TLS 1_1 only server is rejected`() {
        NtripTlsFixture.Server { peer ->
            peer.enabledProtocols = arrayOf("TLSv1.1")
            runCatching { peer.startHandshake() }
        }.use { server ->
            assertThrows(Exception::class.java) {
                trustedConnector.connect(NtripEndpointSecurityPolicy.systemTrust("localhost", server.port))
            }
            server.await()
        }
    }

    @Test
    fun `failed TLS handshake never sends NTRIP bytes or falls back to plaintext`() {
        ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 2_000
            val received = CompletableFuture<ByteArray>()
            val peer = Thread {
                server.accept().use { socket ->
                    socket.soTimeout = 2_000
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    try {
                        while (true) {
                            val count = socket.getInputStream().read(buffer)
                            if (count < 0) break
                            bytes.write(buffer, 0, count)
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // End the stalled handshake after collecting all bytes sent by the client.
                    }
                    received.complete(bytes.toByteArray())
                }
            }.apply { isDaemon = true; start() }
            val policy = NtripEndpointSecurityPolicy.systemTrust("127.0.0.1", server.localPort)
            val result = NtripClient(
                NtripRequest(policy, "MOUNT", NtripCredentials("user", "secret")),
                trustedConnector,
                NtripReconnectPolicy(maxAttempts = 1),
            ).connectOnce()
            assertEquals(NtripFailureKind.CONNECT_FAILED, (result as NtripConnectionResult.Failure).failure.kind)
            val bytes = received.get(3, TimeUnit.SECONDS)
            assertTrue(bytes.size > 128, "the complete TLS greeting must exceed the old 128-byte prefix")
            assertEquals(0x16, bytes[0].toInt() and 0xff)
            assertEquals(0x03, bytes[1].toInt() and 0xff)
            val text = bytes.toString(Charsets.ISO_8859_1)
            assertFalse(text.contains("GET /"))
            assertFalse(text.contains("Authorization:"))
            assertFalse(text.contains("user:secret"))
            assertFalse(text.contains("dXNlcjpzZWNyZXQ="))
            assertFalse(text.contains("\$GPGGA"))
            assertThrows(java.net.SocketTimeoutException::class.java) { server.accept().close() }
            peer.join(2_000)
        }
    }

    @Test
    fun `TLS handshake failure exposes no exception secrets in correction status`() {
        val secret = "handshake-password-marker"
        val token = java.util.Base64.getEncoder()
            .encodeToString("user:$secret".toByteArray(Charsets.UTF_8))
        val requestBytes = "GET /PRIVATE HTTP/1.1"
        val certificate = java.util.Base64.getEncoder().encodeToString(
            javaClass.getResourceAsStream("/tls/localhost.crt")!!.use { it.readBytes() },
        ).take(48)
        val keyStore = java.security.KeyStore.getInstance("PKCS12").apply {
            NtripTlsSocketConnectorTest::class.java.getResourceAsStream("/tls/localhost.p12")!!.use {
                load(it, "test-only".toCharArray())
            }
        }
        val privateKey = java.util.Base64.getEncoder().encodeToString(
            keyStore.getKey(keyStore.aliases().nextElement(), "test-only".toCharArray()).encoded,
        ).take(48)
        val exception = javax.net.ssl.SSLHandshakeException(
            listOf(secret, token, requestBytes, certificate, privateKey).joinToString(" "),
        )
        val connector = object : NtripSocketConnector {
            override fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket = throw exception
        }
        val statuses = mutableListOf<CorrectionStatus>()
        val result = NtripClient(
            NtripRequest(NtripEndpointSecurityPolicy.systemTrust("localhost", 2101), "PRIVATE",
                NtripCredentials("user", secret)),
            connector,
        ).connectOnce(onState = statuses::add)
        val failure = (result as NtripConnectionResult.Failure).failure
        val exposed = statuses.mapNotNull(CorrectionStatus::lastError).joinToString() + failure.message + failure.cause
        listOf(secret, token, requestBytes, certificate, privateKey).forEach { assertFalse(exposed.contains(it)) }
    }

    @Test
    fun `v2 response fallback retains identical TLS policy on both connections`() {
        val frames = mutableListOf<String>()
        NtripTlsFixture.Server(2) { peer ->
            val frame = readHeader(peer)
            synchronized(frames) { frames += frame }
            val response = if (frame.startsWith("GET /MOUNT HTTP/1.1")) {
                "HTTP/1.1 505 HTTP Version Not Supported\r\n\r\n"
            } else {
                "ICY 200 OK\r\n\r\n"
            }
            peer.outputStream.write(response.toByteArray(Charsets.US_ASCII))
            peer.outputStream.flush()
        }.use { server ->
            val policy = NtripEndpointSecurityPolicy.systemTrust("localhost", server.port)
            val seen = mutableListOf<NtripEndpointSecurityPolicy>()
            val delegate = trustedConnector
            val recording = object : NtripSocketConnector {
                override fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket {
                    seen += policy
                    return delegate.connect(policy)
                }
            }
            val result = NtripClient(NtripRequest(policy, "MOUNT"), recording).connectOnce()
            assertInstanceOf(NtripConnectionResult.Completed::class.java, result)
            server.await()
            assertEquals(2, seen.size)
            seen.forEach { assertSame(policy, it) }
            assertTrue(frames[0].startsWith("GET /MOUNT HTTP/1.1\r\n"))
            assertTrue(frames[1].startsWith("GET /MOUNT HTTP/1.0\r\n"))
        }
    }

    private fun readHeader(peer: SSLSocket): String {
        val bytes = ByteArrayOutputStream()
        var tail = ""
        while (!tail.endsWith("\r\n\r\n")) {
            val next = peer.inputStream.read()
            require(next >= 0) { "NTRIP request ended early" }
            bytes.write(next)
            tail = (tail + next.toChar()).takeLast(4)
        }
        return bytes.toString(Charsets.US_ASCII.name())
    }
}
