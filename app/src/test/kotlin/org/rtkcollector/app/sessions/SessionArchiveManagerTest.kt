package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rtkcollector.app.testing.TestFiles
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    fun `temporary share zip rejects source symlinks`() {
        val session = sessionDir("session-share-link")
        val outside = Files.write(tempDir.resolve("outside-secret.txt"), "secret".toByteArray())
        val link = session.resolve("linked-secret.txt")
        runCatching { Files.createSymbolicLink(link, outside) }
            .getOrElse { return }

        assertThrows(IllegalArgumentException::class.java) {
            SessionArchiveManager.createTemporaryShareZip(session, tempDir.resolve("cache"))
        }
        assertTrue(Files.exists(session.resolve("receiver-rx.raw")))
    }

    @Test
    fun `active session is rejected before temporary share opens source files`() {
        val session = sessionDir("session-share-active")
        val cache = tempDir.resolve("cache")
        ActiveRecordingSessionRegistry.activate(session.toString())

        try {
            assertThrows(IllegalArgumentException::class.java) {
                SessionArchiveManager.createTemporaryShareZip(session, cache)
            }
            assertFalse(Files.exists(cache))
            assertTrue(Files.exists(session.resolve("receiver-rx.raw")))
        } finally {
            ActiveRecordingSessionRegistry.deactivate(session.toString())
        }
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
    fun `active session is rejected before archive output is created`() {
        val session = sessionDir("session-active")
        ActiveRecordingSessionRegistry.activate(session.toString())

        try {
            assertThrows(IllegalArgumentException::class.java) {
                SessionArchiveManager.archiveSession(session, tempDir)
            }
            assertTrue(Files.exists(session.resolve("receiver-rx.raw")))
            assertFalse(Files.exists(tempDir.resolve("session-active.zip")))
        } finally {
            ActiveRecordingSessionRegistry.deactivate(session.toString())
        }
    }

    @Test
    fun `archive rejects activation while source export is in progress`() {
        val session = sessionDir("session-race")
        var activationAttempted = false

        assertThrows(IllegalArgumentException::class.java) {
            SessionArchiveManager.archiveSession(session, tempDir) { progress ->
                if (!activationAttempted && progress.totalFiles > 0 && progress.filesCompleted == progress.totalFiles) {
                    activationAttempted = true
                    ActiveRecordingSessionRegistry.activate(session.toString())
                }
            }
        }

        assertTrue(activationAttempted)
        assertTrue(Files.exists(session.resolve("receiver-rx.raw")))
        assertFalse(Files.exists(tempDir.resolve("session-race.zip")))
    }

    @Test
    fun `verification failure preserves source and removes partial archive`() {
        val session = sessionDir("session-changing")
        var changed = false

        assertThrows(IllegalArgumentException::class.java) {
            SessionArchiveManager.archiveSession(session, tempDir) { progress ->
                if (!changed && progress.totalFiles > 0 && progress.filesCompleted == progress.totalFiles) {
                    Files.write(session.resolve("receiver-rx.raw"), byteArrayOf(9, 8, 7))
                    changed = true
                }
            }
        }

        assertArrayEquals(byteArrayOf(9, 8, 7), Files.readAllBytes(session.resolve("receiver-rx.raw")))
        assertFalse(Files.exists(tempDir.resolve("session-changing.zip")))
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

    @Test
    fun `restore preserves nested paths and verifies their bytes`() {
        val session = sessionDir("session-nested")
        Files.createDirectories(session.resolve("nested"))
        Files.write(session.resolve("nested/one.bin"), byteArrayOf(4, 5))
        Files.write(session.resolve("nested/two.bin"), byteArrayOf(6, 7))
        val zip = SessionArchiveManager.archiveSession(session, tempDir)

        val restored = SessionArchiveManager.restoreArchive(zip, tempDir)

        assertArrayEquals(byteArrayOf(4, 5), Files.readAllBytes(restored.resolve("nested/one.bin")))
        assertArrayEquals(byteArrayOf(6, 7), Files.readAllBytes(restored.resolve("nested/two.bin")))
        assertFalse(Files.exists(zip))
    }

    @Test
    fun `restore rejects bounded non-session zip before creating destination`() {
        val zip = tempDir.resolve("notes.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { output ->
            output.putNextEntry(ZipEntry("notes.txt"))
            output.write("not a session".toByteArray())
            output.closeEntry()
        }

        assertThrows(IllegalArgumentException::class.java) {
            SessionArchiveManager.restoreArchive(zip, tempDir)
        }

        assertTrue(Files.exists(zip))
        assertFalse(Files.exists(tempDir.resolve("notes")))
    }

    @Test
    fun `archive destination inside source is rejected`() {
        val session = sessionDir("session-contained-output")

        assertThrows(IllegalArgumentException::class.java) {
            SessionArchiveManager.archiveSession(session, session.resolve("archives"))
        }

        assertTrue(Files.exists(session.resolve("receiver-rx.raw")))
    }

    private fun sessionDir(name: String): Path {
        val session = Files.createDirectory(tempDir.resolve(name))
        TestFiles.writeString(session.resolve("session.json"), "{}\n")
        Files.write(session.resolve("receiver-rx.raw"), byteArrayOf(1, 2, 3))
        return session
    }
}
