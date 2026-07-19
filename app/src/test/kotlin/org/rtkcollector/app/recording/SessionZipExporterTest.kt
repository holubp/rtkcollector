package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rtkcollector.app.testing.TestFiles
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

class SessionZipExporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `zip exporter preserves session file names and reports progress`() {
        val session = Files.createDirectory(tempDir.resolve("session"))
        TestFiles.writeString(session.resolve("session.json"), "{}\n")
        Files.write(session.resolve("receiver-rx.raw"), byteArrayOf(1, 2, 3))
        val zip = tempDir.resolve("session.zip")
        val progress = mutableListOf<SessionZipProgress>()

        val plan = SessionZipPlan.fromSessionDirectory(session, zip)
        SessionZipExporter.export(plan = plan, onProgress = progress::add)

        assertEquals(listOf("receiver-rx.raw", "session.json"), zipEntries(zip).sorted())
        assertEquals(0, progress.first().filesCompleted)
        assertEquals(2, progress.last().filesCompleted)
        assertEquals(1.0, progress.last().fraction)
        assertTrue(Files.exists(session.resolve("receiver-rx.raw")))
    }

    private fun zipEntries(zip: Path): List<String> {
        val entries = mutableListOf<String>()
        ZipInputStream(Files.newInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entries += entry.name
            }
        }
        return entries
    }
}
