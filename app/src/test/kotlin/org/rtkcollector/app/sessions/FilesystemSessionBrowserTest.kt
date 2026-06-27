package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class FilesystemSessionBrowserTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `discovers sessions and archives latest first`() {
        val oldSession = sessionDir("session-old")
        Thread.sleep(5)
        val newSession = sessionDir("session-new")
        Thread.sleep(5)
        val archive = Files.write(tempDir.resolve("session-archive.zip"), byteArrayOf(1, 2, 3))

        val state = FilesystemSessionBrowser.discover(tempDir)

        assertEquals(listOf("session-new", "session-old"), state.groups.first { it.kind == SessionBrowserGroupKind.RECORDINGS }.entries.map { it.title })
        assertEquals(listOf(archive.toString()), state.groups.first { it.kind == SessionBrowserGroupKind.ARCHIVES }.entries.map { it.id })
        assertTrue(Files.exists(oldSession))
        assertTrue(Files.exists(newSession))
    }

    @Test
    fun `delete removes recordings and archives`() {
        val session = sessionDir("session-delete")
        val archive = Files.write(tempDir.resolve("session-delete.zip"), byteArrayOf(1))

        FilesystemSessionBrowser.deleteRecording(session)
        FilesystemSessionBrowser.deleteArchive(archive)

        assertFalse(Files.exists(session))
        assertFalse(Files.exists(archive))
    }

    @Test
    fun `android runtime code avoids Path of`() {
        val sourceRoot = locateProjectPath("app/src/main/kotlin", Files::isDirectory)
        val offenders = Files.walk(sourceRoot).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                .filter { path -> Files.readString(path).contains("Path.of(") }
                .map { path -> sourceRoot.relativize(path).toString() }
                .sorted()
                .toList()
        }

        assertTrue(
            offenders.isEmpty(),
            "Path.of is unavailable on supported Android runtimes; use Paths.get instead. Offenders: $offenders",
        )
    }

    private fun sessionDir(name: String): Path {
        val session = Files.createDirectory(tempDir.resolve(name))
        Files.writeString(session.resolve("session.json"), "{}\n")
        Files.write(session.resolve("receiver-rx.raw"), byteArrayOf(1))
        return session
    }

    private fun locateProjectPath(relative: String, predicate: (Path) -> Boolean): Path =
        sequenceOf(Paths.get(relative), Paths.get("app").resolve(relative.removePrefix("app/")))
            .firstOrNull(predicate)
            ?: error("Cannot locate project path $relative from ${Paths.get("").toAbsolutePath()}")
}
