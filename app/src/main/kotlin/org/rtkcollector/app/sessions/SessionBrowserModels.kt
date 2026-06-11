package org.rtkcollector.app.sessions

enum class SessionEntryKind {
    CURRENT_ACTIVE,
    CURRENT_STOPPED,
    RECORDING,
    ARCHIVE,
}

enum class SessionBrowserGroupKind {
    CURRENT,
    RECORDINGS,
    ARCHIVES,
}

data class SessionBrowserEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val location: String,
    val kind: SessionEntryKind,
    val modifiedEpochMillis: Long,
    val sizeBytes: Long? = null,
    val filesystemBacked: Boolean = true,
) {
    val isActive: Boolean
        get() = kind == SessionEntryKind.CURRENT_ACTIVE

    val isArchive: Boolean
        get() = kind == SessionEntryKind.ARCHIVE

    val canShareZip: Boolean
        get() = filesystemBacked && !isActive && (kind == SessionEntryKind.CURRENT_STOPPED || kind == SessionEntryKind.RECORDING)

    val canArchive: Boolean
        get() = filesystemBacked && !isActive && (kind == SessionEntryKind.CURRENT_STOPPED || kind == SessionEntryKind.RECORDING)

    val canRestore: Boolean
        get() = filesystemBacked && kind == SessionEntryKind.ARCHIVE

    val canDelete: Boolean
        get() = filesystemBacked && !isActive
}

data class SessionBrowserGroup(
    val kind: SessionBrowserGroupKind,
    val title: String,
    val entries: List<SessionBrowserEntry>,
)

data class SessionBrowserState(
    val groups: List<SessionBrowserGroup> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
) {
    val allEntries: List<SessionBrowserEntry>
        get() = groups.flatMap { it.entries }

    val selectedEntries: List<SessionBrowserEntry>
        get() = allEntries.filter { it.id in selectedIds }

    fun toggle(id: String): SessionBrowserState =
        if (id in selectedIds) {
            copy(selectedIds = selectedIds - id)
        } else {
            copy(selectedIds = selectedIds + id)
        }.dropUnavailableSelections()

    fun clearSelection(): SessionBrowserState =
        copy(selectedIds = emptySet())

    fun selectCurrent(): SessionBrowserState =
        selectGroup(SessionBrowserGroupKind.CURRENT)

    fun selectRecordings(): SessionBrowserState =
        selectGroup(SessionBrowserGroupKind.RECORDINGS)

    fun selectArchives(): SessionBrowserState =
        selectGroup(SessionBrowserGroupKind.ARCHIVES)

    fun selectAll(): SessionBrowserState =
        copy(selectedIds = selectableEntries().map { it.id }.toSet())

    private fun selectGroup(kind: SessionBrowserGroupKind): SessionBrowserState =
        copy(
            selectedIds = groups
                .filter { it.kind == kind }
                .flatMap { group -> group.entries.filter(SessionBrowserEntry::canDelete) }
                .map(SessionBrowserEntry::id)
                .toSet(),
        )

    private fun dropUnavailableSelections(): SessionBrowserState =
        copy(selectedIds = selectedIds intersect selectableEntries().map { it.id }.toSet())

    private fun selectableEntries(): List<SessionBrowserEntry> =
        allEntries.filter(SessionBrowserEntry::canDelete)
}

fun sessionBrowserStateOf(entries: List<SessionBrowserEntry>, selectedIds: Set<String> = emptySet()): SessionBrowserState {
    val groups = listOf(
        SessionBrowserGroup(
            kind = SessionBrowserGroupKind.CURRENT,
            title = "Current session",
            entries = entries
                .filter { it.kind == SessionEntryKind.CURRENT_ACTIVE || it.kind == SessionEntryKind.CURRENT_STOPPED }
                .sortedByDescending(SessionBrowserEntry::modifiedEpochMillis),
        ),
        SessionBrowserGroup(
            kind = SessionBrowserGroupKind.RECORDINGS,
            title = "Recordings",
            entries = entries
                .filter { it.kind == SessionEntryKind.RECORDING }
                .sortedByDescending(SessionBrowserEntry::modifiedEpochMillis),
        ),
        SessionBrowserGroup(
            kind = SessionBrowserGroupKind.ARCHIVES,
            title = "Archived recordings",
            entries = entries
                .filter { it.kind == SessionEntryKind.ARCHIVE }
                .sortedByDescending(SessionBrowserEntry::modifiedEpochMillis),
        ),
    ).filter { it.entries.isNotEmpty() }
    return SessionBrowserState(groups, selectedIds).let { state ->
        state.copy(selectedIds = selectedIds intersect state.allEntries.map { it.id }.toSet())
    }
}
