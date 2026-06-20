package org.rtkcollector.receiver.ublox

import java.nio.ByteBuffer
import java.nio.ByteOrder

object UbloxNavSatParser {
    fun parse(frame: ByteArray, nowMillis: Long): UbloxNavSatTelemetry? {
        if (!UbloxFrame.isValid(frame)) return null
        if ((frame[2].toInt() and 0xff) != 0x01 || (frame[3].toInt() and 0xff) != 0x35) return null
        val length = (frame[4].toInt() and 0xff) or ((frame[5].toInt() and 0xff) shl 8)
        if (length < HEADER_LENGTH) return null
        val payload = ByteBuffer.wrap(frame.copyOfRange(6, 6 + length)).order(ByteOrder.LITTLE_ENDIAN)
        val satelliteCount = payload.get(5).toInt() and 0xff
        if (HEADER_LENGTH + satelliteCount * SATELLITE_BLOCK_LENGTH > length) return null
        var used = 0
        repeat(satelliteCount) { index ->
            val flagsOffset = HEADER_LENGTH + index * SATELLITE_BLOCK_LENGTH + FLAGS_OFFSET_IN_BLOCK
            val flags = payload.getInt(flagsOffset)
            if (flags and SV_USED_FLAG != 0) {
                used += 1
            }
        }
        return UbloxNavSatTelemetry(
            updatedAtMillis = nowMillis,
            satellitesInView = satelliteCount,
            satellitesUsed = used,
        )
    }

    private const val HEADER_LENGTH = 8
    private const val SATELLITE_BLOCK_LENGTH = 12
    private const val FLAGS_OFFSET_IN_BLOCK = 8
    private const val SV_USED_FLAG = 0x08
}
