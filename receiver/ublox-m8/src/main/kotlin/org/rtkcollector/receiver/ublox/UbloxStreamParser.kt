package org.rtkcollector.receiver.ublox

data class UbloxStreamRecord(
    val kind: String,
    val bytes: ByteArray,
    val text: String? = null,
)

class UbloxStreamParser {
    private var pending = ByteArray(0)

    fun accept(input: ByteArray): List<UbloxStreamRecord> {
        val data = pending + input
        pending = ByteArray(0)
        val records = mutableListOf<UbloxStreamRecord>()
        var index = 0
        while (index < data.size) {
            when {
                hasUbxSync(data, index) -> {
                    val end = frameEnd(data, index)
                    if (end == INCOMPLETE) {
                        pending = data.copyOfRange(index, data.size)
                        index = data.size
                    } else if (end > 0 && UbloxFrame.isValid(data.copyOfRange(index, end))) {
                        records += UbloxStreamRecord("ubx", data.copyOfRange(index, end))
                        index = end
                    } else {
                        records += UbloxStreamRecord("noise", data.copyOfRange(index, index + 1))
                        index += 1
                    }
                }
                data[index] == '$'.code.toByte() -> {
                    val end = findLineEnd(data, index)
                    if (end < 0) {
                        pending = data.copyOfRange(index, data.size)
                        index = data.size
                    } else {
                        val bytes = data.copyOfRange(index, end)
                        records += UbloxStreamRecord("nmea", bytes, bytes.decodeToString())
                        index = end
                    }
                }
                else -> {
                    records += UbloxStreamRecord("noise", data.copyOfRange(index, index + 1))
                    index += 1
                }
            }
        }
        return records
    }

    private fun hasUbxSync(data: ByteArray, index: Int): Boolean =
        index + 1 < data.size && data[index] == 0xB5.toByte() && data[index + 1] == 0x62.toByte()

    private fun frameEnd(data: ByteArray, start: Int): Int {
        if (start + 6 > data.size) return INCOMPLETE
        val length = (data[start + 4].toInt() and 0xff) or ((data[start + 5].toInt() and 0xff) shl 8)
        if (length > MAX_PAYLOAD_LENGTH) return INVALID
        val end = start + 8 + length
        return if (end <= data.size) end else INCOMPLETE
    }

    private fun findLineEnd(input: ByteArray, start: Int): Int {
        for (i in start until input.size) {
            if (input[i] == '\n'.code.toByte()) return i + 1
        }
        return -1
    }

    private companion object {
        const val INCOMPLETE = -1
        const val INVALID = -2
        const val MAX_PAYLOAD_LENGTH = 8192
    }
}
