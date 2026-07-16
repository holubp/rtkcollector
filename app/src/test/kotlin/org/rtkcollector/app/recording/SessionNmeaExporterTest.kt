package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rtkcollector.app.sessions.ActiveRecordingSessionRegistry
import java.nio.file.Files
import java.nio.file.Path

class SessionNmeaExporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `receiver only share keeps legacy session nmea name`() {
        val session = Files.createDirectory(tempDir.resolve("session-2026-06-14T12-00-00Z-abc"))
        Files.writeString(session.resolve("receiver-solution.nmea"), "\$GPGGA,data\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(
            sessionDirectories = listOf(session),
            outputDirectory = cache,
        )

        assertEquals(1, selection.plans.size)
        assertEquals(SessionNmeaSource.RECEIVER_SOLUTION, selection.plans.single().source)
        assertEquals(session.resolve("receiver-solution.nmea"), selection.plans.single().sourceNmea)
        assertEquals(cache.resolve("session-2026-06-14T12-00-00Z-abc.nmea"), selection.plans.single().outputNmea)
        assertEquals(0, selection.skippedCount)
    }

    @Test
    fun `rtklib realtime share uses source suffix`() {
        val session = Files.createDirectory(tempDir.resolve("session-a"))
        Files.writeString(session.resolve("rtklib-solution.nmea"), "\$GNGGA,rtklib\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(
            sessionDirectories = listOf(session),
            outputDirectory = cache,
        )

        assertEquals(1, selection.plans.size)
        assertEquals(SessionNmeaSource.RTKLIB_REALTIME, selection.plans.single().source)
        assertEquals(cache.resolve("session-a-rtklib-realtime.nmea"), selection.plans.single().outputNmea)
    }

    @Test
    fun `multiple nmea sources use source suffixes`() {
        val session = Files.createDirectory(tempDir.resolve("session-a"))
        Files.writeString(session.resolve("receiver-solution.nmea"), "\$GNGGA,receiver\n")
        Files.writeString(session.resolve("rtklib-solution.nmea"), "\$GNGGA,rtklib\n")
        Files.writeString(session.resolve("rtklib-postprocessed-forward.nmea"), "\$GNGGA,postforward\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(
            sessionDirectories = listOf(session),
            outputDirectory = cache,
        )

        assertEquals(
            listOf(
                cache.resolve("session-a-receiver-solution.nmea"),
                cache.resolve("session-a-rtklib-realtime.nmea"),
                cache.resolve("session-a-rtklib-postprocessed-forward.nmea"),
            ),
            selection.plans.map(SessionNmeaSharePlan::outputNmea),
        )
    }

    @Test
    fun `empty nmea files are not offered`() {
        val session = Files.createDirectory(tempDir.resolve("session-a"))
        Files.writeString(session.resolve("receiver-solution.nmea"), "")
        Files.writeString(session.resolve("rtklib-solution.nmea"), "\$GNGGA,rtklib\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(
            sessionDirectories = listOf(session),
            outputDirectory = cache,
        )

        assertEquals(listOf(SessionNmeaSource.RTKLIB_REALTIME), selection.plans.map(SessionNmeaSharePlan::source))
        assertEquals(0, selection.skippedCount)
        assertTrue(selection.hasShareableNmea)
    }

    @Test
    fun `selection reports skipped session when no nmea sources exist`() {
        val session = Files.createDirectory(tempDir.resolve("session"))
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(listOf(session), cache)

        assertEquals(emptyList<SessionNmeaSharePlan>(), selection.plans)
        assertEquals(1, selection.skippedCount)
        assertFalse(selection.hasShareableNmea)
    }

    @Test
    fun `export copies selected nmea bytes without altering source`() {
        val session = Files.createDirectory(tempDir.resolve("session"))
        val source = session.resolve("rtklib-solution.nmea")
        Files.writeString(source, "\$GPGGA,data\n\$GPRMC,data\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))
        val plan = SessionNmeaSharePlan(
            source = SessionNmeaSource.RTKLIB_REALTIME,
            sourceNmea = source,
            outputNmea = cache.resolve("session-rtklib-realtime.nmea"),
        )

        val output = SessionNmeaExporter.export(plan)

        assertEquals("\$GPGGA,data\n\$GPRMC,data\n", Files.readString(output))
        assertEquals("\$GPGGA,data\n\$GPRMC,data\n", Files.readString(source))
    }

    @Test
    fun `export rejects an active filesystem session at the sharing boundary`() {
        val session = Files.createDirectory(tempDir.resolve("session"))
        val source = session.resolve("receiver-solution.nmea")
        Files.writeString(source, "\$GPGGA,data\n")
        val output = tempDir.resolve("cache/session.nmea")
        val plan = SessionNmeaSharePlan(SessionNmeaSource.RECEIVER_SOLUTION, source, output)

        ActiveRecordingSessionRegistry.activate(session.toString())
        try {
            assertThrows(IllegalArgumentException::class.java) { SessionNmeaExporter.export(plan) }
            assertFalse(Files.exists(output))
        } finally {
            ActiveRecordingSessionRegistry.deactivate(session.toString())
        }
    }

    @Test
    fun `failed export removes final and temporary cache artifacts`() {
        val session = Files.createDirectory(tempDir.resolve("broken-session"))
        val source = session.resolve("receiver-solution.nmea")
        val cache = Files.createDirectory(tempDir.resolve("cache"))
        val output = cache.resolve("session.nmea")
        val plan = SessionNmeaSharePlan(SessionNmeaSource.RECEIVER_SOLUTION, source, output)

        assertThrows(Exception::class.java) { SessionNmeaExporter.export(plan) }

        assertFalse(Files.exists(output))
        Files.list(cache).use { files -> assertEquals(0L, files.count()) }
    }

    @Test
    fun `multi-plan export removes earlier outputs when a later export fails`() {
        val cache = Files.createDirectory(tempDir.resolve("multi-cache"))
        val plans = listOf(
            SessionNmeaSharePlan(
                SessionNmeaSource.RECEIVER_SOLUTION,
                tempDir.resolve("first-source"),
                cache.resolve("first.nmea"),
            ),
            SessionNmeaSharePlan(
                SessionNmeaSource.RTKLIB_FORWARD,
                tempDir.resolve("second-source"),
                cache.resolve("second.nmea"),
            ),
        )

        assertThrows(IllegalStateException::class.java) {
            SessionNmeaExporter.exportAll(plans, onBeforeExport = {}) { plan ->
                if (plan === plans.last()) error("second export failed")
                Files.writeString(plan.outputNmea, "first")
            }
        }

        assertFalse(Files.exists(plans.first().outputNmea))
        Files.list(cache).use { files -> assertEquals(0L, files.count()) }
    }
}
