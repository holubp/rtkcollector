package org.rtkcollector.app.sessions

import org.rtkcollector.app.recording.SessionZipExporter
import org.rtkcollector.app.recording.SessionZipPlan
import org.rtkcollector.app.recording.SessionZipProgress
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater
import java.util.zip.ZipInputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name

object SessionArchiveManager {
    fun createTemporaryShareZip(
        sessionDirectory: Path,
        cacheRoot: Path,
        onProgress: (SessionZipProgress) -> Unit = {},
    ): Path {
        require(Files.isDirectory(sessionDirectory)) { "Share source must be a session directory." }
        Files.createDirectories(cacheRoot)
        val output = cacheRoot.resolve("${sessionDirectory.name}-${System.currentTimeMillis()}.zip")
        return SessionZipExporter.export(
            plan = SessionZipPlan.fromSessionDirectory(sessionDirectory, output),
            onProgress = onProgress,
        )
    }

    fun cleanupTemporaryShareZips(cacheRoot: Path) {
        if (!Files.isDirectory(cacheRoot)) return
        Files.list(cacheRoot).use { stream ->
            stream
                .filter { it.name.endsWith(".zip") }
                .forEach { it.deleteIfExists() }
        }
    }

    fun archiveSession(
        sessionDirectory: Path,
        archiveRoot: Path = sessionDirectory.parent,
        onProgress: (SessionZipProgress) -> Unit = {},
    ): Path {
        require(Files.isDirectory(sessionDirectory)) { "Archive source must be a session directory." }
        val output = uniqueArchivePath(archiveRoot, sessionDirectory.name)
        val archive = SessionZipExporter.export(
            plan = SessionZipPlan.fromSessionDirectory(sessionDirectory, output),
            compressionLevel = Deflater.BEST_COMPRESSION,
            onProgress = onProgress,
        )
        require(verifyArchive(archive)) { "Archive verification failed: $archive" }
        FilesystemSessionBrowser.deleteRecording(sessionDirectory)
        return archive
    }

    fun restoreArchive(
        archive: Path,
        restoreRoot: Path = archive.parent,
    ): Path {
        require(Files.isRegularFile(archive)) { "Restore source must be an archive file." }
        require(verifyArchive(archive)) { "Archive verification failed: $archive" }
        val sessionName = archive.name.removeSuffix(".zip")
        val destination = uniqueDirectory(restoreRoot, sessionName)
        Files.createDirectories(destination)
        runCatching {
            ZipInputStream(Files.newInputStream(archive)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val target = destination.resolve(entry.name).normalize()
                    require(target.startsWith(destination)) { "Archive entry escapes destination: ${entry.name}" }
                    if (entry.isDirectory) {
                        Files.createDirectories(target)
                    } else {
                        target.parent?.let(Files::createDirectories)
                        Files.newOutputStream(target).use { output -> input.copyTo(output) }
                    }
                    input.closeEntry()
                }
            }
            require(looksRestored(destination)) { "Restored archive does not look like a session." }
        }.onFailure {
            FilesystemSessionBrowser.deleteRecording(destination)
            throw it
        }
        archive.deleteIfExists()
        return destination
    }

    fun verifyArchive(archive: Path): Boolean {
        if (!Files.isRegularFile(archive)) return false
        var regularFiles = 0
        var hasSessionJson = false
        var hasReceiverRx = false
        return runCatching {
            ZipInputStream(Files.newInputStream(archive)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (!entry.isDirectory) {
                        regularFiles++
                        hasSessionJson = hasSessionJson || entry.name == "session.json"
                        hasReceiverRx = hasReceiverRx || entry.name == "receiver-rx.raw"
                    }
                    input.closeEntry()
                }
            }
            regularFiles > 0 && (hasSessionJson || hasReceiverRx)
        }.getOrDefault(false)
    }

    private fun looksRestored(sessionDirectory: Path): Boolean =
        Files.exists(sessionDirectory.resolve("session.json")) ||
            Files.exists(sessionDirectory.resolve("receiver-rx.raw"))

    private fun uniqueArchivePath(root: Path, baseName: String): Path {
        Files.createDirectories(root)
        var candidate = root.resolve("$baseName.zip")
        var index = 2
        while (Files.exists(candidate)) {
            candidate = root.resolve("$baseName-$index.zip")
            index++
        }
        return candidate
    }

    private fun uniqueDirectory(root: Path, baseName: String): Path {
        Files.createDirectories(root)
        var candidate = root.resolve(baseName)
        var index = 2
        while (Files.exists(candidate)) {
            candidate = root.resolve("$baseName-restored-$index")
            index++
        }
        return candidate
    }
}
