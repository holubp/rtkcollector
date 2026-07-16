package org.rtkcollector.core.session

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** Bounds applied while inspecting or extracting a session ZIP. */
data class ArchiveIntegrityPolicy(
    val maxEntries: Int = 1_024,
    val maxPathDepth: Int = 16,
    val maxEntryUncompressedBytes: Long = 16L * 1024L * 1024L * 1024L,
    val maxTotalUncompressedBytes: Long = 32L * 1024L * 1024L * 1024L,
    val maxCompressionRatio: Long = 200L,
) {
    init {
        require(maxEntries > 0) { "Maximum ZIP entry count must be positive." }
        require(maxPathDepth > 0) { "Maximum ZIP path depth must be positive." }
        require(maxEntryUncompressedBytes > 0L) { "Maximum ZIP entry size must be positive." }
        require(maxTotalUncompressedBytes >= maxEntryUncompressedBytes) {
            "Maximum ZIP total size must cover one entry."
        }
        require(maxCompressionRatio > 0L) { "Maximum ZIP compression ratio must be positive." }
    }
}

/** A file whose path and bytes must match an archive entry exactly. */
data class ArchiveSourceFile(
    val relativePath: String,
    val openInput: () -> InputStream,
)

/** Digest produced after fully reading a validated ZIP entry. */
data class ArchiveEntryDigest(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** File-only manifest of a validated ZIP archive. */
data class ArchiveManifest(
    val entries: List<ArchiveEntryDigest>,
)

/**
 * Stream-safe ZIP inspection, comparison and extraction for completed session archives.
 *
 * All methods reject unsafe paths and duplicate entries before accepting their bytes. They
 * also consume every entry so ZIP CRC failures and declared size inconsistencies are surfaced.
 */
object ArchiveIntegrity {
    fun requireSessionArchive(manifest: ArchiveManifest): ArchiveManifest {
        require(
            manifest.entries.any {
                it.relativePath == SessionArtifactFile.SESSION_JSON.fileName ||
                    it.relativePath == SessionArtifactFile.RECEIVER_RX_RAW.fileName
            },
        ) {
            "ZIP archive is not a session archive: session.json or receiver-rx.raw is required."
        }
        return manifest
    }

    fun inspect(
        input: InputStream,
        policy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
    ): ArchiveManifest =
        readArchive(input, policy) { relativePath, zipInput, maxBytes ->
            copyEntry(relativePath, zipInput, null, maxBytes)
        }

    fun verifyArchiveAgainstSources(
        input: InputStream,
        sources: Collection<ArchiveSourceFile>,
        policy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
    ): ArchiveManifest {
        val expected = sourceMap(sources, policy)
        val manifest = readArchive(input, policy) { relativePath, zipInput, maxBytes ->
            val source = expected.remove(relativePath)
                ?: error("Archive contains unexpected file: $relativePath")
            source.openInput().use { sourceInput ->
                copyEntryComparing(relativePath, zipInput, sourceInput, maxBytes)
            }
        }
        require(expected.isEmpty()) {
            "Archive is missing source files: ${expected.keys.sorted().joinToString()}"
        }
        return manifest
    }

    fun extract(
        input: InputStream,
        openOutput: (relativePath: String) -> OutputStream,
        policy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
    ): ArchiveManifest =
        readArchive(input, policy) { relativePath, zipInput, maxBytes ->
            openOutput(relativePath).use { output ->
                copyEntry(relativePath, zipInput, output, maxBytes)
            }
        }

    fun verifySourcesAgainstManifest(
        sources: Collection<ArchiveSourceFile>,
        manifest: ArchiveManifest,
        policy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
    ) {
        val actual = sourceMap(sources, policy)
        val expected = linkedMapOf<String, ArchiveEntryDigest>()
        var totalBytes = 0L
        manifest.entries.forEach { entry ->
            require(expected.size < policy.maxEntries) {
                "Archive manifest entry count exceeds ${policy.maxEntries}."
            }
            validateRelativePath(entry.relativePath, policy)
            require(entry.sizeBytes in 0L..policy.maxEntryUncompressedBytes) {
                "Archive manifest entry exceeds the configured size limit: ${entry.relativePath}"
            }
            totalBytes = checkedAdd(totalBytes, entry.sizeBytes, "Archive manifest cumulative size")
            require(totalBytes <= policy.maxTotalUncompressedBytes) {
                "Archive manifest cumulative size exceeds ${policy.maxTotalUncompressedBytes}."
            }
            require(expected.put(entry.relativePath, entry) == null) {
                "Duplicate archive manifest path: ${entry.relativePath}"
            }
        }
        require(actual.keys == expected.keys) {
            "Restored file set does not match archive contents."
        }
        expected.forEach { (relativePath, entry) ->
            val digest = actual.getValue(relativePath).openInput().use { input ->
                digestInput(input, policy.maxEntryUncompressedBytes, relativePath)
            }
            require(digest.sizeBytes == entry.sizeBytes && digest.sha256 == entry.sha256) {
                "Restored file does not match archive contents: $relativePath"
            }
        }
    }

    fun pathComponents(
        relativePath: String,
        policy: ArchiveIntegrityPolicy = ArchiveIntegrityPolicy(),
    ): List<String> {
        validateRelativePath(relativePath, policy)
        return relativePath.split('/')
    }

    private fun sourceMap(
        sources: Collection<ArchiveSourceFile>,
        policy: ArchiveIntegrityPolicy,
    ): MutableMap<String, ArchiveSourceFile> {
        val result = linkedMapOf<String, ArchiveSourceFile>()
        sources.forEach { source ->
            validateRelativePath(source.relativePath, policy)
            require(result.put(source.relativePath, source) == null) {
                "Duplicate source file path: ${source.relativePath}"
            }
        }
        return result
    }

    private fun readArchive(
        input: InputStream,
        policy: ArchiveIntegrityPolicy,
        consumeFile: (
            relativePath: String,
            zipInput: ZipInputStream,
            maxBytes: Long,
        ) -> EntryCopyResult,
    ): ArchiveManifest {
        val entries = mutableListOf<ArchiveEntryDigest>()
        val paths = linkedMapOf<String, ArchiveEntryKind>()
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= policy.maxEntries) { "ZIP entry count exceeds ${policy.maxEntries}." }
                val relativePath = entry.name.removeDirectorySuffix(entry.isDirectory)
                validateRelativePath(relativePath, policy)
                registerPath(paths, relativePath, entry.isDirectory)
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val remainingTotalBytes = policy.maxTotalUncompressedBytes - totalBytes
                entry.size.takeIf { it >= 0L }?.let { declaredSize ->
                    require(declaredSize <= policy.maxEntryUncompressedBytes) {
                        "ZIP entry exceeds per-entry size limit: $relativePath"
                    }
                    require(declaredSize <= remainingTotalBytes) {
                        "ZIP cumulative size exceeds ${policy.maxTotalUncompressedBytes}."
                    }
                }
                val maxBytes = minOf(policy.maxEntryUncompressedBytes, remainingTotalBytes)
                val result = consumeFile(relativePath, zip, maxBytes)
                totalBytes = checkedAdd(totalBytes, result.sizeBytes, "ZIP cumulative size")
                zip.closeEntry()
                validateCompressionRatio(entry, result.sizeBytes, policy)
                entries += ArchiveEntryDigest(relativePath, result.sizeBytes, result.sha256)
            }
        }
        require(entries.isNotEmpty()) { "ZIP archive contains no files." }
        return ArchiveManifest(entries)
    }

    private fun copyEntry(
        relativePath: String,
        zipInput: ZipInputStream,
        output: OutputStream?,
        maxBytes: Long,
    ): EntryCopyResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
        var sizeBytes = 0L
        while (true) {
            if (sizeBytes == maxBytes) {
                require(zipInput.read() < 0) { "ZIP entry exceeds expanded-size limit: $relativePath" }
                break
            }
            val read = zipInput.read(buffer, 0, minOf(buffer.size.toLong(), maxBytes - sizeBytes).toInt())
            if (read < 0) break
            sizeBytes = checkedAdd(sizeBytes, read.toLong(), "ZIP entry size")
            require(sizeBytes <= maxBytes) { "ZIP entry exceeds expanded-size limit: $relativePath" }
            digest.update(buffer, 0, read)
            output?.write(buffer, 0, read)
        }
        return EntryCopyResult(sizeBytes, digest.digest().toHex())
    }

    private fun copyEntryComparing(
        relativePath: String,
        zipInput: ZipInputStream,
        sourceInput: InputStream,
        maxBytes: Long,
    ): EntryCopyResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val zipBuffer = ByteArray(DEFAULT_BUFFER_BYTES)
        val sourceBuffer = ByteArray(DEFAULT_BUFFER_BYTES)
        var sizeBytes = 0L
        while (true) {
            if (sizeBytes == maxBytes) {
                val zipExtra = zipInput.read()
                val sourceExtra = sourceInput.read()
                require(zipExtra == sourceExtra) { "Archive entry size differs from source file: $relativePath" }
                require(zipExtra < 0) { "ZIP entry exceeds expanded-size limit: $relativePath" }
                break
            }
            val readLength = minOf(zipBuffer.size.toLong(), maxBytes - sizeBytes).toInt()
            val zipRead = zipInput.read(zipBuffer, 0, readLength)
            if (zipRead < 0) {
                require(sourceInput.read() < 0) {
                    "Archive entry size differs from source file: $relativePath"
                }
                break
            }
            readExactly(sourceInput, sourceBuffer, zipRead, relativePath)
            sizeBytes = checkedAdd(sizeBytes, zipRead.toLong(), "ZIP entry size")
            require(sizeBytes <= maxBytes) { "ZIP entry exceeds expanded-size limit: $relativePath" }
            require(bytesMatch(zipBuffer, sourceBuffer, zipRead)) {
                "Archive entry content differs from source file: $relativePath"
            }
            digest.update(zipBuffer, 0, zipRead)
        }
        return EntryCopyResult(sizeBytes, digest.digest().toHex())
    }

    private fun readExactly(
        input: InputStream,
        buffer: ByteArray,
        length: Int,
        relativePath: String,
    ) {
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            require(read >= 0) { "Archive entry size differs from source file: $relativePath" }
            offset += read
        }
    }

    private fun digestInput(
        input: InputStream,
        maxBytes: Long,
        relativePath: String,
    ): ArchiveEntryDigest {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
        var sizeBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            sizeBytes = checkedAdd(sizeBytes, read.toLong(), "Source file size")
            require(sizeBytes <= maxBytes) { "Source file exceeds size limit: $relativePath" }
            digest.update(buffer, 0, read)
        }
        return ArchiveEntryDigest("", sizeBytes, digest.digest().toHex())
    }

    private fun validateRelativePath(path: String, policy: ArchiveIntegrityPolicy) {
        require(path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') && !path.contains('\u0000')) {
            "ZIP path must be a non-empty relative slash-separated path: $path"
        }
        val components = path.split('/')
        require(components.size <= policy.maxPathDepth) { "ZIP path depth exceeds ${policy.maxPathDepth}: $path" }
        require(components.all { it.isNotEmpty() && it != "." && it != ".." }) {
            "ZIP path contains an unsafe component: $path"
        }
    }

    private fun registerPath(
        paths: MutableMap<String, ArchiveEntryKind>,
        relativePath: String,
        isDirectory: Boolean,
    ) {
        require(relativePath !in paths) { "Duplicate ZIP path: $relativePath" }
        val components = relativePath.split('/')
        components.indices.drop(1).forEach { componentCount ->
            val parent = components.take(componentCount).joinToString("/")
            require(paths[parent] != ArchiveEntryKind.FILE) {
                "ZIP file path is also used as a directory: $parent"
            }
        }
        if (!isDirectory) {
            require(paths.keys.none { it.startsWith("$relativePath/") }) {
                "ZIP file path is also used as a directory: $relativePath"
            }
        }
        paths[relativePath] = if (isDirectory) ArchiveEntryKind.DIRECTORY else ArchiveEntryKind.FILE
    }

    private fun validateCompressionRatio(
        entry: ZipEntry,
        uncompressedSize: Long,
        policy: ArchiveIntegrityPolicy,
    ) {
        val compressedSize = entry.compressedSize
        if (compressedSize < 0L) return
        require(compressedSize > 0L || uncompressedSize == 0L) {
            "ZIP entry has zero compressed size but non-empty contents: ${entry.name}"
        }
        val quotient = if (compressedSize == 0L) 0L else uncompressedSize / compressedSize
        val remainder = if (compressedSize == 0L) 0L else uncompressedSize % compressedSize
        require(
            compressedSize == 0L ||
                quotient < policy.maxCompressionRatio ||
                (quotient == policy.maxCompressionRatio && remainder == 0L),
        ) {
            "ZIP entry exceeds compression-ratio limit: ${entry.name}"
        }
    }

    private fun checkedAdd(current: Long, added: Long, description: String): Long {
        require(added >= 0L && current <= Long.MAX_VALUE - added) { "$description overflows." }
        return current + added
    }

    private fun bytesMatch(left: ByteArray, right: ByteArray, length: Int): Boolean {
        for (index in 0 until length) {
            if (left[index] != right[index]) return false
        }
        return true
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private data class EntryCopyResult(
        val sizeBytes: Long,
        val sha256: String,
    )

    private enum class ArchiveEntryKind {
        FILE,
        DIRECTORY,
    }

    private fun String.removeDirectorySuffix(isDirectory: Boolean): String =
        if (isDirectory && endsWith('/')) dropLast(1) else this

    private const val DEFAULT_BUFFER_BYTES = 64 * 1024
}
