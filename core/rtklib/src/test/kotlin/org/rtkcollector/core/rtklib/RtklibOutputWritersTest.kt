package org.rtkcollector.core.rtklib

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class RtklibOutputWritersTest {
    @Test
    fun `NMEA and POS lines are written to separate streams`() {
        val nmea = ByteArrayOutputStream()
        val pos = ByteArrayOutputStream()
        val writers = RtklibOutputWriters(nmea, pos)

        writers.write(
            RtklibNativeOutputBatch(
                nmeaLines = listOf("\$GPGGA,rtklib"),
                posLines = listOf("% pos header", "2026/01/01 00:00:00 50.0 14.0"),
            ),
        )
        writers.close()

        assertEquals("\$GPGGA,rtklib\n", nmea.toString(Charsets.US_ASCII.name()))
        assertEquals("% pos header\n2026/01/01 00:00:00 50.0 14.0\n", pos.toString(Charsets.US_ASCII.name()))
    }

    @Test
    fun `callback writers append newline terminated lines`() {
        val nmea = mutableListOf<String>()
        val pos = mutableListOf<String>()
        val writers = RtklibOutputWriters.fromCallbacks(
            appendNmeaLine = nmea::add,
            appendPosLine = pos::add,
        )

        writers.write(
            RtklibNativeOutputBatch(
                nmeaLines = listOf("\$GPGGA,rtklib"),
                posLines = listOf("pos line\n"),
            ),
        )
        writers.close()

        assertEquals(listOf("\$GPGGA,rtklib\n"), nmea)
        assertEquals(listOf("pos line\n"), pos)
    }
}
