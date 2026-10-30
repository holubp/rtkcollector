package org.rtkcollector.core.rtklib

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RtklibOutputWritersTest {
    @Test
    fun `owned output failures cannot skip remaining flushes or closes`() {
        val attempts = mutableListOf<String>()
        fun output(name: String) = object : java.io.OutputStream() {
            override fun write(value: Int) = Unit
            override fun flush() { attempts += "$name flush"; throw java.io.IOException("$name flush") }
            override fun close() { attempts += "$name close"; throw java.io.IOException("$name close") }
        }
        val writers = RtklibOutputWriters(output("nmea"), output("pos"))
        val failure = assertFailsWith<java.io.IOException> { writers.close() }
        assertEquals(listOf("nmea flush", "pos flush", "nmea close", "pos close"), attempts)
        assertEquals("nmea flush", failure.message)
        assertEquals(3, failure.suppressed.size)
        assertFailsWith<IllegalStateException> { writers.write(RtklibNativeOutputBatch()) }
    }
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
