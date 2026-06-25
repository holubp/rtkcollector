package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteMonitorSource
import org.rtkcollector.core.quality.SatelliteSignalKey
import org.rtkcollector.core.quality.SatelliteSignalObservation
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Um980BinaryParserTest {
    @Test
    fun `parses documented BESTNAVB solution fields`() {
        val frame = bestnavbFrame()

        val telemetry = Um980BinaryParser.parseBestnavb(frame)

        requireNotNull(telemetry)
        assertEquals("BESTNAVB", telemetry.source)
        assertEquals("SOL_COMPUTED", telemetry.solutionStatus)
        assertEquals("NARROW_INT", telemetry.positionType)
        assertEquals(50.087451234, telemetry.latDeg)
        assertEquals(14.421253456, telemetry.lonDeg)
        assertEquals(243.812, telemetry.altitudeM)
        assertEquals(287.423, telemetry.ellipsoidalHeightM!!, 0.0001)
        assertEquals(0.008, telemetry.latErrorM!!, 0.0001)
        assertEquals(0.007, telemetry.lonErrorM!!, 0.0001)
        assertEquals(18, telemetry.satellitesUsed)
        assertEquals(31, telemetry.satellitesInView)
        assertEquals(0.8, telemetry.differentialAgeS!!, 0.0001)
        assertEquals("1234", telemetry.stationId)
        assertEquals("2026-05-18T12:49:14Z", telemetry.utcTime)
        assertEquals(1.2, telemetry.horizontalSpeedMps!!, 0.0001)
        assertEquals(123.4, telemetry.trackDeg!!, 0.0001)
        assertEquals(-0.2, telemetry.verticalSpeedMps!!, 0.0001)
    }

    @Test
    fun `extracts receiver timestamp millis from binary header`() {
        val frame = bestnavbFrame()

        assertEquals(2419L * 604_800_000L + 132_572_000L, Um980BinaryParser.receiverTimestampMillis(frame))
    }

    @Test
    fun `parses documented STADOPB dop fields`() {
        val frame = stadopbFrame()

        val telemetry = Um980BinaryParser.parseStadopb(frame)

        requireNotNull(telemetry)
        assertEquals("STADOPB", telemetry.source)
        assertEquals(0.8094, telemetry.gdop!!, 0.0001)
        assertEquals(0.7129, telemetry.pdop!!, 0.0001)
        assertEquals(0.3831, telemetry.tdop!!, 0.0001)
        assertEquals(0.6046, telemetry.vdop!!, 0.0001)
        assertEquals(0.3779, telemetry.hdop!!, 0.0001)
        assertEquals(0.2902, telemetry.ndop!!, 0.0001)
        assertEquals(0.2421, telemetry.edop!!, 0.0001)
        assertEquals(50, telemetry.satellitesTracked)
        assertEquals(50, telemetry.satellitesInView)
    }

    @Test
    fun `parses documented PPPNAVB status fields`() {
        val frame = pppnavbFrame(positionType = 68)

        val telemetry = Um980BinaryParser.parsePppnavb(frame)

        requireNotNull(telemetry)
        assertEquals("PPPNAVB", telemetry.source)
        assertEquals("SOL_COMPUTED", telemetry.solutionStatus)
        assertEquals("PPP_CONVERGING", telemetry.positionType)
        assertEquals(50.087451234, telemetry.latDeg)
        assertEquals(14.421253456, telemetry.lonDeg)
        assertEquals(243.812, telemetry.altitudeM)
    }

    @Test
    fun `parses adrnavb common navigation payload as rtk view`() {
        val payload = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 0)
            putInt(4, 50)
            putDouble(8, 50.087)
            putDouble(16, 14.421)
            putDouble(24, 287.3)
            putFloat(32, 44.0f)
            putFloat(40, 0.012f)
            putFloat(44, 0.013f)
            putFloat(48, 0.025f)
            position(52)
            put("TUBO".encodeToByteArray())
            putFloat(56, 1.2f)
            putFloat(60, 0.3f)
            put(64, 21)
            put(65, 17)
        }.array()

        val telemetry = Um980BinaryParser.parseAdrnavb(unicoreFrame(142, payload))

        requireNotNull(telemetry)
        assertEquals("ADRNAVB", telemetry.source)
        assertEquals("SOL_COMPUTED", telemetry.solutionStatus)
        assertEquals("NARROW_INT", telemetry.positionType)
        assertEquals("TUBO", telemetry.stationId)
        assertEquals(17, telemetry.satellitesUsed)
        assertEquals(21, telemetry.satellitesInView)
    }

    @Test
    fun `parses rtkstatusb calculate status and adr number`() {
        val payload = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 0b11)
            putInt(8, 0b101)
            putInt(12, 0b1001)
            putInt(20, 0b10)
            putInt(28, 0b100)
            putInt(32, 0b1100)
            putInt(36, 0b1000)
            putInt(44, 34)
            putInt(48, 5)
            put(52, 0)
            put(54, 23)
        }.array()

        val telemetry = Um980BinaryParser.parseRtkstatusb(unicoreFrame(509, payload))

        requireNotNull(telemetry)
        assertEquals("RTKSTATUSB", telemetry.source)
        assertEquals("NARROW_FLOAT", telemetry.rtkPositionType)
        assertEquals(5, telemetry.rtkCalculateStatus)
        assertEquals(23, telemetry.adrNumber)
        assertEquals(0b11, telemetry.gpsSource)
        assertEquals(0b101, telemetry.bdsSource1)
        assertEquals(0b1001, telemetry.bdsSource2)
        assertEquals(0b10, telemetry.gloSource)
        assertEquals(0b100, telemetry.galSource1)
        assertEquals(0b1100, telemetry.galSource2)
        assertEquals(0b1000, telemetry.qzssSource)
    }

    @Test
    fun `parses rtcmstatusb decoded message status`() {
        val payload = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 1077)
            putInt(4, 42)
            putInt(8, 9901)
            putInt(12, 14)
            put(16, 14)
            put(17, 14)
            put(18, 0)
            put(19, 0)
            put(20, 0)
            put(21, 0)
        }.array()

        val telemetry = Um980BinaryParser.parseRtcmstatusb(unicoreFrame(2125, payload))

        requireNotNull(telemetry)
        assertEquals("RTCMSTATUSB", telemetry.source)
        assertEquals(1077, telemetry.rtcmMessageId)
        assertEquals(9901, telemetry.rtcmBaseId)
        assertEquals(14, telemetry.rtcmSatelliteCount)
    }

    @Test
    fun `parses documented OBSVMB rover observations into shared satellite keys`() {
        val frame = obsvmbFrame(
            observationRecord(
                system = 0,
                prn = 7,
                signalType = 0,
                cn0Times100 = 4525,
            ),
            observationRecord(
                system = 0,
                prn = 9,
                signalType = 9,
                l2cFlag = true,
                cn0Times100 = 4100,
            ),
            observationRecord(
                system = 3,
                prn = 11,
                signalType = 12,
                cn0Times100 = 3850,
            ),
            observationRecord(
                system = 4,
                prn = 21,
                signalType = 21,
                cn0Times100 = 3675,
            ),
        )

        val observations = Um980BinaryParser.parseObsvmbObservations(frame)

        assertEquals(
            listOf(
                SatelliteSignalKey(SatelliteConstellation.GPS, 7, "L1", "L1"),
                SatelliteSignalKey(SatelliteConstellation.GPS, 9, "L2", "L2C"),
                SatelliteSignalKey(SatelliteConstellation.GALILEO, 11, "E5A", "E5A"),
                SatelliteSignalKey(SatelliteConstellation.BEIDOU, 21, "B3", "B3I"),
            ),
            observations.map { it.key },
        )
        assertEquals(List(4) { SatelliteMonitorSource.ROVER }, observations.map { it.source })
        assertEquals(listOf(false, false, false, false), observations.map { it.used })
        assertEquals(listOf(45.25, 41.0, 38.5, 36.75), observations.map { it.cn0DbHz })
    }

    @Test
    fun `parses documented OBSVMCMPB rover observations into shared satellite keys`() {
        val frame = obsvmcmpbFrame(
            compressedObservationRecord(
                system = 0,
                prn = 15,
                signalType = 3,
                cn0DbHz = 44,
            ),
            compressedObservationRecord(
                system = 5,
                prn = 195,
                signalType = 27,
                cn0DbHz = 40,
            ),
            compressedObservationRecord(
                system = 4,
                prn = 10,
                signalType = 12,
                cn0DbHz = 35,
            ),
        )

        val observations = Um980BinaryParser.parseObsvmcmpbObservations(frame)

        assertEquals(
            listOf(
                SatelliteSignalKey(SatelliteConstellation.GPS, 15, "L1", "L1C"),
                SatelliteSignalKey(SatelliteConstellation.QZSS, 195, "L6", "L6E"),
                SatelliteSignalKey(SatelliteConstellation.BEIDOU, 10, "B2", "B2A"),
            ),
            observations.map { it.key },
        )
        assertEquals(listOf(44.0, 40.0, 35.0), observations.map { it.cn0DbHz })
    }

    @Test
    fun `parses documented BESTSATB solution usage and falls back to satellite level when needed`() {
        val frame = bestsatbFrame(
            bestsatEntry(system = 0, satelliteId = 7, signalMask = 0x05),
            bestsatEntry(system = 1, satelliteId = 43, signalMask = 0x03),
            bestsatEntry(system = 4, satelliteId = 10, signalMask = 0x06),
            bestsatEntry(system = 5, satelliteId = 195, signalMask = 0x17),
        )

        val observations = Um980BinaryParser.parseBestsatbObservations(frame)

        assertEquals(
            listOf(
                SatelliteSignalKey(SatelliteConstellation.GPS, 7, "L1", "L1"),
                SatelliteSignalKey(SatelliteConstellation.GPS, 7, "L5", "L5"),
                SatelliteSignalKey(SatelliteConstellation.GLONASS, 43, "L1", "L1"),
                SatelliteSignalKey(SatelliteConstellation.GLONASS, 43, "L2", "L2"),
                SatelliteSignalKey(SatelliteConstellation.BEIDOU, 10, "B2", "B2"),
                SatelliteSignalKey(SatelliteConstellation.BEIDOU, 10, "B3", "B3"),
                SatelliteSignalKey(SatelliteConstellation.QZSS, 195, SatelliteSignalKey.BAND_ANY),
            ),
            observations.map { it.key },
        )
        assertEquals(List(observations.size) { SatelliteMonitorSource.SOLUTION }, observations.map { it.source })
        assertTrue(observations.all { it.used })
    }

    @Test
    fun `malformed UM980 satellite monitor frames return empty and do not throw`() {
        assertEquals(
            emptyList<SatelliteSignalObservation>(),
            assertDoesNotThrow {
                Um980BinaryParser.parseObsvmbObservations(unicoreFrame(12, byteArrayOf(1, 0, 0, 0)))
            },
        )
        assertEquals(
            emptyList<SatelliteSignalObservation>(),
            assertDoesNotThrow {
                Um980BinaryParser.parseObsvmcmpbObservations(unicoreFrame(138, byteArrayOf(1, 0, 0, 0)))
            },
        )
        assertEquals(
            emptyList<SatelliteSignalObservation>(),
            assertDoesNotThrow {
                Um980BinaryParser.parseBestsatbObservations(unicoreFrame(1041, byteArrayOf(1, 0, 0, 0)))
            },
        )
        assertEquals(
            emptyList<SatelliteSignalObservation>(),
            assertDoesNotThrow {
                Um980BinaryParser.parseObsvmbObservations(byteArrayOf(0xAA.toByte(), 0x44, 0xB5.toByte()))
            },
        )
    }

    @Test
    fun `returns null for non BESTNAVB message id`() {
        val frame = bestnavbFrame(messageId = 999)

        assertNull(Um980BinaryParser.parseBestnavb(frame))
    }

    @Test
    fun `frame extractor accepts only valid crc`() {
        val frame = bestnavbFrame()
        val invalid = frame.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }

        assertEquals(0, frame[3].toInt())
        assertEquals(frame.toList(), Um980BinaryParser.extractFrames(frame).single().toList())
        assertTrue(Um980BinaryParser.isValidFrame(frame))
        assertFalse(Um980BinaryParser.isValidFrame(invalid))
        assertEquals(emptyList<ByteArray>(), Um980BinaryParser.extractFrames(invalid))
    }

    @Test
    fun `frame extractor skips noise and incomplete frames`() {
        val frame = bestnavbFrame()
        val mixed = byteArrayOf(1, 2, 3) + frame + frame.copyOfRange(0, 10)

        assertEquals(frame.toList(), Um980BinaryParser.extractFrames(mixed).single().toList())
    }

    companion object {
        fun bestnavbFrame(messageId: Int = 2118, positionType: Int = 50): ByteArray {
            val payloadLength = 120
            val frame = ByteArray(24 + payloadLength + 4)
            frame[0] = 0xAA.toByte()
            frame[1] = 0x44
            frame[2] = 0xB5.toByte()
            putU16(frame, 4, messageId)
            putU16(frame, 6, payloadLength)
            putU16(frame, 10, 2419)
            putU32(frame, 12, 132_572_000)
            val payloadBytes = ByteArray(payloadLength)
            val payload = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
            payload.putInt(0, 0)
            payload.putInt(4, positionType)
            payload.putDouble(8, 50.087451234)
            payload.putDouble(16, 14.421253456)
            payload.putDouble(24, 243.812)
            payload.putFloat(32, 43.611f)
            payload.putFloat(40, 0.008f)
            payload.putFloat(44, 0.007f)
            payload.putFloat(48, 0.06f)
            "1234".encodeToByteArray().copyInto(payloadBytes, destinationOffset = 52)
            payload.putFloat(56, 0.8f)
            payload.put(64, 31)
            payload.put(65, 18)
            payload.putInt(72, 0)
            payload.putInt(76, 50)
            payload.putDouble(88, 1.2)
            payload.putDouble(96, 123.4)
            payload.putDouble(104, -0.2)
            payloadBytes.copyInto(frame, destinationOffset = 24)
            putU32(frame, 24 + payloadLength, crc32(frame, 0, 24 + payloadLength).toInt())
            return frame
        }

        private fun pppnavbFrame(positionType: Int): ByteArray {
            val payloadLength = 72
            val frame = ByteArray(24 + payloadLength + 4)
            frame[0] = 0xAA.toByte()
            frame[1] = 0x44
            frame[2] = 0xB5.toByte()
            putU16(frame, 4, 1026)
            putU16(frame, 6, payloadLength)
            putU16(frame, 10, 2419)
            putU32(frame, 12, 132_572_000)
            val payloadBytes = ByteArray(payloadLength)
            val payload = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
            payload.putInt(0, 0)
            payload.putInt(4, positionType)
            payload.putDouble(8, 50.087451234)
            payload.putDouble(16, 14.421253456)
            payload.putDouble(24, 243.812)
            payload.putFloat(32, 43.611f)
            payload.putFloat(40, 0.008f)
            payload.putFloat(44, 0.007f)
            payload.putFloat(48, 0.06f)
            payload.putFloat(56, 0.8f)
            payload.putFloat(60, 0.0f)
            payload.put(64, 31)
            payload.put(65, 18)
            payloadBytes.copyInto(frame, destinationOffset = 24)
            putU32(frame, 24 + payloadLength, crc32(frame, 0, 24 + payloadLength).toInt())
            return frame
        }

        fun stadopbFrame(messageId: Int = 954): ByteArray {
            val payloadLength = 42 + 4
            val frame = ByteArray(24 + payloadLength + 4)
            frame[0] = 0xAA.toByte()
            frame[1] = 0x44
            frame[2] = 0xB5.toByte()
            putU16(frame, 4, messageId)
            putU16(frame, 6, payloadLength)
            putU16(frame, 10, 2419)
            putU32(frame, 12, 132_572_000)
            val payloadBytes = ByteArray(payloadLength)
            val payload = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
            payload.putInt(0, 0)
            payload.putFloat(4, 0.8094f)
            payload.putFloat(8, 0.7129f)
            payload.putFloat(12, 0.3831f)
            payload.putFloat(16, 0.6046f)
            payload.putFloat(20, 0.3779f)
            payload.putFloat(24, 0.2902f)
            payload.putFloat(28, 0.2421f)
            payload.putFloat(32, 5.0f)
            payload.putFloat(36, 0.0f)
            payload.putShort(40, 50)
            payload.putShort(42, 4)
            payloadBytes.copyInto(frame, destinationOffset = 24)
            putU32(frame, 24 + payloadLength, crc32(frame, 0, 24 + payloadLength).toInt())
            return frame
        }

        private fun obsvmbFrame(vararg records: ByteArray): ByteArray {
            val payload = ByteBuffer.allocate(4 + records.size * 40).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(records.size)
                records.forEach { put(it) }
            }.array()
            return unicoreFrame(12, payload)
        }

        private fun observationRecord(
            system: Int,
            prn: Int,
            signalType: Int,
            cn0Times100: Int,
            l2cFlag: Boolean = false,
        ): ByteArray =
            ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(0, 0)
                putShort(2, prn.toShort())
                putDouble(4, 20_000_000.0 + prn)
                putDouble(12, 100_000_000.0 + prn)
                putShort(20, 1)
                putShort(22, 10)
                putFloat(24, -1234.5f)
                putShort(28, cn0Times100.toShort())
                putShort(30, 0)
                putFloat(32, 12.5f)
                putInt(36, observationStatus(system = system, signalType = signalType, l2cFlag = l2cFlag))
            }.array()

        private fun obsvmcmpbFrame(vararg records: ByteArray): ByteArray {
            val payload = ByteBuffer.allocate(4 + records.size * 24).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(records.size)
                records.forEach { put(it) }
            }.array()
            return unicoreFrame(138, payload)
        }

        private fun compressedObservationRecord(
            system: Int,
            prn: Int,
            signalType: Int,
            cn0DbHz: Int,
            l2cFlag: Boolean = false,
        ): ByteArray {
            val record = ByteArray(24)
            setBits(record, 0, 32, observationStatus(system = system, signalType = signalType, l2cFlag = l2cFlag).toLong())
            setBits(record, 32, 28, 4_096)
            setBits(record, 60, 36, 128_000)
            setBits(record, 96, 32, 65_536)
            setBits(record, 128, 4, 0)
            setBits(record, 132, 4, 0)
            setBits(record, 136, 8, prn.toLong())
            setBits(record, 144, 21, 128)
            setBits(record, 165, 5, (cn0DbHz - 20).toLong())
            setBits(record, 170, 6, 7)
            return record
        }

        private fun bestsatbFrame(vararg entries: ByteArray): ByteArray {
            val payload = ByteBuffer.allocate(4 + entries.size * 16).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(entries.size)
                entries.forEach { put(it) }
            }.array()
            return unicoreFrame(1041, payload)
        }

        private fun bestsatEntry(
            system: Int,
            satelliteId: Int,
            signalMask: Int,
            glonassFrequencyChannel: Int = 0,
        ): ByteArray =
            ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0, system)
                putShort(4, satelliteId.toShort())
                putShort(6, glonassFrequencyChannel.toShort())
                putInt(8, 0)
                putInt(12, signalMask)
            }.array()

        private fun unicoreFrame(messageId: Int, payload: ByteArray): ByteArray {
            val frame = ByteArray(24 + payload.size + 4)
            frame[0] = 0xAA.toByte()
            frame[1] = 0x44
            frame[2] = 0xB5.toByte()
            putU16(frame, 4, messageId)
            putU16(frame, 6, payload.size)
            putU16(frame, 10, 2300)
            putU32(frame, 12, 100_000)
            payload.copyInto(frame, destinationOffset = 24)
            putU32(frame, 24 + payload.size, crc32(frame, 0, 24 + payload.size).toInt())
            return frame
        }

        private fun observationStatus(
            system: Int,
            signalType: Int,
            l2cFlag: Boolean,
        ): Int {
            var status = 0
            status = status or (1 shl 10)
            status = status or (1 shl 12)
            status = status or ((system and 0x7) shl 16)
            status = status or ((signalType and 0x1f) shl 21)
            if (l2cFlag) {
                status = status or (1 shl 26)
            }
            return status
        }

        private fun setBits(bytes: ByteArray, startBit: Int, bitCount: Int, value: Long) {
            for (bit in 0 until bitCount) {
                val absoluteBit = startBit + bit
                val byteIndex = absoluteBit / 8
                val bitIndex = absoluteBit % 8
                val mask = 1 shl bitIndex
                if (((value ushr bit) and 1L) != 0L) {
                    bytes[byteIndex] = (bytes[byteIndex].toInt() or mask).toByte()
                } else {
                    bytes[byteIndex] = (bytes[byteIndex].toInt() and mask.inv()).toByte()
                }
            }
        }

        private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value and 0xff).toByte()
            bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
        }

        private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value and 0xff).toByte()
            bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
            bytes[offset + 2] = ((value ushr 16) and 0xff).toByte()
            bytes[offset + 3] = ((value ushr 24) and 0xff).toByte()
        }

        private fun crc32(bytes: ByteArray, offset: Int, length: Int): UInt {
            var crc = 0u
            for (i in offset until offset + length) {
                crc = crc xor (bytes[i].toUInt() and 0xffu)
                repeat(8) {
                    crc = if ((crc and 1u) != 0u) {
                        (crc shr 1) xor 0xEDB88320u
                    } else {
                        crc shr 1
                    }
                }
            }
            return crc
        }
    }
}
