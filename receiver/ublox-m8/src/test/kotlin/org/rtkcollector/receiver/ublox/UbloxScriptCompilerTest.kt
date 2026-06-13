package org.rtkcollector.receiver.ublox

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

    private fun org.rtkcollector.receiver.api.ReceiverCommand.payloadLength(): Int =
        (payload[4].toInt() and 0xff) or ((payload[5].toInt() and 0xff) shl 8)
}
