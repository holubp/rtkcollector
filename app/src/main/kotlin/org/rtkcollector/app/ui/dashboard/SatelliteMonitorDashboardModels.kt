package org.rtkcollector.app.ui.dashboard

internal const val SatelliteMonitorBoxedBarSegments = 10

internal fun satelliteMonitorBoxWidthPx(barWidthPx: Int, gapPx: Int): Int {
    require(gapPx >= 0)
    // Weighted boxes may differ by one pixel; protect the smallest interior.
    return (barWidthPx - (SatelliteMonitorBoxedBarSegments - 1) * gapPx)
        .coerceAtLeast(0) / SatelliteMonitorBoxedBarSegments
}

internal fun satelliteMonitorShowsBoxBorders(
    barWidthPx: Int,
    gapPx: Int,
    borderWidthPx: Int,
    minimumFillPx: Int,
): Boolean {
    require(borderWidthPx >= 0 && minimumFillPx > 0)
    return satelliteMonitorBoxWidthPx(barWidthPx, gapPx) >= 2 * borderWidthPx + minimumFillPx
}

internal fun satelliteMonitorFrequencyColumns(
    availableWidthPx: Int,
    frequencyGapPx: Int,
    reservedWidthPx: Int,
    boxGapPx: Int,
    minimumFillPx: Int,
): Int {
    require(frequencyGapPx >= 0 && reservedWidthPx >= 0 && minimumFillPx > 0)
    return (3 downTo 1).firstOrNull { columns ->
        val cellWidth = (availableWidthPx - (columns - 1) * frequencyGapPx).coerceAtLeast(0) / columns
        satelliteMonitorBoxWidthPx((cellWidth - reservedWidthPx).coerceAtLeast(0), boxGapPx) >= minimumFillPx
    } ?: 1
}

enum class SatelliteMonitorSourceFreshness(
    val displayName: String,
) {
    FRESH("Fresh"),
    STALE("Stale"),
    UNAVAILABLE("Unavailable"),
}

data class SatelliteMonitorSourceStatus(
    val label: String,
    val freshness: SatelliteMonitorSourceFreshness,
)

data class SatelliteMonitorSourceStatuses(
    val rover: SatelliteMonitorSourceStatus = SatelliteMonitorSourceStatus("R", SatelliteMonitorSourceFreshness.UNAVAILABLE),
    val base: SatelliteMonitorSourceStatus = SatelliteMonitorSourceStatus("B", SatelliteMonitorSourceFreshness.UNAVAILABLE),
    val solution: SatelliteMonitorSourceStatus = SatelliteMonitorSourceStatus("S", SatelliteMonitorSourceFreshness.UNAVAILABLE),
)

enum class SatelliteMonitorBarSegment {
    USED,
    VISIBLE,
    EMPTY,
}

enum class SatelliteMonitorDetailGroupingMode(
    val storageId: String,
    val displayName: String,
) {
    FREQUENCY("frequency", "Frequency"),
    CONSTELLATION("constellation", "Constellation"),
    ;
}

class SatelliteMonitorSignalCount(
    used: Int,
    visible: Int,
) {
    val visible: Int = visible.coerceAtLeast(0)
    val used: Int = used.coerceAtLeast(0).coerceAtMost(this.visible)

    val displayValue: String
        get() = "$used/$visible"

    fun boxedSegments(totalSegments: Int): List<SatelliteMonitorBarSegment> {
        val segmentCount = totalSegments.coerceAtLeast(0)
        val usedSegments = used.coerceAtMost(segmentCount)
        val visibleSegments = visible.coerceAtMost(segmentCount)
        return List(segmentCount) { index ->
            when {
                index < usedSegments -> SatelliteMonitorBarSegment.USED
                index < visibleSegments -> SatelliteMonitorBarSegment.VISIBLE
                else -> SatelliteMonitorBarSegment.EMPTY
            }
        }
    }
}

data class SatelliteMonitorFrequencyRow(
    val bandLabel: String,
    val rover: SatelliteMonitorSignalCount,
    val base: SatelliteMonitorSignalCount,
)

data class SatelliteMonitorConstellationGroup(
    val label: String,
    val frequencies: List<SatelliteMonitorFrequencyRow>,
) {
    fun frequencyRows(maxColumns: Int): List<List<SatelliteMonitorFrequencyRow>> =
        frequencies
            .sortedBy { it.bandLabel }
            .chunked(maxColumns.coerceAtLeast(1))
}

data class SatelliteMonitorDetailGroup(
    val primaryLabel: String,
    val sections: List<SatelliteMonitorDetailSection>,
)

data class SatelliteMonitorDetailSection(
    val secondaryLabel: String,
    val frequencies: List<SatelliteMonitorFrequencyRow>,
)

data class SatelliteMonitorDashboardState(
    val engineLabel: String,
    val sources: SatelliteMonitorSourceStatuses,
    val constellations: List<SatelliteMonitorConstellationGroup>,
    val message: String,
) {
    val hasFrequencyGroups: Boolean
        get() = constellations.any { it.frequencies.isNotEmpty() }

    val statusMessage: String?
        get() = message.takeIf { it.isNotBlank() }

    companion object {
        fun unavailable(
            engineLabel: String = "In-device RTK",
            sources: SatelliteMonitorSourceStatuses = SatelliteMonitorSourceStatuses(),
            message: String = "Satellite monitor unavailable",
        ): SatelliteMonitorDashboardState =
            SatelliteMonitorDashboardState(
                engineLabel = engineLabel,
                sources = sources,
                constellations = emptyList(),
                message = message,
            )

        fun preview(): SatelliteMonitorDashboardState =
            SatelliteMonitorDashboardState(
                engineLabel = "In-device RTK",
                sources = SatelliteMonitorSourceStatuses(
                    rover = SatelliteMonitorSourceStatus("R", SatelliteMonitorSourceFreshness.FRESH),
                    base = SatelliteMonitorSourceStatus("B", SatelliteMonitorSourceFreshness.FRESH),
                    solution = SatelliteMonitorSourceStatus("S", SatelliteMonitorSourceFreshness.STALE),
                ),
                constellations = listOf(
                    SatelliteMonitorConstellationGroup(
                        label = "GPS",
                        frequencies = listOf(
                            SatelliteMonitorFrequencyRow(
                                bandLabel = "L1",
                                rover = SatelliteMonitorSignalCount(used = 8, visible = 11),
                                base = SatelliteMonitorSignalCount(used = 9, visible = 12),
                            ),
                            SatelliteMonitorFrequencyRow(
                                bandLabel = "L2",
                                rover = SatelliteMonitorSignalCount(used = 6, visible = 9),
                                base = SatelliteMonitorSignalCount(used = 7, visible = 10),
                            ),
                            SatelliteMonitorFrequencyRow(
                                bandLabel = "L5",
                                rover = SatelliteMonitorSignalCount(used = 2, visible = 4),
                                base = SatelliteMonitorSignalCount(used = 3, visible = 5),
                            ),
                        ),
                    ),
                    SatelliteMonitorConstellationGroup(
                        label = "Galileo",
                        frequencies = listOf(
                            SatelliteMonitorFrequencyRow(
                                bandLabel = "E1",
                                rover = SatelliteMonitorSignalCount(used = 7, visible = 9),
                                base = SatelliteMonitorSignalCount(used = 8, visible = 10),
                            ),
                            SatelliteMonitorFrequencyRow(
                                bandLabel = "E5",
                                rover = SatelliteMonitorSignalCount(used = 4, visible = 6),
                                base = SatelliteMonitorSignalCount(used = 4, visible = 7),
                            ),
                        ),
                    ),
                ),
                message = "Live satellite monitor",
            )
    }
}

fun SatelliteMonitorDashboardState.detailGroups(
    groupingMode: SatelliteMonitorDetailGroupingMode,
): List<SatelliteMonitorDetailGroup> =
    when (groupingMode) {
        SatelliteMonitorDetailGroupingMode.CONSTELLATION -> constellations.map { constellation ->
            SatelliteMonitorDetailGroup(
                primaryLabel = constellation.label,
                sections = constellation.frequencies
                    .sortedBy { it.bandLabel }
                    .map { frequency ->
                        SatelliteMonitorDetailSection(
                            secondaryLabel = frequency.bandLabel,
                            frequencies = listOf(frequency),
                        )
                    },
            )
        }
        SatelliteMonitorDetailGroupingMode.FREQUENCY -> constellations
            .flatMap { constellation ->
                constellation.frequencies
                    .sortedBy { it.bandLabel }
                    .map { frequency ->
                        constellation.label to frequency
                    }
            }
            .groupBy { (_, frequency) -> frequency.bandLabel }
            .toSortedMap()
            .map { (bandLabel, rows) ->
                SatelliteMonitorDetailGroup(
                    primaryLabel = bandLabel,
                    sections = rows
                        .groupBy { (constellation, _) -> constellation }
                        .toSortedMap()
                        .map { (constellation, frequencyRows) ->
                            SatelliteMonitorDetailSection(
                                secondaryLabel = constellation,
                                frequencies = frequencyRows.map { (_, frequency) -> frequency },
                            )
                        },
                )
            }
    }
