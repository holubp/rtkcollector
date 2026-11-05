package org.rtkcollector.app.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.ceil
import kotlin.math.roundToInt

class SatelliteMonitorDashboardModelsTest {
    @Test
    fun `narrow boxes lose borders without losing their colored interior`() {
        assertEquals(2, satelliteMonitorBoxWidthPx(barWidthPx = 39, gapPx = 2))
        assertFalse(satelliteMonitorShowsBoxBorders(39, 2, borderWidthPx = 1, minimumFillPx = 2))
        assertFalse(satelliteMonitorShowsBoxBorders(57, 2, borderWidthPx = 1, minimumFillPx = 2))
        assertTrue(satelliteMonitorShowsBoxBorders(58, 2, borderWidthPx = 1, minimumFillPx = 2))
    }

    @Test
    fun `pixel rounding must not retain a border on the smaller weighted boxes`() {
        assertEquals(3, satelliteMonitorBoxWidthPx(barWidthPx = 57, gapPx = 2))
        assertEquals(4, satelliteMonitorBoxWidthPx(barWidthPx = 58, gapPx = 2))
        assertFalse(satelliteMonitorShowsBoxBorders(114, 5, borderWidthPx = 3, minimumFillPx = 5))
        assertTrue(satelliteMonitorShowsBoxBorders(155, 5, borderWidthPx = 3, minimumFillPx = 5))
    }

    @Test
    fun `frequency columns keep compact borderless boxes and reflow only when necessary`() {
        assertEquals(3, satelliteMonitorFrequencyColumns(324, 5, 66, 2, 2))
        assertEquals(2, satelliteMonitorFrequencyColumns(284, 5, 66, 2, 2))
        assertEquals(1, satelliteMonitorFrequencyColumns(144, 5, 66, 2, 2))
        assertEquals(2, satelliteMonitorFrequencyColumns(321, 5, 66, 2, 2))
        assertEquals(3, satelliteMonitorFrequencyColumns(322, 5, 66, 2, 2))
        assertEquals(2, satelliteMonitorFrequencyColumns(324, 5, 100, 2, 2))
    }

    @Test
    fun `density and large label budgets preserve fill and prefer the largest fitting column count`() {
        for (density in listOf(1f, 1.5f, 2f, 2.75f, 3.5f)) {
            for (fontScale in listOf(1f, 1.3f, 2f)) {
                for (availableDp in listOf(140, 160, 284, 324, 600)) {
                    val width = (availableDp * density).roundToInt()
                    val frequencyGap = (5 * density).roundToInt()
                    val boxGap = (2 * density).roundToInt()
                    val minimumFill = ceil(2 * density).toInt()
                    val border = ceil(density).toInt()
                    val reserved = 2 * (5 * density).roundToInt() +
                        (8 * fontScale * density).roundToInt() +
                        maxOf((40 * density).roundToInt(), (32 * fontScale * density).roundToInt()) +
                        2 * (4 * density).roundToInt()
                    fun boxWidth(columns: Int): Int =
                        (((width - (columns - 1) * frequencyGap) / columns - reserved -
                            9 * boxGap).coerceAtLeast(0)) / 10
                    val columns = satelliteMonitorFrequencyColumns(width, frequencyGap, reserved, boxGap, minimumFill)
                    assertTrue(columns in 1..3)
                    assertTrue(boxWidth(columns) >= minimumFill,
                        "width=$availableDp density=$density fontScale=$fontScale columns=$columns")
                    if (columns < 3) assertTrue(boxWidth(columns + 1) < minimumFill)
                    val barWidth = (width - (columns - 1) * frequencyGap) / columns - reserved
                    val bordered = satelliteMonitorShowsBoxBorders(barWidth, boxGap, border, minimumFill)
                    assertEquals(boxWidth(columns) >= minimumFill + 2 * border, bordered)
                }
            }
        }
    }

    @Test
    fun `zero available width cannot create a colored border or zero-column row`() {
        assertEquals(0, satelliteMonitorBoxWidthPx(0, 2))
        assertFalse(satelliteMonitorShowsBoxBorders(0, 2, 1, 2))
        assertEquals(1, satelliteMonitorFrequencyColumns(0, 5, 66, 2, 2))
    }

    @Test
    fun `used visible counts format as used over visible`() {
        val count = SatelliteMonitorSignalCount(used = 8, visible = 11)

        assertEquals("8/11", count.displayValue)
    }

    @Test
    fun `boxed bar segments show used as saturated prefix inside visible total`() {
        val count = SatelliteMonitorSignalCount(used = 3, visible = 5)

        assertEquals(
            listOf(
                SatelliteMonitorBarSegment.USED,
                SatelliteMonitorBarSegment.USED,
                SatelliteMonitorBarSegment.USED,
                SatelliteMonitorBarSegment.VISIBLE,
                SatelliteMonitorBarSegment.VISIBLE,
                SatelliteMonitorBarSegment.EMPTY,
            ),
            count.boxedSegments(totalSegments = 6),
        )
    }

    @Test
    fun `boxed bar segments clamp impossible used count to visible total`() {
        val count = SatelliteMonitorSignalCount(used = 7, visible = 4)

        assertEquals(
            listOf(
                SatelliteMonitorBarSegment.USED,
                SatelliteMonitorBarSegment.USED,
                SatelliteMonitorBarSegment.USED,
                SatelliteMonitorBarSegment.USED,
                SatelliteMonitorBarSegment.EMPTY,
                SatelliteMonitorBarSegment.EMPTY,
            ),
            count.boxedSegments(totalSegments = 6),
        )
        assertEquals("4/4", count.displayValue)
    }

    @Test
    fun `dashboard state is unavailable until explicit monitor data is supplied`() {
        val state = SatelliteMonitorDashboardState.unavailable()

        assertFalse(state.hasFrequencyGroups)
        assertEquals("In-device RTK", state.engineLabel)
        assertEquals("Satellite monitor unavailable", state.message)
    }

    @Test
    fun `preview dashboard data groups constellation first and frequency second`() {
        val state = SatelliteMonitorDashboardState.preview()

        assertTrue(state.hasFrequencyGroups)
        assertEquals(listOf("GPS", "Galileo"), state.constellations.map { it.label })
        assertEquals(listOf("L1", "L2", "L5"), state.constellations.first().frequencies.map { it.bandLabel })
    }

    @Test
    fun `nonblank operational message remains available when frequency groups are populated`() {
        val state = SatelliteMonitorDashboardState.preview().copy(
            message = "Per-frequency used counts inferred",
        )

        assertTrue(state.hasFrequencyGroups)
        assertEquals("Per-frequency used counts inferred", state.statusMessage)
    }

    @Test
    fun `blank operational message is not exposed for display`() {
        val state = SatelliteMonitorDashboardState.preview().copy(message = "  ")

        assertEquals(null, state.statusMessage)
    }

    @Test
    fun `frequency chunks keep compact card rows bounded`() {
        val group = SatelliteMonitorConstellationGroup(
            label = "GPS",
            frequencies = listOf(
                SatelliteMonitorFrequencyRow("L1", SatelliteMonitorSignalCount(1, 2), SatelliteMonitorSignalCount(1, 2)),
                SatelliteMonitorFrequencyRow("L2", SatelliteMonitorSignalCount(1, 2), SatelliteMonitorSignalCount(1, 2)),
                SatelliteMonitorFrequencyRow("L5", SatelliteMonitorSignalCount(1, 2), SatelliteMonitorSignalCount(1, 2)),
                SatelliteMonitorFrequencyRow("L6", SatelliteMonitorSignalCount(1, 2), SatelliteMonitorSignalCount(1, 2)),
            ),
        )

        assertEquals(listOf(3, 1), group.frequencyRows(maxColumns = 3).map { it.size })
    }

    @Test
    fun `compact frequency rows are sorted by band label before chunking`() {
        val group = SatelliteMonitorConstellationGroup(
            label = "GPS",
            frequencies = listOf(
                SatelliteMonitorFrequencyRow("L5", SatelliteMonitorSignalCount(1, 2), SatelliteMonitorSignalCount(1, 2)),
                SatelliteMonitorFrequencyRow("L1", SatelliteMonitorSignalCount(1, 2), SatelliteMonitorSignalCount(1, 2)),
                SatelliteMonitorFrequencyRow("L2", SatelliteMonitorSignalCount(1, 2), SatelliteMonitorSignalCount(1, 2)),
            ),
        )

        assertEquals(listOf("L1", "L2", "L5"), group.frequencyRows(maxColumns = 10).flatten().map { it.bandLabel })
    }

    @Test
    fun `detailed frequency primary grouping flattens constellations by band`() {
        val groups = SatelliteMonitorDashboardState.preview().detailGroups(SatelliteMonitorDetailGroupingMode.FREQUENCY)

        assertEquals(listOf("E1", "E5", "L1", "L2", "L5"), groups.map { it.primaryLabel })
        assertEquals("Galileo", groups.first().sections.single().secondaryLabel)
        assertEquals("GPS", groups.last().sections.single().secondaryLabel)
    }

    @Test
    fun `detailed constellation primary grouping keeps frequencies nested`() {
        val groups = SatelliteMonitorDashboardState.preview().detailGroups(SatelliteMonitorDetailGroupingMode.CONSTELLATION)

        assertEquals(listOf("GPS", "Galileo"), groups.map { it.primaryLabel })
        assertEquals(listOf("L1", "L2", "L5"), groups.first().sections.map { it.secondaryLabel })
    }

    @Test
    fun `detail frequency grouping keeps per-constellation counts when sorted order changes source payload`() {
        val state = SatelliteMonitorDashboardState(
            engineLabel = "RTKLIB",
            sources = SatelliteMonitorSourceStatuses(),
            constellations = listOf(
                SatelliteMonitorConstellationGroup(
                    label = "Galileo",
                    frequencies = listOf(
                        SatelliteMonitorFrequencyRow("E5", SatelliteMonitorSignalCount(2, 2), SatelliteMonitorSignalCount(1, 1)),
                        SatelliteMonitorFrequencyRow("E1", SatelliteMonitorSignalCount(3, 4), SatelliteMonitorSignalCount(2, 3)),
                    ),
                ),
                SatelliteMonitorConstellationGroup(
                    label = "GPS",
                    frequencies = listOf(
                        SatelliteMonitorFrequencyRow("L5", SatelliteMonitorSignalCount(0, 2), SatelliteMonitorSignalCount(0, 2)),
                        SatelliteMonitorFrequencyRow("L1", SatelliteMonitorSignalCount(1, 2), SatelliteMonitorSignalCount(1, 2)),
                    ),
                ),
            ),
            message = "mock",
        )

        val groups = state.detailGroups(SatelliteMonitorDetailGroupingMode.CONSTELLATION)

        assertEquals(listOf("Galileo", "GPS"), groups.map { it.primaryLabel })
        assertEquals(listOf("E1", "E5"), groups.first().sections.map { it.secondaryLabel })
        assertEquals("3/4", groups.first().sections.first().frequencies.first().rover.displayValue)
    }
}
