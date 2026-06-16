package org.rtkcollector.receiver.ublox

import org.rtkcollector.receiver.api.ReceiverCommand

object UbloxScriptCompiler {
    private val supportedMessages = mapOf(
        "CFG-MSG" to Pair(0x06, 0x01),
        "CFG-GNSS" to Pair(0x06, 0x3E),
        "CFG-NAV5" to Pair(0x06, 0x24),
        "CFG-RATE" to Pair(0x06, 0x08),
    )

    fun compile(script: String): List<ReceiverCommand> =
        script.lineSequence()
            .mapIndexedNotNull { index, rawLine -> compileLine(index + 1, rawLine) }
            .toList()

    private fun compileLine(lineNumber: Int, rawLine: String): ReceiverCommand? {
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("#")) return null
        require(line.startsWith("!UBX ")) { "Unsupported u-blox script line $lineNumber: expected !UBX prefix." }
        val parts = line.split(Regex("\\s+"))
        require(parts.size >= 2) { "Malformed !UBX line $lineNumber: missing command name." }
        require(parts.size >= 3) { "Malformed !UBX line $lineNumber: missing payload arguments." }
        val commandName = parts[1]
        val classAndId = supportedMessages[commandName]
            ?: throw IllegalArgumentException("Unsupported !UBX command on line $lineNumber: $commandName")
        val args = parts.drop(2).map { token -> parseInteger(lineNumber, token) }
        val payload = payload(commandName, lineNumber, args)
        return ReceiverCommand(
            label = "UBX $commandName",
            payload = UbloxFrame.build(classAndId.first, classAndId.second, payload),
        )
    }

    private fun parseInteger(lineNumber: Int, token: String): Long {
        return token.toLongOrNull()
            ?: throw IllegalArgumentException("Malformed !UBX payload on line $lineNumber: '$token' is not an integer.")
    }

    private fun payload(commandName: String, lineNumber: Int, args: List<Long>): ByteArray =
        when (commandName) {
            "CFG-MSG" -> packCfgMsg(lineNumber, args)
            "CFG-GNSS" -> packCfgGnss(lineNumber, args)
            "CFG-NAV5" -> packCfgNav5(lineNumber, args)
            "CFG-RATE" -> packCfgRate(lineNumber, args)
            else -> throw IllegalArgumentException("Unsupported !UBX command on line $lineNumber: $commandName")
        }

    private fun packCfgMsg(lineNumber: Int, args: List<Long>): ByteArray {
        require(args.size == 8) { "CFG-MSG on line $lineNumber requires 8 integer fields." }
        return args.map { u8(lineNumber, it) }.toByteArray()
    }

    private fun packCfgGnss(lineNumber: Int, args: List<Long>): ByteArray {
        require(args.size == 9) { "CFG-GNSS on line $lineNumber requires 9 integer fields for one GNSS block." }
        return byteArrayOf(
            u8(lineNumber, args[0]),
            u8(lineNumber, args[1]),
            u8(lineNumber, args[2]),
            u8(lineNumber, args[3]),
            u8(lineNumber, args[4]),
            u8(lineNumber, args[5]),
            u8(lineNumber, args[6]),
            u8(lineNumber, args[7]),
        ) + u32(lineNumber, args[8])
    }

    private fun packCfgRate(lineNumber: Int, args: List<Long>): ByteArray {
        require(args.size == 3) { "CFG-RATE on line $lineNumber requires measRate, navRate and timeRef." }
        return u16(lineNumber, args[0]) + u16(lineNumber, args[1]) + u16(lineNumber, args[2])
    }

    private fun packCfgNav5(lineNumber: Int, args: List<Long>): ByteArray {
        require(args.size >= 18 && args.size <= 24) {
            "CFG-NAV5 on line $lineNumber requires 18 base fields and up to 6 trailing reserved byte fields."
        }
        val reservedTail = args.drop(18).take(5)
        return u16(lineNumber, args[0]) +
            byteArrayOf(u8(lineNumber, args[1]), u8(lineNumber, args[2])) +
            i32(lineNumber, args[3]) +
            u32(lineNumber, args[4]) +
            byteArrayOf(u8(lineNumber, args[5]), u8(lineNumber, args[6])) +
            u16(lineNumber, args[7]) +
            u16(lineNumber, args[8]) +
            u16(lineNumber, args[9]) +
            u16(lineNumber, args[10]) +
            byteArrayOf(u8(lineNumber, args[11]), u8(lineNumber, args[12]), u8(lineNumber, args[13]), u8(lineNumber, args[14])) +
            u16(lineNumber, args[15]) +
            u16(lineNumber, args[16]) +
            byteArrayOf(u8(lineNumber, args[17])) +
            ByteArray(5) { index -> reservedTail.getOrNull(index)?.let { u8(lineNumber, it) } ?: 0 }
    }

    private fun u8(lineNumber: Int, value: Long): Byte {
        require(value in 0..255) { "Payload value on line $lineNumber does not fit uint8: $value" }
        return value.toByte()
    }

    private fun u16(lineNumber: Int, value: Long): ByteArray {
        require(value in 0..65_535) { "Payload value on line $lineNumber does not fit uint16: $value" }
        return byteArrayOf((value and 0xff).toByte(), ((value ushr 8) and 0xff).toByte())
    }

    private fun u32(lineNumber: Int, value: Long): ByteArray {
        require(value in 0..0xffff_ffffL) { "Payload value on line $lineNumber does not fit uint32: $value" }
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value ushr 8) and 0xff).toByte(),
            ((value ushr 16) and 0xff).toByte(),
            ((value ushr 24) and 0xff).toByte(),
        )
    }

    private fun i32(lineNumber: Int, value: Long): ByteArray {
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "Payload value on line $lineNumber does not fit int32: $value" }
        return u32(lineNumber, value and 0xffff_ffffL)
    }
}
