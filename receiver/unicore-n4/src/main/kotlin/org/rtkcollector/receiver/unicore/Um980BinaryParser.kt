package org.rtkcollector.receiver.unicore

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

object Um980BinaryParser {
    private const val BESTNAVB_MESSAGE_ID = 2118
    private const val STADOPB_MESSAGE_ID = 954
    private const val BINARY_HEADER_LENGTH = 24
    private const val CRC_LENGTH = 4
    private const val BESTNAVB_MIN_PAYLOAD_LENGTH = 120
    private const val STADOPB_MIN_PAYLOAD_LENGTH = 42

    private val solutionStatusNames = mapOf(
        0 to "SOL_COMPUTED",
        1 to "INSUFFICIENT_OBS",
        2 to "NO_CONVERGENCE",
        4 to "COV_TRACE",
    )
    private val positionTypeNames = mapOf(
        0 to "NONE",
        1 to "FIXEDPOS",
        2 to "FIXEDHEIGHT",
        8 to "DOPPLER_VELOCITY",
        16 to "SINGLE",
        17 to "PSRDIFF",
        18 to "SBAS",
        32 to "L1_FLOAT",
        33 to "IONOFREE_FLOAT",
        34 to "NARROW_FLOAT",
        48 to "L1_INT",
        49 to "WIDE_INT",
        50 to "NARROW_INT",
        52 to "INS",
        53 to "INS_PSRSP",
        54 to "INS_PSRDIFF",
        55 to "INS_RTKFLOAT",
        56 to "INS_RTKFIXED",
        68 to "PPP_CONVERGING",
        69 to "PPP",
    )

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
        if (messageId(frame) != BESTNAVB_MESSAGE_ID) return null
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
            utcTime = gpsWeekTowUtc(frame),
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
            solutionAgeS = payload.getFloat(60).toDouble(),
            satellitesInView = payload.get(64).toInt() and 0xff,
            satellitesUsed = payload.get(65).toInt() and 0xff,
            horizontalSpeedMps = payload.getDouble(88),
            trackDeg = payload.getDouble(96),
            verticalSpeedMps = payload.getDouble(104),
        )
    }

    fun parseStadopb(frame: ByteArray): Um980Telemetry? {
        if (!isValidFrame(frame)) return null
        val headerLength = frame[3].toInt() and 0xff
        if (messageId(frame) != STADOPB_MESSAGE_ID) return null
        val payloadLength = u16(frame, 6)
        if (payloadLength < STADOPB_MIN_PAYLOAD_LENGTH) return null
        val payload = ByteBuffer
            .wrap(frame.copyOfRange(headerLength, headerLength + payloadLength))
            .order(ByteOrder.LITTLE_ENDIAN)
        val tracked = payload.getShort(40).toInt() and 0xffff
        return Um980Telemetry(
            source = "STADOPB",
            utcTime = gpsWeekTowUtc(frame),
            satellitesTracked = tracked,
            gdop = payload.getFloat(4).toDouble(),
            pdop = payload.getFloat(8).toDouble(),
            tdop = payload.getFloat(12).toDouble(),
            vdop = payload.getFloat(16).toDouble(),
            hdop = payload.getFloat(20).toDouble(),
            ndop = payload.getFloat(24).toDouble(),
            edop = payload.getFloat(28).toDouble(),
            cutoffDeg = payload.getFloat(32).toDouble(),
            satellitesInView = tracked,
        )
    }

    fun messageId(frame: ByteArray): Int? =
        if (frame.size >= BINARY_HEADER_LENGTH && hasBinarySync(frame, 0)) u16(frame, 4) else null

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

    private fun gpsWeekTowUtc(frame: ByteArray): String? =
        runCatching {
            val gpsWeek = u16(frame, 10)
            val towMillis = u32(frame, 12).toLong()
            java.time.Instant.parse("1980-01-06T00:00:00Z")
                .plus(java.time.Duration.ofDays(gpsWeek.toLong() * 7L))
                .plusMillis(towMillis)
                .minusSeconds(18)
                .toString()
        }.getOrNull()
}
