package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.channels.ClosedChannelException

class FailureDiagnosticsTest {
    @Test fun `partial-open cleanup failures retain bounded artifact operation and cause without messages`() {
        val opening = org.rtkcollector.core.session.SessionWriterOpenException("tx-to-receiver.raw", "open", IOException())
        repeat(8) {
            opening.addSuppressed(org.rtkcollector.core.session.SessionWriterCleanupException("receiver-rx.raw", "close",
                ClosedChannelException()))
        }
        val error = org.rtkcollector.core.capture.RawRecordingStorageException("opening failed", opening,
            artifact = opening.artifact, operation = opening.operation)
        val fields = failureDiagnosticAttributes(error)
        assertEquals("tx-to-receiver.raw", fields["artifact"])
        val cleanup = fields["cleanupFailures"]!!
        assertTrue(cleanup.contains("receiver-rx.raw:close:java.nio.channels.ClosedChannelException"))
        assertTrue(cleanup.length <= 256)
    }

    @Test fun `raw storage boundary retains actual artifact and operation`() {
        val error = org.rtkcollector.core.capture.RawRecordingStorageException("raw failed", ClosedChannelException(),
            artifact = "receiver-rx.raw", operation = "append")
        val fields = failureDiagnosticAttributes(error)
        assertEquals("receiver-rx.raw", fields["artifact"])
        assertEquals("append", fields["operation"])
        assertTrue(fields["causeClasses"]!!.contains("ClosedChannelException"))
    }

    @Test fun `process correlation is stable across Activity attachments and does not bind PID`() {
        val first = ProcessDiagnosticIdentity.attributes(42)
        val second = ProcessDiagnosticIdentity.attributes(43)
        assertEquals(first["processInstanceId"], second["processInstanceId"])
        assertEquals("42", first["processId"])
        assertEquals("43", second["processId"])
        assertNotNull(java.util.UUID.fromString(first["processInstanceId"]))
    }

    @Test fun `failure identity survives runtime and minimal session allowlists`() {
        val fields = mapOf("exceptionClass" to "java.nio.channels.ClosedChannelException",
            "artifact" to "receiver-rx.raw", "operation" to "flush", "causeClasses" to "java.io.IOException",
            "stage" to "storage-open", "stageElapsedMillis" to "17", "startupGeneration" to "2",
            "processId" to "42", "processInstanceId" to "instance", "unknown" to "password=secret")
        val runtime = org.json.JSONObject(RuntimeDiagnosticRecord(1, DiagnosticCategory.STORAGE,
            "ERROR", "failed", fields).toJsonLine()).getJSONObject("attributes")
        fields.filterKeys { it != "unknown" }.forEach { (key, value) -> assertEquals(value, runtime.getString(key)) }
        assertFalse(runtime.has("unknown"))
        val lines = mutableListOf<String>()
        val log = SessionDiagnostics("session", write = { lines += it })
        log.record("failed", "failed", fields = fields)
        log.seal()
        assertTrue(log.awaitClosed(5_000))
        val event = org.json.JSONObject(lines.single())
        fields.filterKeys { it != "unknown" }.forEach { (key, value) -> assertEquals(value, event.getString(key)) }
        assertFalse(event.has("unknown"))
    }

    @Test fun `message-less storage failures retain class artifact and operation`() {
        val attributes = failureDiagnosticAttributes(ClosedChannelException(), "receiver-rx.raw", "flush")
        assertEquals("java.nio.channels.ClosedChannelException", attributes["exceptionClass"])
        assertEquals("receiver-rx.raw", attributes["artifact"])
        assertEquals("flush", attributes["operation"])
    }

    @Test fun `cause traversal is bounded and excludes credential-bearing messages`() {
        var cause: Throwable = IOException("password=do-not-export")
        repeat(12) { cause = IllegalStateException("Authorization: Basic private-value", cause) }
        val fields = failureDiagnosticAttributes(cause)
        assertTrue(fields.values.all { it.length <= 256 })
        assertFalse(fields.toString().contains("do-not-export"))
        assertFalse(fields.toString().contains("private-value"))
        assertTrue(fields["causeClasses"]!!.split(" -> ").size <= 4)
    }

    @Test fun `cyclic causes cannot loop diagnostic production`() {
        val first = IOException()
        val second = IllegalStateException()
        first.initCause(second); second.initCause(first)
        val fields = failureDiagnosticAttributes(first)
        assertEquals("java.lang.IllegalStateException", fields["causeClasses"])
    }

    @Test fun `operation details are redacted before truncation`() {
        val fields = failureDiagnosticAttributes(IOException(), "x".repeat(400), "password=private-value")
        assertEquals(256, fields["artifact"]!!.length)
        assertFalse(fields["operation"]!!.contains("private-value"))
    }
}
