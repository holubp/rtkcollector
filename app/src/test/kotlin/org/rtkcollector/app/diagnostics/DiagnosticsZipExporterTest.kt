package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipFile

class DiagnosticsZipExporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `runtime zip contains summary readme and runtime jsonl`() {
        val store = DiagnosticsStore(tempDir.resolve("files").toFile())
        store.appendRuntime(
            enabled = true,
            RuntimeDiagnosticRecord(1, DiagnosticCategory.USB, "FATAL", "USB failed"),
        )
        val exporter = DiagnosticsZipExporter(tempDir.resolve("cache").toFile())

        val zip = exporter.exportRuntimeLogs(store, DiagnosticsStatus(true, false, 10, 0, 1, 0))

        ZipFile(zip).use { opened ->
            val names = opened.entries().asSequence().map { it.name }.toSet()
            assertTrue("diagnostic-summary.json" in names)
            assertTrue("README.txt" in names)
            assertTrue(names.any { it.startsWith("runtime/") && it.endsWith(".jsonl") })
        }
    }
}
