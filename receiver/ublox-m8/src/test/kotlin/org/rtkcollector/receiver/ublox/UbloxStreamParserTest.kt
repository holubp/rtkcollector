package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UbloxStreamParserTest {
    @Test
    fun `preserves a valid UBX frame at every input split point`() {
        val frame = UbloxFrame.build(0x01, 0x07, byteArrayOf(0xB5.toByte(), 0x62, 0x00, 0x7f))

        for (split in 1 until frame.size) {
            val parser = UbloxStreamParser()
            val records = parser.accept(frame.copyOfRange(0, split)) +
                parser.accept(frame.copyOfRange(split, frame.size))

            assertEquals(listOf("ubx"), records.map { it.kind }, "split point $split")
            assertEquals(frame.toList(), records.single().bytes.toList(), "split point $split")
        }
    }

    @Test
    fun `preserves UBX frame with one byte feeds`() {
        val frame = UbloxFrame.build(0x02, 0x15, ByteArray(20) { it.toByte() })
        val parser = UbloxStreamParser()
        val records = frame.flatMap { parser.accept(byteArrayOf(it)) }

        assertEquals(listOf("ubx"), records.map { it.kind })
        assertEquals(frame.toList(), records.single().bytes.toList())
    }

    @Test
    fun `repeated sync prefix noise keeps the final possible sync byte`() {
        val frame = UbloxFrame.build(0x01, 0x07, byteArrayOf(0x42))
        val parser = UbloxStreamParser()
        val records = parser.accept(byteArrayOf(0xB5.toByte(), 0xB5.toByte())) +
            parser.accept(frame.copyOfRange(1, frame.size))

        assertEquals(listOf("noise", "ubx"), records.map { it.kind })
        assertEquals(frame.toList(), records.last().bytes.toList())
    }

    @Test
    fun `frames binary data between NMEA lines when UBX sync is split`() {
        val nmeaBefore = "\$GNGGA,one\r\n".encodeToByteArray()
        val nmeaAfter = "\$GNRMC,two\r\n".encodeToByteArray()
        val frame = UbloxFrame.build(0x02, 0x15, byteArrayOf(1, 2, 3))
        val parser = UbloxStreamParser()
        val first = parser.accept(nmeaBefore + frame.copyOfRange(0, 1))
        val second = parser.accept(frame.copyOfRange(1, frame.size) + nmeaAfter)
        val records = first + second

        assertEquals(listOf("nmea", "ubx", "nmea"), records.map { it.kind })
        assertEquals(frame.toList(), records[1].bytes.toList())
    }

    @Test
    fun `accepts the maximum 16 bit UBX payload in bounded fragments`() {
        val frame = UbloxFrame.build(0x01, 0x07, ByteArray(65_535) { it.toByte() })
        val parser = UbloxStreamParser()

        assertEquals(0, parser.accept(frame.copyOfRange(0, 32_768)).size)
        val records = parser.accept(frame.copyOfRange(32_768, frame.size))

        assertEquals(listOf("ubx"), records.map { it.kind })
        assertEquals(frame.toList(), records.single().bytes.toList())
    }

    @Test
    fun `resynchronizes to following valid frame after corrupt checksum`() {
        val corrupt = UbloxFrame.build(0x01, 0x07, byteArrayOf(1, 2, 3)).also {
            it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte()
        }
        val valid = UbloxFrame.build(0x02, 0x15, byteArrayOf(4, 5))
        val records = UbloxStreamParser().accept(corrupt + valid)

        assertEquals("ubx", records.last().kind)
        assertEquals(valid.toList(), records.last().bytes.toList())
    }

    @Test
    fun `extracts ubx frame from noise`() {
        val frame = UbloxFrame.build(0x01, 0x07, ByteArray(92))
        val records = UbloxStreamParser().accept(byteArrayOf(0x55) + frame + byteArrayOf(0x33))

        assertEquals(listOf("noise", "ubx", "noise"), records.map { it.kind })
        assertEquals(frame.toList(), records[1].bytes.toList())
    }

    @Test
    fun `holds incomplete ubx frame until complete`() {
        val frame = UbloxFrame.build(0x01, 0x07, ByteArray(92))
        val parser = UbloxStreamParser()

        assertEquals(emptyList<UbloxStreamRecord>(), parser.accept(frame.copyOfRange(0, 20)))
        val records = parser.accept(frame.copyOfRange(20, frame.size))

        assertEquals(1, records.size)
        assertEquals("ubx", records.single().kind)
    }

    @Test
    fun `extracts nmea line separately`() {
        val records = UbloxStreamParser().accept("\$GNGGA,120000,5000.0,N,01400.0,E,1,12,0.8,300.0,M,0.0,M,,*00\r\n".encodeToByteArray())

        assertEquals("nmea", records.single().kind)
    }

    @Test
    fun `discards unterminated oversized NMEA across small feeds without event growth`() {
        val parser = UbloxStreamParser()
        assertEquals(emptyList<UbloxStreamRecord>(), parser.accept(byteArrayOf('$'.code.toByte())))
        repeat(9_000) {
            assertEquals(emptyList<UbloxStreamRecord>(), parser.accept(byteArrayOf('x'.code.toByte())))
        }

        val valid = "\$GNRMC,recovered\r\n".encodeToByteArray()
        val records = parser.accept(byteArrayOf('\n'.code.toByte()) + valid)

        assertEquals(listOf("nmea"), records.map { it.kind })
        assertEquals(valid.toList(), records.single().bytes.toList())
    }

    @Test
    fun `discards giant LF terminated NMEA and recovers following NMEA and UBX`() {
        val validNmea = "\$GNGGA,recovered\r\n".encodeToByteArray()
        val frame = UbloxFrame.build(0x01, 0x07, byteArrayOf(1, 2, 3))
        val oversized = "\$".encodeToByteArray() + ByteArray(20_000) { 'x'.code.toByte() } + byteArrayOf('\n'.code.toByte())

        val records = UbloxStreamParser().accept(oversized + validNmea + frame)

        assertEquals(listOf("nmea", "ubx"), records.map { it.kind })
        assertEquals(validNmea.toList(), records[0].bytes.toList())
        assertEquals(frame.toList(), records[1].bytes.toList())
    }

    @Test
    fun `does not inspect NMEA markers inside complete UBX payload`() {
        val payload = byteArrayOf('$'.code.toByte(), '\n'.code.toByte(), 1, 2)
        val frame = UbloxFrame.build(0x01, 0x07, payload)

        val records = UbloxStreamParser().accept(frame)

        assertEquals(listOf("ubx"), records.map { it.kind })
        assertEquals(frame.toList(), records.single().bytes.toList())
    }
}
