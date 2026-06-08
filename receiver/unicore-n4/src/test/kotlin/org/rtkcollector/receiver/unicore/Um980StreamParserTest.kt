package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Um980StreamParserTest {
    @Test
    fun `classifies nmea ascii binary and noise records`() {
        val binary = Um980BinaryParserTest.bestnavbFrame()
        val bytes = "xx\$GPGGA,123519,4807.038,N,01131.000,E,4,12,0.8,545.4,M,46.9,M,,*47\r\n".encodeToByteArray() +
            "#BESTNAVA,COM1,0,80.0,FINE,2300,1000,0,0,18,0;SOL_COMPUTED,NARROW_INT,50.0,14.0,300.0,0.1,0.1,0.2,12,12*00000000\r\n".encodeToByteArray() +
            binary

        val records = Um980StreamParser().accept(bytes)

        assertEquals(listOf("noise", "nmea", "unicore_ascii", "unicore_binary"), records.map { it.kind })
        assertEquals(binary.toList(), records.last().bytes.toList())
    }

    @Test
    fun `invalid binary crc is emitted as noise`() {
        val invalid = Um980BinaryParserTest.bestnavbFrame().also { it[it.lastIndex] = (it.last() + 1).toByte() }

        val records = Um980StreamParser().accept(invalid)

        assertEquals(listOf("noise"), records.map { it.kind })
        assertEquals(invalid.toList(), records.single().bytes.toList())
    }

    @Test
    fun `partial binary frame is buffered until complete`() {
        val binary = Um980BinaryParserTest.bestnavbFrame()
        val parser = Um980StreamParser()

        assertEquals(emptyList<Um980StreamRecord>(), parser.accept(binary.copyOfRange(0, 40)))
        val records = parser.accept(binary.copyOfRange(40, binary.size))

        assertEquals(listOf("unicore_binary"), records.map { it.kind })
        assertEquals(binary.toList(), records.single().bytes.toList())
    }
}
