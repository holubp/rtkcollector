package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class Rtcm3MsmParserTest {
    @Test
    fun `parses gps msm signal masks into visible bands`() {
        val frame = RtcmMsmTestFrameBuilder(messageType = 1074, stationId = 321)
            .satellite(12)
            .satellite(24)
            .signal(2)
            .signal(22)
            .cell(satellite = 12, signal = 2, cn0DbHz = null)
            .cell(satellite = 12, signal = 22, cn0DbHz = null)
            .cell(satellite = 24, signal = 2, cn0DbHz = null)
            .cell(satellite = 24, signal = 22, cn0DbHz = null)
            .build()

        val result = Rtcm3MsmParser.parse(frame)

        require(result.diagnostic == null) { "Unexpected diagnostic: ${result.diagnostic}" }

        assertEquals(1074, result.messageType)
        assertEquals(321, result.stationId)
        assertEquals(4, result.signals.size)
        assertEquals(setOf(12, 24), result.signals.map { it.key.svid }.toSet())
        val seenL1 = result.signals.filter { it.key.band == SatelliteFrequencyBand.L1 }
        val seenL5 = result.signals.filter { it.key.band == SatelliteFrequencyBand.L5 }
        assertEquals(2, seenL1.size)
        assertEquals(2, seenL5.size)
    }

    @Test
    fun `parses galileo msm signal ids using rtklib band table`() {
        val frame = RtcmMsmTestFrameBuilder(messageType = 1091, stationId = 77)
            .satellite(4)
            .signal(2)
            .signal(8)
            .cell(satellite = 4, signal = 2, cn0DbHz = null)
            .cell(satellite = 4, signal = 8, cn0DbHz = null)
            .build()

        val result = Rtcm3MsmParser.parse(frame)

        require(result.diagnostic == null) { "Unexpected diagnostic: ${result.diagnostic}" }

        assertEquals(1091, result.messageType)
        assertEquals(SatelliteConstellation.GALILEO, result.signals.single { it.key.svid == 4 && it.key.signalCode == "MSM2" }.key.constellation)
        val bands = result.signals.mapTo(mutableSetOf()) { it.key.band }
        assertEquals(setOf(SatelliteFrequencyBand.L1, SatelliteFrequencyBand.L6), bands)
    }

    @Test
    fun `returns empty result with diagnostic for malformed frame`() {
        val malformed = Rtcm3Frame(
            bytes = byteArrayOf(0xd3.toByte(), 0x00, 0x00),
            payloadLength = 0,
            messageType = 1074,
            crcValid = true,
        )

        val result = Rtcm3MsmParser.parse(malformed)

        assertEquals(emptyList<Rtcm3MsmSignal>(), result.signals)
        assertNotNull(result.diagnostic)
    }

    @Test
    fun `extracts cn0 for msm7 variants`() {
        val frame = RtcmMsmTestFrameBuilder(messageType = 1077, stationId = 22)
            .satellite(10)
            .signal(15)
            .cell(satellite = 10, signal = 15, cn0DbHz = 47.0)
            .build()

        val result = Rtcm3MsmParser.parse(frame)

        require(result.diagnostic == null) { "Unexpected diagnostic: ${result.diagnostic}" }

        assertEquals(47.0, result.signals.single().base.cn0DbHz!!, 0.5)
        assertFalse(result.signals.single().key.signalCode.isNullOrBlank())
    }

    @Test
    fun `maps rtklib-defined msm signal ids and leaves reserved ids unknown`() {
        val gps = RtcmMsmTestFrameBuilder(messageType = 1075, stationId = 1022)
            .satellite(8)
            .signal(8)
            .signal(16)
            .signal(21)
            .signal(29)
            .cell(satellite = 8, signal = 8, cn0DbHz = null)
            .cell(satellite = 8, signal = 16, cn0DbHz = null)
            .cell(satellite = 8, signal = 21, cn0DbHz = null)
            .cell(satellite = 8, signal = 29, cn0DbHz = null)
            .build()
        val galileo = RtcmMsmTestFrameBuilder(messageType = 1095, stationId = 1022)
            .satellite(8)
            .signal(8)
            .signal(14)
            .signal(22)
            .cell(satellite = 8, signal = 8, cn0DbHz = null)
            .cell(satellite = 8, signal = 14, cn0DbHz = null)
            .cell(satellite = 8, signal = 22, cn0DbHz = null)
            .build()
        val beidou = RtcmMsmTestFrameBuilder(messageType = 1125, stationId = 1022)
            .satellite(30)
            .signal(8)
            .signal(14)
            .signal(22)
            .cell(satellite = 30, signal = 8, cn0DbHz = null)
            .cell(satellite = 30, signal = 14, cn0DbHz = null)
            .cell(satellite = 30, signal = 22, cn0DbHz = null)
            .build()

        assertEquals(
            listOf(
                SatelliteFrequencyBand.L2,
                SatelliteFrequencyBand.L2,
                SatelliteFrequencyBand.UNKNOWN,
                SatelliteFrequencyBand.UNKNOWN,
            ),
            Rtcm3MsmParser.parse(gps).signals.map { it.key.band },
        )
        assertEquals(
            listOf(
                SatelliteFrequencyBand.L6,
                SatelliteFrequencyBand.valueOf("L7"),
                SatelliteFrequencyBand.L5,
            ),
            Rtcm3MsmParser.parse(galileo).signals.map { it.key.band },
        )
        assertEquals(
            listOf(
                SatelliteFrequencyBand.L6,
                SatelliteFrequencyBand.valueOf("L7"),
                SatelliteFrequencyBand.L5,
            ),
            Rtcm3MsmParser.parse(beidou).signals.map { it.key.band },
        )
    }
}

private class RtcmMsmTestFrameBuilder(
    private val messageType: Int,
    private val stationId: Int,
) {
    private val satellites = mutableListOf<Int>()
    private val signals = mutableListOf<Int>()
    private val cells = mutableListOf<Cell>()

    fun satellite(satellite: Int) = apply { satellites += satellite }
    fun signal(signal: Int) = apply { signals += signal }
    fun cell(satellite: Int, signal: Int, cn0DbHz: Double?) = apply {
        cells += Cell(satellite, signal, cn0DbHz)
    }

    fun build(): Rtcm3Frame {
        val payload = RtcmBitWriter()
            .unsigned(messageType.toLong(), 12)
            .unsigned(stationId.toLong(), 12)
            .unsigned(0, 30)
            .unsigned(0, 1)
            .unsigned(0, 3)
            .unsigned(0, 7)
            .unsigned(0, 2)
            .mask64(satellites)
            .mask32(signals)
            .cellMask(satellites, signals, cells)
            .msmCellPayload(messageType, satellites, signals, cells)
            .toByteArray()

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

    private data class Cell(val satellite: Int, val signal: Int, val cn0DbHz: Double?)

    private class RtcmBitWriter {
        private val bits = mutableListOf<Int>()

        fun unsigned(value: Long, width: Int): RtcmBitWriter {
            for (bit in width - 1 downTo 0) {
                bits += ((value ushr bit) and 1L).toInt()
            }
            return this
        }

        fun mask64(ids: List<Int>): RtcmBitWriter =
            mask(64, ids)

        fun mask32(ids: List<Int>): RtcmBitWriter =
            mask(32, ids)

        fun cellMask(satellites: List<Int>, signals: List<Int>, cells: List<Cell>): RtcmBitWriter {
            val cellSet = cells.mapTo(mutableSetOf()) { it.satellite to it.signal }
            satellites.forEach { satellite ->
                signals.forEach { signal ->
                    bits += if (cellSet.contains(satellite to signal)) 1 else 0
                }
            }
            return this
        }

        fun msmCellPayload(
            messageType: Int,
            satellites: List<Int>,
            signals: List<Int>,
            cells: List<Cell>,
        ): RtcmBitWriter {
            val kind = messageType % 10
            val cellCn0 = cells.associate { (it.satellite to it.signal) to it.cn0DbHz }
            val cellSet = cells.mapTo(mutableSetOf()) { it.satellite to it.signal }
            val activeCn0 = buildList {
                satellites.forEach { satellite ->
                    signals.forEach { signal ->
                        if (cellSet.contains(satellite to signal)) {
                            add(cellCn0[satellite to signal])
                        }
                    }
                }
            }

            when (kind) {
                4, 5 -> {
                    unsigned(0, if (kind == 4) 18 * satellites.size else 36 * satellites.size)
                    unsigned(0, (15 + 22 + 4 + 1) * activeCn0.size)
                    activeCn0.forEach { cn0 ->
                        unsigned((cn0?.coerceIn(0.0, 63.0) ?: 0.0).toLong(), 6)
                    }
                    if (kind == 5) unsigned(0, 15 * activeCn0.size)
                }

                6, 7 -> {
                    unsigned(0, if (kind == 6) 22 * satellites.size else 36 * satellites.size)
                    unsigned(0, (20 + 24 + 10 + 1) * activeCn0.size)
                    activeCn0.forEach { cn0 ->
                        val encoded = cn0?.let { (it * 16.0).toInt().coerceIn(0, 1023) } ?: 0
                        unsigned(encoded.toLong(), 10)
                    }
                    if (kind == 7) unsigned(0, 15 * activeCn0.size)
                }

                else -> {
                    // Unsupported payload for this conservative parser.
                    // Intentionally no per-cell data.
                }
            }

            return this
        }

        fun toByteArray(): ByteArray {
            val bytes = ByteArray((bits.size + 7) / 8)
            bits.forEachIndexed { index, bit ->
                if (bit == 1) {
                    bytes[index / 8] = (bytes[index / 8].toInt() or (1 shl (7 - index % 8))).toByte()
                }
            }
            return bytes
        }

        private fun mask(width: Int, ids: List<Int>): RtcmBitWriter {
            for (index in 1..width) {
                bits += if (ids.contains(index)) 1 else 0
            }
            return this
        }
    }
}
