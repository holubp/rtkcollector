package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class NtripClientTest {
    @Test
    fun `request rendering includes ntrip v1 headers and runtime basic auth`() {
        val request = NtripRequest(
            host = "caster.example",
            port = 2101,
            mountpoint = "MOUNT",
            credentials = NtripCredentials(username = "rover", password = "secret"),
            userAgent = "RtkCollectorTest/1",
        )

        val rendered = request.render()

        assertTrue(rendered.startsWith("GET /MOUNT HTTP/1.0\r\n"))
        assertTrue(rendered.contains("Host: caster.example:2101\r\n"))
        assertTrue(rendered.contains("User-Agent: RtkCollectorTest/1\r\n"))
        assertTrue(rendered.contains("Ntrip-Version: Ntrip/1.0\r\n"))
        assertTrue(rendered.contains("Connection: close\r\n"))
        assertTrue(rendered.contains("Authorization: Basic cm92ZXI6c2VjcmV0\r\n"))
        assertTrue(rendered.endsWith("\r\n\r\n"))
    }

    @Test
    fun `redacted metadata does not expose plaintext credentials`() {
        val request = NtripRequest(
            host = "caster.example",
            port = 2101,
            mountpoint = "MOUNT",
            credentials = NtripCredentials(username = "rover", password = "secret-token"),
        )

        val metadata = request.toRedactedMetadata().toString()

        assertTrue(metadata.contains("caster.example"))
        assertTrue(metadata.contains("MOUNT"))
        assertTrue(metadata.contains("rover"))
        assertFalse(metadata.contains("secret-token"))
    }

    @Test
    fun `sourcetable response is rejected as typed failure`() {
        val connector = FakeNtripSocketConnector(
            FakeNtripSocket(
                inputBytes = "SOURCETABLE 200 OK\r\n\r\nSTR;MOUNT;RTCM 3\r\nENDSOURCETABLE\r\n".toByteArray(),
            ),
        )
        val client = NtripClient(request = defaultRequest(), connector = connector)

        val result = client.connectOnce()

        assertInstanceOf(NtripConnectionResult.Failure::class.java, result)
        assertEquals(NtripFailureKind.SOURCETABLE_RESPONSE, (result as NtripConnectionResult.Failure).failure.kind)
    }

    @Test
    fun `non icy and non http 200 response is rejected as typed failure`() {
        val connector = FakeNtripSocketConnector(
            FakeNtripSocket(inputBytes = "HTTP/1.1 401 Unauthorized\r\n\r\n".toByteArray()),
        )
        val client = NtripClient(request = defaultRequest(), connector = connector)

        val result = client.connectOnce()

        assertInstanceOf(NtripConnectionResult.Failure::class.java, result)
        assertEquals(NtripFailureKind.UNSUPPORTED_RESPONSE, (result as NtripConnectionResult.Failure).failure.kind)
    }

    @Test
    fun `accepted response streams rtcm payload bytes to callback`() {
        val connector = FakeNtripSocketConnector(
            FakeNtripSocket(inputBytes = "ICY 200 OK\r\n\r\n".toByteArray() + byteArrayOf(0xD3.toByte(), 0x00, 0x13)),
        )
        val streamed = ByteArrayOutputStream()
        val client = NtripClient(request = defaultRequest(), connector = connector)

        val result = client.connectOnce { chunk -> streamed.write(chunk) }

        assertInstanceOf(NtripConnectionResult.Completed::class.java, result)
        assertArrayEquals(byteArrayOf(0xD3.toByte(), 0x00, 0x13), streamed.toByteArray())
    }

    @Test
    fun `optional gga lines are written to socket output after request`() {
        val socket = FakeNtripSocket(inputBytes = "ICY 200 OK\r\n\r\n".toByteArray())
        val connector = FakeNtripSocketConnector(socket)
        val client = NtripClient(request = defaultRequest(), connector = connector)

        client.connectOnce(ggaLines = listOf("\$GPGGA,1*00", "\$GPGGA,2*00"))

        val output = socket.outputText()
        assertTrue(output.contains("GET /MOUNT HTTP/1.0\r\n"))
        assertTrue(output.contains("\$GPGGA,1*00\r\n"))
        assertTrue(output.contains("\$GPGGA,2*00\r\n"))
    }

    @Test
    fun `reconnect runner exposes retry state transitions with configurable zero delay`() {
        val connector = QueueingNtripSocketConnector(
            FakeNtripSocket(inputBytes = "HTTP/1.1 503 Service Unavailable\r\n\r\n".toByteArray()),
            FakeNtripSocket(inputBytes = "ICY 200 OK\r\n\r\n".toByteArray() + byteArrayOf(0x01, 0x02)),
        )
        val states = mutableListOf<NtripConnectionState>()
        val streamed = ByteArrayOutputStream()
        val client = NtripClient(
            request = defaultRequest(),
            connector = connector,
            reconnectPolicy = NtripReconnectPolicy(maxAttempts = 2, delayMillis = 0),
            delay = { states.add(NtripConnectionState.RECONNECT_WAIT) },
        )

        val result = client.runWithReconnect(
            onState = { states.add(it.state) },
            onRtcmBytes = { streamed.write(it) },
        )

        assertInstanceOf(NtripConnectionResult.Completed::class.java, result)
        assertTrue(states.contains(NtripConnectionState.CONNECTING))
        assertTrue(states.contains(NtripConnectionState.AUTHENTICATING))
        assertTrue(states.contains(NtripConnectionState.RECONNECT_WAIT))
        assertTrue(states.contains(NtripConnectionState.STREAMING))
        assertArrayEquals(byteArrayOf(0x01, 0x02), streamed.toByteArray())
    }

    private fun defaultRequest(): NtripRequest = NtripRequest(
        host = "caster.example",
        port = 2101,
        mountpoint = "MOUNT",
        userAgent = "RtkCollectorTest/1",
    )

    private class FakeNtripSocket(inputBytes: ByteArray) : NtripSocket {
        private val outputBuffer = ByteArrayOutputStream()

        override val input: InputStream = ByteArrayInputStream(inputBytes)
        override val output: OutputStream = outputBuffer

        override fun close() = Unit

        fun outputText(): String = outputBuffer.toString(Charsets.US_ASCII.name())
    }

    private class FakeNtripSocketConnector(private val socket: FakeNtripSocket) : NtripSocketConnector {
        override fun connect(host: String, port: Int): NtripSocket = socket
    }

    private class QueueingNtripSocketConnector(vararg sockets: FakeNtripSocket) : NtripSocketConnector {
        private val sockets = ArrayDeque(sockets.toList())

        override fun connect(host: String, port: Int): NtripSocket = sockets.removeFirst()
    }
}
