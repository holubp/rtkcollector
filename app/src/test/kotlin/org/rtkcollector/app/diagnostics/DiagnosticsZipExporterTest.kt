package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipFile

class DiagnosticsZipExporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test fun `legacy retained logs are sanitized at export without rewriting originals`() {
        val root = tempDir.resolve("legacy").toFile()
        val legacy = root.resolve("diagnostics/runtime/runtime.jsonl")
        checkNotNull(legacy.parentFile).mkdirs()
        legacy.writeText(org.json.JSONObject().put("message",
            org.json.JSONObject().put("password", "legacy-secret-marker").toString()).toString() + "\n")
        val store = DiagnosticsStore(root)
        val zip = DiagnosticsZipExporter(tempDir.resolve("cache").toFile()).exportRuntimeLogs(store, store.status(true, false))
        ZipFile(zip).use { opened ->
            val entry = opened.entries().asSequence().first { it.name.startsWith("runtime/") }
            org.junit.jupiter.api.Assertions.assertFalse(opened.getInputStream(entry).bufferedReader().use { it.readText() }.contains("legacy-secret-marker"))
        }
        assertTrue(legacy.readText().contains("legacy-secret-marker"))
    }

    @Test fun `sharing refuses low space before allocating snapshot copies`() {
        val store = DiagnosticsStore(tempDir.resolve("files").toFile())
        store.appendEssential(RuntimeDiagnosticRecord(1, DiagnosticCategory.APP, "NOTICE", "evidence"))
        val cache = object : java.io.File(tempDir.resolve("low-space").toString()) {
            override fun getUsableSpace(): Long = 1
        }
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException::class.java) {
            DiagnosticsZipExporter(cache).exportRuntimeLogs(store, store.status(false, false))
        }
        org.junit.jupiter.api.Assertions.assertFalse(cache.listFiles().orEmpty().any { it.name.startsWith("diagnostic-snapshot") })
    }
    @Test fun `a recreated exporter cannot delete a recently shared zip`() {
        val store = DiagnosticsStore(tempDir.resolve("files").toFile())
        store.appendEssential(RuntimeDiagnosticRecord(1, DiagnosticCategory.APP, "NOTICE", "evidence"))
        val cache = tempDir.resolve("cache").toFile()
        val zip = DiagnosticsZipExporter(cache).exportRuntimeLogs(store, store.status(false, false))
        DiagnosticsZipExporter(cache).deleteTemporaryShares()
        assertTrue(zip.isFile)
        ZipFile(zip).use { assertTrue(it.entries().hasMoreElements()) }
    }

    @Test
    fun `minimal app export contains essential evidence and level without optional logs`() {
        val store = DiagnosticsStore(tempDir.resolve("minimal").toFile())
        store.appendEssential(RuntimeDiagnosticRecord(1, DiagnosticCategory.SERVICE, "NOTICE", "stop accepted"))
        val zip = DiagnosticsZipExporter(tempDir.resolve("cache").toFile())
            .exportRuntimeLogs(store, store.status(false, false))
        store.deleteDiagnostics()
        ZipFile(zip).use { opened ->
            val essential = opened.entries().asSequence().firstOrNull { it.name.startsWith("essential/") }
            assertTrue(essential != null)
            assertTrue(opened.getInputStream(essential).bufferedReader().use { it.readText() }.contains("stop accepted"))
            assertTrue(opened.getInputStream(opened.getEntry("diagnostic-summary.json")).bufferedReader()
                .use { it.readText() }.contains("MINIMAL"))
        }
    }

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
