package org.rtkcollector.app.diagnostics

import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** One owner for essential event serialization and session-writer access. */
internal class SessionDiagnostics(
    private val sessionId: String,
    private val capacity: Int = 64,
    private val fallback: (String) -> Unit = {},
    private val closeEventStream: () -> Boolean = { true },
    private val scheduleFailedWorkerCleanup: (Runnable) -> Unit = { it.run() },
    private val startWorker: ((Runnable) -> Unit)? = null,
    private val write: (String) -> Unit,
) {
    private data class Pending(val code: String, val message: String, val severity: String,
        val correlation: String?, val wall: Long, val elapsed: Long, val critical: Boolean,
        val fields: Map<String, Any?>)
    private val lock = Object()
    private val pending = ArrayDeque<Pending>()
    private val closed = CountDownLatch(1)
    private var accepting = true
    private var workerStartFailed = false
    private var failedCleanupScheduled = false
    private var lastKey: Pair<String, String>? = null
    private var lastTime = 0L
    private val dropped = AtomicLong()
    private val failures = AtomicLong()
    private val criticalDrops = AtomicLong()
    val droppedRecords: Long get() = dropped.get()
    val writeFailures: Long get() = failures.get()
    val hasCriticalLoss: Boolean get() = criticalDrops.get() > 0

    init {
        require(capacity >= 4)
        runCatching {
            val task = Runnable { consume() }
            if (startWorker != null) startWorker.invoke(task)
            else Thread(task, "rtkcollector-session-events").apply { isDaemon = true; start() }
        }.onFailure {
            accepting = false; failures.incrementAndGet()
            workerStartFailed = true
        }
    }

    fun record(code: String, message: String, severity: String = "INFO", correlationId: String? = null,
        critical: Boolean = false, fields: Map<String, Any?> = emptyMap()): Boolean = synchronized(lock) {
        if (!accepting) return false
        val now = System.nanoTime() / 1_000_000
        val boundedMessage = if (message.length > 2048) "<oversized diagnostic message omitted>" else message
        val key = code.take(80) to boundedMessage
        if (!critical && key == lastKey && now - lastTime < 10_000) { dropped.incrementAndGet(); return false }
        val ordinaryLimit = capacity - minOf(8, capacity / 2)
        if (pending.size >= if (critical) capacity else ordinaryLimit) {
            dropped.incrementAndGet()
            val replaceable = if (critical) pending.firstOrNull { !it.critical } else null
            if (replaceable == null) {
                if (critical) criticalDrops.incrementAndGet()
                return false
            }
            pending.remove(replaceable)
        }
        lastKey = key; lastTime = now
        if (message.length > 2048) dropped.incrementAndGet()
        pending.addLast(Pending(code.take(80), boundedMessage, severity.take(16), correlationId,
            System.currentTimeMillis(), now, critical, fields.filterKeys { it in SAFE_EVENT_FIELDS }))
        lock.notifyAll()
        true
    }

    fun seal() {
        val cleanupNeeded = synchronized(lock) {
            accepting = false
            lock.notifyAll()
            (workerStartFailed && !failedCleanupScheduled).also {
                if (it) failedCleanupScheduled = true
            }
        }
        // Only sealing confirms that producers have relinquished the event stream.
        if (cleanupNeeded) runCatching {
            scheduleFailedWorkerCleanup(Runnable { finishClose() })
        }.onFailure { failures.incrementAndGet() }
    }
    fun awaitClosed(timeoutMillis: Long): Boolean {
        if (closed.count == 0L) return true
        return try { closed.await(timeoutMillis, TimeUnit.MILLISECONDS) }
        catch (_: InterruptedException) { Thread.currentThread().interrupt(); false }
    }

    private fun consume() {
        try {
            while (true) {
                val event = synchronized(lock) {
                    while (pending.isEmpty() && accepting) lock.wait()
                    if (pending.isEmpty()) return
                    pending.removeFirst()
                }
                runCatching {
                    val json = JSONObject().put("schemaVersion", 1).put("sessionId", sessionId)
                        .put("type", event.code).put("severity", event.severity)
                        .put("timestamp", Instant.ofEpochMilli(event.wall).toString())
                        .put("timestampMillis", event.wall).put("elapsedRealtimeMillis", event.elapsed)
                        .put("message", redactDiagnosticText(event.message))
                        .put("droppedRecords", droppedRecords).put("writeFailures", writeFailures)
                    event.correlation?.let { json.put("correlationId", it) }
                    event.fields.forEach { (key, value) ->
                        json.put(key, when (value) {
                            null -> JSONObject.NULL
                            is String -> redactDiagnosticText(value).take(1024)
                            is Number, is Boolean -> value
                            else -> "<unsupported>"
                        })
                    }
                    val line = json.toString()
                    try { write(line) } catch (error: Exception) {
                        failures.incrementAndGet()
                        runCatching { fallback(line) }
                    }
                }.onFailure { failures.incrementAndGet() }
            }
        } finally {
            finishClose()
        }
    }

    private fun finishClose() {
        try {
            runCatching { if (!closeEventStream()) failures.incrementAndGet() }
                .onFailure { failures.incrementAndGet() }
        } finally { closed.countDown() }
    }

    companion object {
        private val SAFE_EVENT_FIELDS = setOf("state", "uploadedBytes", "droppedBytes", "bitrateKbps",
            "rtcmHz", "stopReason", "url", "lastError")
    }
}
