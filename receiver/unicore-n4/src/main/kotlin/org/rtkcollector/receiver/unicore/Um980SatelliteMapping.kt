package org.rtkcollector.receiver.unicore

import org.rtkcollector.core.quality.SatelliteConstellation

internal data class Um980BandAndSignal(
    val band: String,
    val signalCode: String,
)

internal object Um980SatelliteMapping {
    fun constellationForSystem(system: Int): SatelliteConstellation = when (system) {
        0 -> SatelliteConstellation.GPS
        1 -> SatelliteConstellation.GLONASS
        2 -> SatelliteConstellation.SBAS
        3 -> SatelliteConstellation.GALILEO
        4 -> SatelliteConstellation.BEIDOU
        5 -> SatelliteConstellation.QZSS
        else -> SatelliteConstellation.UNKNOWN
    }

    fun bandAndSignalForTrackingStatus(
        constellation: SatelliteConstellation,
        signalType: Int,
        l2cFlag: Boolean,
    ): Um980BandAndSignal? =
        when (constellation) {
            SatelliteConstellation.GPS -> when (signalType) {
                0 -> Um980BandAndSignal("L1", "L1")
                3, 11 -> Um980BandAndSignal("L1", "L1C")
                6, 14 -> Um980BandAndSignal("L5", "L5")
                9 -> Um980BandAndSignal("L2", if (l2cFlag) "L2C" else "L2")
                17 -> Um980BandAndSignal("L2", "L2C")
                else -> null
            }

            SatelliteConstellation.GLONASS -> when (signalType) {
                0 -> Um980BandAndSignal("L1", "L1")
                5 -> Um980BandAndSignal("L2", "L2")
                6, 7 -> Um980BandAndSignal("L3", "L3")
                else -> null
            }

            SatelliteConstellation.GALILEO -> when (signalType) {
                1, 2 -> Um980BandAndSignal("E1", "E1")
                12 -> Um980BandAndSignal("E5A", "E5A")
                17 -> Um980BandAndSignal("E5B", "E5B")
                18, 22 -> Um980BandAndSignal("E6", "E6")
                else -> null
            }

            SatelliteConstellation.BEIDOU -> when (signalType) {
                0, 4, 8, 23 -> Um980BandAndSignal("B1", "B1")
                12 -> Um980BandAndSignal("B2", "B2A")
                5, 13, 17, 28 -> Um980BandAndSignal("B2", "B2")
                6, 21 -> Um980BandAndSignal("B3", "B3I")
                else -> null
            }

            SatelliteConstellation.QZSS -> when (signalType) {
                0 -> Um980BandAndSignal("L1", "L1")
                3, 11 -> Um980BandAndSignal("L1", "L1C")
                6, 14 -> Um980BandAndSignal("L5", "L5")
                9 -> Um980BandAndSignal("L2", if (l2cFlag) "L2C" else "L2")
                27 -> Um980BandAndSignal("L6", "L6E")
                21 -> Um980BandAndSignal("L6", "L6")
                else -> null
            }

            SatelliteConstellation.SBAS -> when (signalType) {
                0 -> Um980BandAndSignal("L1", "L1")
                14 -> Um980BandAndSignal("L5", "L5")
                else -> null
            }

            SatelliteConstellation.UNKNOWN -> null
        }

    fun bestsatSignalsFor(
        constellation: SatelliteConstellation,
        signalMask: Int,
    ): List<Um980BandAndSignal> {
        val entries = when (constellation) {
            SatelliteConstellation.GPS -> listOf(
                0x01 to Um980BandAndSignal("L1", "L1"),
                0x02 to Um980BandAndSignal("L2", "L2"),
                0x04 to Um980BandAndSignal("L5", "L5"),
            )

            SatelliteConstellation.GLONASS -> listOf(
                0x01 to Um980BandAndSignal("L1", "L1"),
                0x02 to Um980BandAndSignal("L2", "L2"),
                0x04 to Um980BandAndSignal("L3", "L3"),
            )

            SatelliteConstellation.BEIDOU -> listOf(
                0x01 to Um980BandAndSignal("B1", "B1"),
                0x02 to Um980BandAndSignal("B2", "B2"),
                0x04 to Um980BandAndSignal("B3", "B3"),
            )

            SatelliteConstellation.GALILEO -> listOf(
                0x01 to Um980BandAndSignal("E1", "E1"),
                0x02 to Um980BandAndSignal("E5A", "E5A"),
                0x04 to Um980BandAndSignal("E5B", "E5B"),
            )

            else -> emptyList()
        }
        return entries
            .filter { (mask, _) -> signalMask and mask != 0 }
            .map { (_, bandInfo) -> bandInfo }
    }
}
