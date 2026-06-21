package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NtripClientTest {
    @Test
    fun `default stream request user agent is accepted by strict ntrip casters`() {
        val request = NtripRequest(
            host = "caster.example",
            port = 2101,
            mountpoint = "MOUNT",
        )

        val rendered = request.render()

        assertTrue(rendered.contains("User-Agent: NTRIP RtkCollector/0.1\r\n"))
        assertTrue(rendered.contains("Ntrip-Version: Ntrip/2.0\r\n"))
    }

    @Test
    fun `default sourcetable request user agent is accepted by strict ntrip casters`() {
        val request = NtripSourcetableRequest(
            host = "caster.example",
            port = 2101,
        )

        val rendered = request.render()

        assertTrue(rendered.contains("User-Agent: NTRIP RtkCollector/0.1\r\n"))
        assertTrue(rendered.contains("Ntrip-Version: Ntrip/2.0\r\n"))
    }

    @Test
    fun `request rendering uses ntrip v2 headers by default`() {
        val request = NtripRequest(
            host = "caster.example",
            port = 2101,
            mountpoint = "MOUNT",
            credentials = NtripCredentials(username = "rover", password = "secret"),
            userAgent = "RtkCollectorTest/1",
        )

        val rendered = request.render()

        assertTrue(rendered.startsWith("GET /MOUNT HTTP/1.1\r\n"))
        assertTrue(rendered.contains("Host: caster.example:2101\r\n"))
        assertTrue(rendered.contains("User-Agent: RtkCollectorTest/1\r\n"))
        assertTrue(rendered.contains("Ntrip-Version: Ntrip/2.0\r\n"))
        assertTrue(rendered.contains("Connection: close\r\n"))
        assertTrue(rendered.contains("Authorization: Basic cm92ZXI6c2VjcmV0\r\n"))
        assertTrue(rendered.endsWith("\r\n\r\n"))
    }

    @Test
    fun `request rendering can use ntrip v1 compatibility headers`() {
        val request = NtripRequest(
            host = "caster.example",
            port = 2101,
            mountpoint = "MOUNT",
            credentials = NtripCredentials(username = "rover", password = "secret"),
            userAgent = "RtkCollectorTest/1",
            protocolVersion = NtripProtocolVersion.NTRIP_V1,
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
    fun `sourcetable request renders caster root without mountpoint`() {
        val request = NtripSourcetableRequest(
            host = "caster.example",
            port = 2101,
            credentials = NtripCredentials(username = "rover", password = "secret"),
            userAgent = "RtkCollectorTest/1",
        )

        val rendered = request.render()

        assertTrue(rendered.startsWith("GET / HTTP/1.1\r\n"))
        assertTrue(rendered.contains("Host: caster.example:2101\r\n"))
        assertTrue(rendered.contains("User-Agent: RtkCollectorTest/1\r\n"))
        assertTrue(rendered.contains("Ntrip-Version: Ntrip/2.0\r\n"))
        assertTrue(rendered.contains("Authorization: Basic cm92ZXI6c2VjcmV0\r\n"))
        assertTrue(rendered.endsWith("\r\n\r\n"))
    }

    @Test
    fun `sourcetable parser extracts unique str mountpoints in order`() {
        val raw = """
            SOURCETABLE 200 OK
            CAS;caster.example;2101;Example caster
            STR;MOUNT_A;Example A;RTCM 3.2;1004(1),1005(10);2;GPS;EUREF;CZE;50.0;14.0;0;1;none;B;N;0;
            STR;MOUNT_B;Example B;RTCM 3.3;1077(1);2;GPS+GAL;EUREF;CZE;49.0;15.0;0;1;none;B;N;0;
            STR;MOUNT_A;Duplicate;RTCM 3.2;;;;;;;;
            ENDSOURCETABLE
        """.trimIndent().replace("\n", "\r\n")

        val mountpoints = NtripSourcetableParser.mountpoints(raw)

        assertEquals(listOf("MOUNT_A", "MOUNT_B"), mountpoints)
    }

    @Test
    fun `sourcetable client fetches and parses mountpoints`() {
        val socket = FakeNtripSocket(
            inputBytes = (
                "SOURCETABLE 200 OK\r\n" +
                    "\r\n" +
                    "STR;MOUNT_A;Example A;RTCM 3.2\r\n" +
                    "STR;MOUNT_B;Example B;RTCM 3.3\r\n" +
                    "ENDSOURCETABLE\r\n"
                ).toByteArray(),
        )
        val client = NtripSourcetableClient(
            request = NtripSourcetableRequest(
                host = "caster.example",
                port = 2101,
                userAgent = "RtkCollectorTest/1",
            ),
            connector = FakeNtripSocketConnector(socket),
        )

        val result = client.fetch()

        assertEquals(listOf("MOUNT_A", "MOUNT_B"), result.mountpoints)
        assertTrue(socket.outputText().startsWith("GET / HTTP/1.1\r\n"))
    }

    @Test
    fun `request rejects crlf in rendered host mountpoint and user agent fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            defaultRequest().copy(host = "caster.example\r\nX-Bad: yes")
        }
        assertThrows(IllegalArgumentException::class.java) {
            defaultRequest().copy(mountpoint = "MOUNT\r\nX-Bad: yes")
        }
        assertThrows(IllegalArgumentException::class.java) {
            defaultRequest().copy(userAgent = "RtkCollector\r\nX-Bad: yes")
        }
    }

    @Test
    fun `http 403 response is rejected as authorization failure`() {
        val connector = FakeNtripSocketConnector(
            FakeNtripSocket(inputBytes = "HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray()),
        )
        val client = NtripClient(request = defaultRequest(), connector = connector)

        val result = client.connectOnce()

        assertInstanceOf(NtripConnectionResult.Failure::class.java, result)
        assertEquals(NtripFailureKind.AUTHORIZATION_FAILED, (result as NtripConnectionResult.Failure).failure.kind)
    }

    @Test
    fun `authentication failures do not enter reconnect loop`() {
        val connector = QueueingNtripSocketConnector(
            FakeNtripSocket(inputBytes = "HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray()),
            FakeNtripSocket(inputBytes = "ICY 200 OK\r\n\r\n".toByteArray()),
        )
        val states = mutableListOf<NtripConnectionState>()
        val client = NtripClient(
            request = defaultRequest(),
            connector = connector,
            reconnectPolicy = NtripReconnectPolicy(maxAttempts = 2, delayMillis = 0),
        )

        val result = client.runWithReconnect(onState = { states += it.state })

        assertInstanceOf(NtripConnectionResult.Failure::class.java, result)
        assertEquals(NtripFailureKind.AUTHORIZATION_FAILED, (result as NtripConnectionResult.Failure).failure.kind)
        assertFalse(states.contains(NtripConnectionState.RECONNECT_WAIT))
    }

    @Test
    fun `v2 request can fall back to v1 compatibility on unsupported caster response`() {
        val v2Socket = FakeNtripSocket(inputBytes = "HTTP/1.1 505 HTTP Version Not Supported\r\n\r\n".toByteArray())
        val v1Socket = FakeNtripSocket(inputBytes = "ICY 200 OK\r\n\r\n".toByteArray() + byteArrayOf(0x01, 0x02))
        val connector = QueueingNtripSocketConnector(
            v2Socket,
            v1Socket,
        )
        val streamed = ByteArrayOutputStream()
        val client = NtripClient(
            request = defaultRequest(),
            connector = connector,
            reconnectPolicy = NtripReconnectPolicy(maxAttempts = 1, delayMillis = 0),
        )

        val result = client.runWithReconnect(onRtcmBytes = { streamed.write(it) })

        assertInstanceOf(NtripConnectionResult.Completed::class.java, result)
        assertArrayEquals(byteArrayOf(0x01, 0x02), streamed.toByteArray())
        assertTrue(v2Socket.outputText().startsWith("GET /MOUNT HTTP/1.1\r\n"))
        assertTrue(v1Socket.outputText().startsWith("GET /MOUNT HTTP/1.0\r\n"))
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
    fun `http 401 response is rejected as authentication failure`() {
        val connector = FakeNtripSocketConnector(
            FakeNtripSocket(inputBytes = "HTTP/1.1 401 Unauthorized\r\n\r\n".toByteArray()),
        )
        val client = NtripClient(request = defaultRequest(), connector = connector)

        val result = client.connectOnce()

        assertInstanceOf(NtripConnectionResult.Failure::class.java, result)
        assertEquals(NtripFailureKind.AUTHENTICATION_FAILED, (result as NtripConnectionResult.Failure).failure.kind)
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
    fun `http chunked stream is decoded before rtcm callback`() {
        val connector = FakeNtripSocketConnector(
            FakeNtripSocket(
                inputBytes = (
                    "HTTP/1.1 200 OK\r\n" +
                        "Transfer-Encoding: chunked\r\n" +
                        "\r\n" +
                        "3\r\nabc\r\n" +
                        "4\r\ndefg\r\n" +
                        "0\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII),
            ),
        )
        val streamed = ByteArrayOutputStream()
        val client = NtripClient(request = defaultRequest(), connector = connector)

        val result = client.connectOnce { chunk -> streamed.write(chunk) }

        assertInstanceOf(NtripConnectionResult.Completed::class.java, result)
        assertEquals(7, (result as NtripConnectionResult.Completed).bytesRead)
        assertArrayEquals("abcdefg".toByteArray(Charsets.US_ASCII), streamed.toByteArray())
    }

    @Test
    fun `optional gga lines are written to socket output after request`() {
        val socket = FakeNtripSocket(inputBytes = "ICY 200 OK\r\n\r\n".toByteArray())
        val connector = FakeNtripSocketConnector(socket)
        val client = NtripClient(request = defaultRequest(), connector = connector)

        client.connectOnce(ggaLines = listOf("\$GPGGA,1*00", "\$GPGGA,2*00"))

        val output = socket.outputText()
        assertTrue(output.contains("GET /MOUNT HTTP/1.1\r\n"))
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

    @Test
    fun `unexpected eof after streaming retries instead of stopping corrections`() {
        val connector = QueueingNtripSocketConnector(
            FakeNtripSocket(inputBytes = "ICY 200 OK\r\n\r\n".toByteArray() + byteArrayOf(0x01)),
            FakeNtripSocket(inputBytes = "ICY 200 OK\r\n\r\n".toByteArray() + byteArrayOf(0x02, 0x03)),
        )
        val states = mutableListOf<NtripConnectionState>()
        val streamed = ByteArrayOutputStream()
        val client = NtripClient(
            request = defaultRequest(),
            connector = connector,
            reconnectPolicy = NtripReconnectPolicy(maxAttempts = 2, delayMillis = 0),
        )

        val result = client.runWithReconnect(
            onState = { states.add(it.state) },
            onRtcmBytes = { streamed.write(it) },
        )

        assertInstanceOf(NtripConnectionResult.Completed::class.java, result)
        assertTrue(states.contains(NtripConnectionState.RECONNECT_WAIT))
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), streamed.toByteArray())
    }

    @Test
    fun `cancel closes active socket and stops stream thread`() {
        val socket = BlockingNtripSocket()
        val client = NtripClient(
            request = defaultRequest(),
            connector = FakeNtripSocketConnector(socket),
        )
        val streaming = CountDownLatch(1)
        val thread = Thread {
            client.runWithReconnect(
                onState = {
                    if (it.state == NtripConnectionState.STREAMING) {
                        streaming.countDown()
                    }
                },
            )
        }

        thread.start()
        assertTrue(streaming.await(2, TimeUnit.SECONDS))
        client.cancel()
        thread.join(2_000)

        assertFalse(thread.isAlive)
        assertTrue(socket.closed)
    }

    @Test
    fun `cancel during reconnect delay returns stopped instead of throwing interrupted exception`() {
        val connector = QueueingNtripSocketConnector(
            FakeNtripSocket(inputBytes = "HTTP/1.1 503 Service Unavailable\r\n\r\n".toByteArray()),
            FakeNtripSocket(inputBytes = "ICY 200 OK\r\n\r\n".toByteArray()),
        )
        val states = mutableListOf<NtripConnectionState>()
        val delayStarted = CountDownLatch(1)
        val result = AtomicReference<NtripConnectionResult>()
        val client = NtripClient(
            request = defaultRequest(),
            connector = connector,
            reconnectPolicy = NtripReconnectPolicy(maxAttempts = 2, delayMillis = 5_000),
            delay = {
                delayStarted.countDown()
                Thread.sleep(it)
            },
        )
        val thread = Thread {
            result.set(client.runWithReconnect(onState = { states += it.state }))
        }

        thread.start()
        assertTrue(delayStarted.await(2, TimeUnit.SECONDS))
        client.cancel()
        thread.interrupt()
        thread.join(2_000)

        assertFalse(thread.isAlive)
        assertTrue(states.contains(NtripConnectionState.STOPPED))
        assertInstanceOf(NtripConnectionResult.Failure::class.java, result.get())
        assertEquals(NtripFailureKind.CANCELLED, (result.get() as NtripConnectionResult.Failure).failure.kind)
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

    private class FakeNtripSocketConnector(private val socket: NtripSocket) : NtripSocketConnector {
        override fun connect(host: String, port: Int): NtripSocket = socket
    }

    private class BlockingNtripSocket : NtripSocket {
        private val outputBuffer = ByteArrayOutputStream()
        @Volatile
        var closed: Boolean = false
            private set

        override val input: InputStream = object : InputStream() {
            private val header = "ICY 200 OK\r\n\r\n".toByteArray()
            private var index = 0

            override fun read(): Int {
                if (index < header.size) {
                    return header[index++].toInt() and 0xff
                }
                while (!closed) {
                    Thread.sleep(10)
                }
                return -1
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val first = read()
                if (first == -1) {
                    return -1
                }
                buffer[offset] = first.toByte()
                return 1
            }
        }
        override val output: OutputStream = outputBuffer

        override fun close() {
            closed = true
        }
    }

    private class QueueingNtripSocketConnector(vararg sockets: FakeNtripSocket) : NtripSocketConnector {
        private val sockets = ArrayDeque(sockets.toList())

        override fun connect(host: String, port: Int): NtripSocket = sockets.removeFirst()
    }
}
