package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.testing.TestFiles
import java.nio.file.Files
import java.nio.file.Path

class SafSessionActionsSourceTest {
    @Test
    fun `SAF destructive entry points hold a session operation lease`() {
        val source = TestFiles.readString(sourceFile())

        assertTrue(source.contains("ActiveRecordingSessionRegistry.withDestructiveOperation(sessionUri.toString(), \"archive\")"))
        assertTrue(source.contains("deleteRecordingWhileDestructiveOperationIsLeased(resolver, sessionUri)"))
        assertTrue(source.contains("ActiveRecordingSessionRegistry.withDestructiveOperation(uri.toString(), \"delete\")"))
        assertTrue(source.contains("ActiveRecordingSessionRegistry.requireInactive(archiveUri.toString(), \"restore\")"))
    }

    @Test
    fun `SAF restore requires session shape and reuses nested parent directories`() {
        val source = TestFiles.readString(sourceFile())

        assertTrue(source.contains("ArchiveIntegrity.requireSessionArchive(ArchiveIntegrity.inspect(input, integrityPolicy))"))
        assertTrue(source.contains("val existing = current.findChild(resolver, directoryName)"))
        assertTrue(source.contains("require(existing.isDirectory)"))
        assertTrue(source.contains("ArchiveIntegrity.verifySourcesAgainstManifest("))
        assertTrue(source.contains("visitedDirectories.add(directory.toString())"))
        assertTrue(source.contains("depth <= integrityPolicy.maxPathDepth"))
    }

    @Test
    fun `SAF archive lease starts before session export`() {
        val source = TestFiles.readString(sourceFile())
        val archiveLease = source.indexOf(
            "ActiveRecordingSessionRegistry.withDestructiveOperation(sessionUri.toString(), \"archive\")",
        )
        val export = source.indexOf("exportSessionZip(", archiveLease)

        assertTrue(archiveLease >= 0 && export > archiveLease)
    }

    @Test
    fun `SAF temporary share holds operation lease and checks before source streams`() {
        val source = TestFiles.readString(sourceFile())
        val shareLease = source.indexOf(
            "ActiveRecordingSessionRegistry.withDestructiveOperation(sessionUri.toString(), \"share\")",
        )
        val export = source.indexOf("exportSessionZip(", shareLease)
        val guard = source.indexOf(
            "ActiveRecordingSessionRegistry.requireInactive(sessionUri.toString(), \"share\")",
            export,
        )
        val sourceOpen = source.indexOf("resolver.openInputStream(file.uri)", guard)

        assertTrue(shareLease >= 0 && export > shareLease && guard > export && sourceOpen > guard)
        assertTrue(source.contains("Files.deleteIfExists(output)"))
    }

    @Test
    fun `SAF temporary NMEA shares hold operation lease and check before source streams`() {
        val source = TestFiles.readString(sourceFile())
        val nmeaEntry = source.indexOf("fun createTemporaryNmeaShares")
        val shareLease = source.indexOf(
            "ActiveRecordingSessionRegistry.withDestructiveOperation(sessionUri.toString(), \"share\")",
            nmeaEntry,
        )
        val guard = source.indexOf(
            "ActiveRecordingSessionRegistry.requireInactive(sessionUri.toString(), \"share\")",
            nmeaEntry,
        )
        val sourceOpen = source.indexOf("resolver.openInputStream(child.uri)", guard)

        assertTrue(nmeaEntry >= 0 && shareLease > nmeaEntry && guard > nmeaEntry && sourceOpen > guard)
        assertTrue(source.contains("val temporary = Files.createTempFile(cacheRoot, outputName, \".tmp\")"))
        assertTrue(source.contains("Files.move(temporary, output"))
        assertTrue(source.contains("(outputs + temporaries).forEach"))
    }

    private fun sourceFile(): Path = TestFiles.locateProjectPath(
        "app/src/main/kotlin/org/rtkcollector/app/sessions/SafSessionActions.kt",
        Files::isRegularFile,
    )
}
