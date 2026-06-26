package org.rtkcollector.receiver.unicore

import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteSignalKey

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

    fun bestsatConstellationForSystem(system: Int): SatelliteConstellation = when (system) {
        0 -> SatelliteConstellation.GPS
        1 -> SatelliteConstellation.GLONASS
        2 -> SatelliteConstellation.SBAS
        5 -> SatelliteConstellation.GALILEO
        6 -> SatelliteConstellation.BEIDOU
        7 -> SatelliteConstellation.QZSS
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

    fun signalKeyFromTrackingStatus(svid: Int, trackingStatus: Int): SatelliteSignalKey? {
        if (svid <= 0) return null
        val constellation = constellationForSystem((trackingStatus ushr 16) and 0x7)
        if (constellation == SatelliteConstellation.UNKNOWN) return null
        val monitorSvid = normalizeMonitorSvid(constellation, svid) ?: return null
        val signalType = (trackingStatus ushr 21) and 0x1f
        val l2cFlag = ((trackingStatus ushr 26) and 0x1) != 0
        val signal = bandAndSignalForTrackingStatus(
            constellation = constellation,
            signalType = signalType,
            l2cFlag = l2cFlag,
        ) ?: return null
        return SatelliteSignalKey(
            constellation = constellation,
            svid = monitorSvid,
            band = signal.band,
            signalCode = signal.signalCode,
        )
    }

    fun bestsatMonitorSvid(
        constellation: SatelliteConstellation,
        satelliteIdLow: Int,
        satelliteIdHigh: Int,
    ): Int? {
        // The UM980 manual describes the low half as the satellite identifier,
        // but field captures encode the monitor PRN in the high half for
        // BESTSATB. Accept both layouts so the monitor stays compatible with
        // documented and observed firmware output.
        return when (constellation) {
            SatelliteConstellation.GLONASS ->
                normalizeGlonassMonitorSvid(satelliteIdHigh)
                    ?: normalizeGlonassMonitorSvid(satelliteIdLow)

            else ->
                normalizeMonitorSvid(constellation, satelliteIdHigh)
                    ?: normalizeMonitorSvid(constellation, satelliteIdLow)
        }
    }

    private fun normalizeMonitorSvid(
        constellation: SatelliteConstellation,
        svid: Int,
    ): Int? =
        when {
            svid <= 0 -> null
            constellation == SatelliteConstellation.GLONASS && svid in GLONASS_UNICORE_PRN_RANGE ->
                svid - GLONASS_UNICORE_PRN_OFFSET

            else -> svid
        }

    private fun normalizeGlonassMonitorSvid(svid: Int): Int? =
        when {
            svid in GLONASS_UNICORE_PRN_RANGE -> svid - GLONASS_UNICORE_PRN_OFFSET
            svid in GLONASS_RINEX_SLOT_RANGE -> svid
            else -> null
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

            SatelliteConstellation.QZSS -> listOf(
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
                0x08 to Um980BandAndSignal("E5", "ALTBOC"),
            )

            else -> emptyList()
        }
        return entries
            .filter { (mask, _) -> signalMask and mask != 0 }
            .map { (_, bandInfo) -> bandInfo }
    }

    private val GLONASS_UNICORE_PRN_RANGE = 38..61
    private val GLONASS_RINEX_SLOT_RANGE = 1..24
    private const val GLONASS_UNICORE_PRN_OFFSET = 37
}
