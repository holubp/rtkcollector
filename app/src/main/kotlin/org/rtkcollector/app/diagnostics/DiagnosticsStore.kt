package org.rtkcollector.app.diagnostics

import java.io.File

class DiagnosticsStore(
    private val appFilesDir: File,
    private val maxRuntimeBytes: Long = 1_000_000L,
    private val maxPerformanceBytes: Long = 1_000_000L,
) {
    private val diagnosticsDir: File = appFilesDir.resolve("diagnostics")
    private val runtimeDir: File = diagnosticsDir.resolve("runtime")
    private val performanceDir: File = diagnosticsDir.resolve("performance")
    private val runtimeFile: File = runtimeDir.resolve("runtime.jsonl")
    private val performanceFile: File = performanceDir.resolve("performance.jsonl")

    @Synchronized
    fun appendRuntime(enabled: Boolean, record: RuntimeDiagnosticRecord) {
        if (!enabled) return
        appendBounded(runtimeDir, runtimeFile, record.toJsonLine(), maxRuntimeBytes)
    }

    @Synchronized
    fun appendPerformance(enabled: Boolean, sample: PerformanceDiagnosticSample) {
        if (!enabled) return
        appendBounded(performanceDir, performanceFile, sample.toJsonLine(), maxPerformanceBytes)
    }

    @Synchronized
    fun status(
        runtimeEnabled: Boolean,
        performanceEnabled: Boolean,
    ): DiagnosticsStatus =
        DiagnosticsStatus(
            runtimeLoggingEnabled = runtimeEnabled,
            performanceMonitoringEnabled = performanceEnabled,
            runtimeBytes = runtimeDir.totalBytes(),
            performanceBytes = performanceDir.totalBytes(),
            runtimeFiles = runtimeDir.fileCount(),
            performanceFiles = performanceDir.fileCount(),
        )

    @Synchronized
    fun runtimeFiles(): List<File> = runtimeDir.listFiles()?.filter(File::isFile).orEmpty()

    @Synchronized
    fun performanceFiles(): List<File> = performanceDir.listFiles()?.filter(File::isFile).orEmpty()

    @Synchronized
    fun deleteDiagnostics() {
        runtimeDir.deleteRecursively()
        performanceDir.deleteRecursively()
    }

    private fun appendBounded(directory: File, file: File, line: String, maxBytes: Long) {
        directory.mkdirs()
        val maxBytesInt = maxBytes.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()

        if (file.length() + line.length.toLong() + 1L <= maxBytes) {
            file.appendText(line)
            file.appendText("\n")
            return
        }

        val existingText = if (file.exists()) file.readText(Charsets.UTF_8) else ""
        val combined = existingText + line + "\n"
        val keepFrom = (combined.length - maxBytesInt).coerceAtLeast(0)
        if (combined.length <= maxBytesInt) {
            file.writeText(combined, Charsets.UTF_8)
            return
        }

        val tail = combined.substring(keepFrom)
        val firstNewline = tail.indexOf('\n')
        val normalizedTail = if (firstNewline >= 0) {
            tail.substring(firstNewline + 1)
        } else {
            tail
        }
        if (normalizedTail.isEmpty()) {
            file.writeText("$line\n")
        } else {
            file.writeText(normalizedTail, Charsets.UTF_8)
        }
    }
}

private fun File.totalBytes(): Long =
    walkExistingFiles().sumOf(File::length)

private fun File.fileCount(): Int =
    walkExistingFiles().count()

private fun File.walkExistingFiles(): Sequence<File> =
    if (exists()) walkTopDown().filter(File::isFile) else emptySequence()
