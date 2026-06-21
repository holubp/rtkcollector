package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PerformanceDiagnosticsTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `disabled performance recorder writes no samples`() {
        val store = DiagnosticsStore(tempDir.toFile())
        val recorder = PerformanceDiagnostics(store, directWrites = true) { false }

        recorder.recordIfDue(nowMillis = 10_000L) {
            PerformanceDiagnosticSample(10_000L, 1, 2, 3, 4, 5, 6, 7, null)
        }

        assertEquals(0, store.status(false, false).performanceBytes)
    }

    @Test
    fun `enabled performance recorder samples only after interval`() {
        val store = DiagnosticsStore(tempDir.toFile())
        var enabled = true
        val recorder = PerformanceDiagnostics(store, sampleIntervalMillis = 5_000L, directWrites = true) { enabled }

        recorder.recordIfDue(nowMillis = 1_000L) {
            PerformanceDiagnosticSample(1_000L, 1, 2, 3, 4, 5, 6, 7, null)
        }
        recorder.recordIfDue(nowMillis = 2_000L) {
            PerformanceDiagnosticSample(2_000L, 9, 9, 9, 9, 9, 9, 9, null)
        }
        recorder.recordIfDue(nowMillis = 6_100L) {
            PerformanceDiagnosticSample(6_100L, 8, 8, 8, 8, 8, 8, 8, null)
        }
        enabled = false
        recorder.recordIfDue(nowMillis = 12_000L) {
            PerformanceDiagnosticSample(12_000L, 7, 7, 7, 7, 7, 7, 7, null)
        }

        val text = tempDir.resolve("diagnostics/performance/performance.jsonl").toFile().readText()
        assertEquals(2, text.lineSequence().filter(String::isNotBlank).count())
    }
}
