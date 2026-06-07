package org.rtkcollector.core.correction

data class Rtcm3Frame(
    val bytes: ByteArray,
    val payloadLength: Int,
    val messageType: Int?,
    val crcValid: Boolean?,
) {
    override fun equals(other: Any?): Boolean =
        other is Rtcm3Frame &&
            bytes.contentEquals(other.bytes) &&
            payloadLength == other.payloadLength &&
            messageType == other.messageType &&
            crcValid == other.crcValid

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + payloadLength
        result = 31 * result + (messageType ?: 0)
        result = 31 * result + (crcValid?.hashCode() ?: 0)
        return result
    }
}

class Rtcm3Extractor(private val validateCrc: Boolean = true) {
    private val buffer = ArrayDeque<Byte>()

    fun accept(bytes: ByteArray): List<Rtcm3Frame> {
        bytes.forEach(buffer::addLast)
        val frames = mutableListOf<Rtcm3Frame>()

        while (true) {
            while (buffer.isNotEmpty() && buffer.first() != RTCM3_PREAMBLE) {
                buffer.removeFirst()
            }
            if (buffer.size < RTCM3_MIN_FRAME_BYTES) {
                return frames
            }

            val second = buffer.elementAt(1).toInt() and BYTE_MASK
            val third = buffer.elementAt(2).toInt() and BYTE_MASK
            val payloadLength = ((second and LENGTH_HIGH_BITS_MASK) shl 8) or third
            val frameLength = RTCM3_HEADER_BYTES + payloadLength + RTCM3_CRC_BYTES
            if (buffer.size < frameLength) {
                return frames
            }

            val frameBytes = ByteArray(frameLength) { buffer.removeFirst() }
            frames += Rtcm3Frame(
                bytes = frameBytes,
                payloadLength = payloadLength,
                messageType = messageType(frameBytes, payloadLength),
                crcValid = crcStatus(frameBytes),
            )
        }
    }

    private fun crcStatus(frameBytes: ByteArray): Boolean? {
        if (!validateCrc) {
            return null
        }
        val crcStart = frameBytes.size - RTCM3_CRC_BYTES
        val expected = ((frameBytes[crcStart].toInt() and BYTE_MASK) shl 16) or
            ((frameBytes[crcStart + 1].toInt() and BYTE_MASK) shl 8) or
            (frameBytes[crcStart + 2].toInt() and BYTE_MASK)
        return crc24q(frameBytes, crcStart) == expected
    }

    private fun messageType(frameBytes: ByteArray, payloadLength: Int): Int? =
        if (payloadLength >= 2) {
            ((frameBytes[3].toInt() and BYTE_MASK) shl 4) or
                ((frameBytes[4].toInt() and 0xf0) ushr 4)
        } else {
            null
        }

    companion object {
        private const val RTCM3_PREAMBLE: Byte = 0xd3.toByte()
        private const val RTCM3_HEADER_BYTES = 3
        private const val RTCM3_CRC_BYTES = 3
        private const val RTCM3_MIN_FRAME_BYTES = RTCM3_HEADER_BYTES + RTCM3_CRC_BYTES
        private const val BYTE_MASK = 0xff
        private const val LENGTH_HIGH_BITS_MASK = 0x03
        private const val CRC24Q_POLYNOMIAL = 0x1864cfb
        private const val CRC24Q_OVERFLOW_BIT = 0x1000000
        private const val CRC24Q_MASK = 0xffffff

        fun crc24q(bytes: ByteArray, length: Int): Int {
            require(length in 0..bytes.size) { "length must be within bytes bounds" }
            var crc = 0
            for (index in 0 until length) {
                crc = crc xor ((bytes[index].toInt() and BYTE_MASK) shl 16)
                repeat(8) {
                    crc = crc shl 1
                    if ((crc and CRC24Q_OVERFLOW_BIT) != 0) {
                        crc = crc xor CRC24Q_POLYNOMIAL
                    }
                }
            }
            return crc and CRC24Q_MASK
        }
    }
}
