package org.rtkcollector.core.session

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SessionWritersTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `session writers append to distinct artifact files`() {
        val writers = SessionWriters.open(tempDir)

        writers.writeSessionJson("""{"sessionUuid":"test-session"}""")
        writers.appendReceiverRx(byteArrayOf(0x01, 0x02))
        writers.appendReceiverRx(byteArrayOf(0x03))
        writers.appendTxToReceiver(byteArrayOf(0x10))
        writers.appendTxToReceiver(byteArrayOf(0x11, 0x12))
        writers.appendCorrectionInput(byteArrayOf(0x20, 0x21))
        writers.appendCorrectionInput(byteArrayOf(0x22))
        writers.appendEventJson("""{"event":"started"}""")
        writers.appendEventJson("""{"event":"stopped"}""")
        writers.appendQualityLiveJson("""{"fix":"float"}""")
        writers.appendQualityLiveJson("""{"fix":"rtk"}""")
        writers.flush()
        writers.close()

        assertEquals("""{"sessionUuid":"test-session"}""", Files.readString(tempDir.resolve("session.json")).trim())
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), Files.readAllBytes(tempDir.resolve("receiver-rx.raw")))
        assertArrayEquals(byteArrayOf(0x10, 0x11, 0x12), Files.readAllBytes(tempDir.resolve("tx-to-receiver.raw")))
        assertArrayEquals(byteArrayOf(0x20, 0x21, 0x22), Files.readAllBytes(tempDir.resolve("correction-input.raw")))
        assertEquals(
            listOf("""{"event":"started"}""", """{"event":"stopped"}"""),
            Files.readAllLines(tempDir.resolve("events.jsonl")),
        )
        assertEquals(
            listOf("""{"fix":"float"}""", """{"fix":"rtk"}"""),
            Files.readAllLines(tempDir.resolve("quality-live.jsonl")),
        )
    }

    @Test
    fun `export metadata contains redacted ntrip metadata only`() {
        val metadata = SessionMetadata(
            appVersion = "0.1.0",
            androidDeviceModel = "test-device",
            androidVersion = "test-android",
            receiverDriverId = "generic-nmea-rtcm",
            receiverIdentification = null,
            usbVid = null,
            usbPid = null,
            baudRate = 921600,
            serialParameters = SerialParameters(),
            mode = SessionMode.ROVER,
            startedAt = "2026-06-07T10:00:00Z",
            stoppedAt = null,
            ntrip = NtripSessionMetadata(
                casterHost = "caster.example",
                mountpoint = "MOUNT",
                usernamePresent = true,
            ),
            antenna = AntennaMetadata(),
            sessionUuid = "00000000-0000-0000-0000-000000000001",
            linkedBaseSessionUuid = null,
        )

        val exported = exportSessionMetadata(metadata)

        assertFalse(exported.contains("password", ignoreCase = true))
        assertFalse(exported.contains("token", ignoreCase = true))
        assertFalse(exported.contains("secret", ignoreCase = true))
        assertEquals(true, exported.contains("usernamePresent"))
    }
}
