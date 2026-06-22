package org.rtkcollector.receiver.ublox

import java.nio.ByteBuffer
import java.nio.ByteOrder

object UbloxNavDopParser {
    fun parse(frame: ByteArray, nowMillis: Long): UbloxNavDopTelemetry? {
        if (!UbloxFrame.isValid(frame)) return null
        if ((frame[2].toInt() and 0xff) != MESSAGE_CLASS || (frame[3].toInt() and 0xff) != MESSAGE_ID) {
            return null
        }
        val length = (frame[4].toInt() and 0xff) or ((frame[5].toInt() and 0xff) shl 8)
        if (length < PAYLOAD_LENGTH) return null

        val payload = ByteBuffer.wrap(frame.copyOfRange(6, 6 + length)).order(ByteOrder.LITTLE_ENDIAN)
        return UbloxNavDopTelemetry(
            updatedAtMillis = nowMillis,
            gdop = payload.centiDop(4),
            pdop = payload.centiDop(6),
            tdop = payload.centiDop(8),
            vdop = payload.centiDop(10),
            hdop = payload.centiDop(12),
            ndop = payload.centiDop(14),
            edop = payload.centiDop(16),
        )
    }

    private fun ByteBuffer.centiDop(offset: Int): Double =
        (getShort(offset).toInt() and 0xffff) / 100.0

    private const val MESSAGE_CLASS = 0x01
    private const val MESSAGE_ID = 0x04
    private const val PAYLOAD_LENGTH = 18
}
