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
