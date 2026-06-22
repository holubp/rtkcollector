package org.rtkcollector.app.sessions

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import org.rtkcollector.app.recording.ReceiverNmeaReexporter
import org.rtkcollector.app.recording.SessionZipProgress
import org.rtkcollector.app.recording.SessionNmeaShareSelection
import org.rtkcollector.app.recording.SessionNmeaSource
import org.rtkcollector.core.session.SessionArtifactFile
import org.rtkcollector.receiver.unicore.Um980NmeaExportOptions
import org.rtkcollector.receiver.unicore.Um980NmeaReexportProgress
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SafSessionActions {
    fun createTemporaryShareZip(
        resolver: ContentResolver,
        sessionUri: Uri,
        cacheRoot: Path,
        onProgress: (SessionZipProgress) -> Unit = {},
    ): Path {
        Files.createDirectories(cacheRoot)
        val sessionName = sessionUri.displayName(resolver) ?: "saf-session"
        val output = cacheRoot.resolve("$sessionName-${System.currentTimeMillis()}.zip")
        exportSessionZip(
            resolver = resolver,
            sessionUri = sessionUri,
            outputZip = output,
            compressionLevel = Deflater.DEFAULT_COMPRESSION,
            onProgress = onProgress,
        )
        return output
    }

    fun createTemporaryNmeaShares(
        resolver: ContentResolver,
        sessionUri: Uri,
        cacheRoot: Path,
        requestedSources: Set<SessionNmeaSource>? = null,
        useLegacyReceiverName: Boolean = true,
    ): List<Path> {
        Files.createDirectories(cacheRoot)
        val sessionName = sessionUri.displayName(resolver) ?: "saf-session"
        val allowed = requestedSources ?: SessionNmeaSource.entries.toSet()
        val availableSources = SessionNmeaSource.entries.filter { source ->
            source in allowed && hasNonEmptyChild(resolver, sessionUri, source.artifactFileName)
        }
        val receiverOnly = availableSources == listOf(SessionNmeaSource.RECEIVER_SOLUTION)
        return availableSources.mapNotNull { source ->
            val child = sessionUri.findChild(resolver, source.artifactFileName) ?: return@mapNotNull null
            val outputName = SessionNmeaShareSelection.exportName(
                sessionName = sessionName,
                source = source,
                useLegacyReceiverName =
                    useLegacyReceiverName && receiverOnly && source == SessionNmeaSource.RECEIVER_SOLUTION,
            )
            val output = cacheRoot.resolve(outputName)
            resolver.openInputStream(child.uri)?.use { input ->
                Files.newOutputStream(output).use { fileOutput -> input.copyTo(fileOutput) }
            } ?: return@mapNotNull null
            output
        }
    }

    fun createTemporaryNmeaShare(
        resolver: ContentResolver,
        sessionUri: Uri,
        cacheRoot: Path,
    ): Path? {
        return createTemporaryNmeaShares(
            resolver = resolver,
            sessionUri = sessionUri,
            cacheRoot = cacheRoot,
            requestedSources = setOf(SessionNmeaSource.RECEIVER_SOLUTION),
            useLegacyReceiverName = true,
        ).singleOrNull()
    }

    fun reexportNmea(
        resolver: ContentResolver,
        sessionUri: Uri,
        receiverFamily: String?,
        options: Um980NmeaExportOptions = Um980NmeaExportOptions(),
        onProgress: (Um980NmeaReexportProgress) -> Unit = {},
    ): Long {
        val receiverRxRaw = sessionUri.findChild(resolver, SessionArtifactFile.RECEIVER_RX_RAW.fileName)
            ?: return 0L
        val temporary = createFile(
            resolver = resolver,
            parentUri = sessionUri,
            fileName = "${SessionArtifactFile.RECEIVER_SOLUTION_NMEA.fileName}.tmp",
            mimeType = "text/plain",
            replaceExisting = true,
        )
        return runCatching {
            val result = resolver.openInputStream(receiverRxRaw.uri).use { input ->
                if (input == null) {
                    runCatching { DocumentsContract.deleteDocument(resolver, temporary) }
                    return 0L
                }
                resolver.openOutputStream(temporary, "wt").use { output ->
                    if (output == null) {
                        runCatching { DocumentsContract.deleteDocument(resolver, temporary) }
                        return 0L
                    }
                    ReceiverNmeaReexporter.reexportReceiverRxRaw(
                        input = input,
                        output = output,
                        receiverFamily = receiverFamily,
                        totalBytes = receiverRxRaw.sizeBytes,
                        options = options,
                        onProgress = onProgress,
                    )
                }
            }
            if (result == 0L) {
                runCatching { DocumentsContract.deleteDocument(resolver, temporary) }
                return@runCatching 0L
            }

            sessionUri.findChild(resolver, SessionArtifactFile.RECEIVER_SOLUTION_NMEA.fileName)
                ?.let { DocumentsContract.deleteDocument(resolver, it.uri) }
            val renamed = runCatching {
                DocumentsContract.renameDocument(resolver, temporary, SessionArtifactFile.RECEIVER_SOLUTION_NMEA.fileName)
            }.getOrNull()
            if (renamed == null) {
                val finalUri = createFile(
                    resolver = resolver,
                    parentUri = sessionUri,
                    fileName = SessionArtifactFile.RECEIVER_SOLUTION_NMEA.fileName,
                    mimeType = "text/plain",
                    replaceExisting = true,
                )
                resolver.openInputStream(temporary)?.use { input ->
                    resolver.openOutputStream(finalUri, "wt")?.use { output -> input.copyTo(output) }
                }
                runCatching { DocumentsContract.deleteDocument(resolver, temporary) }
            }
            result
        }.onFailure {
            runCatching { DocumentsContract.deleteDocument(resolver, temporary) }
        }.getOrThrow()
    }

    fun hasNonEmptyChild(resolver: ContentResolver, sessionUri: Uri, fileName: String): Boolean =
        sessionUri.findChild(resolver, fileName)?.let { (it.sizeBytes ?: 0L) > 0L } == true

    fun archiveSession(
        resolver: ContentResolver,
        sessionUri: Uri,
        rootTreeUri: Uri,
        cacheRoot: Path,
        onProgress: (SessionZipProgress) -> Unit = {},
    ): Uri {
        Files.createDirectories(cacheRoot)
        val sessionName = sessionUri.displayName(resolver) ?: "saf-session"
        val temporaryZip = cacheRoot.resolve("$sessionName-${System.currentTimeMillis()}.zip")
        exportSessionZip(
            resolver = resolver,
            sessionUri = sessionUri,
            outputZip = temporaryZip,
            compressionLevel = Deflater.BEST_COMPRESSION,
            onProgress = onProgress,
        )
        require(SessionArchiveManager.verifyArchive(temporaryZip)) { "Archive verification failed: $temporaryZip" }
        val rootUri = rootTreeUri.rootDocumentUri()
        val archiveUri = createFile(
            resolver = resolver,
            parentUri = rootUri,
            fileName = uniqueSafName(resolver, rootUri, "$sessionName.zip"),
            mimeType = "application/zip",
            replaceExisting = false,
        )
        Files.newInputStream(temporaryZip).use { input ->
            resolver.openOutputStream(archiveUri, "wt")?.use { output -> input.copyTo(output) }
                ?: error("Unable to write SAF archive.")
        }
        Files.deleteIfExists(temporaryZip)
        deleteRecording(resolver, sessionUri)
        return archiveUri
    }

    fun restoreArchive(
        resolver: ContentResolver,
        archiveUri: Uri,
        rootTreeUri: Uri,
    ): Uri {
        val archiveName = archiveUri.displayName(resolver)?.removeSuffix(".zip") ?: "restored-session"
        val rootUri = rootTreeUri.rootDocumentUri()
        val sessionUri = DocumentsContract.createDocument(
            resolver,
            rootUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            uniqueSafName(resolver, rootUri, archiveName),
        ) ?: error("Unable to create restored SAF session folder.")
        runCatching {
            resolver.openInputStream(archiveUri)?.use { rawInput ->
                ZipInputStream(rawInput).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(!entry.name.contains("..")) { "Archive entry escapes session folder: ${entry.name}" }
                        if (!entry.isDirectory) {
                            val fileName = entry.name.substringAfterLast('/')
                            val fileUri = createFile(
                                resolver = resolver,
                                parentUri = sessionUri,
                                fileName = fileName,
                                mimeType = mimeTypeFor(fileName),
                                replaceExisting = true,
                            )
                            resolver.openOutputStream(fileUri, "wt")?.use { output -> zip.copyTo(output) }
                                ?: error("Unable to write restored SAF file: $fileName")
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("Unable to read SAF archive.")
            require(sessionUri.looksLikeSessionDirectory(resolver)) { "Restored SAF archive does not look like a session." }
        }.onFailure {
            deleteRecording(resolver, sessionUri)
            throw it
        }
        DocumentsContract.deleteDocument(resolver, archiveUri)
        return sessionUri
    }

    fun deleteRecording(resolver: ContentResolver, uri: Uri) {
        uri.children(resolver).forEach { child ->
            if (child.isDirectory) {
                deleteRecording(resolver, child.uri)
            } else {
                DocumentsContract.deleteDocument(resolver, child.uri)
            }
        }
        DocumentsContract.deleteDocument(resolver, uri)
    }

    fun deleteArchive(resolver: ContentResolver, uri: Uri) {
        DocumentsContract.deleteDocument(resolver, uri)
    }

    private fun exportSessionZip(
        resolver: ContentResolver,
        sessionUri: Uri,
        outputZip: Path,
        compressionLevel: Int,
        onProgress: (SessionZipProgress) -> Unit,
    ) {
        val files = sessionUri.sessionFiles(resolver)
        onProgress(SessionZipProgress(filesCompleted = 0, totalFiles = files.size))
        ZipOutputStream(Files.newOutputStream(outputZip)).use { zip ->
            zip.setLevel(compressionLevel)
            files.forEachIndexed { index, file ->
                zip.putNextEntry(ZipEntry(file.relativeName))
                resolver.openInputStream(file.uri)?.use { input -> input.copyTo(zip) }
                    ?: error("Unable to read SAF session file: ${file.relativeName}")
                zip.closeEntry()
                onProgress(
                    SessionZipProgress(
                        filesCompleted = index + 1,
                        totalFiles = files.size,
                        currentFile = file.relativeName,
                    ),
                )
            }
        }
    }

    private fun Uri.sessionFiles(resolver: ContentResolver, prefix: String = ""): List<SafSessionFile> =
        children(resolver).flatMap { child ->
            val relativeName = if (prefix.isBlank()) child.displayName else "$prefix/${child.displayName}"
            if (child.isDirectory) {
                child.uri.sessionFiles(resolver, relativeName)
            } else {
                listOf(SafSessionFile(child.uri, relativeName, child.sizeBytes))
            }
        }.sortedBy(SafSessionFile::relativeName)

    private fun createFile(
        resolver: ContentResolver,
        parentUri: Uri,
        fileName: String,
        mimeType: String,
        replaceExisting: Boolean,
    ): Uri {
        if (replaceExisting) {
            parentUri.findChild(resolver, fileName)?.let { DocumentsContract.deleteDocument(resolver, it.uri) }
        }
        return DocumentsContract.createDocument(resolver, parentUri, mimeType, fileName)
            ?: error("Unable to create SAF file: $fileName")
    }

    private fun uniqueSafName(resolver: ContentResolver, parentUri: Uri, requestedName: String): String {
        val existing = parentUri.children(resolver).map { it.displayName }.toSet()
        if (requestedName !in existing) return requestedName
        val base = requestedName.substringBeforeLast('.', requestedName)
        val suffix = requestedName.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it != requestedName }
            ?.let { ".$it" }
            .orEmpty()
        var index = 2
        while (true) {
            val candidate = "$base-$index$suffix"
            if (candidate !in existing) return candidate
            index++
        }
    }
}

private data class SafSessionFile(
    val uri: Uri,
    val relativeName: String,
    val sizeBytes: Long?,
)

private fun mimeTypeFor(fileName: String): String =
    when {
        fileName.endsWith(".json") || fileName.endsWith(".jsonl") -> "application/json"
        fileName.endsWith(".txt") || fileName.endsWith(".nmea") -> "text/plain"
        fileName.endsWith(".zip") -> "application/zip"
        else -> "application/octet-stream"
    }
