package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardOwnershipAttentionTest {
    @Test
    fun `graph policy and migration problems expand the setup without transport warnings`() {
        val status = DashboardStatus(configurationProblems = listOf("RTKLIB profile must be selected."))
        assertTrue(effectiveDashboardSetupExpanded(false, status))
        assertEquals("RTKLIB profile must be selected.", status.setupWarningReason(DashboardSetupItem.SETTINGS))
    }

    @Test
    fun `idle refresh replaces configuration problems and preserves real operational error`() {
        val previous = DashboardState.planned("Plain rover", mountpoint = "n/a", initProfile = "Receiver",
            storage = "Files", lastError = "Storage full")
        val next = previous.withPlannedConfiguration(DashboardState.planned("Plain rover", mountpoint = "n/a",
            initProfile = "Receiver", storage = "Files", configurationProblems = listOf("Missing storage profile")))
        assertEquals("Storage full", next.lastError)
        assertEquals(listOf("Missing storage profile"), next.status.configurationProblems)
    }

    @Test
    fun `library refresh cannot replace running configuration or locks`() {
        val running = DashboardState.planned("Plain rover", mountpoint = "n/a", initProfile = "Accepted",
            storage = "Files", fixedSetupItems = setOf(DashboardSetupItem.STORAGE)).copy(isRecording = true)
        val pending = DashboardState.planned("Fixed base", mountpoint = "Other", initProfile = "Edited",
            storage = "Other files", configurationProblems = listOf("Mismatch"))
        assertEquals(running, running.withPlannedConfiguration(pending))
    }
}
