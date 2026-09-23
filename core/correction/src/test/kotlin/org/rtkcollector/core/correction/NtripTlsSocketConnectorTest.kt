package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class NtripTlsSocketConnectorTest {
    @Test
    fun `TLS handshake fails before an NTRIP request is written to a plaintext peer`() {
        ServerSocket(0).use { server ->
            val received = CompletableFuture<ByteArray>()
            val peer = Thread {
                server.accept().use { socket ->
                    socket.soTimeout = 2_000
                    received.complete(socket.getInputStream().readNBytes(64))
                }
            }.apply { start() }

            assertThrows(Exception::class.java) {
                JavaNtripSocketConnector().connect(
                    host = "127.0.0.1",
                    port = server.localPort,
                    security = NtripTransportSecurity(),
                )
            }

            val bytes = received.get(3, TimeUnit.SECONDS).toString(Charsets.US_ASCII)
            assertFalse(bytes.contains("GET /"))
            assertFalse(bytes.contains("POST /"))
            assertFalse(bytes.contains("SOURCE "))
            peer.join(2_000)
        }
    }
}
