package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.rtkcollector.app.profile.NtripMountpointOverride
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.SettingsSetOverrides
import org.junit.jupiter.api.Test

class ProfileListModelsTest {
    @Test
    fun `protected profile row is copy only`() {
        val row = ProfileListRow(
            id = "built-in",
            name = "Built-in",
            isProtected = true,
            hasLocalOverrides = false,
        )

        assertFalse(row.canEdit)
        assertTrue(row.canCopy)
        assertFalse(row.canRename)
        assertFalse(row.canDelete)
        assertFalse(row.isSelected)
        assertEquals("Built-in", row.displayName)
    }

    @Test
    fun `settings set local override label is visible`() {
        val state = SettingsSetListState.from(
            settingsSets = listOf(
                RecordingSettingsSet.builtInRoverNtrip().copy(
                    overrides = SettingsSetOverrides(
                        ntripMountpoint = NtripMountpointOverride(mountpoint = "TUBO00CZE0"),
                    ),
                ),
            ),
            selectedId = "um980-rover-ntrip",
        )

        assertEquals("UM980 rover + NTRIP + local changes", state.rows.single().displayName)
        assertTrue(state.rows.single().isSelected)
        assertTrue(state.rows.single().canDelete)
    }

    @Test
    fun `editable profile field can expose fixed options`() {
        val field = EditableProfileField(
            key = "serialBaud",
            label = "Host serial baud",
            value = "230400",
            options = listOf("115200", "230400", "921600"),
        )

        assertEquals(listOf("115200", "230400", "921600"), field.options)
    }
}
