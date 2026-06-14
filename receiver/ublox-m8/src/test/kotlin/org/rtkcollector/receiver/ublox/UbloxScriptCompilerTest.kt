package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UbloxScriptCompilerTest {
    @Test
    fun `compiles cfg rate command`() {
        val commands = UbloxScriptCompiler.compile("!UBX CFG-RATE 1000 1 1")

        assertEquals(1, commands.size)
        assertTrue(UbloxFrame.isValid(commands.single().payload))
        assertEquals("UBX CFG-RATE", commands.single().label)
        assertEquals(6, commands.single().payloadLength())
    }

    @Test
    fun `compiles cfg msg rawx usb enable command`() {
        val commands = UbloxScriptCompiler.compile("!UBX CFG-MSG 2 21 0 0 0 1 0 0")

        val payload = commands.single().payload
        assertEquals(0x06.toByte(), payload[2])
        assertEquals(0x01.toByte(), payload[3])
        assertEquals(8, commands.single().payloadLength())
    }

    @Test
    fun `compiles cfg gnss command with one config block`() {
        val commands = UbloxScriptCompiler.compile("!UBX CFG-GNSS 0 32 32 1 0 8 16 0 65537")

        assertEquals(12, commands.single().payloadLength())
    }

    @Test
    fun `compiles cfg nav5 command to documented payload size`() {
        val commands = UbloxScriptCompiler.compile(
            "!UBX CFG-NAV5 1 3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0",
        )

        assertEquals(36, commands.single().payloadLength())
    }

    @Test
    fun `rejects unsupported command with line number`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            UbloxScriptCompiler.compile("!UBX CFG-UNKNOWN 1 2 3")
        }

        assertTrue(error.message!!.contains("line 1"))
        assertTrue(error.message!!.contains("CFG-UNKNOWN"))
    }

    @Test
    fun `compiles cfg rate command byte for byte`() {
        val commands = UbloxScriptCompiler.compile("!UBX CFG-RATE 1000 1 1")
        val frame = commands.single().payload

        // 0xB5 0x62 06 08 06 00 | E8 03 01 00 01 00 | ck_a=0x01 ck_b=0x39
        val expected = byteArrayOf(
            0xB5.toByte(), 0x62, 0x06, 0x08, 0x06, 0x00,
            0xE8.toByte(), 0x03, 0x01, 0x00, 0x01, 0x00,
            0x01, 0x39,
        )
        assertArrayEquals(expected, frame)
    }

    @Test
    fun `compiles cfg nav5 with negative fixedAlt int32`() {
        val script = "!UBX CFG-NAV5 1 3 0 -1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
        val commands = UbloxScriptCompiler.compile(script)
        val payload = commands.single().payload

        // fixedAlt is the i32 at byte offset 4 of the payload (after u16 + u8 + u8)
        val fixedAltBytes = payload.copyOfRange(6 + 4, 6 + 8)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            fixedAltBytes,
        )
    }

    @Test
    fun `missing payload reports actionable line message`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            UbloxScriptCompiler.compile("!UBX CFG-RATE")
        }
        assertTrue(error.message!!.contains("line 1"))
        assertTrue(error.message!!.contains("payload"))
    }

    private fun org.rtkcollector.receiver.api.ReceiverCommand.payloadLength(): Int =
        (payload[4].toInt() and 0xff) or ((payload[5].toInt() and 0xff) shl 8)
}
