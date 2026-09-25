package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class NtripCasterUploadClientTest {
    @Test
    fun `v1 request renders classic bkg source upload header`() {
        val request = defaultRequest(
            mountpoint = "UM980BASE",
            credentials = NtripCredentials(username = "ignored", password = "pass123"),
            userAgent = "NTRIP RtkCollector/test",
            protocolVersion = NtripProtocolVersion.NTRIP_V1,
        )

        val rendered = request.render()

        assertEquals(
            "SOURCE pass123 /UM980BASE\r\n" +
                "Source-Agent: NTRIP RtkCollector/test\r\n" +
                "\r\n",
            rendered,
        )
        assertFalse(rendered.contains("GET"))
        assertFalse(rendered.contains("POST"))
        assertFalse(rendered.contains("HTTP/1.0"))
        assertFalse(rendered.contains("HTTP/1.1"))
        assertFalse(rendered.contains("Authorization"))
        assertFalse(rendered.contains("ignored"))
    }

    @Test
    fun `v2 request renders http post source upload headers`() {
        val request = defaultRequest(protocolVersion = NtripProtocolVersion.NTRIP_V2)

        val rendered = request.render()

        assertTrue(rendered.startsWith("POST /BASE HTTP/1.1\r\n"))
        assertTrue(rendered.contains("Host: caster.example:2101\r\n"))
        assertTrue(rendered.contains("User-Agent: RtkCollectorTest/1\r\n"))
        assertTrue(rendered.contains("Ntrip-Version: Ntrip/2.0\r\n"))
        assertTrue(rendered.contains("Connection: close\r\n"))
        assertTrue(rendered.contains("Content-Type: gnss/data\r\n"))
        assertTrue(rendered.contains("Transfer-Encoding: chunked\r\n"))
        assertTrue(rendered.contains("Authorization: Basic dXBsb2FkZXI6cGFzc3dvcmQ=\r\n"))
        assertFalse(rendered.startsWith("SOURCE "))
        assertFalse(rendered.contains("SOURCE password"))
    }

    @Test
    fun `source mountpoint is normalized`() {
        listOf(
            "UM980BASE",
            "/UM980BASE",
            " UM980BASE ",
            "/UM980BASE/",
        ).forEach { mountpoint ->
            val v1Rendered = defaultRequest(
                mountpoint = mountpoint,
                protocolVersion = NtripProtocolVersion.NTRIP_V1,
            ).render()
            val v2Rendered = defaultRequest(
                mountpoint = mountpoint,
                protocolVersion = NtripProtocolVersion.NTRIP_V2,
            ).render()

            assertTrue(v1Rendered.startsWith("SOURCE password /UM980BASE\r\n"), mountpoint)
            assertFalse(v1Rendered.startsWith("SOURCE password /UM980BASE/\r\n"), mountpoint)
            assertTrue(v2Rendered.startsWith("POST /UM980BASE HTTP/1.1\r\n"), mountpoint)
            assertFalse(v2Rendered.startsWith("POST /UM980BASE/ HTTP/1.1\r\n"), mountpoint)
        }
    }

    @Test
    fun `source mountpoint rejects request injection and embedded paths`() {
        listOf(
            "UM980BASE HTTP/1.1",
            "UM980\nBASE",
            "UM980\rBASE",
            "UM980\tBASE",
            "UM980/BASE",
            "/UM980/BASE",
        ).forEach { mountpoint ->
            assertThrows(IllegalArgumentException::class.java) {
                defaultRequest(mountpoint = mountpoint, protocolVersion = NtripProtocolVersion.NTRIP_V1)
            }
            assertThrows(IllegalArgumentException::class.java) {
                defaultRequest(mountpoint = mountpoint, protocolVersion = NtripProtocolVersion.NTRIP_V2)
            }
        }
    }

    @Test
    fun `request rejects crlf in rendered fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            defaultRequest(host = "caster.example\r\nBad: yes")
        }
        assertThrows(IllegalArgumentException::class.java) {
            defaultRequest().copy(mountpoint = "BASE\r\nBad: yes")
        }
        assertThrows(IllegalArgumentException::class.java) {
            defaultRequest().copy(userAgent = "Agent\r\nBad: yes")
        }
        assertThrows(IllegalArgumentException::class.java) {
            defaultRequest().copy(credentials = NtripCredentials("user\r\nBad: yes", "password"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            defaultRequest().copy(credentials = NtripCredentials("user", "password\r\nBad: yes"))
        }
    }

    @Test
    fun `accepted response uploads bytes to socket`() {
        val socket = FakeUploadSocket("ICY 200 OK\r\n\r\n".toByteArray())
        val client = NtripCasterUploadClient(
            defaultRequest(
                mountpoint = "UM980BASE",
                credentials = NtripCredentials(username = "ignored", password = "pass123"),
                protocolVersion = NtripProtocolVersion.NTRIP_V1,
            ),
            FakeUploadConnector(socket),
        )

        val result = client.connectOnce { output -> output.write(byteArrayOf(0xD3.toByte(), 0x00, 0x01)) }

        assertInstanceOf(NtripCasterUploadResult.Completed::class.java, result)
        assertEquals(3, (result as NtripCasterUploadResult.Completed).bytesUploaded)
        assertTrue(socket.outputText().startsWith("SOURCE pass123 /UM980BASE\r\n"))
        assertEquals("SOURCE pass123 /UM980BASE", socket.outputText().lineSequence().first())
        assertArrayEquals(byteArrayOf(0xD3.toByte(), 0x00, 0x01), socket.uploadPayload())
    }

    @Test
    fun `v1 line-only accepted response starts streaming without reading beyond status line`() {
        val socket = FakeUploadSocket(
            LineTerminatedInputStream("ICY 200 OK\r\n".toByteArray()),
        )
        val client = NtripCasterUploadClient(
            defaultRequest(protocolVersion = NtripProtocolVersion.NTRIP_V1),
            FakeUploadConnector(socket),
        )

        val result = assertDoesNotThrow<NtripCasterUploadResult> {
            client.connectOnce { output -> output.write(byteArrayOf(0xD3.toByte(), 0x00, 0x01)) }
        }

        assertInstanceOf(NtripCasterUploadResult.Completed::class.java, result)
        assertArrayEquals(byteArrayOf(0xD3.toByte(), 0x00, 0x01), socket.uploadPayload())
    }

    @Test
    fun `fragmented v2 http accepted response starts streaming after status line`() {
        val socket = FakeUploadSocket(
            LineTerminatedInputStream(
                "HTTP/1.1 200 OK\r\n".toByteArray(),
                fragmentSize = 1,
            ),
        )
        val client = NtripCasterUploadClient(
            defaultRequest(protocolVersion = NtripProtocolVersion.NTRIP_V2),
            FakeUploadConnector(socket),
        )

        val result = assertDoesNotThrow<NtripCasterUploadResult> {
            client.connectOnce { output -> output.write(byteArrayOf(0xD3.toByte(), 0x00, 0x01)) }
        }

        assertInstanceOf(NtripCasterUploadResult.Completed::class.java, result)
    }

    @Test
    fun `line-only fragmented caster response streams raw rtcm over a real socket`() {
        LocalCaster(
            responseFragments = listOf(
                "ICY 200 ".toByteArray(),
                "OK\r\n".toByteArray(),
            ),
        ).use { caster ->
            val client = NtripCasterUploadClient(
                defaultRequest(
                    host = "127.0.0.1",
                    port = caster.port,
                    protocolVersion = NtripProtocolVersion.NTRIP_V1,
                    policy = NtripEndpointSecurityPolicy(NtripEndpoint.parse("127.0.0.1", caster.port),
                        NtripTransportMode.PLAINTEXT, NtripTlsVerification.SystemTrust,
                        allowInsecure = false, unsafeAcknowledged = false),
                ),
            )

            val result = client.connectOnce { output ->
                output.write(byteArrayOf(0xD3.toByte(), 0x00, 0x01))
            }

            assertInstanceOf(NtripCasterUploadResult.Completed::class.java, result)
            assertArrayEquals(
                byteArrayOf(0xD3.toByte(), 0x00, 0x01),
                caster.uploaded.get(2, TimeUnit.SECONDS),
            )
        }
    }

    @Test
    fun `v2 accepted response uploads chunked bytes to socket`() {
        val socket = FakeUploadSocket("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
        val client = NtripCasterUploadClient(
            defaultRequest(
                mountpoint = "UM980BASE",
                credentials = NtripCredentials(username = "base01", password = "pass123"),
                protocolVersion = NtripProtocolVersion.NTRIP_V2,
            ),
            FakeUploadConnector(socket),
        )

        val result = client.connectOnce { output -> output.write(byteArrayOf(0xD3.toByte(), 0x00, 0x01)) }

        assertInstanceOf(NtripCasterUploadResult.Completed::class.java, result)
        assertEquals(3, (result as NtripCasterUploadResult.Completed).bytesUploaded)
        assertEquals("POST /UM980BASE HTTP/1.1", socket.outputText().lineSequence().first())
        assertArrayEquals(
            byteArrayOf(
                '3'.code.toByte(),
                '\r'.code.toByte(),
                '\n'.code.toByte(),
                0xD3.toByte(),
                0x00,
                0x01,
                '\r'.code.toByte(),
                '\n'.code.toByte(),
            ),
            socket.uploadPayload(),
        )
    }

    @Test
    fun `tls selected socket preserves exact v1 source header bytes`() {
        val socket = FakeUploadSocket("ICY 200 OK\r\n\r\n".toByteArray())
        val connector = TlsInjectedUploadConnector(socket)
        val request = defaultRequest(
            credentials = NtripCredentials("retained-user", "pass123"),
            protocolVersion = NtripProtocolVersion.NTRIP_V1,
        )

        NtripCasterUploadClient(request, connector).connectOnce { }

        assertEquals(NtripTransportMode.TLS, connector.connectedPolicy?.transport)
        assertEquals(
            "SOURCE pass123 /BASE\r\n" +
                "Source-Agent: RtkCollectorTest/1\r\n\r\n",
            socket.outputText(),
        )
    }

    @Test
    fun `tls selected socket preserves v2 chunked post bytes`() {
        val socket = FakeUploadSocket("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
        val connector = TlsInjectedUploadConnector(socket)
        val request = defaultRequest(
            credentials = NtripCredentials("base01", "pass123"),
            protocolVersion = NtripProtocolVersion.NTRIP_V2,
        )

        NtripCasterUploadClient(request, connector).connectOnce { output ->
            output.write(byteArrayOf(0xD3.toByte(), 0x00, 0x01))
        }

        assertEquals(NtripTransportMode.TLS, connector.connectedPolicy?.transport)
        assertEquals(
            "POST /BASE HTTP/1.1\r\n" +
                "Host: caster.example:2101\r\n" +
                "User-Agent: RtkCollectorTest/1\r\n" +
                "Ntrip-Version: Ntrip/2.0\r\n" +
                "Connection: close\r\n" +
                "Content-Type: gnss/data\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "Authorization: Basic YmFzZTAxOnBhc3MxMjM=\r\n\r\n" +
                "3\r\n\u00d3\u0000\u0001\r\n",
            socket.outputText(),
        )
    }

    @Test
    fun `v1 source upload rejects non icy accepted response`() {
        val client = NtripCasterUploadClient(
            defaultRequest(protocolVersion = NtripProtocolVersion.NTRIP_V1),
            FakeUploadConnector(FakeUploadSocket("HTTP/1.1 200 OK\r\n\r\n".toByteArray())),
        )

        val result = client.connectOnce { error("must not write") }

        assertInstanceOf(NtripCasterUploadResult.Failure::class.java, result)
        assertEquals(
            NtripCasterUploadFailureKind.UNSUPPORTED_RESPONSE,
            (result as NtripCasterUploadResult.Failure).failure.kind,
        )
    }

    @Test
    fun `v1 rejects malformed icy status code`() {
        val client = NtripCasterUploadClient(
            defaultRequest(protocolVersion = NtripProtocolVersion.NTRIP_V1),
            FakeUploadConnector(FakeUploadSocket("ICY 2000 OK\r\n".toByteArray())),
        )

        val result = client.connectOnce { error("must not write") }

        assertEquals(
            NtripCasterUploadFailureKind.UNSUPPORTED_RESPONSE,
            (result as NtripCasterUploadResult.Failure).failure.kind,
        )
    }

    @Test
    fun `v2 rejects icy response even when the status code is 200`() {
        val client = NtripCasterUploadClient(
            defaultRequest(protocolVersion = NtripProtocolVersion.NTRIP_V2),
            FakeUploadConnector(FakeUploadSocket("ICY 200 OK\r\n".toByteArray())),
        )

        val result = client.connectOnce { error("must not write") }

        assertEquals(
            NtripCasterUploadFailureKind.UNSUPPORTED_RESPONSE,
            (result as NtripCasterUploadResult.Failure).failure.kind,
        )
    }

    @Test
    fun `rejected response does not echo reflected source password`() {
        val client = NtripCasterUploadClient(
            defaultRequest(
                mountpoint = "UM980BASE",
                credentials = NtripCredentials(username = "base01", password = "pass123"),
                protocolVersion = NtripProtocolVersion.NTRIP_V1,
            ),
            FakeUploadConnector(
                FakeUploadSocket("ERROR SOURCE pass123 /UM980BASE\r\n".toByteArray()),
            ),
        )

        val result = client.connectOnce { error("must not write") }

        val failure = (result as NtripCasterUploadResult.Failure).failure
        assertEquals(NtripCasterUploadFailureKind.UNSUPPORTED_RESPONSE, failure.kind)
        assertFalse(failure.message.contains("pass123"))
        assertTrue(failure.message.contains("rejected"))
    }

    @Test
    fun `hostile caster response and TLS failure do not expose upload secrets`() {
        val secret = "upload-password-marker"
        val token = java.util.Base64.getEncoder()
            .encodeToString("user:$secret".toByteArray(Charsets.UTF_8))
        val requestBytes = "POST /PRIVATE HTTP/1.1"
        val certificate = "certificate-bytes-marker"
        val privateKey = "private-key-marker"
        val markers = listOf(secret, token, requestBytes, certificate, privateKey)
        val request = defaultRequest(credentials = NtripCredentials("user", secret))
        val response = "HTTP/1.1 403 " + markers.joinToString(" ") + "\r\n\r\n"
        val responseFailure = (NtripCasterUploadClient(
            request, FakeUploadConnector(FakeUploadSocket(response.toByteArray())),
        ).connectOnce { error("must not write") } as NtripCasterUploadResult.Failure).failure
        val exception = javax.net.ssl.SSLHandshakeException(markers.joinToString(" "))
        val connector = object : NtripSocketConnector {
            override fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket = throw exception
        }
        val handshakeFailure = (NtripCasterUploadClient(request, connector)
            .connectOnce { error("must not write") } as NtripCasterUploadResult.Failure).failure
        val exposed = responseFailure.message + responseFailure.cause + handshakeFailure.message + handshakeFailure.cause
        markers.forEach { assertFalse(exposed.contains(it)) }
    }

    @Test
    fun `upload transport exceptions are sanitized and active socket is cleared`() {
        val markers = listOf(
            "upload-password-marker", "POST /PRIVATE HTTP/1.1",
            "certificate-bytes-marker", "private-key-marker",
        )
        val hostile = IOException(markers.joinToString(" "))
        for (stage in listOf("write", "flush", "read", "stream", "close")) {
            var closes = 0
            val socket = object : NtripSocket {
                override val input: InputStream = object : ByteArrayInputStream("HTTP/1.1 200 OK\r\n".toByteArray()) {
                    override fun read(): Int {
                        if (stage == "read") throw hostile
                        return super.read()
                    }
                }
                override val output: OutputStream = object : ByteArrayOutputStream() {
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        if (stage == "write" || stage == "stream" && size() > 0) throw hostile
                        super.write(b, off, len)
                    }
                    override fun flush() {
                        if (stage == "flush") throw hostile
                    }
                }
                override fun close() {
                    closes++
                    if (stage == "close") throw hostile
                }
            }
            val client = NtripCasterUploadClient(
                defaultRequest(credentials = NtripCredentials("user", markers[0])),
                FakeUploadConnector(socket),
            )
            val result = assertDoesNotThrow<NtripCasterUploadResult> {
                client.connectOnce { it.write(byteArrayOf(0xD3.toByte())) }
            }
            val failure = (result as NtripCasterUploadResult.Failure).failure
            assertEquals(
                if (stage == "stream" || stage == "close") NtripCasterUploadFailureKind.STREAM_FAILED
                else NtripCasterUploadFailureKind.CONNECT_FAILED,
                failure.kind,
                stage,
            )
            markers.forEach { assertFalse(failure.message.contains(it), stage) }
            assertEquals(null, failure.cause, stage)
            assertEquals(1, closes, stage)
            client.cancel()
            assertEquals(1, closes, "$stage left active socket set")
        }
    }

    @Test
    fun `bad password response is authentication failure`() {
        val client = NtripCasterUploadClient(
            defaultRequest(protocolVersion = NtripProtocolVersion.NTRIP_V1),
            FakeUploadConnector(FakeUploadSocket("ERROR - Bad Password\r\n\r\n".toByteArray())),
        )

        val result = client.connectOnce { error("must not write") }

        assertInstanceOf(NtripCasterUploadResult.Failure::class.java, result)
        assertEquals(
            NtripCasterUploadFailureKind.AUTHENTICATION_FAILED,
            (result as NtripCasterUploadResult.Failure).failure.kind,
        )
    }

    @Test
    fun `unauthorized response is authentication failure`() {
        val client = NtripCasterUploadClient(
            defaultRequest(protocolVersion = NtripProtocolVersion.NTRIP_V1),
            FakeUploadConnector(FakeUploadSocket("HTTP/1.1 401 Unauthorized\r\n\r\n".toByteArray())),
        )

        val result = client.connectOnce { error("must not write") }

        assertInstanceOf(NtripCasterUploadResult.Failure::class.java, result)
        assertEquals(
            NtripCasterUploadFailureKind.AUTHENTICATION_FAILED,
            (result as NtripCasterUploadResult.Failure).failure.kind,
        )
    }

    @Test
    fun `forbidden response is authorization failure`() {
        val client = NtripCasterUploadClient(
            defaultRequest(),
            FakeUploadConnector(FakeUploadSocket("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray())),
        )

        val result = client.connectOnce { error("must not write") }

        assertInstanceOf(NtripCasterUploadResult.Failure::class.java, result)
        assertEquals(
            NtripCasterUploadFailureKind.AUTHORIZATION_FAILED,
            (result as NtripCasterUploadResult.Failure).failure.kind,
        )
    }

    @Test
    fun `connect failure is retryable network failure`() {
        val client = NtripCasterUploadClient(
            defaultRequest(),
            object : NtripSocketConnector {
                override fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket = error("network down")
            },
        )

        val result = client.connectOnce { error("must not write") }

        assertInstanceOf(NtripCasterUploadResult.Failure::class.java, result)
        assertEquals(
            NtripCasterUploadFailureKind.CONNECT_FAILED,
            (result as NtripCasterUploadResult.Failure).failure.kind,
        )
    }

    @Test
    fun `cancel closes active socket`() {
        val socket = BlockingUploadSocket()
        val client = NtripCasterUploadClient(defaultRequest(), FakeUploadConnector(socket))
        val thread = Thread {
            client.connectOnce { output ->
                while (!socket.closed) {
                    output.write(0)
                    Thread.sleep(10)
                }
            }
        }

        thread.start()
        Thread.sleep(100)
        client.cancel()
        thread.join(2_000)

        assertTrue(socket.closed)
    }

    private fun defaultRequest(
        host: String = "caster.example",
        port: Int = 2101,
        mountpoint: String = "BASE",
        credentials: NtripCredentials = NtripCredentials(username = "uploader", password = "password"),
        userAgent: String = "RtkCollectorTest/1",
        protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
        policy: NtripEndpointSecurityPolicy = NtripEndpointSecurityPolicy.systemTrust(host, port),
    ): NtripCasterUploadRequest =
        NtripCasterUploadRequest(
            policy = policy,
            mountpoint = mountpoint,
            credentials = credentials,
            userAgent = userAgent,
            protocolVersion = protocolVersion,
        )

    private class FakeUploadConnector(private val socket: NtripSocket) : NtripSocketConnector {
        override fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket = socket
    }

    private class TlsInjectedUploadConnector(private val socket: NtripSocket) : NtripSocketConnector {
        var connectedPolicy: NtripEndpointSecurityPolicy? = null

        override fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket {
            connectedPolicy = policy
            return socket
        }
    }

    private open class FakeUploadSocket(inputBytes: ByteArray) : NtripSocket {
        constructor(input: InputStream) : this(ByteArray(0)) {
            this.input = input
        }

        protected val outputBuffer = ByteArrayOutputStream()

        override var input: InputStream = ByteArrayInputStream(inputBytes)
        override val output: OutputStream = outputBuffer

        override open fun close() = Unit

        fun outputText(): String = outputBuffer.toString(Charsets.ISO_8859_1.name())

        fun uploadPayload(): ByteArray {
            val marker = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
            val all = outputBuffer.toByteArray()
            val start = all.indexOf(marker).let { if (it < 0) all.size else it + marker.size }
            return all.copyOfRange(start, all.size)
        }

        private fun ByteArray.indexOf(needle: ByteArray): Int {
            for (index in 0..size - needle.size) {
                if (needle.indices.all { this[index + it] == needle[it] }) return index
            }
            return -1
        }
    }

    private class BlockingUploadSocket : FakeUploadSocket("ICY 200 OK\r\n\r\n".toByteArray()) {
        @Volatile
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
        }
    }

    private class LineTerminatedInputStream(
        private val bytes: ByteArray,
        private val fragmentSize: Int = bytes.size,
    ) : InputStream() {
        private var position = 0

        override fun read(): Int {
            check(position < bytes.size) { "Uploader read past the complete response line." }
            return bytes[position++].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            check(position < bytes.size) { "Uploader read past the complete response line." }
            val count = minOf(length, fragmentSize, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }

    private inner class LocalCaster(
        private val responseFragments: List<ByteArray>,
    ) : AutoCloseable {
        private val server = ServerSocket(0)
        private val serverTask: Thread
        val uploaded = CompletableFuture<ByteArray>()
        val port: Int = server.localPort

        init {
            serverTask = Thread {
                runCatching {
                    server.accept().use { socket ->
                        socket.soTimeout = 2_000
                        socket.getInputStream().readUntilHeaderTerminator()
                        responseFragments.forEach { fragment ->
                            socket.getOutputStream().write(fragment)
                            socket.getOutputStream().flush()
                        }
                        uploaded.complete(socket.getInputStream().readNBytes(3))
                    }
                }.onFailure(uploaded::completeExceptionally)
            }.apply {
                isDaemon = true
                start()
            }
        }

        override fun close() {
            server.close()
            serverTask.join(2_000)
        }
    }

    private fun InputStream.readUntilHeaderTerminator() {
        var previous = 0
        var current = 0
        var beforePrevious = 0
        var beforeBeforePrevious = 0
        while (true) {
            val next = read()
            check(next >= 0) { "Caster client closed before completing request headers." }
            beforeBeforePrevious = beforePrevious
            beforePrevious = previous
            previous = current
            current = next
            if (
                beforeBeforePrevious == '\r'.code &&
                beforePrevious == '\n'.code &&
                previous == '\r'.code &&
                current == '\n'.code
            ) {
                return
            }
        }
    }
}
