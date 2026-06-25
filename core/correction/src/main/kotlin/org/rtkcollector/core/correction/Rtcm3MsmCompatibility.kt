package org.rtkcollector.core.correction

data class SatelliteSignalKey(
    val constellation: SatelliteConstellation,
    val svid: Int,
    val band: SatelliteFrequencyBand,
    val signalCode: String? = null,
)

data class BaseSignalState(
    val stationId: Int,
    val cn0DbHz: Double? = null,
)

data class Rtcm3MsmSignal(
    val key: SatelliteSignalKey,
    val base: BaseSignalState,
)

enum class SatelliteFrequencyBand {
    UNKNOWN,
    L1,
    L2,
    L5,
    L6,
}

enum class SatelliteConstellation {
    UNKNOWN,
    GPS,
    GLONASS,
    GALILEO,
    BEIDOU,
    QZSS,
    SBAS,
}

data class Rtcm3MsmObservation(
    val messageType: Int? = null,
    val stationId: Int? = null,
    val signals: List<Rtcm3MsmSignal> = emptyList(),
    val diagnostic: String? = null,
)
