package org.rtkcollector.app.diagnostics

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SessionDiagnosticsTest {
    @Test fun `oversized structured messages do not leak secrets through truncated framing`() {
        val lines = mutableListOf<String>()
        val body = "{\"password\":\"oversize-secret-marker\",\"padding\":\"" + "x".repeat(4000) + "\"}"
        val log = SessionDiagnostics("s", write = { lines.add(it) })
        log.record("error", JSONObject().put("body", body).toString())
        log.seal()
        assertTrue(log.awaitClosed(1000))
        assertFalse(lines.single().contains("oversize-secret-marker"))
    }
    @Test fun `interrupted drain does not throw or close an active writer`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val log = SessionDiagnostics("s", write = { entered.countDown(); release.await() })
        log.record("start", "start")
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        log.seal()
        try {
            Thread.currentThread().interrupt()
            assertFalse(log.awaitClosed(10))
        } finally { Thread.interrupted(); release.countDown() }
        assertTrue(log.awaitClosed(1000))
    }
    @Test fun `minimal events have identity and are delivered off caller thread`() {
        val lines = mutableListOf<String>()
        val caller = Thread.currentThread()
        val log = SessionDiagnostics("s1", write = { assertNotEquals(caller, Thread.currentThread()); synchronized(lines) { lines.add(it) } })
        log.record("session-created", "Started")
        log.record("stop-requested", "USER", correlationId = "stop1", critical = true)
        log.seal()
        assertTrue(log.awaitClosed(1000))
        val event = JSONObject(lines.last())
        assertEquals("s1", event.getString("sessionId"))
        assertEquals("stop1", event.getString("correlationId"))
        assertEquals(1, event.getInt("schemaVersion"))
        assertTrue(event.has("elapsedRealtimeMillis"))
        assertFalse(log.record("late", "must not write"))
    }

    @Test fun `ordinary event saturation reserves stop evidence and reports drops`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val lines = mutableListOf<String>()
        val log = SessionDiagnostics("s", capacity = 4, write = {
            entered.countDown(); release.await()
            synchronized(lines) { lines.add(it) }
        })
        try {
            log.record("first", "first")
            assertTrue(entered.await(10, TimeUnit.SECONDS), "Worker failures: ${log.writeFailures}")
            repeat(100) { log.record("ordinary", "repeat $it") }
            assertTrue(log.record("stop-requested", "USER", critical = true))
            log.seal()
            assertFalse(log.awaitClosed(1))
        } finally { release.countDown(); log.seal() }
        assertTrue(log.awaitClosed(10_000))
        assertTrue(log.droppedRecords > 0)
        assertTrue(lines.any { JSONObject(it).getString("type") == "stop-requested" })
    }

    @Test fun `failed event writes do not escape and are observable`() {
        val log = SessionDiagnostics("s", write = { throw java.io.IOException("disk full") })
        log.record("session-created", "Started")
        log.seal()
        assertTrue(log.awaitClosed(1000))
        assertEquals(1, log.writeFailures)
    }
}
