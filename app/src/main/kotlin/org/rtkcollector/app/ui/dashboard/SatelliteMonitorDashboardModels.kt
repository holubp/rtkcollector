package org.rtkcollector.app.ui.dashboard

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
