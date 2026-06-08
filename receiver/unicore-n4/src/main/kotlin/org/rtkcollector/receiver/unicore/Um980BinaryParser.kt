package org.rtkcollector.receiver.unicore

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

object Um980BinaryParser {
    private const val BESTNAVB_MESSAGE_ID = 2118
    private const val BINARY_HEADER_LENGTH = 24
    private const val CRC_LENGTH = 4
    private const val BESTNAVB_MIN_PAYLOAD_LENGTH = 120

    private val solutionStatusNames = mapOf(0 to "SOL_COMPUTED")
    private val positionTypeNames = mapOf(50 to "NARROW_INT")

    fun extractFrames(input: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        var index = 0
        while (index <= input.size - BINARY_HEADER_LENGTH) {
            if (!hasBinarySync(input, index)) {
                index += 1
                continue
            }
            val headerLength = input[index + 3].toInt() and 0xff
            if (headerLength < BINARY_HEADER_LENGTH || index + headerLength + CRC_LENGTH > input.size) {
                index += 1
                continue
            }
            val payloadLength = u16(input, index + 6)
            val frameLength = headerLength + payloadLength + CRC_LENGTH
            if (index + frameLength > input.size) {
                break
            }
            val frame = input.copyOfRange(index, index + frameLength)
            if (isValidFrame(frame)) {
                frames += frame
                index += frameLength
            } else {
                index += 1
            }
        }
        return frames
    }

    fun isValidFrame(frame: ByteArray): Boolean {
        if (frame.size < BINARY_HEADER_LENGTH + CRC_LENGTH) return false
        if (!hasBinarySync(frame, 0)) return false
        val headerLength = frame[3].toInt() and 0xff
        if (headerLength < BINARY_HEADER_LENGTH) return false
        if (frame.size < headerLength + CRC_LENGTH) return false
        val payloadLength = u16(frame, 6)
        val expectedLength = headerLength + payloadLength + CRC_LENGTH
        if (frame.size != expectedLength) return false
        return u32(frame, frame.size - CRC_LENGTH).toLong() == crc32(frame, 0, frame.size - CRC_LENGTH)
    }

    fun parseBestnavb(frame: ByteArray): Um980Telemetry? {
        if (!isValidFrame(frame)) return null
        val headerLength = frame[3].toInt() and 0xff
        val messageId = u16(frame, 4)
        if (messageId != BESTNAVB_MESSAGE_ID) return null
        val payloadLength = u16(frame, 6)
        if (payloadLength < BESTNAVB_MIN_PAYLOAD_LENGTH) return null
        val payload = ByteBuffer
            .wrap(frame.copyOfRange(headerLength, headerLength + payloadLength))
            .order(ByteOrder.LITTLE_ENDIAN)
        val solutionStatus = payload.getInt(0)
        val positionType = payload.getInt(4)
        val altitudeM = payload.getDouble(24)
        val undulationM = payload.getFloat(32).toDouble()
        return Um980Telemetry(
            source = "BESTNAVB",
            solutionStatus = solutionStatusNames[solutionStatus] ?: "STATUS_$solutionStatus",
            positionType = positionTypeNames[positionType] ?: "TYPE_$positionType",
            latDeg = payload.getDouble(8),
            lonDeg = payload.getDouble(16),
            altitudeM = altitudeM,
            ellipsoidalHeightM = altitudeM + undulationM,
            latErrorM = payload.getFloat(40).toDouble(),
            lonErrorM = payload.getFloat(44).toDouble(),
            verticalAccuracyM = payload.getFloat(48).toDouble(),
            stationId = stationId(frame.copyOfRange(headerLength + 52, headerLength + 56)),
            differentialAgeS = payload.getFloat(56).toDouble(),
            satellitesInView = payload.get(64).toInt() and 0xff,
            satellitesUsed = payload.get(65).toInt() and 0xff,
        )
    }

    private fun hasBinarySync(input: ByteArray, index: Int): Boolean =
        index + 2 < input.size &&
            input[index] == 0xAA.toByte() &&
            input[index + 1] == 0x44.toByte() &&
            input[index + 2] == 0xB5.toByte()

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): UInt =
        ((bytes[offset].toInt() and 0xff).toUInt()) or
            ((bytes[offset + 1].toInt() and 0xff).toUInt() shl 8) or
            ((bytes[offset + 2].toInt() and 0xff).toUInt() shl 16) or
            ((bytes[offset + 3].toInt() and 0xff).toUInt() shl 24)

    private fun crc32(bytes: ByteArray, offset: Int, length: Int): Long {
        val crc = CRC32()
        crc.update(bytes, offset, length)
        return crc.value
    }

    private fun stationId(bytes: ByteArray): String? =
        bytes.takeWhile { it != 0.toByte() }
            .toByteArray()
            .decodeToString()
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .takeIf(String::isNotBlank)
}
