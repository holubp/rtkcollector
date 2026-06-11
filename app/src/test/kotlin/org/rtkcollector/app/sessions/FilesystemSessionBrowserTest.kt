package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

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

    private fun sessionDir(name: String): Path {
        val session = Files.createDirectory(tempDir.resolve(name))
        Files.writeString(session.resolve("session.json"), "{}\n")
        Files.write(session.resolve("receiver-rx.raw"), byteArrayOf(1))
        return session
    }
}
