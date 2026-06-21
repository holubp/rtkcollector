package org.rtkcollector.app.diagnostics

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class RuntimeDiagnostics(
    private val store: DiagnosticsStore,
    private val directWrites: Boolean = false,
    private val enabled: () -> Boolean,
) {
    val isEnabled: Boolean
        get() = enabled()

    fun record(record: RuntimeDiagnosticRecord) {
        if (!enabled()) return
        if (directWrites) {
            runCatching { store.appendRuntime(enabled = true, record = record) }
            return
        }
        enqueue {
            store.appendRuntime(enabled = true, record = record)
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
            Thread(runnable, "rtkcollector-diagnostics").apply { isDaemon = true }
        }
    }
}
