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
        val utcTime = utcTime(payload)
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
            utcTime = utcTime,
        )
    }

    private fun utcTime(payload: ByteBuffer): String? {
        val validFlags = payload.get(11).toInt() and 0xff
        val dateAndTimeValid = validFlags and 0x03 == 0x03
        if (!dateAndTimeValid) return null
        val year = payload.getShort(4).toInt() and 0xffff
        val month = payload.get(6).toInt() and 0xff
        val day = payload.get(7).toInt() and 0xff
        val hour = payload.get(8).toInt() and 0xff
        val minute = payload.get(9).toInt() and 0xff
        val second = payload.get(10).toInt() and 0xff
        if (year !in 1980..2099 || month !in 1..12 || day !in 1..31) return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null
        return "%04d-%02d-%02dT%02d:%02d:%02dZ".format(year, month, day, hour, minute, second)
    }

    private fun fixClass(fixType: Int, flags: Int): FixClass {
        val gnssFixOk = flags and 0x01 != 0
        val valid3dFix = fixType >= 3
        val differential = flags and 0x02 != 0
        val carrSoln = (flags ushr 6) and 0x03
        return when {
            fixType < 2 -> FixClass.NONE
            valid3dFix && gnssFixOk && carrSoln == 2 -> FixClass.RTK_FIXED
            valid3dFix && gnssFixOk && carrSoln == 1 -> FixClass.RTK_FLOAT
            valid3dFix && gnssFixOk && differential -> FixClass.DGPS
            fixType >= 3 -> FixClass.SINGLE
            else -> FixClass.NONE
        }
    }
}
