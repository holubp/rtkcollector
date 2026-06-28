package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.NtripCasterUploadProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet

class DashboardUploadSelectorTest {
    @Test
    fun `upload selector rows start with off row`() {
        val selected = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
            baseCasterUploadEnabled = true,
        )

        val rows = dashboardUploadSelectorRows(
            profiles = listOf(NtripCasterUploadProfile(id = "upload", name = "Upload")),
            selectedSettingsSet = selected,
        )

        assertEquals(UploadSelectorOffProfileId, rows.first().id)
        assertEquals("Off", rows.first().name)
        assertTrue(rows.any { it.id == "upload" && it.isSelected })
    }

    @Test
    fun `upload selector shows off when profile has enabled by default but selector is explicitly off`() {
        val selected = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
            baseCasterUploadEnabled = false,
        )

        val rows = dashboardUploadSelectorRows(
            profiles = listOf(
                NtripCasterUploadProfile(
                    id = "upload",
                    name = "Upload",
                    enabledByDefault = true,
                ),
            ),
            selectedSettingsSet = selected,
        )

        assertEquals(UploadSelectorOffProfileId, rows.first { it.id == UploadSelectorOffProfileId }.id)
        assertTrue(rows.first { it.id == UploadSelectorOffProfileId }.isSelected)
        assertEquals("Enabled by default", rows.first { it.id == "upload" }.summary)
        assertEquals(false, rows.first { it.id == "upload" }.isSelected)
    }

    @Test
    fun `upload selector selects enabled profile`() {
        val selected = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
            baseCasterUploadEnabled = true,
        )

        val rows = dashboardUploadSelectorRows(
            profiles = listOf(NtripCasterUploadProfile(id = "upload", name = "Upload")),
            selectedSettingsSet = selected,
        )

        assertTrue(rows.first { it.id == "upload" }.isSelected)
    }

    @Test
    fun `upload selector selects off when upload disabled`() {
        val rows = dashboardUploadSelectorRows(
            profiles = listOf(NtripCasterUploadProfile(id = "upload", name = "Upload")),
            selectedSettingsSet = RecordingSettingsSet.builtInRoverNtrip(),
        )

        assertTrue(rows.first { it.id == UploadSelectorOffProfileId }.isSelected)
    }
}
