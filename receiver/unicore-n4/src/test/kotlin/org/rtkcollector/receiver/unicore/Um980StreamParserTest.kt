package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Um980StreamParserTest {
    @Test
    fun `nmea health requires a complete printable sane checksummed sentence`() {
        val valid = "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47\r\n"
        assertTrue(ReceiverProtocolHealth.isValidNmea(valid.encodeToByteArray()))
        assertTrue(ReceiverProtocolHealth.isValidNmea(valid.replace("\r\n", "\n").encodeToByteArray()))
        listOf(
            valid.replace("*47", "*00"),
            valid.substringBefore('*') + "\r\n",
            valid.replace("*47", "*4"),
            valid.replace("*47", "*047"),
            valid.replace("*47", "*GG"),
            valid.replace("*47", "*47 "),
            valid.trimEnd(),
            "\$garbage\r\n",
            nmea("GPGGA"),
            nmea("gpgga,1"),
            nmea("GPGGA,"),
            nmea("GP,1"),
            nmea("GPGGA,1\u0000"),
            nmea("GPGGA,1\t"),
            nmea("GPGGA,1\r"),
            nmea("GPGGA,1\n"),
            nmea("GPGGA,\$GPGGA,1"),
            nmea("GPGGA,#noise"),
            nmea("GPGGA,\u00e9"),
            nmea("GPGGA," + "1".repeat(8 * 1024)),
        ).forEach { assertFalse(ReceiverProtocolHealth.isValidNmea(it.encodeToByteArray()), it.take(100)) }
    }

    @Test
    fun `nmea health supports ublox proprietary and Unicore extended addresses`() {
        listOf("PUBX,00,123519.00", "GPGGAH,123519.00", "GPTHS2,1.2,A").forEach {
            assertTrue(ReceiverProtocolHealth.isValidNmea(nmea(it).encodeToByteArray()), it)
        }
        val rmc = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6a\r\n"
        assertTrue(ReceiverProtocolHealth.isValidNmea(rmc.encodeToByteArray()))
    }

    @Test
    fun `Unicore health verifies published VERSIONA crc not NMEA xor or java crc defaults`() {
        val bytes = documentedVersion().encodeToByteArray()
        val original = bytes.copyOf()
        assertTrue(ReceiverProtocolHealth.isValidUnicoreAscii(bytes))
        assertTrue(ReceiverProtocolHealth.isValidUnicoreAscii(documentedVersion().replace("769fd54f", "769FD54F").encodeToByteArray()))
        assertEquals(original.toList(), bytes.toList())
        listOf(
            documentedVersion().replace("769fd54f", "00000000"),
            documentedVersion().replace("769fd54f", "769fd54"),
            documentedVersion().replace("769fd54f", "0769fd54f"),
            documentedVersion().replace("769fd54f", "769fd54z"),
            documentedVersion().replace("*769fd54f", "*4f"),
            documentedVersion().substringBefore('*') + "\r\n",
            documentedVersion().replace("UM982", "UM980"),
            documentedVersion().replace("VERSIONA", "versiona"),
            documentedVersion().replace(';', ','),
            documentedVersion().replace("UM982", "UM\u0000982"),
            documentedVersion().replace("UM982", "UM\u00e9982"),
            documentedVersion().replace("\r\n", " \r\n"),
            documentedVersion().trimEnd(),
            "#noise\r\n",
            "#" + "1".repeat(64 * 1024) + "\r\n",
        ).forEach { assertFalse(ReceiverProtocolHealth.isValidUnicoreAscii(it.encodeToByteArray()), it.take(100)) }
    }

    @Test
    fun `classification alone including old fake checksums is not health evidence`() {
        val input = "\$GPGGA,1*00\r\n#BESTNAVA,COM1,0,80.0,FINE,2300,1000,0,0,18,0;SOL_COMPUTED*00000000\r\n".encodeToByteArray()
        val original = input.copyOf()
        val records = Um980StreamParser().accept(input)
        assertEquals(listOf("nmea", "unicore_ascii"), records.map { it.kind })
        assertFalse(ReceiverProtocolHealth.isValidNmea(records[0].bytes))
        assertFalse(ReceiverProtocolHealth.isValidUnicoreAscii(records[1].bytes))
        assertEquals(original.toList(), records.flatMap { it.bytes.toList() })
        assertEquals(original.toList(), input.toList())
    }

    @Test
    fun `strict admission rejects position text while compatibility parsers stay permissive`() {
        val gga = "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,\r\n".encodeToByteArray()
        val bestnav = "#BESTNAVA,94,GPS,FINE,2190,117395000,0,0,18,17;SOL_COMPUTED,NARROW_INT,50.0,14.0,300.0*00000000\r\n".encodeToByteArray()
        assertEquals(1, NmeaGgaParser().accept(gga).size, "compatibility API still accepts checksumless GGA")
        assertEquals(1, Um980AsciiSolutionParser().accept(bestnav).size, "compatibility API remains permissive")
        assertFalse(ReceiverProtocolHealth.isValidNmea(gga))
        assertFalse(ReceiverProtocolHealth.isValidUnicoreAscii(bestnav))

        var positionsAdmitted = 0
        Um980StreamParser().accept(gga + bestnav).forEach { record ->
            val validProtocolFrame = when (record.kind) {
                "nmea" -> ReceiverProtocolHealth.isValidNmea(record.bytes)
                "unicore_ascii" -> ReceiverProtocolHealth.isValidUnicoreAscii(record.bytes)
                else -> false
            }
            if (!validProtocolFrame) return@forEach
            positionsAdmitted += when (record.kind) {
                "nmea" -> NmeaGgaParser().accept(record.bytes).size
                "unicore_ascii" -> Um980AsciiSolutionParser().accept(record.bytes).size
                else -> 0
            }
        }
        assertEquals(0, positionsAdmitted)
    }

    @Test
    fun `valid text health survives every input split point`() {
        listOf(
            nmea("PUBX,00,123519.00") to "nmea",
            documentedVersion() to "unicore_ascii",
        ).forEach { (line, kind) ->
            val input = line.encodeToByteArray()
            (0..input.size).forEach { split ->
                val parser = Um980StreamParser()
                val records = parser.accept(input.copyOfRange(0, split)) + parser.accept(input.copyOfRange(split, input.size))
                assertEquals(kind, records.single().kind, "split $split")
                val record = records.single()
                val valid = if (kind == "nmea") ReceiverProtocolHealth.isValidNmea(record.bytes)
                    else ReceiverProtocolHealth.isValidUnicoreAscii(record.bytes)
                assertTrue(valid, "split $split")
                assertEquals(input.toList(), record.bytes.toList())
            }
        }
    }

    private fun nmea(body: String): String {
        val checksum = body.fold(0) { crc, char -> crc xor char.code }
        return "\$$body*${checksum.toString(16).padStart(2, '0')}\r\n"
    }

    // Unicore N4 reference manual V2 EN R1.6, section 7.3.1, printed p.133.
    // Only visual PDF line wraps are removed; the published CRC is unchanged.
    private fun documentedVersion(): String =
        "#VERSIONA,79,GPS,FINE,2326,378237000,15434,0,18,889;\"UM982\",\"R4.10Build15434\"," +
            "\"HRPT00-S10C-P\",\"2310415000012-LR23A2225208904\",\"ff2740966a10124c\",\"2024/08/08\"*769fd54f\r\n"

    @Test
    fun `unterminated text pending is bounded cumulatively across small chunks`() {
        listOf('$' to 8 * 1024, '#' to 64 * 1024).forEach { (prefix, limit) ->
            val parser = Um980StreamParser()
            val input = "$prefix" + "x".repeat(limit + 256)
            val output = input.encodeToByteArray().asList().chunked(127).flatMap {
                val records = parser.accept(it.toByteArray())
                assertTrue(pendingBytes(parser) <= limit, "pending must be bounded after every feed for $prefix")
                records
            }
            assertTrue(pendingBytes(parser) <= limit, "pending must be bounded for $prefix")
            assertTrue(output.isNotEmpty(), "oversized text must be released as noise for $prefix")
            val tail = parser.accept("\r\n".encodeToByteArray())
            assertTrue((output + tail).all { it.kind == "noise" })
            assertEquals((input + "\r\n").encodeToByteArray().toList(), (output + tail).flatMap { it.bytes.toList() })
        }
    }

    @Test
    fun `complete oversized text resynchronises at following text start without losing bytes`() {
        listOf('$' to 8 * 1024, '#' to 64 * 1024).forEach { (prefix, limit) ->
            val noise = ("$prefix" + "x".repeat(limit)).encodeToByteArray()
            val valid = "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47\r\n".encodeToByteArray()
            val input = noise + valid
            val records = Um980StreamParser().accept(input)

            assertEquals(listOf("noise", "nmea"), records.map { it.kind })
            assertEquals(input.toList(), records.flatMap { it.bytes.toList() })
            assertEquals(valid.toList(), records.last().bytes.toList())
        }
    }

    @Test
    fun `oversized text preserves split binary sync and frame bytes`() {
        listOf('$' to 8 * 1024, '#' to 64 * 1024).forEach { (prefix, limit) ->
            (1..3).forEach { split ->
                val parser = Um980StreamParser()
                val noise = ("$prefix" + "x".repeat(limit)).encodeToByteArray()
                val binary = Um980BinaryParserTest.bestnavbFrame()
                val first = parser.accept(noise + binary.copyOfRange(0, split))
                val second = parser.accept(binary.copyOfRange(split, binary.size))

                assertEquals(listOf("noise", "unicore_binary"), (first + second).map { it.kind })
                assertEquals((noise + binary).toList(), (first + second).flatMap { it.bytes.toList() })
                assertEquals(binary.toList(), second.last().bytes.toList())
            }
        }
    }

    @Test
    fun `text limits include terminators and exact boundary remains framed`() {
        listOf('$' to 8 * 1024, '#' to 64 * 1024).forEach { (prefix, limit) ->
            val kind = if (prefix == '$') "nmea" else "unicore_ascii"
            val boundary = ("$prefix" + "x".repeat(limit - 3) + "\r\n").encodeToByteArray()
            assertEquals(kind, Um980StreamParser().accept(boundary).single().kind)
            val oversized = ("$prefix" + "x".repeat(limit - 2) + "\r\n").encodeToByteArray()
            val records = Um980StreamParser().accept(oversized)
            assertTrue(records.all { it.kind == "noise" }, "one byte over limit must be noise")
            assertEquals(oversized.toList(), records.flatMap { it.bytes.toList() })
        }
    }

    private fun pendingBytes(parser: Um980StreamParser): Int {
        val field = Um980StreamParser::class.java.getDeclaredField("pending")
        field.isAccessible = true
        return (field.get(parser) as ByteArray).size
    }

    @Test
    fun `classifies nmea ascii binary and noise records`() {
        val binary = Um980BinaryParserTest.bestnavbFrame()
        assertEquals(0, binary[3].toInt())
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

    @Test
    fun `binary sync can be split across chunks`() {
        val binary = Um980BinaryParserTest.bestnavbFrame()
        val parser = Um980StreamParser()

        assertEquals(emptyList<Um980StreamRecord>(), parser.accept(binary.copyOfRange(0, 2)))
        val records = parser.accept(binary.copyOfRange(2, binary.size))

        assertEquals(listOf("unicore_binary"), records.map { it.kind })
        assertEquals(binary.toList(), records.single().bytes.toList())
    }

    @Test
    fun `malformed binary header is classified as noise and parser resynchronises`() {
        val malformedSync = byteArrayOf(0xAA.toByte(), 0x44, 0xB5.toByte(), 1, 2, 3)
        val valid = Um980BinaryParserTest.bestnavbFrame()

        val records = Um980StreamParser().accept(malformedSync + valid)

        assertEquals(listOf("noise", "unicore_binary"), records.map { it.kind })
        assertEquals(malformedSync.toList(), records.first().bytes.toList())
        assertEquals(valid.toList(), records.last().bytes.toList())
    }

    @Test
    fun `short binary frame header is buffered until enough bytes arrive`() {
        val parser = Um980StreamParser()

        val records = parser.accept(byteArrayOf(0xAA.toByte(), 0x44, 0xB5.toByte(), 1))

        assertEquals(emptyList<Um980StreamRecord>(), records)
    }

    @Test
    fun `implausibly large binary payload length is rejected instead of buffered`() {
        val oversized = byteArrayOf(
            0xAA.toByte(),
            0x44,
            0xB5.toByte(),
            24,
            0,
            0,
            0xff.toByte(),
            0xff.toByte(),
        ) + ByteArray(16)

        val records = Um980StreamParser().accept(oversized)

        assertEquals(listOf("noise"), records.map { it.kind })
        assertEquals(oversized.toList(), records.single().bytes.toList())
    }
}
