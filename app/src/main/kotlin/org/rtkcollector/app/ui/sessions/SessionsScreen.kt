package org.rtkcollector.app.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.sessions.SessionBrowserEntry
import org.rtkcollector.app.sessions.SessionBrowserGroupKind
import org.rtkcollector.app.sessions.SessionBrowserState
import org.rtkcollector.app.sessions.sessionPathCopyText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionsScreen(
    state: SessionBrowserState,
    progressText: String? = null,
    progressFraction: Float? = null,
    onToggle: (String) -> Unit,
    onSelectCurrent: () -> Unit,
    onSelectRecordings: () -> Unit,
    onSelectArchives: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onShareSelected: () -> Unit,
    onShareNmeaSelected: () -> Unit,
    onReexportNmeaSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onRestoreSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCopyPath: (String) -> Unit,
    onBack: () -> Unit,
) {
    var confirmation by remember { mutableStateOf<ConfirmationAction?>(null) }
    val selected = state.selectedEntries
    val selectionMode = state.selectedIds.isNotEmpty()
    val canShare = selected.any(SessionBrowserEntry::canShareZip)
    val canShareNmea = selected.any(SessionBrowserEntry::canShareNmea)
    val canReexportNmea = selected.any(SessionBrowserEntry::canReexportNmea)
    val canArchive = selected.any(SessionBrowserEntry::canArchive)
    val canRestore = selected.any(SessionBrowserEntry::canRestore)
    val canDelete = selected.any(SessionBrowserEntry::canDelete)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recorded sessions", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Back") }
        }
        progressText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                progress = { progressFraction?.coerceIn(0f, 1f) ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onSelectCurrent) { Text("Select current") }
            OutlinedButton(onClick = onSelectRecordings) { Text("Select recordings") }
            OutlinedButton(onClick = onSelectArchives) { Text("Select archives") }
            OutlinedButton(onClick = onSelectAll, enabled = state.hasSelectableEntries) { Text(state.selectAllButtonLabel) }
            OutlinedButton(onClick = onClearSelection) { Text("Clear") }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onShareSelected, enabled = canShare) { Text("Share ZIP") }
            Button(onClick = onShareNmeaSelected, enabled = canShareNmea) { Text("Share NMEA") }
            Button(onClick = onReexportNmeaSelected, enabled = canReexportNmea) { Text("Regenerate NMEA") }
            Button(onClick = { confirmation = ConfirmationAction.ARCHIVE }, enabled = canArchive) { Text("Archive") }
            Button(onClick = { confirmation = ConfirmationAction.RESTORE }, enabled = canRestore) { Text("Restore") }
            Button(onClick = { confirmation = ConfirmationAction.DELETE }, enabled = canDelete) { Text("Delete") }
        }
        Text(
            text = "Regenerate NMEA updates receiver-solution.nmea from the in-device receiver solution only.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${selected.size} selected",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.groups.isEmpty()) {
                Text(
                    text = "No recordings found in the configured storage yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.groups.forEach { group ->
                Text(group.title, style = MaterialTheme.typography.titleMedium)
                group.entries.forEach { entry ->
                    SessionRow(
                        entry = entry,
                        selected = entry.id in state.selectedIds,
                        selectionMode = selectionMode,
                        onToggle = { onToggle(entry.id) },
                        onCopyPath = { path -> onCopyPath(path) },
                    )
                }
            }
        }
    }

    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(action.title) },
            text = { Text(action.message(selected.count(action.predicate))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = null
                        when (action) {
                            ConfirmationAction.ARCHIVE -> onArchiveSelected()
                            ConfirmationAction.RESTORE -> onRestoreSelected()
                            ConfirmationAction.DELETE -> onDeleteSelected()
                        }
                    },
                ) {
                    Text(action.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SessionRow(
    entry: SessionBrowserEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onCopyPath: (String) -> Unit,
) {
    val pathToCopy = sessionPathCopyText(entry, selectionMode)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = pathToCopy != null) {
                pathToCopy?.let(onCopyPath)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = entry.canDelete,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(entry.kind.label, entry.sizeBytes?.let(::formatBytes)).joinToString(" | "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = if (entry.isArchive) "Archived" else if (entry.isActive) "Active" else "Recording",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.widthIn(min = 72.dp),
            )
        }
    }
}

private enum class ConfirmationAction(
    val title: String,
    val confirmLabel: String,
    val predicate: (SessionBrowserEntry) -> Boolean,
) {
    ARCHIVE("Archive recordings?", "Archive", SessionBrowserEntry::canArchive),
    RESTORE("Restore archives?", "Restore", SessionBrowserEntry::canRestore),
    DELETE("Delete selected?", "Delete", SessionBrowserEntry::canDelete);

    fun message(count: Int): String =
        when (this) {
            ARCHIVE -> "Archive $count recording(s) into permanent ZIP files and remove the original folders after verification?"
            RESTORE -> "Restore $count archive(s) back into session folders and remove the ZIP files after verification?"
            DELETE -> "Delete $count selected item(s)? This cannot be undone."
        }
}

private val org.rtkcollector.app.sessions.SessionEntryKind.label: String
    get() = when (this) {
        org.rtkcollector.app.sessions.SessionEntryKind.CURRENT_ACTIVE -> "current active"
        org.rtkcollector.app.sessions.SessionEntryKind.CURRENT_STOPPED -> "current stopped"
        org.rtkcollector.app.sessions.SessionEntryKind.RECORDING -> "recording"
        org.rtkcollector.app.sessions.SessionEntryKind.ARCHIVE -> "archive"
    }

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f kB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
