package org.rtkcollector.receiver.ublox

import org.rtkcollector.core.quality.SatelliteConstellation

internal data class UbloxBandAndSignal(
    val band: String,
    val signalCode: String,
)

internal object UbloxSatelliteMapping {
    fun constellationFor(gnssId: Int): SatelliteConstellation = when (gnssId) {
        0 -> SatelliteConstellation.GPS
        2 -> SatelliteConstellation.GALILEO
        3 -> SatelliteConstellation.BEIDOU
        5 -> SatelliteConstellation.QZSS
        6 -> SatelliteConstellation.GLONASS
        else -> SatelliteConstellation.UNKNOWN
    }

    fun bandAndSignalFor(constellation: SatelliteConstellation, signalId: Int): UbloxBandAndSignal? = when (constellation) {
        SatelliteConstellation.GPS -> when (signalId) {
            in 0..1 -> UbloxBandAndSignal("L1", "L1")
            in 3..4 -> UbloxBandAndSignal("L2", "L2")
            else -> null
        }
        SatelliteConstellation.GALILEO -> when (signalId) {
            0 -> UbloxBandAndSignal("L1", "E1")
            5 -> UbloxBandAndSignal("L5", "E5a")
            in 6..7 -> UbloxBandAndSignal("L5", "E5a")
            else -> null
        }
        SatelliteConstellation.BEIDOU -> when (signalId) {
            in 0..1 -> UbloxBandAndSignal("L1", "B1")
            in 2..3 -> UbloxBandAndSignal("L2", "B2")
            else -> null
        }
        SatelliteConstellation.GLONASS -> when (signalId) {
            0, 1 -> UbloxBandAndSignal("L1", "L1")
            2, 3 -> UbloxBandAndSignal("L2", "L2")
            else -> null
        }
        SatelliteConstellation.QZSS -> when (signalId) {
            in 0..1 -> UbloxBandAndSignal("L1", "L1")
            in 2..3 -> UbloxBandAndSignal("L2", "L2")
            in 5..6 -> UbloxBandAndSignal("L5", "L5")
            else -> null
        }
        else -> null
    }
}
