package org.rtkcollector.core.correction

internal data class RtcmMsmFamily(
    val constellation: SatelliteConstellation,
    val firstMessageType: Int,
    val lastMessageType: Int,
)

internal object RtcmSignalMapping {
    private val families = listOf(
        RtcmMsmFamily(SatelliteConstellation.GPS, 1071, 1077),
        RtcmMsmFamily(SatelliteConstellation.GLONASS, 1081, 1087),
        RtcmMsmFamily(SatelliteConstellation.GALILEO, 1091, 1097),
        RtcmMsmFamily(SatelliteConstellation.BEIDOU, 1121, 1127),
        RtcmMsmFamily(SatelliteConstellation.QZSS, 1111, 1117),
        RtcmMsmFamily(SatelliteConstellation.SBAS, 1101, 1107),
    )

    fun constellationFor(messageType: Int): SatelliteConstellation? =
        families.firstOrNull { messageType in it.firstMessageType..it.lastMessageType }?.constellation

    fun bandFor(constellation: SatelliteConstellation, signalId: Int): SatelliteFrequencyBand =
        when (constellation) {
            SatelliteConstellation.GPS -> when (signalId) {
                2, 3, 4, 30 -> SatelliteFrequencyBand.L1
                8, 9, 10, 15, 16 -> SatelliteFrequencyBand.L2
                22, 23, 24 -> SatelliteFrequencyBand.L5
                else -> SatelliteFrequencyBand.UNKNOWN
            }
            SatelliteConstellation.GLONASS -> when (signalId) {
                2, 3 -> SatelliteFrequencyBand.L1
                8, 9 -> SatelliteFrequencyBand.L2
                else -> SatelliteFrequencyBand.UNKNOWN
            }
            SatelliteConstellation.GALILEO -> when (signalId) {
                2, 3, 4 -> SatelliteFrequencyBand.L1
                8, 9, 10 -> SatelliteFrequencyBand.L5
                14, 15, 16 -> SatelliteFrequencyBand.L6
                else -> SatelliteFrequencyBand.UNKNOWN
            }
            SatelliteConstellation.BEIDOU -> when (signalId) {
                2, 3, 4 -> SatelliteFrequencyBand.L1
                8, 9, 10 -> SatelliteFrequencyBand.L2
                14, 15, 16 -> SatelliteFrequencyBand.L5
                else -> SatelliteFrequencyBand.UNKNOWN
            }
            SatelliteConstellation.QZSS -> when (signalId) {
                2, 3, 4, 30 -> SatelliteFrequencyBand.L1
                8, 9, 10 -> SatelliteFrequencyBand.L2
                22, 23, 24 -> SatelliteFrequencyBand.L5
                14, 15, 16 -> SatelliteFrequencyBand.L6
                else -> SatelliteFrequencyBand.UNKNOWN
            }
            SatelliteConstellation.SBAS -> when (signalId) {
                1, 2, 3, 4, 30 -> SatelliteFrequencyBand.L1
                8, 9, 10, 23, 24 -> SatelliteFrequencyBand.L5
                else -> SatelliteFrequencyBand.UNKNOWN
            }
            SatelliteConstellation.UNKNOWN -> SatelliteFrequencyBand.UNKNOWN
        }

    fun signalCode(signalId: Int): String = "MSM$signalId"
}
