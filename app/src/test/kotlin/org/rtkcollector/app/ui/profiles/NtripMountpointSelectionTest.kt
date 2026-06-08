package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NtripMountpointSelectionTest {
    @Test
    fun `fetching mountpoints does not overwrite typed mountpoint`() {
        val state = NtripMountpointEditorState(mountpointText = "USER_TYPED")

        val updated = state.withFetchedMountpoints(listOf("FIRST", "SECOND"))

        assertEquals("USER_TYPED", updated.mountpointText)
        assertEquals(listOf("FIRST", "SECOND"), updated.availableMountpoints)
    }

    @Test
    fun `explicit selection updates mountpoint text`() {
        val state = NtripMountpointEditorState(
            mountpointText = "USER_TYPED",
            availableMountpoints = listOf("FIRST", "SECOND"),
        )

        val updated = state.selectMountpoint("SECOND")

        assertEquals("SECOND", updated.mountpointText)
    }

    @Test
    fun `fetched mountpoints are deduplicated without selecting first result`() {
        val state = NtripMountpointEditorState(mountpointText = "TUBO00CZE0")

        val updated = state.withFetchedMountpoints(listOf("FIRST", "", "FIRST", "SECOND"))

        assertEquals("TUBO00CZE0", updated.mountpointText)
        assertEquals(listOf("FIRST", "SECOND"), updated.availableMountpoints)
    }

    @Test
    fun `selection must be from fetched list when list is available`() {
        val state = NtripMountpointEditorState(
            mountpointText = "USER_TYPED",
            availableMountpoints = listOf("FIRST"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            state.selectMountpoint("SECOND")
        }
    }
}
