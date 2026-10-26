package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RuntimeDiagnosticsTest {
    @TempDir lateinit var tempDir: Path
    @Test fun `rejected submissions are isolated and do not leak queue capacity`() {
        val store = DiagnosticsStore(tempDir.toFile())
        val log = RuntimeDiagnostics(store, submit = { _, _ -> throw java.util.concurrent.RejectedExecutionException() }) { true }
        repeat(100) { log.record(RuntimeDiagnosticRecord(1, DiagnosticCategory.APP, "ERROR", "failure")) }
        assertEquals(100, store.status(false, false).writeFailures)
        assertEquals(0, store.status(false, false).droppedRecords)
    }
    @Test fun `minimal errors persist without enabling verbose and debug stays filtered`() {
        val store = DiagnosticsStore(tempDir.toFile())
        var level = DiagnosticLevel.MINIMAL
        val log = RuntimeDiagnostics(store, directWrites = true, level = { level }) { false }
        log.record(RuntimeDiagnosticRecord(1, DiagnosticCategory.APP, "FATAL", "failure"))
        log.record(RuntimeDiagnosticRecord(2, DiagnosticCategory.APP, "INFO", "optional"))
        log.record(RuntimeDiagnosticRecord(3, DiagnosticCategory.APP, "INFO", "debug detail"), DiagnosticLevel.DEBUG)
        assertEquals(0, store.status(false, false).runtimeBytes)
        assertTrue(store.status(false, false).essentialBytes > 0)
        level = DiagnosticLevel.DEBUG
        log.record(RuntimeDiagnosticRecord(4, DiagnosticCategory.APP, "INFO", "debug detail"), DiagnosticLevel.DEBUG)
        assertTrue(store.runtimeFiles().joinToString { it.readText() }.contains("debug detail"))
    }
}
