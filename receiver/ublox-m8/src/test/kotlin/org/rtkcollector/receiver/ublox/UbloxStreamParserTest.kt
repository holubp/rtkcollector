package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UbloxStreamParserTest {
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
}
