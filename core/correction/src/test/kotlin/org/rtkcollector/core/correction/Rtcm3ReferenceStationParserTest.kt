package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Rtcm3ReferenceStationParserTest {
    @Test
    fun `WGS84 equatorial axis points give independent geodetic expectations`() {
        // On the equator, the WGS84 semi-major axis plus height is the ECEF radius.
        val radius = 6_378_137.0 + 250.1234
        listOf(
            Triple(radius, 0.0, 0.0),
            Triple(0.0, radius, 90.0),
            Triple(0.0, -radius, -90.0),
        ).forEach { (x, y, longitude) ->
            val frame = referenceStationFrame(1006, 42, x, y, 0.0, antennaHeightM = 1.5)
            val station = requireNotNull(Rtcm3ReferenceStationParser.parse(frame))
            assertEquals(0.0, station.latDeg, 1e-9)
            assertEquals(longitude, station.lonDeg, 1e-9)
            assertEquals(250.1234, station.heightM, 0.0002)
            assertEquals(1.5, station.antennaHeightM!!, 0.0001)
        }
    }

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

    @Test
    fun `rejects truncated 1005 payload without throwing`() {
        val complete = referenceStationFrame(
            messageType = 1005,
            stationId = 321,
            ecefXM = 3_978_649.1234,
            ecefYM = 1_028_234.5678,
            ecefZM = 4_864_321.0123,
        )
        val truncatedPayload = complete.bytes.copyOfRange(3, complete.bytes.size - 3).copyOf(18)

        assertNull(Rtcm3ReferenceStationParser.parse(rtcmFrame(truncatedPayload)))
    }

    @Test
    fun `rejects 1006 without its complete antenna height field`() {
        val complete = referenceStationFrame(
            messageType = 1006,
            stationId = 42,
            ecefXM = 3_978_649.1234,
            ecefYM = 1_028_234.5678,
            ecefZM = 4_864_321.0123,
            antennaHeightM = null,
        )

        assertNull(Rtcm3ReferenceStationParser.parse(complete))
    }

    @Test
    fun `rejects overlong 1005 payload`() {
        val complete = referenceStationFrame(
            messageType = 1005,
            stationId = 321,
            ecefXM = 3_978_649.1234,
            ecefYM = 1_028_234.5678,
            ecefZM = 4_864_321.0123,
        )
        val extendedPayload = complete.bytes.copyOfRange(3, complete.bytes.size - 3) + byteArrayOf(0)

        assertNull(Rtcm3ReferenceStationParser.parse(rtcmFrame(extendedPayload)))
    }

    @Test
    fun `rejects frame marked CRC invalid`() {
        val valid = referenceStationFrame(
            messageType = 1005,
            stationId = 321,
            ecefXM = 3_978_649.1234,
            ecefYM = 1_028_234.5678,
            ecefZM = 4_864_321.0123,
        )

        val corrupted = valid.bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertNull(Rtcm3ReferenceStationParser.parse(valid.copy(bytes = corrupted, crcValid = false)))
    }

    @Test
    fun `accepts structurally valid frame when CRC status is unknown`() {
        val valid = referenceStationFrame(
            messageType = 1005,
            stationId = 321,
            ecefXM = 3_978_649.1234,
            ecefYM = 1_028_234.5678,
            ecefZM = 4_864_321.0123,
        )

        assertEquals(321, requireNotNull(Rtcm3ReferenceStationParser.parse(valid.copy(crcValid = null))).stationId)
    }

    @Test
    fun `recomputes unknown CRC before deriving reference coordinates`() {
        val valid = referenceStationFrame(1005, 321, 3_978_649.1234, 1_028_234.5678, 4_864_321.0123)
        val corrupted = valid.bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertNull(Rtcm3ReferenceStationParser.parse(valid.copy(bytes = corrupted, crcValid = null)))
    }

    @Test
    fun `rejects inconsistent frame header and metadata`() {
        val valid = referenceStationFrame(
            messageType = 1005,
            stationId = 321,
            ecefXM = 3_978_649.1234,
            ecefYM = 1_028_234.5678,
            ecefZM = 4_864_321.0123,
        )

        assertNull(Rtcm3ReferenceStationParser.parse(valid.copy(messageType = 1006)))
        assertNull(Rtcm3ReferenceStationParser.parse(valid.copy(payloadLength = valid.payloadLength - 1)))
        assertNull(Rtcm3ReferenceStationParser.parse(valid.copy(bytes = valid.bytes.copyOf(valid.bytes.size - 1))))
        assertNull(Rtcm3ReferenceStationParser.parse(valid.copy(bytes = valid.bytes.copyOf().also { it[0] = 0 })))
        assertNull(
            Rtcm3ReferenceStationParser.parse(
                valid.copy(bytes = valid.bytes.copyOf().also { it[1] = (it[1].toInt() or 0x04).toByte() }),
            ),
        )
        assertNull(
            Rtcm3ReferenceStationParser.parse(
                valid.copy(bytes = valid.bytes.copyOf().also { it[2] = (it[2].toInt() - 1).toByte() }),
            ),
        )
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
            if (messageType == 1006 && antennaHeightM != null) {
                unsigned((antennaHeightM * 10_000.0).toLong(), 16)
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
