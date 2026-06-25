package org.rtkcollector.app.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SatelliteMonitorDashboardModelsTest {
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
