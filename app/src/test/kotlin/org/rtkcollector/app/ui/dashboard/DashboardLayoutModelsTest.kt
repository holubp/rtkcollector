package org.rtkcollector.app.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardLayoutModelsTest {
    @Test
    fun `default layout is compact field dashboard`() {
        assertEquals(DashboardLayoutPreference.COMPACT_FIELD, DashboardLayoutPreference.default)
        assertEquals("compact_field", DashboardLayoutPreference.COMPACT_FIELD.storageId)
        assertEquals("Compact field dashboard", DashboardLayoutPreference.COMPACT_FIELD.displayName)
    }

    @Test
    fun `stored layout ids parse defensively`() {
        assertEquals(DashboardLayoutPreference.RAIL, DashboardLayoutPreference.fromStorageId("rail"))
        assertEquals(DashboardLayoutPreference.COMPACT_FIELD, DashboardLayoutPreference.fromStorageId("unknown"))
        assertEquals(DashboardLayoutPreference.COMPACT_FIELD, DashboardLayoutPreference.fromStorageId(""))
        assertEquals(DashboardLayoutPreference.COMPACT_FIELD, DashboardLayoutPreference.fromStorageId(null))
    }

    @Test
    fun `default distance units are dynamic`() {
        assertEquals(DashboardDistanceUnitPreference.DYNAMIC, DashboardDistanceUnitPreference.default)
        assertEquals("dynamic", DashboardDistanceUnitPreference.DYNAMIC.storageId)
        assertEquals("Dynamic m/cm/mm", DashboardDistanceUnitPreference.DYNAMIC.displayName)
    }

    @Test
    fun `stored distance unit ids parse defensively`() {
        assertEquals(DashboardDistanceUnitPreference.STATIC_METERS, DashboardDistanceUnitPreference.fromStorageId("meters"))
        assertEquals(DashboardDistanceUnitPreference.DYNAMIC, DashboardDistanceUnitPreference.fromStorageId("unknown"))
        assertEquals(DashboardDistanceUnitPreference.DYNAMIC, DashboardDistanceUnitPreference.fromStorageId(""))
        assertEquals(DashboardDistanceUnitPreference.DYNAMIC, DashboardDistanceUnitPreference.fromStorageId(null))
    }

    @Test
    fun `default satellite card theme follows light dashboard`() {
        assertEquals(SatelliteMonitorCardThemePreference.LIGHT, SatelliteMonitorCardThemePreference.default)
        assertEquals("light", SatelliteMonitorCardThemePreference.LIGHT.storageId)
        assertEquals("Light satellite card", SatelliteMonitorCardThemePreference.LIGHT.displayName)
    }

    @Test
    fun `stored satellite card theme ids parse defensively`() {
        assertEquals(SatelliteMonitorCardThemePreference.DARK, SatelliteMonitorCardThemePreference.fromStorageId("dark"))
        assertEquals(SatelliteMonitorCardThemePreference.LIGHT, SatelliteMonitorCardThemePreference.fromStorageId("unknown"))
        assertEquals(SatelliteMonitorCardThemePreference.LIGHT, SatelliteMonitorCardThemePreference.fromStorageId(""))
        assertEquals(SatelliteMonitorCardThemePreference.LIGHT, SatelliteMonitorCardThemePreference.fromStorageId(null))
    }

    @Test
    fun `compact dashboard uses two columns on standard wide widths`() {
        assertEquals(2, compactDashboardCardColumnCount(370, 240))
        assertEquals(2, compactDashboardCardColumnCount(340, 240))
        assertEquals(1, compactDashboardCardColumnCount(339))
        assertEquals(1, compactDashboardCardColumnCount(320))
    }

    @Test
    fun `compact dashboard uses one card column in portrait`() {
        assertEquals(1, compactDashboardCardColumnCount(370, 700))
        assertEquals(1, compactDashboardCardColumnCount(700, 700))
    }

    @Test
    fun `rail dashboard only applies in landscape`() {
        assertEquals(true, shouldUseRailDashboard(DashboardLayoutPreference.RAIL, 900, 500))
        assertEquals(false, shouldUseRailDashboard(DashboardLayoutPreference.RAIL, 700, 1000))
        assertEquals(false, shouldUseRailDashboard(DashboardLayoutPreference.RAIL, 500, 300))
        assertEquals(false, shouldUseRailDashboard(DashboardLayoutPreference.COMPACT_FIELD, 900, 500))
    }

    @Test
    fun `default setup strip excludes settings profile details`() {
        assertEquals(
            listOf("Workflow", "Mountpoint", "Receiver", "Storage"),
            defaultDashboardSetupItems.map { it.label },
        )
    }
}
