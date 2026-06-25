package org.rtkcollector.app.recording

import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteFrequencySummary
import org.rtkcollector.core.quality.SatelliteMonitorAggregator
import org.rtkcollector.core.quality.SatelliteMonitorEngine
import org.rtkcollector.core.quality.SatelliteMonitorFreshness
import org.rtkcollector.core.quality.SatelliteMonitorInputBatch
import org.rtkcollector.core.quality.SatelliteMonitorSource
import org.rtkcollector.core.quality.SatelliteMonitorSourceState

internal class SatelliteMonitorController(
    private val aggregator: SatelliteMonitorAggregator = SatelliteMonitorAggregator(),
    private val maxRetainedBatches: Int = 128,
) {
    private val batches = ArrayDeque<SatelliteMonitorInputBatch>()

    init {
        require(maxRetainedBatches > 0) { "Satellite monitor retained batch count must be positive." }
    }

    @Synchronized
    fun offer(batch: SatelliteMonitorInputBatch) {
        batches.addLast(batch)
        while (batches.size > maxRetainedBatches) {
            batches.removeFirst()
        }
    }

    @Synchronized
    fun compactPayload(
        engine: SatelliteMonitorEngine,
        nowEpochMillis: Long,
    ): SatelliteMonitorBroadcastPayload {
        val snapshot = aggregator.summarize(
            engine = engine,
            nowEpochMillis = nowEpochMillis,
            batches = batches,
        )
        return SatelliteMonitorBroadcastPayload(
            engineLabel = engine.displayLabel(),
            sources = snapshot.sourceFreshness.toSourceWire(),
            groups = snapshot.summaries.toGroupsWire(),
            message = snapshot.message.toUserMessage() ?: "Live satellite monitor",
        )
    }

    @Synchronized
    fun clear() {
        batches.clear()
    }
}

internal data class SatelliteMonitorBroadcastPayload(
    val engineLabel: String,
    val sources: String,
    val groups: String,
    val message: String,
)

internal fun SatelliteMonitorEngine.displayLabel(): String =
    when (this) {
        SatelliteMonitorEngine.IN_DEVICE_RTK -> "In-device RTK"
        SatelliteMonitorEngine.RTKLIB -> "RTKLIB"
    }

private fun String?.toUserMessage(): String? =
    when (this) {
        SatelliteMonitorAggregator.PARTIAL_USAGE_FREQUENCY_INFERRED -> "Per-frequency used counts inferred"
        null -> null
        else -> this
    }

private fun Map<SatelliteMonitorSource, SatelliteMonitorFreshness>.toSourceWire(): String =
    listOf(
        "R" to SatelliteMonitorSource.ROVER,
        "B" to SatelliteMonitorSource.BASE,
        "S" to SatelliteMonitorSource.SOLUTION,
    ).joinToString(separator = ";") { (label, source) ->
        "$label:${get(source)?.state?.wireName() ?: SatelliteMonitorSourceState.UNAVAILABLE.wireName()}"
    }

private fun SatelliteMonitorSourceState.wireName(): String =
    when (this) {
        SatelliteMonitorSourceState.FRESH -> "FRESH"
        SatelliteMonitorSourceState.STALE -> "STALE"
        SatelliteMonitorSourceState.UNAVAILABLE -> "UNAVAILABLE"
    }

private fun List<SatelliteFrequencySummary>.toGroupsWire(): String =
    joinToString(separator = ";") { summary ->
        listOf(
            summary.constellation.displayLabel(),
            summary.band,
            summary.roverUsed.toString(),
            summary.roverVisible.toString(),
            summary.baseUsed.toString(),
            summary.baseVisible.toString(),
        ).joinToString(separator = "|")
    }

private fun SatelliteConstellation.displayLabel(): String =
    when (this) {
        SatelliteConstellation.GPS -> "GPS"
        SatelliteConstellation.GLONASS -> "GLONASS"
        SatelliteConstellation.GALILEO -> "Galileo"
        SatelliteConstellation.BEIDOU -> "BeiDou"
        SatelliteConstellation.QZSS -> "QZSS"
        SatelliteConstellation.SBAS -> "SBAS"
        SatelliteConstellation.UNKNOWN -> "Unknown"
    }
