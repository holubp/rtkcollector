package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rtkcollector.app.testing.TestFiles
import org.rtkcollector.receiver.ublox.UbloxFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class ReceiverNmeaReexporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `ublox nav pvt raw regenerates receiver solution nmea`() {
        val raw = tempDir.resolve("receiver-rx.raw")
        Files.write(raw, ubloxNavPvtFrame())
        val output = tempDir.resolve("receiver-solution.nmea")

        val result = ReceiverNmeaReexporter.reexportReceiverRxRaw(
            receiverRxRaw = raw,
            outputNmea = output,
            receiverFamily = "ublox-m8t",
        )

        val text = TestFiles.readString(output)
        assertEquals(1L, result.sentencesWritten)
        assertTrue(text.startsWith("\$GNGGA,120000.000,"))
        assertTrue(text.contains(",2,12,"))
    }

    @Test
    fun `ublox stream passes through existing nmea when no nav pvt is available`() {
        val raw = tempDir.resolve("receiver-rx.raw")
        TestFiles.writeString(raw, "\$GNGGA,120000,5000.0,N,01400.0,E,1,12,0.8,300.0,M,0.0,M,,*00\r\n")
        val output = tempDir.resolve("receiver-solution.nmea")

        val result = ReceiverNmeaReexporter.reexportReceiverRxRaw(
            receiverRxRaw = raw,
            outputNmea = output,
            receiverFamily = "ublox-m8t",
        )

        assertEquals(1L, result.sentencesWritten)
        assertEquals(
            "\$GNGGA,120000,5000.0,N,01400.0,E,1,12,0.8,300.0,M,0.0,M,,*00\r\n",
            TestFiles.readString(output),
        )
    }

    @Test
    fun `empty regeneration leaves previous nmea intact`() {
        val raw = tempDir.resolve("receiver-rx.raw")
        Files.write(raw, byteArrayOf(0x01, 0x02, 0x03))
        val output = tempDir.resolve("receiver-solution.nmea")
        TestFiles.writeString(output, "\$GNGGA,old\n")

        val result = ReceiverNmeaReexporter.reexportReceiverRxRaw(
            receiverRxRaw = raw,
            outputNmea = output,
            receiverFamily = "ublox-m8t",
        )

        assertEquals(0L, result.sentencesWritten)
        assertEquals("\$GNGGA,old\n", TestFiles.readString(output))
    }

    private fun ubloxNavPvtFrame(): ByteArray {
        val payload = ByteArray(92)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0, 43_200_000)
        buffer.putShort(4, 2026.toShort())
        buffer.put(6, 6)
        buffer.put(7, 22)
        buffer.put(8, 12)
        buffer.put(9, 0)
        buffer.put(10, 0)
        buffer.put(11, 0x07)
        buffer.putInt(12, 0)
        buffer.putInt(16, 0)
        buffer.put(20, 0x03)
        buffer.put(21, 0x03)
        buffer.put(23, 12)
        buffer.putInt(24, (14.4212534 * 1e7).toInt())
        buffer.putInt(28, (50.0874512 * 1e7).toInt())
        buffer.putInt(32, (337.4 * 1000.0).toInt())
        buffer.putInt(36, (287.4 * 1000.0).toInt())
        buffer.putInt(40, 800)
        buffer.putInt(44, 1200)
        return UbloxFrame.build(0x01, 0x07, payload)
    }
}
