package org.rtkcollector.app.diagnostics

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class PerformanceDiagnostics(
    private val store: DiagnosticsStore,
    private val sampleIntervalMillis: Long = 5_000L,
    private val directWrites: Boolean = false,
    private val enabled: () -> Boolean,
) {
    private var lastSampleAtMillis: Long = Long.MIN_VALUE

    val isEnabled: Boolean
        get() = enabled()

    fun recordIfDue(nowMillis: Long, sample: () -> PerformanceDiagnosticSample) {
        if (!enabled()) return
        if (nowMillis < 0) return

        if (lastSampleAtMillis != Long.MIN_VALUE && nowMillis - lastSampleAtMillis < sampleIntervalMillis) {
            return
        }

        lastSampleAtMillis = nowMillis
        val capturedSample = sample()
        if (directWrites) {
            runCatching { store.appendPerformance(enabled = true, sample = capturedSample) }
            return
        }
        enqueue {
            store.appendPerformance(enabled = true, sample = capturedSample)
        }
    }

    private fun enqueue(write: () -> Unit) {
        if (pendingWrites.incrementAndGet() > MAX_PENDING_WRITES) {
            pendingWrites.decrementAndGet()
            return
        }
        executor.execute {
            try {
                runCatching(write)
            } finally {
                pendingWrites.decrementAndGet()
            }
        }
    }

    private companion object {
        const val MAX_PENDING_WRITES = 16
        val pendingWrites = AtomicInteger(0)
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "rtkcollector-performance-diagnostics").apply { isDaemon = true }
        }
    }
}
