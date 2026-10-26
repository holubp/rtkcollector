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
    fun `rotation uses byte cap with complete json records`() {
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 1024)
        repeat(100) {
            store.appendRuntime(true, RuntimeDiagnosticRecord(it.toLong(), DiagnosticCategory.APP, "INFO", "測定".repeat(1000)))
        }
        assertTrue(store.runtimeFiles().sumOf { it.length() } <= 1024)
        assertEquals(100, store.status(true, false).droppedRecords)
        repeat(100) { store.appendRuntime(true, RuntimeDiagnosticRecord(it.toLong(), DiagnosticCategory.APP, "INFO", "測定".repeat(10))) }
        assertTrue(store.runtimeFiles().isNotEmpty())
        assertTrue(store.runtimeFiles().sumOf { it.length() } <= 1024)
        store.runtimeFiles().forEach { file ->
            file.readLines().forEach { org.json.JSONObject(it) }
        }
    }

    @Test
    fun `essential journal survives optional rotation and snapshot survives deletion`() {
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 1024)
        store.appendEssential(RuntimeDiagnosticRecord(1, DiagnosticCategory.SERVICE, "INFO", "Stop accepted"))
        repeat(50) { store.appendRuntime(true, RuntimeDiagnosticRecord(2, DiagnosticCategory.APP, "INFO", "detail".repeat(30))) }
        val snapshot = store.snapshot(tempDir.resolve("snapshot").toFile(), performance = false)
        store.deleteDiagnostics()
        assertTrue(snapshot.any { it.first.startsWith("essential/") && it.second.readText().contains("Stop accepted") })
    }

    @Test
    fun `reduced cap and age prune optional files without touching sessions`() {
        var now = 1_000L
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 4096, clock = { now })
        repeat(20) { store.appendRuntime(true, RuntimeDiagnosticRecord(1, DiagnosticCategory.APP, "INFO", "detail".repeat(30))) }
        store.applyRetention(DiagnosticsRetention(1024, 1))
        assertTrue(store.runtimeFiles().sumOf { it.length() } <= 1024)
        now += 2 * 86_400_000L
        store.applyRetention(DiagnosticsRetention(1024, 1))
        assertTrue(store.runtimeFiles().isEmpty())
    }

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
        checkNotNull(unrelated.parentFile).mkdirs()
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
