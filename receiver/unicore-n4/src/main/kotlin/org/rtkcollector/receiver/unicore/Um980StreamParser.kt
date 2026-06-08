package org.rtkcollector.receiver.unicore

data class Um980StreamRecord(
    val kind: String,
    val bytes: ByteArray,
    val text: String? = null,
)

class Um980StreamParser {
    private var pending = ByteArray(0)

    fun accept(input: ByteArray): List<Um980StreamRecord> {
        val data = pending + input
        pending = ByteArray(0)
        val records = mutableListOf<Um980StreamRecord>()
        var index = 0
        while (index < data.size) {
            when {
                data[index] == '$'.code.toByte() -> {
                    val end = findLineEnd(data, index)
                    if (end < 0) {
                        pending = data.copyOfRange(index, data.size)
                        index = data.size
                    } else {
                        val bytes = data.copyOfRange(index, end)
                        records += Um980StreamRecord("nmea", bytes, bytes.decodeToString())
                        index = end
                    }
                }
                data[index] == '#'.code.toByte() -> {
                    val end = findLineEnd(data, index)
                    if (end < 0) {
                        pending = data.copyOfRange(index, data.size)
                        index = data.size
                    } else {
                        val bytes = data.copyOfRange(index, end)
                        records += Um980StreamRecord("unicore_ascii", bytes, bytes.decodeToString())
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

    private fun findLineEnd(input: ByteArray, start: Int): Int {
        for (i in start until input.size) {
            if (input[i] == '\n'.code.toByte()) return i + 1
        }
        return -1
    }

    private fun binaryFrameEnd(input: ByteArray, start: Int): BinaryFrameEnd {
        if (start + 4 > input.size) return BinaryFrameEnd.Incomplete
        val headerLength = input[start + 3].toInt() and 0xff
        if (headerLength < BINARY_HEADER_LENGTH) return BinaryFrameEnd.Invalid
        if (start + BINARY_HEADER_LENGTH > input.size) return BinaryFrameEnd.Incomplete
        if (start + headerLength > input.size) return BinaryFrameEnd.Incomplete
        val payloadLength = u16(input, start + 6)
        val end = start + headerLength + payloadLength + CRC_LENGTH
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
    }

    private sealed class BinaryFrameEnd {
        data class Complete(val endExclusive: Int) : BinaryFrameEnd()
        data object Incomplete : BinaryFrameEnd()
        data object Invalid : BinaryFrameEnd()
    }
}
