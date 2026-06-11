package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SessionArchiveManagerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `temporary share zip leaves original session intact`() {
        val session = sessionDir("session-share")
        val cache = tempDir.resolve("cache")

        val zip = SessionArchiveManager.createTemporaryShareZip(session, cache)

        assertTrue(Files.exists(zip))
        assertTrue(SessionArchiveManager.verifyArchive(zip))
        assertTrue(Files.exists(session.resolve("receiver-rx.raw")))
    }

    @Test
    fun `archive removes original after verified zip is created`() {
        val session = sessionDir("session-archive")

        val zip = SessionArchiveManager.archiveSession(session, tempDir)

        assertTrue(Files.exists(zip))
        assertTrue(SessionArchiveManager.verifyArchive(zip))
        assertFalse(Files.exists(session))
    }

    @Test
    fun `restore expands archive and removes zip`() {
        val session = sessionDir("session-restore")
        val zip = SessionArchiveManager.archiveSession(session, tempDir)

        val restored = SessionArchiveManager.restoreArchive(zip, tempDir)

        assertTrue(Files.exists(restored.resolve("session.json")))
        assertTrue(Files.exists(restored.resolve("receiver-rx.raw")))
        assertFalse(Files.exists(zip))
    }

    private fun sessionDir(name: String): Path {
        val session = Files.createDirectory(tempDir.resolve(name))
        Files.writeString(session.resolve("session.json"), "{}\n")
        Files.write(session.resolve("receiver-rx.raw"), byteArrayOf(1, 2, 3))
        return session
    }
}
