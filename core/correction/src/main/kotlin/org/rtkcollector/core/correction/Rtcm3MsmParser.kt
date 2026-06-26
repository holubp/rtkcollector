package org.rtkcollector.core.correction

object Rtcm3MsmParser {
    fun parse(frame: Rtcm3Frame): Rtcm3MsmObservation {
        return runCatching {
            if (frame.crcValid == false) {
                return Rtcm3MsmObservation(diagnostic = "RTCM frame failed CRC validation.")
            }

            val bits = RtcmBitReader(frame.bytes, bitOffset = 24, bitLength = frame.payloadLength * 8)
            val messageType = bits.unsigned(12).toInt()
            val constellation = RtcmSignalMapping.constellationFor(messageType) ?: return Rtcm3MsmObservation(
                diagnostic = "Unsupported message type $messageType",
            )
            val stationId = bits.unsigned(12).toInt()
            val msmKind = messageType % 10

            bits.skip(30)
            bits.skip(1)
            bits.skip(3)
            bits.skip(7)
            bits.skip(2)
            bits.skip(2)
            bits.skip(1)
            bits.skip(3)

            val satellites = readMask(bits, 64)
            val signals = readMask(bits, 32)
            val activeCells = satellites.flatMap { satellite ->
                signals.mapNotNull { signal ->
                    if (bits.unsigned(1) == 1L) satellite to signal else null
                }
            }

            val cn0Values = readCn0Values(bits, satellites.size, activeCells.size, msmKind)

            val parsedSignals = activeCells.mapIndexed { index, (satellite, signal) ->
                Rtcm3MsmSignal(
                    key = SatelliteSignalKey(
                        constellation = constellation,
                        svid = satellite,
                        band = RtcmSignalMapping.bandFor(constellation, signal),
                        signalCode = RtcmSignalMapping.signalCode(signal),
                    ),
                    base = BaseSignalState(
                        stationId = stationId,
                        cn0DbHz = cn0Values?.getOrNull(index),
                    ),
                )
            }

            Rtcm3MsmObservation(
                messageType = messageType,
                stationId = stationId,
                signals = parsedSignals,
            )
        }.getOrElse {
            Rtcm3MsmObservation(diagnostic = "Malformed or truncated RTCM MSM frame: ${it.message ?: it::class.simpleName}")
        }
    }

    private fun readCn0Values(
        bits: RtcmBitReader,
        satelliteCount: Int,
        cellCount: Int,
        msmKind: Int,
    ): List<Double?>? {
        if (cellCount == 0) return emptyList()
        val layout = MsmLayout.forKind(msmKind) ?: return null
        val requiredBits = layout.satelliteBits * satelliteCount +
            layout.cellBitsBeforeCn0 * cellCount +
            layout.cn0Bits * cellCount
        if (bits.remaining < requiredBits) return null

        bits.skip(layout.satelliteBits * satelliteCount)
        bits.skip(layout.cellBitsBeforeCn0 * cellCount)
        return List(cellCount) {
            when (layout.cn0Bits) {
                6 -> bits.unsigned(6).toDouble()
                10 -> bits.unsigned(10) * 0.0625
                else -> null
            }
        }
    }

    private fun readMask(bits: RtcmBitReader, width: Int): List<Int> {
        val result = mutableListOf<Int>()
        for (index in 1..width) {
            if (bits.unsigned(1) == 1L) {
                result += index
            }
        }
        return result
    }

    private data class MsmLayout(
        val satelliteBits: Int,
        val cellBitsBeforeCn0: Int,
        val cn0Bits: Int,
    ) {
        companion object {
            fun forKind(kind: Int): MsmLayout? = when (kind) {
                4 -> MsmLayout(
                    satelliteBits = 18,
                    cellBitsBeforeCn0 = 15 + 22 + 4 + 1,
                    cn0Bits = 6,
                )
                5 -> MsmLayout(
                    satelliteBits = 36,
                    cellBitsBeforeCn0 = 15 + 22 + 4 + 1,
                    cn0Bits = 6,
                )
                6 -> MsmLayout(
                    satelliteBits = 22,
                    cellBitsBeforeCn0 = 20 + 24 + 10 + 1,
                    cn0Bits = 10,
                )
                7 -> MsmLayout(
                    satelliteBits = 36,
                    cellBitsBeforeCn0 = 20 + 24 + 10 + 1,
                    cn0Bits = 10,
                )
                else -> null
            }
        }
    }
}
