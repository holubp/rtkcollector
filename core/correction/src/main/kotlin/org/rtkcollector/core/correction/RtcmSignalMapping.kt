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
        signalCodeFor(constellation, signalId)?.toBand(constellation) ?: SatelliteFrequencyBand.UNKNOWN

    fun signalCode(signalId: Int): String = "MSM$signalId"

    private fun signalCodeFor(constellation: SatelliteConstellation, signalId: Int): String? {
        if (signalId !in 1..32) return null
        val table = when (constellation) {
            SatelliteConstellation.GPS -> gpsSignalCodes
            SatelliteConstellation.GLONASS -> glonassSignalCodes
            SatelliteConstellation.GALILEO -> galileoSignalCodes
            SatelliteConstellation.BEIDOU -> beidouSignalCodes
            SatelliteConstellation.QZSS -> qzssSignalCodes
            SatelliteConstellation.SBAS -> sbasSignalCodes
            SatelliteConstellation.UNKNOWN -> return null
        }
        return table[signalId - 1].takeIf { it.isNotEmpty() }
    }

    private fun String.toBand(constellation: SatelliteConstellation): SatelliteFrequencyBand =
        if (constellation == SatelliteConstellation.BEIDOU) {
            toBeiDouBand()
        } else {
            firstOrNull()?.toGenericBand() ?: SatelliteFrequencyBand.UNKNOWN
        }

    private fun String.toBeiDouBand(): SatelliteFrequencyBand =
        when (firstOrNull()) {
            '1', '2' -> SatelliteFrequencyBand.L1
            '5' -> SatelliteFrequencyBand.L5
            '6' -> SatelliteFrequencyBand.L6
            '7' -> SatelliteFrequencyBand.L7
            '8' -> SatelliteFrequencyBand.L8
            else -> SatelliteFrequencyBand.UNKNOWN
        }

    private fun Char.toGenericBand(): SatelliteFrequencyBand =
        when (this) {
            '1' -> SatelliteFrequencyBand.L1
            '2' -> SatelliteFrequencyBand.L2
            '3' -> SatelliteFrequencyBand.L3
            '4' -> SatelliteFrequencyBand.L4
            '5' -> SatelliteFrequencyBand.L5
            '6' -> SatelliteFrequencyBand.L6
            '7' -> SatelliteFrequencyBand.L7
            '8' -> SatelliteFrequencyBand.L8
            '9' -> SatelliteFrequencyBand.L9
            else -> SatelliteFrequencyBand.UNKNOWN
        }

    private val gpsSignalCodes = listOf(
        "",
        "1C",
        "1P",
        "1W",
        "",
        "",
        "",
        "2C",
        "2P",
        "2W",
        "",
        "",
        "",
        "",
        "2S",
        "2L",
        "2X",
        "",
        "",
        "",
        "",
        "5I",
        "5Q",
        "5X",
        "",
        "",
        "",
        "",
        "",
        "1S",
        "1L",
        "1X",
    )

    private val glonassSignalCodes = listOf(
        "",
        "1C",
        "1P",
        "",
        "",
        "",
        "",
        "2C",
        "2P",
        "",
        "",
        "",
        "",
        "3I",
        "3Q",
        "3X",
        "",
        "4A",
        "4B",
        "4X",
        "",
        "6A",
        "6B",
        "6X",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
    )

    private val galileoSignalCodes = listOf(
        "",
        "1C",
        "1A",
        "1B",
        "1X",
        "1Z",
        "",
        "6C",
        "6A",
        "6B",
        "6X",
        "6Z",
        "",
        "7I",
        "7Q",
        "7X",
        "",
        "8I",
        "8Q",
        "8X",
        "",
        "5I",
        "5Q",
        "5X",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
    )

    private val qzssSignalCodes = listOf(
        "",
        "1C",
        "",
        "",
        "1E",
        "1Z",
        "1B",
        "",
        "6S",
        "6L",
        "6X",
        "6E",
        "6Z",
        "",
        "2S",
        "2L",
        "2X",
        "",
        "",
        "",
        "",
        "5I",
        "5Q",
        "5X",
        "5D",
        "5P",
        "5Z",
        "",
        "",
        "1S",
        "1L",
        "1X",
    )

    private val sbasSignalCodes = listOf(
        "",
        "1C",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "5I",
        "5Q",
        "5X",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
    )

    private val beidouSignalCodes = listOf(
        "",
        "2I",
        "2Q",
        "2X",
        "1S",
        "1L",
        "1Z",
        "6I",
        "6Q",
        "6X",
        "6D",
        "6P",
        "6Z",
        "7I",
        "7Q",
        "7X",
        "",
        "8D",
        "8P",
        "8X",
        "",
        "5D",
        "5P",
        "5X",
        "7D",
        "7P",
        "7Z",
        "",
        "",
        "1D",
        "1P",
        "1X",
    )
}
