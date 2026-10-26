package org.rtkcollector.app.diagnostics

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class RuntimeDiagnostics(
    private val store: DiagnosticsStore,
    private val directWrites: Boolean = false,
    private val level: (() -> DiagnosticLevel)? = null,
    private val submit: ((Boolean, Runnable) -> Unit)? = null,
    private val enabled: () -> Boolean,
) {
    val isEnabled: Boolean
        get() = true // Minimal errors remain enabled independently of optional detail.

    fun record(record: RuntimeDiagnosticRecord, minimumLevel: DiagnosticLevel =
        when (record.severity) { "DEBUG" -> DiagnosticLevel.DEBUG; "INFO" -> DiagnosticLevel.VERBOSE; else -> DiagnosticLevel.MINIMAL }) {
        val selected = level?.invoke() ?: if (enabled()) DiagnosticLevel.VERBOSE else DiagnosticLevel.MINIMAL
        if (!selected.includes(minimumLevel)) return
        val essential = minimumLevel == DiagnosticLevel.MINIMAL
        val write = {
            if (essential) store.appendEssential(record) else store.appendRuntime(enabled = true, record = record)
        }
        if (directWrites) {
            runCatching(write).onFailure { store.noteWriteFailure() }
            return
        }
        enqueue(essential, write)
    }

    private fun enqueue(essential: Boolean, write: () -> Unit) {
        val count = if (essential) essentialPending else pendingWrites
        if (count.incrementAndGet() > MAX_PENDING_WRITES) {
            count.decrementAndGet()
            store.noteDropped()
            return
        }
        val task = Runnable {
            try {
                runCatching(write).onFailure { store.noteWriteFailure() }
            } finally {
                count.decrementAndGet()
            }
        }
        runCatching {
            if (submit != null) submit.invoke(essential, task)
            else (if (essential) essentialExecutor else executor).execute(task)
        }.onFailure { count.decrementAndGet(); store.noteWriteFailure() }
    }

    private companion object {
        const val MAX_PENDING_WRITES = 16
        val pendingWrites = AtomicInteger(0)
        val essentialPending = AtomicInteger(0)
        val essentialExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "rtkcollector-essential-journal").apply { isDaemon = true }
        }
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "rtkcollector-diagnostics").apply { isDaemon = true }
        }
    }
}
