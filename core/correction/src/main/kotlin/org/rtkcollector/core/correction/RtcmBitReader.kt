package org.rtkcollector.core.correction

internal class RtcmBitReader(
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
