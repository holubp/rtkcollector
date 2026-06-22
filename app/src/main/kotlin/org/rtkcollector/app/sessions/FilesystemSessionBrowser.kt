package org.rtkcollector.app.sessions

import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name

object FilesystemSessionBrowser {
    fun discover(
        storageRoot: Path,
        currentSessionLocation: String? = null,
        currentSessionActive: Boolean = false,
    ): SessionBrowserState {
        val currentPath = currentSessionLocation
            ?.takeIf { it.isNotBlank() && !it.startsWith("content://") }
            ?.let { Path.of(it) }
        val currentEntry = currentPath
            ?.takeIf(Files::exists)
            ?.let { path ->
                SessionBrowserEntry(
                    id = path.toString(),
                    title = if (currentSessionActive) "Current recording" else "Last session",
                    subtitle = path.toString(),
                    location = path.toString(),
                    kind = if (currentSessionActive) SessionEntryKind.CURRENT_ACTIVE else SessionEntryKind.CURRENT_STOPPED,
                    modifiedEpochMillis = modifiedMillis(path),
                    sizeBytes = pathSize(path),
                    receiverFamily = receiverFamily(path),
                )
            }

        val entries = mutableListOf<SessionBrowserEntry>()
        currentEntry?.let(entries::add)
        if (Files.isDirectory(storageRoot)) {
            Files.list(storageRoot).use { stream ->
                stream
                    .filter { path -> Files.isDirectory(path) || path.name.endsWith(".zip") }
                    .filter { path -> currentPath == null || path.toAbsolutePath() != currentPath.toAbsolutePath() }
                    .forEach { path ->
                        if (Files.isDirectory(path) && looksLikeSessionDirectory(path)) {
                            entries += SessionBrowserEntry(
                                id = path.toString(),
                                title = path.name,
                                subtitle = path.toString(),
                                location = path.toString(),
                                kind = SessionEntryKind.RECORDING,
                                modifiedEpochMillis = modifiedMillis(path),
                                sizeBytes = pathSize(path),
                                receiverFamily = receiverFamily(path),
                            )
                        } else if (Files.isRegularFile(path) && path.name.endsWith(".zip")) {
                            entries += SessionBrowserEntry(
                                id = path.toString(),
                                title = path.name.removeSuffix(".zip"),
                                subtitle = "Archived ZIP: $path",
                                location = path.toString(),
                                kind = SessionEntryKind.ARCHIVE,
                                modifiedEpochMillis = modifiedMillis(path),
                                sizeBytes = Files.size(path),
                            )
                        }
                    }
            }
        }
        return sessionBrowserStateOf(entries)
    }

    fun deleteRecording(sessionDirectory: Path) {
        require(Files.isDirectory(sessionDirectory)) { "Recording is not a directory: $sessionDirectory" }
        deleteRecursively(sessionDirectory)
    }

    fun deleteArchive(archive: Path) {
        require(Files.isRegularFile(archive) && archive.name.endsWith(".zip")) { "Archive is not a ZIP file: $archive" }
        archive.deleteIfExists()
    }

    private fun looksLikeSessionDirectory(path: Path): Boolean =
        Files.exists(path.resolve("session.json")) ||
            Files.exists(path.resolve("receiver-rx.raw")) ||
            path.name.startsWith("session-")

    private fun receiverFamily(path: Path): String? =
        runCatching {
            val sessionJson = path.resolve("session.json")
            if (!Files.isRegularFile(sessionJson)) return@runCatching null
            JSONObject(String(Files.readAllBytes(sessionJson), Charsets.UTF_8))
                .optString("receiverDriverId")
                .takeIf(String::isNotBlank)
        }.getOrNull()

    private fun modifiedMillis(path: Path): Long =
        runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)

    private fun pathSize(path: Path): Long =
        if (Files.isRegularFile(path)) {
            Files.size(path)
        } else {
            Files.walk(path).use { stream ->
                stream
                    .filter(Files::isRegularFile)
                    .mapToLong { Files.size(it) }
                    .sum()
            }
        }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream
                .sorted(Comparator.reverseOrder())
                .forEach { it.deleteIfExists() }
        }
    }
}
