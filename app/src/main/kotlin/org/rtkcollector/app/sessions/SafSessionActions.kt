package org.rtkcollector.app.sessions

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import org.rtkcollector.app.recording.ReceiverNmeaReexporter
import org.rtkcollector.app.recording.SessionZipProgress
import org.rtkcollector.app.recording.SessionNmeaShareSelection
import org.rtkcollector.app.recording.SessionNmeaSource
import org.rtkcollector.core.session.ArchiveIntegrity
import org.rtkcollector.core.session.ArchiveIntegrityPolicy
import org.rtkcollector.core.session.ArchiveSourceFile
import org.rtkcollector.core.session.SessionArtifactFile
import org.rtkcollector.receiver.unicore.Um980NmeaExportOptions
import org.rtkcollector.receiver.unicore.Um980NmeaReexportProgress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SafSessionActions {
    fun createTemporaryShareZip(
        resolver: ContentResolver,
        sessionUri: Uri,
        cacheRoot: Path,
        onProgress: (SessionZipProgress) -> Unit = {},
    ): Path = ActiveRecordingSessionRegistry.withDestructiveOperation(sessionUri.toString(), "share") {
        Files.createDirectories(cacheRoot)
        val sessionName = sessionUri.displayName(resolver) ?: "saf-session"
        val output = cacheRoot.resolve("$sessionName-${System.currentTimeMillis()}.zip")
        try {
            exportSessionZip(
                resolver = resolver,
                sessionUri = sessionUri,
                outputZip = output,
                compressionLevel = Deflater.DEFAULT_COMPRESSION,
                onProgress = onProgress,
            )
            output
        } catch (error: Throwable) {
            runCatching { Files.deleteIfExists(output) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    fun createTemporaryNmeaShares(
        resolver: ContentResolver,
        sessionUri: Uri,
        cacheRoot: Path,
        requestedSources: Set<SessionNmeaSource>? = null,
        useLegacyReceiverName: Boolean = true,
    ): List<Path> = ActiveRecordingSessionRegistry.withDestructiveOperation(sessionUri.toString(), "share") {
        Files.createDirectories(cacheRoot)
        val sessionName = sessionUri.displayName(resolver) ?: "saf-session"
        val allowed = requestedSources ?: SessionNmeaSource.entries.toSet()
        val availableSources = SessionNmeaSource.entries.filter { source ->
            source in allowed && hasNonEmptyChild(resolver, sessionUri, source.artifactFileName)
        }
        val receiverOnly = availableSources == listOf(SessionNmeaSource.RECEIVER_SOLUTION)
        val outputs = mutableListOf<Path>()
        val temporaries = mutableListOf<Path>()
        try {
            availableSources.map { source ->
                ActiveRecordingSessionRegistry.requireInactive(sessionUri.toString(), "share")
                val child = sessionUri.findChild(resolver, source.artifactFileName)
                    ?: error("SAF NMEA source disappeared before sharing: ${source.artifactFileName}")
                val outputName = SessionNmeaShareSelection.exportName(
                    sessionName = sessionName,
                    source = source,
                    useLegacyReceiverName =
                        useLegacyReceiverName && receiverOnly && source == SessionNmeaSource.RECEIVER_SOLUTION,
                )
                val output = cacheRoot.resolve(outputName)
                val temporary = Files.createTempFile(cacheRoot, outputName, ".tmp")
                temporaries.add(temporary)
                ActiveRecordingSessionRegistry.requireInactive(sessionUri.toString(), "share")
                val input = resolver.openInputStream(child.uri)
                    ?: error("Unable to open SAF NMEA source for sharing: ${source.artifactFileName}")
                input.use { stream ->
                    Files.newOutputStream(temporary).use { fileOutput -> stream.copyTo(fileOutput) }
                }
                Files.move(temporary, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                temporaries.remove(temporary)
                outputs.add(output)
                output
            }
        } catch (error: Throwable) {
            (outputs + temporaries).forEach { path ->
                runCatching { Files.deleteIfExists(path) }.exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
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
            require(documentDigest(resolver, temporary).sizeBytes > 0L) {
                "Generated SAF NMEA replacement is empty."
            }
            replaceNmeaPreservingPreviousExport(resolver, sessionUri, temporary)
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
        integrityPolicy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
        onProgress: (SessionZipProgress) -> Unit = {},
    ): Uri = ActiveRecordingSessionRegistry.withDestructiveOperation(sessionUri.toString(), "archive") {
        Files.createDirectories(cacheRoot)
        val sessionName = sessionUri.displayName(resolver) ?: "saf-session"
        val temporaryZip = cacheRoot.resolve("$sessionName-${System.currentTimeMillis()}.zip")
        try {
            exportSessionZip(
                resolver = resolver,
                sessionUri = sessionUri,
                outputZip = temporaryZip,
                compressionLevel = Deflater.BEST_COMPRESSION,
                onProgress = onProgress,
                integrityPolicy = integrityPolicy,
            )
            verifyArchiveAgainstSession(resolver, temporaryZip, sessionUri, integrityPolicy)
            val rootUri = rootTreeUri.rootDocumentUri()
            val archiveUri = createFile(
                resolver = resolver,
                parentUri = rootUri,
                fileName = uniqueSafName(resolver, rootUri, "$sessionName.zip"),
                mimeType = "application/zip",
                replaceExisting = false,
            )
            try {
                Files.newInputStream(temporaryZip).use { input ->
                    resolver.openOutputStream(archiveUri, "wt")?.use { output -> input.copyTo(output) }
                        ?: error("Unable to write SAF archive.")
                }
                resolver.openInputStream(archiveUri)?.use { input ->
                    ArchiveIntegrity.requireSessionArchive(
                        ArchiveIntegrity.verifyArchiveAgainstSources(
                            input,
                            sessionSources(resolver, sessionUri, integrityPolicy),
                            integrityPolicy,
                        ),
                    )
                } ?: error("Unable to re-open SAF archive for verification.")
            } catch (error: Throwable) {
                runCatching { DocumentsContract.deleteDocument(resolver, archiveUri) }
                throw error
            }
            deleteRecordingWhileDestructiveOperationIsLeased(resolver, sessionUri)
            archiveUri
        } finally {
            Files.deleteIfExists(temporaryZip)
        }
    }

    fun restoreArchive(
        resolver: ContentResolver,
        archiveUri: Uri,
        rootTreeUri: Uri,
        integrityPolicy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
    ): Uri {
        ActiveRecordingSessionRegistry.requireInactive(archiveUri.toString(), "restore")
        val archiveName = archiveUri.displayName(resolver)?.removeSuffix(".zip") ?: "restored-session"
        val rootUri = rootTreeUri.rootDocumentUri()
        val manifest = resolver.openInputStream(archiveUri)?.use { input ->
            ArchiveIntegrity.requireSessionArchive(ArchiveIntegrity.inspect(input, integrityPolicy))
        }
            ?: error("Unable to inspect SAF archive.")
        val sessionUri = DocumentsContract.createDocument(
            resolver,
            rootUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            uniqueSafName(resolver, rootUri, archiveName),
        ) ?: error("Unable to create restored SAF session folder.")
        try {
            resolver.openInputStream(archiveUri)?.use { input ->
                ArchiveIntegrity.extract(
                    input = input,
                    openOutput = { relativePath ->
                        val fileUri = createSafFileForRelativePath(
                            resolver,
                            sessionUri,
                            relativePath,
                            integrityPolicy,
                        )
                        resolver.openOutputStream(fileUri, "wt")
                            ?: error("Unable to write restored SAF file: $relativePath")
                    },
                    policy = integrityPolicy,
                )
            } ?: error("Unable to read SAF archive.")
            ArchiveIntegrity.verifySourcesAgainstManifest(
                sessionSources(resolver, sessionUri, integrityPolicy),
                manifest,
                integrityPolicy,
            )
        } catch (error: Throwable) {
            runCatching { deleteRecording(resolver, sessionUri) }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            throw error
        }
        deleteArchive(resolver, archiveUri)
        return sessionUri
    }

    fun deleteRecording(resolver: ContentResolver, uri: Uri) {
        ActiveRecordingSessionRegistry.withDestructiveOperation(uri.toString(), "delete") {
            deleteRecordingWhileDestructiveOperationIsLeased(resolver, uri)
        }
    }

    private fun deleteRecordingWhileDestructiveOperationIsLeased(resolver: ContentResolver, uri: Uri) {
        deleteDocumentTree(resolver, uri)
    }

    private fun deleteDocumentTree(resolver: ContentResolver, uri: Uri) {
        uri.children(resolver).forEach { child ->
            if (child.isDirectory) {
                deleteDocumentTree(resolver, child.uri)
            } else {
                require(DocumentsContract.deleteDocument(resolver, child.uri)) {
                    "SAF provider did not delete document: ${child.uri}"
                }
            }
        }
        require(DocumentsContract.deleteDocument(resolver, uri)) {
            "SAF provider did not delete document: $uri"
        }
    }

    fun deleteArchive(resolver: ContentResolver, uri: Uri) {
        ActiveRecordingSessionRegistry.withDestructiveOperation(uri.toString(), "delete") {
            require(DocumentsContract.deleteDocument(resolver, uri)) {
                "SAF provider did not delete archive: $uri"
            }
        }
    }

    private fun exportSessionZip(
        resolver: ContentResolver,
        sessionUri: Uri,
        outputZip: Path,
        compressionLevel: Int,
        onProgress: (SessionZipProgress) -> Unit,
        integrityPolicy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
    ) {
        val files = sessionUri.sessionFiles(resolver, integrityPolicy)
        require(files.size <= integrityPolicy.maxEntries) {
            "SAF session file count exceeds ${integrityPolicy.maxEntries}."
        }
        ActiveRecordingSessionRegistry.requireInactive(sessionUri.toString(), "share")
        onProgress(SessionZipProgress(filesCompleted = 0, totalFiles = files.size))
        var totalBytes = 0L
        ZipOutputStream(Files.newOutputStream(outputZip)).use { zip ->
            zip.setLevel(compressionLevel)
            files.forEachIndexed { index, file ->
                ArchiveIntegrity.pathComponents(file.relativeName, integrityPolicy)
                file.sizeBytes?.let { sizeBytes ->
                    require(sizeBytes <= integrityPolicy.maxEntryUncompressedBytes) {
                        "SAF session file exceeds per-entry archive limit: ${file.relativeName}"
                    }
                    require(totalBytes <= integrityPolicy.maxTotalUncompressedBytes - sizeBytes) {
                        "SAF session files exceed cumulative archive limit ${integrityPolicy.maxTotalUncompressedBytes}."
                    }
                }
                zip.putNextEntry(ZipEntry(file.relativeName))
                val copiedBytes = resolver.openInputStream(file.uri)?.use { input ->
                    copySafArchiveEntry(
                        input,
                        zip,
                        file.relativeName,
                        minOf(
                            integrityPolicy.maxEntryUncompressedBytes,
                            integrityPolicy.maxTotalUncompressedBytes - totalBytes,
                        ),
                    )
                }
                    ?: error("Unable to read SAF session file: ${file.relativeName}")
                totalBytes += copiedBytes
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

    private fun copySafArchiveEntry(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        relativePath: String,
        maxBytes: Long,
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var copiedBytes = 0L
        while (true) {
            if (copiedBytes == maxBytes) {
                require(input.read() < 0) { "SAF session file exceeds archive size limit: $relativePath" }
                return copiedBytes
            }
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), maxBytes - copiedBytes).toInt())
            if (read < 0) return copiedBytes
            output.write(buffer, 0, read)
            copiedBytes += read.toLong()
        }
    }

    private fun verifyArchiveAgainstSession(
        resolver: ContentResolver,
        archive: Path,
        sessionUri: Uri,
        integrityPolicy: ArchiveIntegrityPolicy,
    ) {
        Files.newInputStream(archive).use { input ->
            ArchiveIntegrity.requireSessionArchive(
                ArchiveIntegrity.verifyArchiveAgainstSources(
                    input,
                    sessionSources(resolver, sessionUri, integrityPolicy),
                    integrityPolicy,
                ),
            )
        }
    }

    private fun sessionSources(
        resolver: ContentResolver,
        sessionUri: Uri,
        integrityPolicy: ArchiveIntegrityPolicy,
    ): List<ArchiveSourceFile> =
        sessionUri.sessionFiles(resolver, integrityPolicy).map { file ->
            ArchiveSourceFile(
                relativePath = file.relativeName,
                openInput = {
                    resolver.openInputStream(file.uri)
                        ?: error("Unable to read SAF session file: ${file.relativeName}")
                },
            )
        }

    private fun createSafFileForRelativePath(
        resolver: ContentResolver,
        sessionUri: Uri,
        relativePath: String,
        integrityPolicy: ArchiveIntegrityPolicy,
    ): Uri {
        val components = ArchiveIntegrity.pathComponents(relativePath, integrityPolicy)
        val parent = components.dropLast(1).fold(sessionUri) { current, directoryName ->
            val existing = current.findChild(resolver, directoryName)
            if (existing != null) {
                require(existing.isDirectory) { "Restored SAF path parent is not a directory: $directoryName" }
                existing.uri
            } else {
                val created = DocumentsContract.createDocument(
                    resolver,
                    current,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    directoryName,
                ) ?: error("Unable to create restored SAF directory: $directoryName")
                current.findChild(resolver, directoryName)?.uri
                    ?: created.takeIf { it.displayName(resolver) == directoryName }
                    ?: error("SAF provider did not preserve restored directory name: $directoryName")
            }
        }
        val fileName = components.last()
        return createFile(
            resolver = resolver,
            parentUri = parent,
            fileName = fileName,
            mimeType = mimeTypeFor(fileName),
            replaceExisting = false,
        )
    }

    private fun replaceNmeaPreservingPreviousExport(
        resolver: ContentResolver,
        sessionUri: Uri,
        temporaryUri: Uri,
    ) {
        val targetName = SessionArtifactFile.RECEIVER_SOLUTION_NMEA.fileName
        val backupName = uniqueSafName(resolver, sessionUri, "$targetName.previous")
        SafNmeaReplacement.replace(
            temporary = temporaryUri,
            targetName = targetName,
            backupName = backupName,
            store = object : SafReplacementStore<Uri> {
                override fun find(name: String): Uri? = sessionUri.findChild(resolver, name)?.uri

                override fun rename(document: Uri, name: String): Uri? =
                    DocumentsContract.renameDocument(resolver, document, name)

                override fun create(name: String): Uri =
                    createFile(resolver, sessionUri, name, "text/plain", replaceExisting = false)

                override fun delete(document: Uri): Boolean = DocumentsContract.deleteDocument(resolver, document)

                override fun digest(document: Uri): SafDocumentDigest = documentDigest(resolver, document)

                override fun copy(source: Uri, destination: Uri) {
                    copyDocumentVerified(resolver, source, destination)
                }
            }
        )
    }

    private fun copyDocumentVerified(resolver: ContentResolver, source: Uri, destination: Uri) {
        val sourceDigest = resolver.openInputStream(source)?.use { input ->
            resolver.openOutputStream(destination, "wt")?.use { output -> copyAndDigest(input, output) }
                ?: error("Unable to write SAF document copy.")
        } ?: error("Unable to read SAF document copy source.")
        require(sourceDigest.sizeBytes > 0L) { "SAF document copy source is empty." }
        require(documentDigest(resolver, destination) == sourceDigest) { "SAF document copy verification failed." }
    }

    private fun documentDigest(resolver: ContentResolver, uri: Uri): SafDocumentDigest =
        resolver.openInputStream(uri)?.use { input -> copyAndDigest(input, null) }
            ?: error("Unable to verify SAF document.")

    private fun copyAndDigest(input: java.io.InputStream, output: java.io.OutputStream?): SafDocumentDigest {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var sizeBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            require(sizeBytes <= Long.MAX_VALUE - read.toLong()) { "SAF document size overflows." }
            sizeBytes += read.toLong()
            digest.update(buffer, 0, read)
            output?.write(buffer, 0, read)
        }
        output?.flush()
        return SafDocumentDigest(sizeBytes, digest.digest().toHex())
    }

    private fun Uri.sessionFiles(
        resolver: ContentResolver,
        integrityPolicy: ArchiveIntegrityPolicy,
    ): List<SafSessionFile> {
        val files = mutableListOf<SafSessionFile>()
        val visitedDirectories = mutableSetOf<String>()
        var visitedEntries = 0

        fun visit(directory: Uri, prefix: String, depth: Int) {
            require(depth <= integrityPolicy.maxPathDepth) {
                "SAF session path depth exceeds ${integrityPolicy.maxPathDepth}."
            }
            require(visitedDirectories.add(directory.toString())) {
                "SAF session directory graph contains a cycle: $directory"
            }
            directory.children(resolver).forEach { child ->
                visitedEntries += 1
                require(visitedEntries <= integrityPolicy.maxEntries) {
                    "SAF session entry count exceeds ${integrityPolicy.maxEntries}."
                }
                val relativeName = if (prefix.isBlank()) child.displayName else "$prefix/${child.displayName}"
                ArchiveIntegrity.pathComponents(relativeName, integrityPolicy)
                if (child.isDirectory) {
                    visit(child.uri, relativeName, depth + 1)
                } else {
                    files += SafSessionFile(child.uri, relativeName, child.sizeBytes)
                }
            }
        }

        visit(this, prefix = "", depth = 0)
        return files.sortedBy(SafSessionFile::relativeName)
    }

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

internal data class SafDocumentDigest(
    val sizeBytes: Long,
    val sha256: String,
)

internal interface SafReplacementStore<T> {
    fun find(name: String): T?

    fun rename(document: T, name: String): T?

    fun create(name: String): T

    fun delete(document: T): Boolean

    fun digest(document: T): SafDocumentDigest

    fun copy(source: T, destination: T)
}

internal object SafNmeaReplacement {
    fun <T> replace(
        temporary: T,
        targetName: String,
        backupName: String,
        store: SafReplacementStore<T>,
    ) {
        val replacementDigest = store.digest(temporary)
        require(replacementDigest.sizeBytes > 0L) { "Generated SAF NMEA replacement is empty." }
        val previous = store.find(targetName)
        if (previous == null) {
            installWithoutPrevious(temporary, targetName, replacementDigest, store)
            return
        }

        val previousDigest = store.digest(previous)
        if (previousDigest.sizeBytes == 0L) {
            require(store.delete(previous)) { "SAF provider did not delete the empty previous NMEA export." }
            require(store.find(targetName) == null) { "SAF provider retained the empty previous NMEA export." }
            installWithoutPrevious(temporary, targetName, replacementDigest, store)
            return
        }

        val renamedBackup = runCatching { store.rename(previous, backupName) }.getOrNull()
        val backupAfterRename = renamedBackup ?: store.find(backupName)
        if (backupAfterRename != null) {
            require(store.digest(backupAfterRename) == previousDigest) {
                "SAF provider changed the previous NMEA export while renaming it."
            }
            replaceWithRenamedBackup(
                temporary,
                targetName,
                replacementDigest,
                backupAfterRename,
                previousDigest,
                store,
            )
            return
        }

        replaceWithCopiedBackup(
            temporary,
            targetName,
            replacementDigest,
            previous,
            previousDigest,
            backupName,
            store,
        )
    }

    private fun <T> installWithoutPrevious(
        temporary: T,
        targetName: String,
        replacementDigest: SafDocumentDigest,
        store: SafReplacementStore<T>,
    ) {
        val renamed = runCatching { store.rename(temporary, targetName) }.getOrNull()
        val canonical = store.find(targetName) ?: renamed
        if (canonical != null) {
            require(store.digest(canonical) == replacementDigest && store.find(targetName) == canonical) {
                "SAF provider did not preserve the generated NMEA replacement after rename."
            }
            return
        }
        val created = store.create(targetName)
        try {
            store.copy(temporary, created)
            require(store.find(targetName)?.let(store::digest) == replacementDigest) {
                "SAF provider did not create the canonical NMEA replacement."
            }
            runCatching { store.delete(temporary) }
        } catch (error: Throwable) {
            runCatching { store.delete(created) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    private fun <T> replaceWithRenamedBackup(
        temporary: T,
        targetName: String,
        replacementDigest: SafDocumentDigest,
        backup: T,
        previousDigest: SafDocumentDigest,
        store: SafReplacementStore<T>,
    ) {
        var replacement: T? = null
        try {
            replacement = store.rename(temporary, targetName) ?: store.find(targetName)
            require(replacement != null && store.find(targetName) == replacement) {
                "SAF provider did not rename the generated NMEA replacement."
            }
            require(store.digest(replacement) == replacementDigest) {
                "SAF provider changed the generated NMEA replacement while renaming it."
            }
            runCatching { store.delete(backup) }
        } catch (error: Throwable) {
            replacement?.let { runCatching { store.delete(it) } }
            rollback(error, backup, targetName, previousDigest, store)
            throw error
        }
    }

    private fun <T> replaceWithCopiedBackup(
        temporary: T,
        targetName: String,
        replacementDigest: SafDocumentDigest,
        previous: T,
        previousDigest: SafDocumentDigest,
        backupName: String,
        store: SafReplacementStore<T>,
    ) {
        val backup = store.create(backupName)
        store.copy(previous, backup)
        require(store.digest(backup) == previousDigest) { "SAF NMEA backup verification failed." }

        var replacement: T? = null
        try {
            require(store.delete(previous)) { "SAF provider did not delete the previous NMEA export." }
            require(store.find(targetName) == null) { "SAF provider retained the previous NMEA export." }
            replacement = store.create(targetName)
            store.copy(temporary, replacement)
            require(store.find(targetName)?.let(store::digest) == replacementDigest) {
                "SAF provider did not create the canonical NMEA replacement."
            }
            runCatching { store.delete(backup) }
            runCatching { store.delete(temporary) }
        } catch (error: Throwable) {
            replacement?.let { runCatching { store.delete(it) } }
            val previousStillCanonical = runCatching {
                store.find(targetName)?.let(store::digest) == previousDigest
            }.getOrDefault(false)
            if (!previousStillCanonical) {
                rollback(error, backup, targetName, previousDigest, store)
            }
            throw error
        }
    }

    private fun <T> rollback(
        originalError: Throwable,
        backup: T,
        targetName: String,
        previousDigest: SafDocumentDigest,
        store: SafReplacementStore<T>,
    ) {
        runCatching {
            store.find(targetName)?.let { partial ->
                require(store.delete(partial)) { "SAF provider did not remove the failed NMEA replacement." }
            }
            val renamed = runCatching { store.rename(backup, targetName) }.getOrNull()
            val canonical = store.find(targetName) ?: renamed
            if (canonical == null || store.find(targetName) != canonical || store.digest(canonical) != previousDigest) {
                canonical?.let { require(store.delete(it)) { "SAF provider did not remove invalid rollback output." } }
                val restored = store.create(targetName)
                store.copy(backup, restored)
                require(store.find(targetName)?.let(store::digest) == previousDigest) {
                    "SAF provider did not restore the previous NMEA export."
                }
            }
        }.exceptionOrNull()?.let(originalError::addSuppressed)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

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
