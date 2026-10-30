package org.rtkcollector.receiver.unicore

data class Um980StreamRecord(
    val kind: String,
    val bytes: ByteArray,
    val text: String? = null,
)

/** Strict service admission and health validation; standalone text parsers stay permissive. */
object ReceiverProtocolHealth {
    internal const val MAX_NMEA_RECORD_BYTES = 8 * 1024
    // Advisory retention policy, not a protocol maximum: OBSVMA has variable observations.
    internal const val MAX_UNICORE_ASCII_RECORD_BYTES = 64 * 1024

    fun isValidNmea(bytes: ByteArray): Boolean {
        val line = printableLine(bytes, MAX_NMEA_RECORD_BYTES) ?: return false
        if (!line.startsWith('$')) return false
        val star = line.indexOf('*')
        if (star < 0 || star + 3 != line.length) return false
        val checksum = line.substring(star + 1)
        if (!checksum.all(::isHex)) return false
        val comma = line.indexOf(',')
        if (comma < 0 || comma >= star - 1) return false
        val address = line.substring(1, comma)
        // Includes standard talker/formatter, Unicore extensions and u-blox PUBX.
        val addressLength = if (address.startsWith('P')) 4..8 else 5..8
        if (address.length !in addressLength || !address.take(2).all { it in 'A'..'Z' } ||
            !address.all { it in 'A'..'Z' || it in '0'..'9' }
        ) return false
        val body = line.substring(1, star)
        if (body.any { it == '$' || it == '#' }) return false
        val expected = body.fold(0) { crc, char -> crc xor char.code }
        return expected == checksum.toInt(16)
    }

    fun isValidUnicoreAscii(bytes: ByteArray): Boolean {
        val line = printableLine(bytes, MAX_UNICORE_ASCII_RECORD_BYTES) ?: return false
        if (!line.startsWith('#')) return false
        val star = line.lastIndexOf('*')
        if (star < 0 || star + 9 != line.length) return false
        val checksum = line.substring(star + 1)
        if (!checksum.all(::isHex)) return false
        val comma = line.indexOf(',')
        val separator = line.indexOf(';')
        if (comma !in 2..33 || separator <= comma + 1 || separator >= star - 1) return false
        if (!line.substring(1, comma).all { it in 'A'..'Z' || it in '0'..'9' }) return false
        // N4 reference manual 7.3 / Appendix 1: exclude #, *CRC and CR/LF;
        // reflected CRC-32, initial value zero, no final complement.
        var crc = 0u
        for (i in 1 until star) {
            crc = crc xor line[i].code.toUInt()
            repeat(8) {
                crc = if ((crc and 1u) != 0u) (crc shr 1) xor 0xEDB88320u else crc shr 1
            }
        }
        return crc == checksum.toUInt(16)
    }

    private fun printableLine(bytes: ByteArray, maxBytes: Int): String? {
        if (bytes.size !in 2..maxBytes || bytes.last() != '\n'.code.toByte()) return null
        val end = bytes.size - if (bytes[bytes.lastIndex - 1] == '\r'.code.toByte()) 2 else 1
        if ((0 until end).any { bytes[it].toInt() !in 0x20..0x7e }) return null
        return bytes.decodeToString(0, end)
    }

    private fun isHex(char: Char): Boolean = char in '0'..'9' || char in 'A'..'F' || char in 'a'..'f'
}

class Um980StreamParser {
    private var pending = ByteArray(0)

    fun accept(input: ByteArray): List<Um980StreamRecord> {
        val data = pending + input
        pending = ByteArray(0)
        val records = mutableListOf<Um980StreamRecord>()
        var index = 0
        while (index < data.size) {
            when {
                data[index] == '$'.code.toByte() || data[index] == '#'.code.toByte() -> {
                    val nmea = data[index] == '$'.code.toByte()
                    val maxLength = if (nmea) ReceiverProtocolHealth.MAX_NMEA_RECORD_BYTES
                        else ReceiverProtocolHealth.MAX_UNICORE_ASCII_RECORD_BYTES
                    val end = findLineEnd(data, index, maxLength)
                    if (end < 0 && data.size - index <= maxLength) {
                        pending = data.copyOfRange(index, data.size)
                        index = data.size
                    } else if (end < 0) {
                        val next = nextKnownStart(data, index + 1)
                        val keep = partialBinarySyncSuffixLength(data, index, next)
                        val noiseEnd = next - keep
                        records += Um980StreamRecord("noise", data.copyOfRange(index, noiseEnd))
                        if (keep > 0) pending = data.copyOfRange(noiseEnd, data.size)
                        index = next
                    } else {
                        val bytes = data.copyOfRange(index, end)
                        records += Um980StreamRecord(if (nmea) "nmea" else "unicore_ascii", bytes, bytes.decodeToString())
                        index = end
                    }
                }
                hasBinarySync(data, index) -> {
                    when (val frameEnd = binaryFrameEnd(data, index)) {
                        BinaryFrameEnd.Incomplete -> {
                            pending = data.copyOfRange(index, data.size)
                            index = data.size
                        }
                        BinaryFrameEnd.Invalid -> {
                            val next = nextKnownStart(data, index + 1)
                            records += Um980StreamRecord("noise", data.copyOfRange(index, next))
                            index = next
                        }
                        is BinaryFrameEnd.Complete -> {
                            val frame = data.copyOfRange(index, frameEnd.endExclusive)
                            if (Um980BinaryParser.isValidFrame(frame)) {
                                records += Um980StreamRecord("unicore_binary", frame)
                                index = frameEnd.endExclusive
                            } else {
                                val next = nextKnownStart(data, index + 1)
                                records += Um980StreamRecord("noise", data.copyOfRange(index, next))
                                index = next
                            }
                        }
                    }
                }
                else -> {
                    val next = nextKnownStart(data, index + 1)
                    val keep = partialBinarySyncSuffixLength(data, index, next)
                    if (keep > 0) {
                        val noiseEnd = next - keep
                        if (noiseEnd > index) {
                            records += Um980StreamRecord("noise", data.copyOfRange(index, noiseEnd))
                        }
                        pending = data.copyOfRange(noiseEnd, data.size)
                        index = data.size
                    } else {
                        records += Um980StreamRecord("noise", data.copyOfRange(index, next))
                        index = next
                    }
                }
            }
        }
        return mergeAdjacentNoise(records)
    }

    private fun findLineEnd(input: ByteArray, start: Int, maxLength: Int): Int {
        for (i in start until start + minOf(maxLength, input.size - start)) {
            if (input[i] == '\n'.code.toByte()) return i + 1
        }
        return -1
    }

    private fun binaryFrameEnd(input: ByteArray, start: Int): BinaryFrameEnd {
        if (start + BINARY_HEADER_LENGTH > input.size) return BinaryFrameEnd.Incomplete
        val payloadLength = u16(input, start + 6)
        if (payloadLength > MAX_BINARY_PAYLOAD_LENGTH) return BinaryFrameEnd.Invalid
        val end = start + BINARY_HEADER_LENGTH + payloadLength + CRC_LENGTH
        return if (end <= input.size) BinaryFrameEnd.Complete(end) else BinaryFrameEnd.Incomplete
    }

    private fun nextKnownStart(input: ByteArray, start: Int): Int {
        for (i in start until input.size) {
            if (input[i] == '$'.code.toByte() || input[i] == '#'.code.toByte() || hasBinarySync(input, i)) return i
        }
        return input.size
    }

    private fun hasBinarySync(input: ByteArray, index: Int): Boolean =
        index + 2 < input.size &&
            input[index] == 0xAA.toByte() &&
            input[index + 1] == 0x44.toByte() &&
            input[index + 2] == 0xB5.toByte()

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun partialBinarySyncSuffixLength(input: ByteArray, start: Int, end: Int): Int {
        if (end != input.size) return 0
        val length = end - start
        return when {
            length >= 2 && input[end - 2] == 0xAA.toByte() && input[end - 1] == 0x44.toByte() -> 2
            length >= 1 && input[end - 1] == 0xAA.toByte() -> 1
            else -> 0
        }
    }

    private fun mergeAdjacentNoise(records: List<Um980StreamRecord>): List<Um980StreamRecord> {
        val merged = mutableListOf<Um980StreamRecord>()
        records.forEach { record ->
            val previous = merged.lastOrNull()
            if (record.kind == "noise" && previous?.kind == "noise") {
                merged[merged.lastIndex] = Um980StreamRecord("noise", previous.bytes + record.bytes)
            } else {
                merged += record
            }
        }
        return merged
    }

    private companion object {
        const val BINARY_HEADER_LENGTH = 24
        const val CRC_LENGTH = 4
        const val MAX_BINARY_PAYLOAD_LENGTH = 4096
    }

    private sealed class BinaryFrameEnd {
        data class Complete(val endExclusive: Int) : BinaryFrameEnd()
        data object Incomplete : BinaryFrameEnd()
        data object Invalid : BinaryFrameEnd()
    }
}
