package org.rtkcollector.app.recording

import org.rtkcollector.app.diagnostics.SessionDiagnostics
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SessionDiagnosticsIsolationTest {
    @TempDir lateinit var directory: Path

    @Test fun `start rejection retains writer lease until deferred close finishes`() {
        val registry = org.rtkcollector.app.sessions.ActiveSessionRegistry()
        val lease = registry.retainWriter(directory.toString())
        val writers = PathRecordingSessionWriters.open(directory)
        val cleanup = mutableListOf<Runnable>()
        val log = createSessionDiagnostics("s", writers,
            org.rtkcollector.app.diagnostics.DiagnosticsStore(directory.resolve("app").toFile()), lease,
            scheduleCleanup = { cleanup.add(it) }, startWorker = { throw java.util.concurrent.RejectedExecutionException() })
        assertTrue(cleanup.isEmpty(), "No event close may be scheduled while producers can still write")
        writers.appendEventJson("{\"type\":\"producer-before-shutdown\"}")
        writers.flushEvents()
        assertFalse(log.awaitClosed(1))
        assertThrows(IllegalArgumentException::class.java) { registry.requireInactive(directory.toString(), "share") }
        registry.activate(directory.resolve("new-session").toString())
        log.seal()
        log.seal()
        assertEquals(1, cleanup.size)
        assertFalse(log.awaitClosed(1))
        cleanup.single().run()
        assertTrue(log.awaitClosed(1000))
        registry.requireInactive(directory.toString(), "share")
        writers.closeNonEventStreams()
    }

    @Test fun `metadata failure cannot orphan an already owned event stream`() {
        val registry = org.rtkcollector.app.sessions.ActiveSessionRegistry()
        val lease = registry.retainWriter(directory.toString())
        val writers = PathRecordingSessionWriters.open(directory)
        val log = createSessionDiagnostics("s", writers,
            org.rtkcollector.app.diagnostics.DiagnosticsStore(directory.resolve("app").toFile()), lease,
            scheduleCleanup = { it.run() })
        Files.createDirectory(directory.resolve("session.json"))
        Files.write(directory.resolve("session.json/blocker"), byteArrayOf(1))
        try {
            assertThrows(java.io.IOException::class.java) { writers.writeSessionJson("{}") }
            log.record("start-failed", "Metadata write failed.", critical = true)
            log.seal()
            assertEquals("FINALIZED", closeRecordingSessionWriters(writers, log, 10_000).outcome)
            registry.requireInactive(directory.toString(), "share")
        } finally {
            log.seal()
            assertTrue(log.awaitClosed(10_000))
            writers.closeAll()
        }
    }

    @Test fun `blocked essential writer leaves byte exact raw capture independent`() {
        val writers = PathRecordingSessionWriters.open(directory)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val events = SessionDiagnostics("s", write = {
            entered.countDown()
            release.await()
            writers.appendEventJson(it)
            writers.flushEvents()
        })
        try {
            events.record("session-created", "created", critical = true)
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val bytes = ByteArray(100_000) { it.toByte() }
            writers.appendReceiverRx(bytes)
            writers.flushRaw()
            assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("receiver-rx.raw")))
            events.record("stop-requested", "USER", correlationId = "stop", critical = true)
            events.seal()
            val finalization = closeRecordingSessionWriters(writers, events, timeoutMillis = 10)
            assertEquals("EVIDENCE_INCOMPLETE", finalization.outcome)
            assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("receiver-rx.raw")))
            assertFalse(events.awaitClosed(1))
        } finally {
            release.countDown()
            events.seal()
            assertTrue(events.awaitClosed(1000))
            writers.closeAll()
        }
        assertTrue(String(Files.readAllBytes(directory.resolve("events.jsonl"))).contains("stop-requested"))
    }
}
