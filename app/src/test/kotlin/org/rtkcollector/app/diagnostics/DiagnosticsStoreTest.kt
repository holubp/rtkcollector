package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DiagnosticsStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `disabled runtime logging writes nothing`() {
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 1024, maxPerformanceBytes = 1024)
        store.appendRuntime(
            enabled = false,
            RuntimeDiagnosticRecord(1, DiagnosticCategory.USB, "FATAL", "USB failed"),
        )

        assertEquals(0, store.status(runtimeEnabled = false, performanceEnabled = false).runtimeBytes)
    }

    @Test
    fun `runtime logging writes jsonl when enabled`() {
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 1024, maxPerformanceBytes = 1024)
        store.appendRuntime(
            enabled = true,
            RuntimeDiagnosticRecord(1, DiagnosticCategory.NTRIP, "DEGRADED", "password=secret"),
        )

        val file = tempDir.resolve("diagnostics/runtime/runtime.jsonl").toFile()
        assertTrue(file.exists())
        assertFalse(file.readText().contains("secret"))
    }

    @Test
    fun `delete removes diagnostics only`() {
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 1024, maxPerformanceBytes = 1024)
        val unrelated = tempDir.resolve("sessions/session-a/receiver-rx.raw").toFile()
        unrelated.parentFile.mkdirs()
        unrelated.writeText("raw")

        store.appendPerformance(
            enabled = true,
            PerformanceDiagnosticSample(1, 1, 2, 3, 4, 5, 6, 7, null),
        )
        store.deleteDiagnostics()

        assertTrue(unrelated.exists())
        assertEquals(0, store.status(runtimeEnabled = false, performanceEnabled = false).performanceBytes)
    }
}
