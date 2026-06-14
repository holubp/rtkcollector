package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SessionNmeaExporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `plan for session uses receiver solution nmea and nmea suffix`() {
        val session = Files.createDirectory(tempDir.resolve("session-2026-06-14T12-00-00Z-abc"))
        Files.writeString(session.resolve("receiver-solution.nmea"), "\$GPGGA,data\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val plan = SessionNmeaSharePlan.fromSessionDirectory(session, cache)

        assertEquals(session.resolve("receiver-solution.nmea"), plan.sourceNmea)
        assertEquals(cache.resolve("session-2026-06-14T12-00-00Z-abc.nmea"), plan.outputNmea)
    }

    @Test
    fun `selection skips sessions without receiver solution nmea`() {
        val withNmea = Files.createDirectory(tempDir.resolve("with-nmea"))
        Files.writeString(withNmea.resolve("receiver-solution.nmea"), "\$GPGGA,data\n")
        val withoutNmea = Files.createDirectory(tempDir.resolve("without-nmea"))
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(
            sessionDirectories = listOf(withNmea, withoutNmea),
            outputDirectory = cache,
        )

        assertEquals(1, selection.plans.size)
        assertEquals(1, selection.skippedCount)
        assertTrue(selection.hasShareableNmea)
    }

    @Test
    fun `selection reports no shareable nmea when all sessions are missing sidecar`() {
        val session = Files.createDirectory(tempDir.resolve("session"))
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(listOf(session), cache)

        assertEquals(emptyList<SessionNmeaSharePlan>(), selection.plans)
        assertEquals(1, selection.skippedCount)
        assertFalse(selection.hasShareableNmea)
    }

    @Test
    fun `export copies nmea bytes without altering source`() {
        val session = Files.createDirectory(tempDir.resolve("session"))
        val source = session.resolve("receiver-solution.nmea")
        Files.writeString(source, "\$GPGGA,data\n\$GPRMC,data\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))
        val plan = SessionNmeaSharePlan.fromSessionDirectory(session, cache)

        val output = SessionNmeaExporter.export(plan)

        assertEquals("\$GPGGA,data\n\$GPRMC,data\n", Files.readString(output))
        assertEquals("\$GPGGA,data\n\$GPRMC,data\n", Files.readString(source))
    }
}
