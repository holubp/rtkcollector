package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.correction.NtripCasterUploadEvent
import org.rtkcollector.core.correction.NtripCasterUploadClient
import org.rtkcollector.core.correction.NtripCasterUploadController
import org.rtkcollector.core.correction.NtripCasterUploadRequest
import org.rtkcollector.core.correction.NtripCasterUploadResult
import org.rtkcollector.core.correction.NtripCasterUploadRuntimeConfig
import org.rtkcollector.core.correction.NtripCasterUploadPolicy
import org.rtkcollector.core.correction.NtripCasterUploadRetryPolicy
import org.rtkcollector.core.correction.NtripCredentials
import org.rtkcollector.core.correction.NtripEndpointSecurityPolicy
import org.rtkcollector.core.correction.NtripSocket
import org.rtkcollector.core.correction.NtripSocketConnector
import org.rtkcollector.app.diagnostics.redactDiagnosticText
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import javax.net.ssl.SSLHandshakeException

class CasterUploadEventJsonTest {
    @Test
    fun `upload read exception stays out of controller status and persisted events`() {
        val markers = listOf(
            "event-password-marker", "POST /PRIVATE HTTP/1.1",
            "certificate-bytes-marker", "private-key-marker",
        )
        val socket = object : NtripSocket {
            override val input = object : InputStream() {
                override fun read(): Int = throw IOException(markers.joinToString(" "))
            }
            override val output = ByteArrayOutputStream()
            override fun close() = Unit
        }
        val connector = object : NtripSocketConnector {
            override fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket = socket
        }
        val events = Collections.synchronizedList(mutableListOf<NtripCasterUploadEvent>())
        val controller = NtripCasterUploadController(
            uploadOnce = { config, onState, write ->
                NtripCasterUploadClient(config.request, connector).connectOnce(onState, write)
            },
            delay = {},
            eventSink = { events += it },
        )
        controller.start(NtripCasterUploadRuntimeConfig(
            request = NtripCasterUploadRequest(
                NtripEndpointSecurityPolicy.systemTrust("localhost", 2101), "PRIVATE",
                NtripCredentials("user", markers[0]),
            ),
            policy = NtripCasterUploadPolicy(
                retry = NtripCasterUploadRetryPolicy(stopAfterConsecutiveFailures = 1),
            ),
        ))
        val deadline = System.nanoTime() + 2_000_000_000L
        while (controller.snapshot().stopReason == null && System.nanoTime() < deadline) Thread.sleep(10)
        controller.stop()
        val exposed = controller.snapshot().lastError.orEmpty() + events.joinToString { casterUploadEventJson(it) }
        markers.forEach { assertFalse(exposed.contains(it)) }
        assertTrue(exposed.contains("connection failed"))
    }

    @Test
    fun `persisted caster failure event omits reflected TLS and request material`() {
        val secret = "event-password-marker"
        val basicToken = java.util.Base64.getEncoder()
            .encodeToString("user:$secret".toByteArray(Charsets.UTF_8))
        val markers = listOf(
            secret, basicToken, "POST /PRIVATE HTTP/1.1",
            "certificate-bytes-marker", "private-key-marker",
        )
        val response = "HTTP/1.1 403 " + markers.joinToString(" ") + "\r\n\r\n"
        val socket = object : NtripSocket {
            override val input = ByteArrayInputStream(response.toByteArray())
            override val output = ByteArrayOutputStream()
            override fun close() = Unit
        }
        val connector = object : NtripSocketConnector {
            override fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket = socket
        }
        val failure = (NtripCasterUploadClient(
            NtripCasterUploadRequest(
                NtripEndpointSecurityPolicy.systemTrust("localhost", 2101), "PRIVATE",
                NtripCredentials("user", markers[0]),
            ),
            connector,
        ).connectOnce { error("must not write") } as NtripCasterUploadResult.Failure).failure
        val json = casterUploadEventJson(NtripCasterUploadEvent("connect", failure.message, 1234L))
        markers.forEach { assertFalse(json.contains(it)) }
        assertTrue(json.contains("rejected source upload request"))

        val handshakeConnector = object : NtripSocketConnector {
            override fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket =
                throw SSLHandshakeException(markers.joinToString(" "))
        }
        val handshakeFailure = (NtripCasterUploadClient(
            NtripCasterUploadRequest(
                NtripEndpointSecurityPolicy.systemTrust("localhost", 2101), "PRIVATE",
                NtripCredentials("user", markers[0]),
            ),
            handshakeConnector,
        ).connectOnce { error("must not write") } as NtripCasterUploadResult.Failure).failure
        val handshakeEvent = casterUploadEventJson(
            NtripCasterUploadEvent("connect", handshakeFailure.message, 1235L),
        )
        val diagnostic = redactDiagnosticText(handshakeFailure.message)
        markers.forEach {
            assertFalse(handshakeEvent.contains(it))
            assertFalse(diagnostic.contains(it))
        }
    }

    @Test
    fun `caster upload event json redacts credential-like message content`() {
        val json = casterUploadEventJson(
            NtripCasterUploadEvent(
                kind = "connect",
                message = listOf(
                    "retry after",
                    "Authorization: Basic abc123",
                    "password=secret",
                    "token=abc",
                    "ntrip://user:pass@example.org:2101/MOUNT",
                ).joinToString(" "),
                timestampMillis = 1234L,
            ),
        )

        assertTrue(json.contains("\"type\":\"base-caster-upload\""))
        assertTrue(json.contains("\"kind\":\"connect\""))
        assertTrue(json.contains("\"timestampMillis\":1234"))
        assertTrue(json.contains("retry after"))
        assertTrue(json.contains("<redacted>"))
        assertFalse(json.contains("abc123"))
        assertFalse(json.contains("secret"))
        assertFalse(json.contains("token=abc"))
        assertFalse(json.contains("user:pass"))
    }
}
