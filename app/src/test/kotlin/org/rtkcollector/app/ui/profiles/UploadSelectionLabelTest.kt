package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UploadSelectionLabelTest {
    @Test
    fun `inapplicable upload is not needed regardless of retained selection`() {
        assertEquals(
            "Not needed",
            uploadSelectionLabel(applicable = false, enabled = false, profileName = "Dormant profile"),
        )
    }

    @Test
    fun `unknown enabled state asks user to select`() {
        assertEquals(
            "Select",
            uploadSelectionLabel(applicable = true, enabled = null, profileName = null),
        )
    }

    @Test
    fun `explicitly disabled upload ignores retained profile`() {
        assertEquals(
            "Off",
            uploadSelectionLabel(applicable = true, enabled = false, profileName = "Dormant profile"),
        )
    }

    @Test
    fun `enabled upload without a usable profile name is missing`() {
        assertEquals(
            "Missing profile",
            uploadSelectionLabel(applicable = true, enabled = true, profileName = null),
        )
        assertEquals(
            "Missing profile",
            uploadSelectionLabel(applicable = true, enabled = true, profileName = "  "),
        )
    }

    @Test
    fun `enabled upload uses the selected profile name`() {
        assertEquals(
            "Field caster",
            uploadSelectionLabel(applicable = true, enabled = true, profileName = "Field caster"),
        )
    }
}
