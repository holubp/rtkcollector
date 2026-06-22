package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionBrowserModelsTest {
    @Test
    fun `groups sessions by role and sorts latest first`() {
        val state = sessionBrowserStateOf(
            listOf(
                entry("old", SessionEntryKind.RECORDING, 10),
                entry("archive", SessionEntryKind.ARCHIVE, 20),
                entry("current", SessionEntryKind.CURRENT_ACTIVE, 30),
                entry("new", SessionEntryKind.RECORDING, 40),
            ),
        )

        assertEquals(listOf(SessionBrowserGroupKind.CURRENT, SessionBrowserGroupKind.RECORDINGS, SessionBrowserGroupKind.ARCHIVES), state.groups.map { it.kind })
        assertEquals(listOf("new", "old"), state.groups.first { it.kind == SessionBrowserGroupKind.RECORDINGS }.entries.map { it.id })
        assertFalse(state.allEntries.first { it.id == "current" }.canDelete)
    }

    @Test
    fun `selection helpers only select eligible entries`() {
        val state = sessionBrowserStateOf(
            listOf(
                entry("active", SessionEntryKind.CURRENT_ACTIVE, 30),
                entry("stopped", SessionEntryKind.CURRENT_STOPPED, 20),
                entry("recording", SessionEntryKind.RECORDING, 10),
                entry("archive", SessionEntryKind.ARCHIVE, 5),
                entry("saf", SessionEntryKind.CURRENT_STOPPED, 1).copy(filesystemBacked = false),
            ),
        )

        assertEquals(setOf("stopped"), state.selectCurrent().selectedIds)
        assertEquals(setOf("recording"), state.selectRecordings().selectedIds)
        assertEquals(setOf("archive"), state.selectArchives().selectedIds)
        assertEquals(setOf("stopped", "recording", "archive"), state.selectAll().selectedIds)
        assertTrue(state.toggle("recording").selectedIds.contains("recording"))
        assertFalse(state.toggle("saf").selectedIds.contains("saf"))
    }

    @Test
    fun `SAF entries can expose supported actions without pretending to be filesystem backed`() {
        val saf = entry("saf", SessionEntryKind.RECORDING, 10).copy(
            filesystemBacked = false,
            capabilities = SessionActionCapabilities(
                shareZip = true,
                shareNmea = true,
                reexportNmea = true,
                regenerateRtklib = true,
                archive = false,
                restore = false,
                delete = true,
            ),
        )

        assertTrue(saf.canShareZip)
        assertTrue(saf.canShareNmea)
        assertTrue(saf.canReexportNmea)
        assertTrue(saf.canRegenerateRtklib)
        assertFalse(saf.canArchive)
        assertFalse(saf.canRestore)
        assertTrue(saf.canDelete)
    }

    @Test
    fun `filesystem recordings can regenerate rtklib postprocessed outputs`() {
        val entry = entry("recording", SessionEntryKind.RECORDING, 10)

        assertTrue(entry.canRegenerateRtklib)
    }

    @Test
    fun `select all button label changes when all selectable entries are selected`() {
        val state = sessionBrowserStateOf(
            listOf(
                entry("active", SessionEntryKind.CURRENT_ACTIVE, 30),
                entry("stopped", SessionEntryKind.CURRENT_STOPPED, 20),
                entry("recording", SessionEntryKind.RECORDING, 10),
            ),
        )

        assertEquals("Select all", state.selectAllButtonLabel)

        val selected = state.selectAll()

        assertEquals(setOf("stopped", "recording"), selected.selectedIds)
        assertEquals("Unselect all", selected.selectAllButtonLabel)
    }

    @Test
    fun `toggle select all clears when every selectable entry is selected`() {
        val state = sessionBrowserStateOf(
            listOf(
                entry("stopped", SessionEntryKind.CURRENT_STOPPED, 20),
                entry("recording", SessionEntryKind.RECORDING, 10),
                entry("archive", SessionEntryKind.ARCHIVE, 5),
            ),
        ).selectAll()

        assertEquals("Unselect all", state.selectAllButtonLabel)

        val cleared = state.toggleSelectAll()

        assertEquals(emptySet<String>(), cleared.selectedIds)
        assertEquals("Select all", cleared.selectAllButtonLabel)
    }

    @Test
    fun `toggle select all selects when selection is partial`() {
        val state = sessionBrowserStateOf(
            listOf(
                entry("stopped", SessionEntryKind.CURRENT_STOPPED, 20),
                entry("recording", SessionEntryKind.RECORDING, 10),
                entry("archive", SessionEntryKind.ARCHIVE, 5),
            ),
            selectedIds = setOf("stopped"),
        )

        assertEquals("Select all", state.selectAllButtonLabel)

        val selected = state.toggleSelectAll()

        assertEquals(setOf("stopped", "recording", "archive"), selected.selectedIds)
        assertEquals("Unselect all", selected.selectAllButtonLabel)
    }

    @Test
    fun `session path copy text returns location outside selection mode`() {
        val entry = entry("session-1", SessionEntryKind.RECORDING, 10)
            .copy(location = "/storage/emulated/0/Android/data/org.rtkcollector.app/files/sessions/session-1")

        assertEquals(
            "/storage/emulated/0/Android/data/org.rtkcollector.app/files/sessions/session-1",
            sessionPathCopyText(entry, selectionMode = false),
        )
    }

    @Test
    fun `session path copy text is disabled in selection mode`() {
        val entry = entry("session-1", SessionEntryKind.RECORDING, 10)
            .copy(location = "/storage/emulated/0/Android/data/org.rtkcollector.app/files/sessions/session-1")

        assertEquals(null, sessionPathCopyText(entry, selectionMode = true))
    }

    private fun entry(id: String, kind: SessionEntryKind, modified: Long): SessionBrowserEntry =
        SessionBrowserEntry(
            id = id,
            title = id,
            subtitle = id,
            location = id,
            kind = kind,
            modifiedEpochMillis = modified,
        )
}
