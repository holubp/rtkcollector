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
        val bits = RtcmBitReader(frame.bytes, bitOffset = 24, bitLength = frame.payloadLength * 8)
        val messageType = bits.unsigned(12).toInt()
        if (messageType != 1005 && messageType != 1006) return null
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
        val antennaHeightM = if (messageType == 1006 && bits.remaining >= 16) {
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
}

private data class GeodeticPosition(
    val latDeg: Double,
    val lonDeg: Double,
    val heightM: Double,
)

private class RtcmBitReader(
    private val bytes: ByteArray,
    private var bitOffset: Int,
    private val bitLength: Int,
) {
    val remaining: Int
        get() = bitLength - (bitOffset - 24)

    fun skip(width: Int) {
        require(width >= 0) { "width must be non-negative" }
        require(remaining >= width) { "RTCM payload is shorter than expected." }
        bitOffset += width
    }

    fun unsigned(width: Int): Long {
        require(width in 1..63) { "width must be 1..63" }
        require(remaining >= width) { "RTCM payload is shorter than expected." }
        var value = 0L
        repeat(width) {
            value = (value shl 1) or bitAt(bitOffset).toLong()
            bitOffset += 1
        }
        return value
    }

    fun signed(width: Int): Long {
        val value = unsigned(width)
        val signBit = 1L shl (width - 1)
        return if ((value and signBit) == 0L) value else value - (1L shl width)
    }

    private fun bitAt(index: Int): Int {
        val byte = bytes[index / 8].toInt() and 0xff
        return (byte ushr (7 - (index % 8))) and 1
    }
}
