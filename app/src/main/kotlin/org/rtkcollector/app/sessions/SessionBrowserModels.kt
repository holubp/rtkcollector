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

data class SessionActionCapabilities(
    val shareZip: Boolean,
    val shareNmea: Boolean,
    val reexportNmea: Boolean,
    val archive: Boolean,
    val restore: Boolean,
    val delete: Boolean,
) {
    companion object {
        fun forFilesystem(kind: SessionEntryKind, isActive: Boolean): SessionActionCapabilities =
            SessionActionCapabilities(
                shareZip = !isActive && (kind == SessionEntryKind.CURRENT_STOPPED || kind == SessionEntryKind.RECORDING),
                shareNmea = !isActive && (kind == SessionEntryKind.CURRENT_STOPPED || kind == SessionEntryKind.RECORDING),
                reexportNmea = !isActive && (kind == SessionEntryKind.CURRENT_STOPPED || kind == SessionEntryKind.RECORDING),
                archive = !isActive && (kind == SessionEntryKind.CURRENT_STOPPED || kind == SessionEntryKind.RECORDING),
                restore = kind == SessionEntryKind.ARCHIVE,
                delete = !isActive,
            )
    }
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
    val capabilities: SessionActionCapabilities? = null,
) {
    val isActive: Boolean
        get() = kind == SessionEntryKind.CURRENT_ACTIVE

    val isArchive: Boolean
        get() = kind == SessionEntryKind.ARCHIVE

    private val effectiveCapabilities: SessionActionCapabilities
        get() = capabilities ?: if (filesystemBacked) {
            SessionActionCapabilities.forFilesystem(kind, isActive)
        } else {
            SessionActionCapabilities(
                shareZip = false,
                shareNmea = false,
                reexportNmea = false,
                archive = false,
                restore = false,
                delete = false,
            )
        }

    val canShareZip: Boolean
        get() = effectiveCapabilities.shareZip

    val canShareNmea: Boolean
        get() = effectiveCapabilities.shareNmea

    val canReexportNmea: Boolean
        get() = effectiveCapabilities.reexportNmea

    val canArchive: Boolean
        get() = effectiveCapabilities.archive

    val canRestore: Boolean
        get() = effectiveCapabilities.restore

    val canDelete: Boolean
        get() = effectiveCapabilities.delete
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

    val hasSelectableEntries: Boolean
        get() = selectableEntries().isNotEmpty()

    val allSelectableSelected: Boolean
        get() {
            val selectable = selectableEntries().map { it.id }.toSet()
            return selectable.isNotEmpty() && selectedIds.containsAll(selectable)
        }

    val selectAllButtonLabel: String
        get() = if (allSelectableSelected) "Unselect all" else "Select all"

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

    fun toggleSelectAll(): SessionBrowserState =
        if (allSelectableSelected) {
            clearSelection()
        } else {
            selectAll()
        }

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

fun sessionPathCopyText(
    entry: SessionBrowserEntry,
    selectionMode: Boolean,
): String? {
    if (selectionMode) return null
    return entry.location.takeIf { it.isNotBlank() }
}
