package org.rtkcollector.app.sessions

import org.rtkcollector.app.recording.SessionZipExporter
import org.rtkcollector.app.recording.SessionZipPlan
import org.rtkcollector.app.recording.SessionZipProgress
import org.rtkcollector.core.session.ArchiveIntegrity
import org.rtkcollector.core.session.ArchiveManifest
import org.rtkcollector.core.session.ArchiveIntegrityPolicy
import org.rtkcollector.core.session.ArchiveSourceFile
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.zip.Deflater
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name

object SessionArchiveManager {
    fun createTemporaryShareZip(
        sessionDirectory: Path,
        cacheRoot: Path,
        integrityPolicy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
        onProgress: (SessionZipProgress) -> Unit = {},
    ): Path = ActiveRecordingSessionRegistry.withDestructiveOperation(sessionDirectory.toString(), "share") {
        require(Files.isDirectory(sessionDirectory)) { "Share source must be a session directory." }
        Files.createDirectories(cacheRoot)
        val output = cacheRoot.resolve("${sessionDirectory.name}-${System.currentTimeMillis()}.zip")
        val plan = SessionZipPlan.fromSessionDirectory(sessionDirectory, output)
        requireSafeSourceFiles(sessionDirectory, plan.files, integrityPolicy)
        ActiveRecordingSessionRegistry.requireInactive(sessionDirectory.toString(), "share")
        try {
            SessionZipExporter.export(
                plan = plan,
                onProgress = onProgress,
            ).also { verifyArchiveAgainstDirectory(it, sessionDirectory, integrityPolicy) }
        } catch (error: Throwable) {
            runCatching { output.deleteIfExists() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
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
        integrityPolicy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
        onProgress: (SessionZipProgress) -> Unit = {},
    ): Path = ActiveRecordingSessionRegistry.withDestructiveOperation(sessionDirectory.toString(), "archive") {
        require(Files.isDirectory(sessionDirectory)) { "Archive source must be a session directory." }
        requireArchiveRootOutsideSession(sessionDirectory, archiveRoot)
        val output = uniqueArchivePath(archiveRoot, sessionDirectory.name)
        val plan = SessionZipPlan.fromSessionDirectory(sessionDirectory, output)
        requireSafeSourceFiles(sessionDirectory, plan.files, integrityPolicy)
        val archive = try {
            SessionZipExporter.export(
                plan = plan,
                compressionLevel = Deflater.BEST_COMPRESSION,
                onProgress = onProgress,
            ).also { verifyArchiveAgainstDirectory(it, sessionDirectory, integrityPolicy) }
        } catch (error: Throwable) {
            runCatching { output.deleteIfExists() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
        // Keep the verified archive if recursive source deletion itself fails partway through.
        FilesystemSessionBrowser.deleteRecordingWhileDestructiveOperationIsLeased(sessionDirectory)
        archive
    }

    fun restoreArchive(
        archive: Path,
        restoreRoot: Path = archive.parent,
        integrityPolicy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
    ): Path {
        require(Files.isRegularFile(archive)) { "Restore source must be an archive file." }
        val manifest = Files.newInputStream(archive).use { input ->
            ArchiveIntegrity.requireSessionArchive(ArchiveIntegrity.inspect(input, integrityPolicy))
        }
        requireAvailableRestoreSpace(restoreRoot, manifest)
        val sessionName = archive.name.removeSuffix(".zip")
        val destination = uniqueDirectory(restoreRoot, sessionName)
        Files.createDirectories(destination)
        try {
            Files.newInputStream(archive).use { input ->
                ArchiveIntegrity.extract(
                    input = input,
                    openOutput = { relativePath ->
                        val target = destination.resolve(relativePath).normalize()
                        require(target.startsWith(destination)) { "Archive entry escapes destination: $relativePath" }
                        target.parent?.let(Files::createDirectories)
                        Files.newOutputStream(target)
                    },
                    policy = integrityPolicy,
                )
            }
            ArchiveIntegrity.verifySourcesAgainstManifest(sessionFiles(destination), manifest, integrityPolicy)
        } catch (error: Throwable) {
            runCatching { FilesystemSessionBrowser.deleteRecording(destination) }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            throw error
        }
        FilesystemSessionBrowser.deleteArchive(archive)
        return destination
    }

    fun verifyArchive(
        archive: Path,
        integrityPolicy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
    ): Boolean {
        if (!Files.isRegularFile(archive)) return false
        return runCatching {
            Files.newInputStream(archive).use { input ->
                ArchiveIntegrity.requireSessionArchive(ArchiveIntegrity.inspect(input, integrityPolicy))
            }
        }.isSuccess
    }

    private fun verifyArchiveAgainstDirectory(
        archive: Path,
        sessionDirectory: Path,
        integrityPolicy: ArchiveIntegrityPolicy,
    ) {
        Files.newInputStream(archive).use { input ->
            ArchiveIntegrity.requireSessionArchive(
                ArchiveIntegrity.verifyArchiveAgainstSources(input, sessionFiles(sessionDirectory), integrityPolicy),
            )
        }
    }

    private fun sessionFiles(sessionDirectory: Path): List<ArchiveSourceFile> {
        val files = mutableListOf<Path>()
        Files.walk(sessionDirectory).use { stream ->
            stream.forEach { path ->
                require(!Files.isSymbolicLink(path)) { "Session archive source contains a symbolic link: $path" }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) files.add(path)
            }
        }
        return files.sorted().map { file ->
            ArchiveSourceFile(
                relativePath = sessionDirectory.relativize(file).toZipPath(),
                openInput = { Files.newInputStream(file) },
            )
        }
    }

    private fun requireSafeSourceFiles(
        sessionDirectory: Path,
        files: Collection<Path>,
        integrityPolicy: ArchiveIntegrityPolicy,
    ) {
        require(!Files.isSymbolicLink(sessionDirectory)) {
            "Session archive source must not be a symbolic link: $sessionDirectory"
        }
        require(files.size <= integrityPolicy.maxEntries) {
            "Session file count exceeds ${integrityPolicy.maxEntries}."
        }
        val source = sessionDirectory.toRealPath()
        var totalBytes = 0L
        files.forEach { file ->
            require(!Files.isSymbolicLink(file) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                "Session archive source is not a regular file: $file"
            }
            require(file.toRealPath().startsWith(source)) { "Session archive source escapes the session directory: $file" }
            ArchiveIntegrity.pathComponents(sessionDirectory.relativize(file).toZipPath(), integrityPolicy)
            val sizeBytes = Files.size(file)
            require(sizeBytes <= integrityPolicy.maxEntryUncompressedBytes) {
                "Session file exceeds per-entry archive limit: $file"
            }
            require(totalBytes <= integrityPolicy.maxTotalUncompressedBytes - sizeBytes) {
                "Session files exceed cumulative archive limit ${integrityPolicy.maxTotalUncompressedBytes}."
            }
            totalBytes += sizeBytes
        }
    }

    private fun requireArchiveRootOutsideSession(sessionDirectory: Path, archiveRoot: Path) {
        Files.createDirectories(archiveRoot)
        val session = sessionDirectory.toRealPath()
        val root = archiveRoot.toRealPath()
        require(!root.startsWith(session)) { "Archive destination must be outside the source session directory." }
    }

    private fun requireAvailableRestoreSpace(restoreRoot: Path, manifest: ArchiveManifest) {
        Files.createDirectories(restoreRoot)
        val requiredBytes = manifest.entries.fold(0L) { total, entry ->
            require(entry.sizeBytes >= 0L && total <= Long.MAX_VALUE - entry.sizeBytes) {
                "Archive restore size overflows."
            }
            total + entry.sizeBytes
        }
        val availableBytes = Files.getFileStore(restoreRoot).usableSpace
        require(availableBytes >= requiredBytes) {
            "Insufficient storage to restore archive: $requiredBytes bytes required, $availableBytes bytes available."
        }
    }

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

private fun Path.toZipPath(): String = iterator().asSequence().joinToString("/") { it.toString() }
