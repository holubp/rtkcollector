package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Rtcm3ReferenceStationParserTest {
    @Test
    fun `parses rtcm 1005 reference station id and ecef coordinates`() {
        val frame = referenceStationFrame(
            messageType = 1005,
            stationId = 321,
            ecefXM = 3_978_649.1234,
            ecefYM = 1_028_234.5678,
            ecefZM = 4_864_321.0123,
        )

        val station = Rtcm3ReferenceStationParser.parse(frame)

        requireNotNull(station)
        assertEquals(1005, station.messageType)
        assertEquals(321, station.stationId)
        assertEquals(3_978_649.1234, station.ecefXM, 0.0001)
        assertEquals(1_028_234.5678, station.ecefYM, 0.0001)
        assertEquals(4_864_321.0123, station.ecefZM, 0.0001)
        assertEquals(50.0, station.latDeg, 1.0)
        assertEquals(14.0, station.lonDeg, 1.0)
    }

    @Test
    fun `parses rtcm 1006 antenna height`() {
        val frame = referenceStationFrame(
            messageType = 1006,
            stationId = 42,
            ecefXM = 3_978_649.1234,
            ecefYM = 1_028_234.5678,
            ecefZM = 4_864_321.0123,
            antennaHeightM = 1.2345,
        )

        val station = Rtcm3ReferenceStationParser.parse(frame)

        requireNotNull(station)
        assertEquals(1006, station.messageType)
        assertEquals(42, station.stationId)
        assertEquals(1.2345, station.antennaHeightM!!, 0.0001)
    }

    @Test
    fun `ignores non reference station frame`() {
        val payload = bitPayload {
            unsigned(1077, 12)
            unsigned(1, 12)
        }
        val frame = rtcmFrame(payload)

        assertNull(Rtcm3ReferenceStationParser.parse(frame))
    }

    private fun referenceStationFrame(
        messageType: Int,
        stationId: Int,
        ecefXM: Double,
        ecefYM: Double,
        ecefZM: Double,
        antennaHeightM: Double? = null,
    ): Rtcm3Frame {
        val payload = bitPayload {
            unsigned(messageType.toLong(), 12)
            unsigned(stationId.toLong(), 12)
            unsigned(0, 6)
            unsigned(1, 1)
            unsigned(1, 1)
            unsigned(1, 1)
            unsigned(0, 1)
            signed((ecefXM * 10_000.0).toLong(), 38)
            unsigned(0, 1)
            unsigned(0, 1)
            signed((ecefYM * 10_000.0).toLong(), 38)
            unsigned(0, 2)
            signed((ecefZM * 10_000.0).toLong(), 38)
            if (messageType == 1006) {
                unsigned(((antennaHeightM ?: 0.0) * 10_000.0).toLong(), 16)
            }
        }
        return rtcmFrame(payload)
    }

    private fun rtcmFrame(payload: ByteArray): Rtcm3Frame {
        val bytes = ByteArray(3 + payload.size + 3)
        bytes[0] = 0xd3.toByte()
        bytes[1] = ((payload.size ushr 8) and 0x03).toByte()
        bytes[2] = (payload.size and 0xff).toByte()
        payload.copyInto(bytes, destinationOffset = 3)
        val crc = Rtcm3Extractor.crc24q(bytes, bytes.size - 3)
        bytes[bytes.size - 3] = ((crc ushr 16) and 0xff).toByte()
        bytes[bytes.size - 2] = ((crc ushr 8) and 0xff).toByte()
        bytes[bytes.size - 1] = (crc and 0xff).toByte()
        return Rtcm3Extractor(validateCrc = true).accept(bytes).single()
    }

    private fun bitPayload(build: BitPayloadBuilder.() -> Unit): ByteArray =
        BitPayloadBuilder().apply(build).toByteArray()

    private class BitPayloadBuilder {
        private val bits = mutableListOf<Int>()

        fun unsigned(value: Long, width: Int) {
            for (bit in width - 1 downTo 0) {
                bits += ((value ushr bit) and 1L).toInt()
            }
        }

        fun signed(value: Long, width: Int) {
            val encoded = if (value < 0) (1L shl width) + value else value
            unsigned(encoded, width)
        }

        fun toByteArray(): ByteArray {
            val bytes = ByteArray((bits.size + 7) / 8)
            bits.forEachIndexed { index, bit ->
                if (bit != 0) {
                    bytes[index / 8] = (bytes[index / 8].toInt() or (1 shl (7 - (index % 8)))).toByte()
                }
            }
            return bytes
        }
    }
}
