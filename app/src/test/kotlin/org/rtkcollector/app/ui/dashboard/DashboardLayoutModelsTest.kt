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
}
