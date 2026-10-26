package org.rtkcollector.app.diagnostics

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DiagnosticsStore(
    private val appFilesDir: File,
    private val maxRuntimeBytes: Long = 10_000_000L,
    private val maxPerformanceBytes: Long = 1_000_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retentionProvider: (() -> DiagnosticsRetention)? = null,
) {
    private val diagnosticsDir: File = appFilesDir.resolve("diagnostics")
    private val runtimeDir: File = diagnosticsDir.resolve("runtime")
    private val essentialDir: File = diagnosticsDir.resolve("essential")
    private val performanceDir: File = diagnosticsDir.resolve("performance")
    private val runtimeFile: File = runtimeDir.resolve("runtime.jsonl")
    private val performanceFile: File = performanceDir.resolve("performance.jsonl")
    private val shared = stores.computeIfAbsent(diagnosticsDir.absolutePath) { SharedState() }
    private var retention = DiagnosticsRetention(maxRuntimeBytes)

    fun appendRuntime(enabled: Boolean, record: RuntimeDiagnosticRecord) = synchronized(shared) {
        if (enabled) {
            retention = retentionProvider?.invoke() ?: retention
            appendBounded(runtimeDir, runtimeFile, record.toJsonLine(), retention.maxOptionalBytes, retention.maxOptionalAgeDays)
        }
    }

    fun appendPerformance(enabled: Boolean, sample: PerformanceDiagnosticSample) = synchronized(shared) {
        if (enabled) appendBounded(performanceDir, performanceFile, sample.toJsonLine(), maxPerformanceBytes)
    }

    fun appendEssential(record: RuntimeDiagnosticRecord) = appendEssentialJson(record.toJsonLine())

    fun appendEssentialJson(json: String) = synchronized(shared) {
        appendBounded(essentialDir, essentialDir.resolve("essential.jsonl"), json, 1_000_000L)
    }

    fun applyRetention(policy: DiagnosticsRetention) = synchronized(shared) {
        retention = policy
        prune(runtimeDir, policy.maxOptionalBytes, policy.maxOptionalAgeDays)
    }

    fun status(
        runtimeEnabled: Boolean,
        performanceEnabled: Boolean,
        level: DiagnosticLevel = if (runtimeEnabled) DiagnosticLevel.VERBOSE else DiagnosticLevel.MINIMAL,
    ): DiagnosticsStatus = synchronized(shared) {
        DiagnosticsStatus(
            runtimeLoggingEnabled = runtimeEnabled,
            performanceMonitoringEnabled = performanceEnabled,
            runtimeBytes = runtimeDir.totalBytes(),
            performanceBytes = performanceDir.totalBytes(),
            runtimeFiles = runtimeDir.fileCount(),
            performanceFiles = performanceDir.fileCount(),
            level = level,
            retention = retentionProvider?.invoke() ?: retention,
            essentialBytes = essentialDir.totalBytes(),
            essentialFiles = essentialDir.fileCount(),
            droppedRecords = shared.dropped.get(),
            writeFailures = shared.failures.get(),
        )
    }

    fun runtimeFiles(): List<File> = synchronized(shared) { files(runtimeDir) }

    fun performanceFiles(): List<File> = synchronized(shared) { files(performanceDir) }

    fun deleteDiagnostics() = synchronized(shared) {
        listOf(runtimeDir, essentialDir, performanceDir).forEach {
            if (it.exists()) check(it.deleteRecursively()) { "Unable to delete diagnostic logs." }
        }
    }

    // Copy bounded files before ZIP streaming; no raw-capture code uses this lock.
    fun snapshot(destination: File, performance: Boolean): List<Pair<String, File>> = synchronized(shared) {
        check(destination.mkdirs() || destination.isDirectory)
        val dirs = if (performance) listOf(performanceDir) else listOf(essentialDir, runtimeDir)
        dirs.flatMap { dir -> files(dir).map { file ->
            val name = "${dir.name}/${file.name}"
            val copy = destination.resolve(name)
            val parent = checkNotNull(copy.parentFile)
            check(parent.mkdirs() || parent.isDirectory)
            file.copyTo(copy, overwrite = true)
            name to copy
        } }
    }

    fun noteDropped() { shared.dropped.incrementAndGet() }
    fun noteWriteFailure() { shared.failures.incrementAndGet() }

    private fun appendBounded(directory: File, file: File, line: String, maxBytes: Long, days: Int = Int.MAX_VALUE) {
        check(directory.mkdirs() || directory.isDirectory)
        prune(directory, maxBytes, days)
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        val fileCap = (maxBytes / 4).coerceAtLeast(minOf(maxBytes, 1024L))
        if (bytes.size > minOf(fileCap, 16_384)) { noteDropped(); return }
        if (file.length() + bytes.size > fileCap) {
            val name = file.nameWithoutExtension
            val oldest = directory.resolve("$name-3.jsonl")
            if (oldest.exists()) check(oldest.delete())
            for (index in 2 downTo 1) {
                val old = directory.resolve("$name-$index.jsonl")
                if (old.exists()) check(old.renameTo(directory.resolve("$name-${index + 1}.jsonl")))
            }
            if (file.exists()) check(file.renameTo(directory.resolve("$name-1.jsonl")))
        }
        file.appendBytes(bytes)
        check(file.setLastModified(clock()))
        prune(directory, maxBytes, days)
    }

    private fun prune(directory: File, cap: Long, days: Int) {
        files(directory).filter { clock() - it.lastModified() > days * 86_400_000L }
            .forEach { check(it.delete()) { "Unable to expire diagnostic log." } }
        val ordered = files(directory).sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
        var total = ordered.sumOf(File::length)
        ordered.forEach { if (total > cap) { val size = it.length(); check(it.delete()); total -= size } }
    }

    private fun files(directory: File): List<File> = directory.listFiles()?.filter(File::isFile).orEmpty()
    private class SharedState { val dropped = AtomicLong(); val failures = AtomicLong() }
    companion object { private val stores = ConcurrentHashMap<String, SharedState>() }
}

private fun File.totalBytes(): Long =
    walkExistingFiles().sumOf(File::length)

private fun File.fileCount(): Int =
    walkExistingFiles().count()

private fun File.walkExistingFiles(): Sequence<File> =
    if (exists()) walkTopDown().filter(File::isFile) else emptySequence()
