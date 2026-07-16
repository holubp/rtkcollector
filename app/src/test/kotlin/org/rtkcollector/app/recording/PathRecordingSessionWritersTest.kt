package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rtkcollector.core.session.SessionArtifactFile
import java.nio.file.Files
import java.nio.file.Path

class PathRecordingSessionWritersTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `flushRaw flushes receiver rx only and closeAll delegates finalisation`() {
        val writers = PathRecordingSessionWriters.open(tempDir)
        writers.appendReceiverRx(byteArrayOf(0x01, 0x02))
        writers.appendTxToReceiver(byteArrayOf(0x10, 0x11))

        writers.flushRaw()

        assertEquals(2L, Files.size(tempDir.resolve(SessionArtifactFile.RECEIVER_RX_RAW.fileName)))
        assertEquals(0L, Files.size(tempDir.resolve(SessionArtifactFile.TX_TO_RECEIVER_RAW.fileName)))

        val report = writers.closeAll()

        assertTrue(report.issues.isEmpty())
        assertEquals(2L, Files.size(tempDir.resolve(SessionArtifactFile.TX_TO_RECEIVER_RAW.fileName)))
    }
}
