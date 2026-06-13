package org.rtkcollector.receiver.ublox

import org.rtkcollector.core.solution.FixClass
import java.nio.ByteBuffer
import java.nio.ByteOrder

object UbloxNavPvtParser {
    fun parse(frame: ByteArray, nowMillis: Long): UbloxTelemetry? {
        if (!UbloxFrame.isValid(frame)) return null
        if ((frame[2].toInt() and 0xff) != 0x01 || (frame[3].toInt() and 0xff) != 0x07) return null
        val length = (frame[4].toInt() and 0xff) or ((frame[5].toInt() and 0xff) shl 8)
        if (length < 92) return null
        val payload = ByteBuffer.wrap(frame.copyOfRange(6, 6 + length)).order(ByteOrder.LITTLE_ENDIAN)
        val fixType = payload.get(20).toInt() and 0xff
        val flags = payload.get(21).toInt() and 0xff
        val fixClass = fixClass(fixType, flags)
        val satellites = payload.get(23).toInt() and 0xff
        return UbloxTelemetry(
            source = "UBX-NAV-PVT",
            updatedAtMillis = nowMillis,
            fixClass = fixClass,
            lonDeg = payload.getInt(24) / 1e7,
            latDeg = payload.getInt(28) / 1e7,
            ellipsoidalHeightM = payload.getInt(32) / 1000.0,
            mslAltitudeM = payload.getInt(36) / 1000.0,
            horizontalAccuracyM = payload.getInt(40) / 1000.0,
            verticalAccuracyM = payload.getInt(44) / 1000.0,
            satellitesUsed = satellites,
        )
    }

    private fun fixClass(fixType: Int, flags: Int): FixClass {
        val differential = flags and 0x02 != 0
        return when {
            fixType < 2 -> FixClass.NONE
            differential -> FixClass.DGPS
            fixType >= 3 -> FixClass.SINGLE
            else -> FixClass.NONE
        }
    }
}
