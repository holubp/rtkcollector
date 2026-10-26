package org.rtkcollector.app.diagnostics

import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.UUID
import org.rtkcollector.app.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.io.IOException

class DiagnosticsZipExporter(private val cacheDir: File) {
    private val shareDir: File = cacheDir.resolve("diagnostic-share")
    private val shareLock = shareLocks.computeIfAbsent(shareDir.absolutePath) { Any() }

    fun exportRuntimeLogs(store: DiagnosticsStore, status: DiagnosticsStatus): File =
        synchronized(shareLock) { snapshotExport(store, status, performance = false) }

    fun exportPerformanceLogs(store: DiagnosticsStore, status: DiagnosticsStatus): File =
        synchronized(shareLock) { snapshotExport(store, status, performance = true) }

    fun deleteTemporaryShares() = synchronized(shareLock) {
        shareDir.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() >= SHARE_GRACE_MILLIS }
            .forEach { check(it.delete()) { "Unable to remove expired diagnostic share." } }
    }

    private fun snapshotExport(store: DiagnosticsStore, status: DiagnosticsStatus, performance: Boolean): File {
        deleteTemporaryShares()
        if (shareDir.listFiles().orEmpty().size >= 3) {
            throw IOException("Three recently shared log copies are still reserved for recipients. Try again after one hour.")
        }
        val current = store.status(status.runtimeLoggingEnabled, status.performanceMonitoringEnabled, status.level)
        store.applyRetention(current.retention)
        val retained = store.status(status.runtimeLoggingEnabled, status.performanceMonitoringEnabled, status.level)
        check(cacheDir.mkdirs() || cacheDir.isDirectory)
        val inputBytes = if (performance) retained.performanceBytes else retained.runtimeBytes + retained.essentialBytes
        if (cacheDir.usableSpace < inputBytes * 2 + 64_000_000L) {
            throw IOException("Not enough free space to share diagnostics safely. Free storage and retry.")
        }
        val snapshot = cacheDir.resolve("diagnostic-snapshot-${UUID.randomUUID()}")
        try {
            val files = store.snapshot(snapshot, performance)
            return export(if (performance) "rtkcollector-performance-logs" else "rtkcollector-runtime-logs",
                if (performance) "performance-summary.json" else "diagnostic-summary.json",
                retained.toSummaryJson(if (performance) "performance" else "runtime"), files)
        } finally { snapshot.deleteRecursively() }
    }

    private fun export(
        namePrefix: String,
        summaryName: String,
        summary: String,
        files: List<Pair<String, File>>,
    ): File {
        shareDir.mkdirs()
        val zip = shareDir.resolve("$namePrefix-${UUID.randomUUID()}.zip")
        try { ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.putTextEntry(
                "README.txt",
                "Minimal evidence is always on. Verbose/Debug logs are optional and may span sessions. Known credential forms are redacted. Session recordings are shared separately.\n",
            )
            var omitted = 0
            files.forEach { (entryName, file) ->
                if (file.isFile) {
                    out.putNextEntry(ZipEntry(entryName))
                    omitted += out.putSanitizedRecords(file)
                    out.closeEntry()
                }
            }
            out.putTextEntry(summaryName, JSONObject(summary).put("exportOmittedRecords", omitted).toString(2))
        } } catch (error: Exception) { zip.delete(); throw error }
        return zip
    }

    companion object {
        private const val SHARE_GRACE_MILLIS = 3_600_000L
        private val shareLocks = ConcurrentHashMap<String, Any>()
    }
}

// BufferedReader.readLine is unbounded; old retained records may predate current limits.
private fun ZipOutputStream.putSanitizedRecords(file: File): Int {
    var omitted = 0
    file.bufferedReader(Charsets.UTF_8).use { reader ->
        val line = StringBuilder()
        var oversized = false
        fun emit() {
            if (line.isEmpty() && !oversized) return
            val safe = if (oversized) null else runCatching {
                redactDiagnosticText(JSONObject(line.toString()).toString())
            }.getOrNull()?.takeIf { it.toByteArray(Charsets.UTF_8).size <= 16_384 }
            val text = safe ?: RuntimeDiagnosticRecord(System.currentTimeMillis(), DiagnosticCategory.APP,
                "NOTICE", "Malformed or oversized retained diagnostic record omitted.", code = "diagnostic-export-omission")
                .toJsonLine().also { omitted++ }
            write(text.toByteArray(Charsets.UTF_8))
            write('\n'.code)
            line.setLength(0)
            oversized = false
        }
        while (true) {
            val character = reader.read()
            if (character < 0) break
            if (character == '\n'.code) emit()
            else if (line.length < 16_384) line.append(character.toChar()) else oversized = true
        }
        emit()
    }
    return omitted
}

private fun DiagnosticsStatus.toSummaryJson(kind: String): String =
    JSONObject()
        .put("kind", kind)
        .put("schemaVersion", 1)
        .put("appVersion", BuildConfig.VERSION_NAME)
        .put("level", level.name)
        .put("optionalCapBytes", retention.maxOptionalBytes)
        .put("optionalAgeDays", retention.maxOptionalAgeDays)
        .put("essentialBytes", essentialBytes)
        .put("droppedRecords", droppedRecords)
        .put("writeFailures", writeFailures)
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
