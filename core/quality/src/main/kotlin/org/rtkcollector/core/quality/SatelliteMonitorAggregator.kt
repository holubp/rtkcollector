package org.rtkcollector.core.quality

class SatelliteMonitorAggregator(
    private val freshSourceMillis: Long = DEFAULT_FRESH_SOURCE_MILLIS,
    private val staleSourceMillis: Long = DEFAULT_STALE_SOURCE_MILLIS,
) {
    init {
        require(freshSourceMillis >= 0L) { "Fresh source threshold must be non-negative." }
        require(staleSourceMillis >= freshSourceMillis) {
            "Stale source threshold must be greater than or equal to the fresh threshold."
        }
    }

    fun summarize(
        engine: SatelliteMonitorEngine,
        nowEpochMillis: Long,
        batches: Iterable<SatelliteMonitorInputBatch>,
    ): SatelliteMonitorSnapshot {
        val engineBatches = batches.filter { it.engine == engine }
        val latestBySource = SatelliteMonitorSource.entries.associateWith { source ->
            engineBatches
                .filter { it.source == source }
                .maxByOrNull { it.receivedAtEpochMillis }
        }
        val freshness = latestBySource.mapValues { (_, batch) ->
            val age = batch?.let { (nowEpochMillis - it.receivedAtEpochMillis).coerceAtLeast(0L) }
            SatelliteMonitorFreshness(stateForAge(age), age)
        }
        val activeBatches = engineBatches.filter { batch ->
            val ageMillis = (nowEpochMillis - batch.receivedAtEpochMillis).coerceAtLeast(0L)
            ageMillis <= staleSourceMillis
        }

        val roverVisible = activeBatches.visibleSignals(SatelliteMonitorSource.ROVER)
        val baseVisible = activeBatches.visibleSignals(SatelliteMonitorSource.BASE)
        val solutionUsage = activeBatches.solutionUsage()

        val diagnostics = mutableListOf<String>()
        val roverUsed = usedVisibleSignals(roverVisible.keys, solutionUsage, diagnostics)
        val baseUsed = usedVisibleSignals(baseVisible.keys, solutionUsage, diagnostics)

        val groups = (roverVisible.keys + baseVisible.keys)
            .groupBy { GroupKey(it.constellation, it.band) }

        val summaries = groups.map { (group, keys) ->
            val groupKeys = keys.toSet()
            SatelliteFrequencySummary(
                constellation = group.constellation,
                band = group.band,
                roverVisible = roverVisible.keys.count { it in groupKeys },
                roverUsed = roverUsed.count { it in groupKeys },
                baseVisible = baseVisible.keys.count { it in groupKeys },
                baseUsed = baseUsed.count { it in groupKeys },
                roverAverageCn0DbHz = roverVisible.averageCn0(groupKeys),
                baseAverageCn0DbHz = baseVisible.averageCn0(groupKeys),
            )
        }.sortedWith(compareBy<SatelliteFrequencySummary> { it.constellation.ordinal }.thenBy { it.band })

        return SatelliteMonitorSnapshot(
            engine = engine,
            generatedAtEpochMillis = nowEpochMillis,
            sourceFreshness = freshness,
            summaries = summaries,
            diagnostics = diagnostics.distinct(),
        )
    }

    private fun stateForAge(ageMillis: Long?): SatelliteMonitorSourceState = when {
        ageMillis == null -> SatelliteMonitorSourceState.UNAVAILABLE
        ageMillis <= freshSourceMillis -> SatelliteMonitorSourceState.FRESH
        ageMillis <= staleSourceMillis -> SatelliteMonitorSourceState.STALE
        else -> SatelliteMonitorSourceState.UNAVAILABLE
    }

    private fun List<SatelliteMonitorInputBatch>.visibleSignals(
        source: SatelliteMonitorSource,
    ): Map<SignalBandKey, SatelliteSignalObservation> =
        asSequence()
            .filter { it.source == source }
            .flatMap { it.observations.asSequence() }
            .filter { it.source == source }
            .filterNot { it.key.isSatelliteLevelUsage() }
            .groupBy { it.key.toBandKey() }
            .mapValues { (_, observations) -> observations.maxBy { it.observedAtEpochMillis } }

    private fun List<SatelliteMonitorInputBatch>.solutionUsage(): Set<SatelliteSignalKey> =
        asSequence()
            .filter { it.source == SatelliteMonitorSource.SOLUTION }
            .flatMap { it.observations.asSequence() }
            .filter { it.source == SatelliteMonitorSource.SOLUTION && it.used }
            .map { it.key }
            .toSet()

    private fun usedVisibleSignals(
        visible: Set<SignalBandKey>,
        solutionUsage: Set<SatelliteSignalKey>,
        diagnostics: MutableList<String>,
    ): Set<SignalBandKey> {
        if (visible.isEmpty() || solutionUsage.isEmpty()) return emptySet()

        val explicitUsage = solutionUsage
            .filterNot { it.isSatelliteLevelUsage() }
            .map { it.toBandKey() }
            .toSet()
        val satelliteLevelUsage = solutionUsage
            .filter { it.isSatelliteLevelUsage() }
            .map { SatelliteId(it.constellation, it.svid) }
            .toSet()

        val used = visible.filterTo(mutableSetOf()) { signal ->
            signal in explicitUsage || SatelliteId(signal.constellation, signal.svid) in satelliteLevelUsage
        }

        if (satelliteLevelUsage.isNotEmpty() && used.any { SatelliteId(it.constellation, it.svid) in satelliteLevelUsage }) {
            diagnostics += PARTIAL_USAGE_FREQUENCY_INFERRED
        }

        return used
    }

    private fun Map<SignalBandKey, SatelliteSignalObservation>.averageCn0(
        groupKeys: Set<SignalBandKey>,
    ): Double? {
        val values = entries
            .asSequence()
            .filter { it.key in groupKeys }
            .mapNotNull { it.value.cn0DbHz }
            .toList()
        return values.takeIf { it.isNotEmpty() }?.average()
    }

    private fun SatelliteSignalKey.toBandKey(): SignalBandKey =
        SignalBandKey(
            constellation = constellation,
            svid = svid,
            band = canonicalBandLabel(),
        )

    private fun SatelliteSignalKey.canonicalBandLabel(): String =
        when (constellation) {
            SatelliteConstellation.GALILEO -> when (band.uppercase()) {
                "E1" -> "L1"
                "E5", "E5A" -> "L5"
                "E5B" -> "L7"
                "E6" -> "L6"
                else -> band
            }
            SatelliteConstellation.BEIDOU -> when (signalCode?.uppercase() ?: band.uppercase()) {
                "B1", "B1I", "B1C" -> "L1"
                "B2A" -> "L5"
                "B2", "B2I", "B2B" -> "L7"
                "B3", "B3I" -> "L6"
                else -> band
            }
            else -> when (band.uppercase()) {
                "G1" -> "L1"
                "G2" -> "L2"
                "G3" -> "L3"
                else -> band
            }
        }

    private data class GroupKey(
        val constellation: SatelliteConstellation,
        val band: String,
    )

    private data class SignalBandKey(
        val constellation: SatelliteConstellation,
        val svid: Int,
        val band: String,
    )

    private data class SatelliteId(
        val constellation: SatelliteConstellation,
        val svid: Int,
    )

    companion object {
        const val DEFAULT_FRESH_SOURCE_MILLIS: Long = 2_500L
        const val DEFAULT_STALE_SOURCE_MILLIS: Long = 10_000L
        const val PARTIAL_USAGE_FREQUENCY_INFERRED: String = "PARTIAL_USAGE_FREQUENCY_INFERRED"
    }
}
