package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class Um980NmeaReexporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reexports generated nmea from receiver rx raw with selected ppp mapping`() {
        val receiverRx = tempDir.resolve("receiver-rx.raw")
        Files.write(
            receiverRx,
            byteArrayOf(1, 2, 3) +
                Um980BinaryParserTest.bestnavbFrame(positionType = 69) +
                byteArrayOf(4, 5),
        )
        val output = tempDir.resolve("receiver-solution.nmea")

        val result = Um980NmeaReexporter.reexportReceiverRxRaw(
            receiverRxRaw = receiverRx,
            outputNmea = output,
            options = Um980NmeaExportOptions(pppGgaQuality = 9),
        )

        val text = Files.readString(output)
        assertEquals(output, result.outputNmea)
        assertEquals(3, result.sentencesWritten)
        assertTrue(text.startsWith("\$GPGGA,124914.000,5005.247074,N,01425.275207,E,9,18,"))
        assertTrue("\$GPRMC" in text)
        assertTrue("\$GPVTG" in text)
    }

    @Test
    fun `reexport replaces existing nmea atomically after parsing`() {
        val receiverRx = tempDir.resolve("receiver-rx.raw")
        val output = tempDir.resolve("receiver-solution.nmea")
        Files.write(receiverRx, Um980BinaryParserTest.bestnavbFrame(positionType = 17))
        Files.writeString(output, "old\n")

        val result = Um980NmeaReexporter.reexportReceiverRxRaw(
            receiverRxRaw = receiverRx,
            outputNmea = output,
            options = Um980NmeaExportOptions(pppGgaQuality = 2),
        )

        val text = Files.readString(output)
        assertEquals(3, result.sentencesWritten)
        assertTrue(text.startsWith("\$GPGGA,124914.000,5005.247074,N,01425.275207,E,2,18,"))
        assertTrue(!text.contains("old"))
    }

    @Test
    fun `reexport preserves valid source nmea and generated binary nmea`() {
        val sourceNmea =
            "\$GNGGA,151437.150,4914.6094192,N,01634.8215775,E,2,8,,278.536,M,44.249,M,2.2,1022*79\r\n"
        val receiverRx = tempDir.resolve("receiver-rx.raw")
        Files.write(
            receiverRx,
            sourceNmea.toByteArray(Charsets.US_ASCII) +
                Um980BinaryParserTest.bestnavbFrame(positionType = 17),
        )
        val output = tempDir.resolve("receiver-solution.nmea")

        val result = Um980NmeaReexporter.reexportReceiverRxRaw(
            receiverRxRaw = receiverRx,
            outputNmea = output,
        )

        val text = Files.readString(output)
        assertEquals(4, result.sentencesWritten)
        assertTrue(text.startsWith(sourceNmea))
        assertTrue("\$GPGGA,124914.000,5005.247074,N,01425.275207,E,2,18," in text)
    }
}
