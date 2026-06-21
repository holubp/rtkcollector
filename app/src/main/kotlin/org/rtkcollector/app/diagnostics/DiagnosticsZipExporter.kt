package org.rtkcollector.app.diagnostics

import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiagnosticsZipExporter(private val cacheDir: File) {
    private val shareDir: File = cacheDir.resolve("diagnostic-share")

    fun exportRuntimeLogs(store: DiagnosticsStore, status: DiagnosticsStatus): File =
        export(
            namePrefix = "rtkcollector-runtime-logs",
            summaryName = "diagnostic-summary.json",
            summary = status.toSummaryJson("runtime"),
            files = store.runtimeFiles().map { "runtime/${it.name}" to it },
        )

    fun exportPerformanceLogs(store: DiagnosticsStore, status: DiagnosticsStatus): File =
        export(
            namePrefix = "rtkcollector-performance-logs",
            summaryName = "performance-summary.json",
            summary = status.toSummaryJson("performance"),
            files = store.performanceFiles().map { "performance/${it.name}" to it },
        )

    fun deleteTemporaryShares() {
        shareDir.deleteRecursively()
    }

    private fun export(
        namePrefix: String,
        summaryName: String,
        summary: String,
        files: List<Pair<String, File>>,
    ): File {
        shareDir.mkdirs()
        val zip = shareDir.resolve("$namePrefix-${System.currentTimeMillis()}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.putTextEntry(summaryName, summary)
            out.putTextEntry(
                "README.txt",
                "RtkCollector diagnostics are opt-in. Secrets are redacted before records are written.\n",
            )
            files.forEach { (entryName, file) ->
                if (file.isFile) {
                    out.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { input -> input.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        return zip
    }
}

private fun DiagnosticsStatus.toSummaryJson(kind: String): String =
    JSONObject()
        .put("kind", kind)
        .put("runtimeLoggingEnabled", runtimeLoggingEnabled)
        .put("performanceMonitoringEnabled", performanceMonitoringEnabled)
        .put("runtimeBytes", runtimeBytes)
        .put("performanceBytes", performanceBytes)
        .put("runtimeFiles", runtimeFiles)
        .put("performanceFiles", performanceFiles)
        .toString(2)

private fun ZipOutputStream.putTextEntry(name: String, text: String) {
    putNextEntry(ZipEntry(name))
    write(text.toByteArray(Charsets.UTF_8))
    closeEntry()
}
