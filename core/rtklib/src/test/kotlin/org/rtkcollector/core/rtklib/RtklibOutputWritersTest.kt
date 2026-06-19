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
}
