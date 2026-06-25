package org.rtkcollector.core.quality

enum class SatelliteMonitorEngine {
    IN_DEVICE_RTK,
    RTKLIB,
}

enum class SatelliteMonitorSource {
    ROVER,
    BASE,
    SOLUTION,
}

enum class SatelliteMonitorSourceState {
    FRESH,
    STALE,
    UNAVAILABLE,
}

enum class SatelliteConstellation {
    GPS,
    GLONASS,
    GALILEO,
    BEIDOU,
    QZSS,
    SBAS,
    UNKNOWN,
}

data class SatelliteSignalKey(
    val constellation: SatelliteConstellation,
    val svid: Int,
    val band: String,
    val signalCode: String? = null,
) {
    init {
        require(svid >= 0) { "Satellite SVID must be non-negative." }
        require(band.isNotBlank()) { "Satellite signal band must not be blank." }
    }

    fun isSatelliteLevelUsage(): Boolean = band == BAND_ANY

    companion object {
        const val BAND_ANY: String = "*"
    }
}

data class SatelliteSignalObservation(
    val key: SatelliteSignalKey,
    val source: SatelliteMonitorSource,
    val observedAtEpochMillis: Long,
    val cn0DbHz: Double? = null,
    val used: Boolean = false,
)

data class SatelliteMonitorInputBatch(
    val engine: SatelliteMonitorEngine,
    val source: SatelliteMonitorSource,
    val receivedAtEpochMillis: Long,
    val observations: List<SatelliteSignalObservation>,
)

data class SatelliteMonitorFreshness(
    val state: SatelliteMonitorSourceState,
    val ageMillis: Long?,
)

data class SatelliteFrequencySummary(
    val constellation: SatelliteConstellation,
    val band: String,
    val roverVisible: Int,
    val roverUsed: Int,
    val baseVisible: Int,
    val baseUsed: Int,
    val roverAverageCn0DbHz: Double?,
    val baseAverageCn0DbHz: Double?,
)

data class SatelliteMonitorSnapshot(
    val engine: SatelliteMonitorEngine,
    val generatedAtEpochMillis: Long,
    val sourceFreshness: Map<SatelliteMonitorSource, SatelliteMonitorFreshness>,
    val summaries: List<SatelliteFrequencySummary>,
    val diagnostics: List<String> = emptyList(),
) {
    val message: String?
        get() = diagnostics.firstOrNull()
}
