package org.rtkcollector.core.correction

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Rtcm3ReferenceStation(
    val messageType: Int,
    val stationId: Int,
    val ecefXM: Double,
    val ecefYM: Double,
    val ecefZM: Double,
    val latDeg: Double,
    val lonDeg: Double,
    val heightM: Double,
    val antennaHeightM: Double? = null,
)

object Rtcm3ReferenceStationParser {
    fun parse(frame: Rtcm3Frame): Rtcm3ReferenceStation? {
        if (frame.crcValid == false) return null
        if (frame.payloadLength !in MINIMUM_MESSAGE_TYPE_BYTES..MAX_PAYLOAD_BYTES) return null
        if (frame.bytes.size != RTCM3_OVERHEAD_BYTES + frame.payloadLength) return null
        if ((frame.bytes[0].toInt() and BYTE_MASK) != RTCM3_PREAMBLE) return null
        if ((frame.bytes[1].toInt() and HEADER_RESERVED_BITS_MASK) != 0) return null
        val headerPayloadLength =
            ((frame.bytes[1].toInt() and PAYLOAD_LENGTH_HIGH_BITS_MASK) shl 8) or
                (frame.bytes[2].toInt() and BYTE_MASK)
        if (headerPayloadLength != frame.payloadLength) return null
        if (frame.crcValid == null) {
            val crcOffset = frame.bytes.size - 3
            val expectedCrc = ((frame.bytes[crcOffset].toInt() and BYTE_MASK) shl 16) or
                ((frame.bytes[crcOffset + 1].toInt() and BYTE_MASK) shl 8) or
                (frame.bytes[crcOffset + 2].toInt() and BYTE_MASK)
            if (Rtcm3Extractor.crc24q(frame.bytes, crcOffset) != expectedCrc) return null
        }

        val bits = RtcmBitReader(frame.bytes, bitOffset = 24, bitLength = frame.payloadLength * 8)
        val messageType = bits.unsigned(12).toInt()
        if (messageType != 1005 && messageType != 1006) return null
        if (frame.messageType != null && frame.messageType != messageType) return null
        if (messageType == 1005 && frame.payloadLength != EXACT_1005_PAYLOAD_BYTES) return null
        if (messageType == 1006 && frame.payloadLength < MINIMUM_1006_PAYLOAD_BYTES) return null
        val stationId = bits.unsigned(12).toInt()
        bits.skip(6) // ITRF realization year
        bits.skip(1) // GPS indicator
        bits.skip(1) // GLONASS indicator
        bits.skip(1) // Galileo indicator
        bits.skip(1) // reference-station indicator
        val ecefXM = bits.signed(38) * 0.0001
        bits.skip(1) // single receiver oscillator indicator
        bits.skip(1) // reserved
        val ecefYM = bits.signed(38) * 0.0001
        bits.skip(2) // quarter-cycle indicator
        val ecefZM = bits.signed(38) * 0.0001
        val antennaHeightM = if (messageType == 1006) {
            bits.unsigned(16) * 0.0001
        } else {
            null
        }
        val geodetic = ecefToGeodetic(ecefXM, ecefYM, ecefZM)
        return Rtcm3ReferenceStation(
            messageType = messageType,
            stationId = stationId,
            ecefXM = ecefXM,
            ecefYM = ecefYM,
            ecefZM = ecefZM,
            latDeg = geodetic.latDeg,
            lonDeg = geodetic.lonDeg,
            heightM = geodetic.heightM,
            antennaHeightM = antennaHeightM,
        )
    }

    private fun ecefToGeodetic(x: Double, y: Double, z: Double): GeodeticPosition {
        val a = 6_378_137.0
        val f = 1.0 / 298.257_223_563
        val e2 = f * (2.0 - f)
        val b = a * (1.0 - f)
        val ep2 = (a.pow(2) - b.pow(2)) / b.pow(2)
        val p = sqrt(x * x + y * y)
        val theta = atan2(z * a, p * b)
        val lon = atan2(y, x)
        val lat = atan2(
            z + ep2 * b * sin(theta).pow(3),
            p - e2 * a * cos(theta).pow(3),
        )
        val n = a / sqrt(1.0 - e2 * sin(lat).pow(2))
        val height = p / cos(lat) - n
        return GeodeticPosition(
            latDeg = Math.toDegrees(lat),
            lonDeg = Math.toDegrees(lon),
            heightM = height,
        )
    }

    private const val RTCM3_PREAMBLE = 0xd3
    private const val RTCM3_OVERHEAD_BYTES = 6
    private const val MINIMUM_MESSAGE_TYPE_BYTES = 2
    private const val EXACT_1005_PAYLOAD_BYTES = 19
    private const val MINIMUM_1006_PAYLOAD_BYTES = 21
    private const val MAX_PAYLOAD_BYTES = 1023
    private const val BYTE_MASK = 0xff
    private const val HEADER_RESERVED_BITS_MASK = 0xfc
    private const val PAYLOAD_LENGTH_HIGH_BITS_MASK = 0x03
}

private data class GeodeticPosition(
    val latDeg: Double,
    val lonDeg: Double,
    val heightM: Double,
)
