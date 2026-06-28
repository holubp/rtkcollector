package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertArrayEquals
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
            defaultRequest().copy(host = "caster.example\r\nBad: yes")
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
                override fun connect(host: String, port: Int): NtripSocket = error("network down")
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
        mountpoint: String = "BASE",
        credentials: NtripCredentials = NtripCredentials(username = "uploader", password = "password"),
        userAgent: String = "RtkCollectorTest/1",
        protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
    ): NtripCasterUploadRequest =
        NtripCasterUploadRequest(
            host = "caster.example",
            port = 2101,
            mountpoint = mountpoint,
            credentials = credentials,
            userAgent = userAgent,
            protocolVersion = protocolVersion,
        )

    private class FakeUploadConnector(private val socket: NtripSocket) : NtripSocketConnector {
        override fun connect(host: String, port: Int): NtripSocket = socket
    }

    private open class FakeUploadSocket(inputBytes: ByteArray) : NtripSocket {
        protected val outputBuffer = ByteArrayOutputStream()

        override val input: InputStream = ByteArrayInputStream(inputBytes)
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
}
