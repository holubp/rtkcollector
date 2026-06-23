package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SessionTaskDiagnosticsTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `session task failure is recorded in runtime diagnostics when enabled`() {
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 4096, maxPerformanceBytes = 4096)

        recordSessionTaskFailure(
            store = store,
            enabled = true,
            label = "Regenerate RTKLIB",
            category = DiagnosticCategory.RTKLIB,
            error = IllegalStateException("RTKLIB postpos failed\nnative detail"),
        )

        val text = tempDir.resolve("diagnostics/runtime/runtime.jsonl").toFile().readText()
        assertTrue(text.contains("\"category\":\"RTKLIB\""))
        assertTrue(text.contains("Regenerate RTKLIB failed"))
        assertTrue(text.contains("native detail"))
    }

    @Test
    fun `disabled session task diagnostics write nothing`() {
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 4096, maxPerformanceBytes = 4096)

        recordSessionTaskFailure(
            store = store,
            enabled = false,
            label = "Regenerate RTKLIB",
            category = DiagnosticCategory.RTKLIB,
            error = IllegalStateException("RTKLIB postpos failed"),
        )

        assertEquals(0, store.status(runtimeEnabled = false, performanceEnabled = false).runtimeBytes)
    }
}
