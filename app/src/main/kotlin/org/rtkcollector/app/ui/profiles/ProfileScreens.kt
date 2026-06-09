package org.rtkcollector.app.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSetListScreen(
    title: String,
    state: SettingsSetListState,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    ProfileListScreen(
        title = title,
        rows = state.rows,
        onSelect = onSelect,
        onEdit = onEdit,
        onCopy = onCopy,
        onRename = onRename,
        onDelete = onDelete,
        onBack = onBack,
        supportsSelection = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    title: String,
    rows: List<ProfileListRow>,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    supportsSelection: Boolean,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No profiles yet.")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { it.id }) { row ->
                ProfileListItem(
                    row = row,
                    supportsSelection = supportsSelection,
                    onSelect = onSelect,
                    onEdit = onEdit,
                    onCopy = onCopy,
                    onRename = onRename,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun ProfileListItem(
    row: ProfileListRow,
    supportsSelection: Boolean,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.displayName, style = MaterialTheme.typography.titleSmall)
                    if (row.isProtected) {
                        AssistChip(onClick = {}, label = { Text("Built-in") })
                    }
                }
                if (supportsSelection && row.isSelected) {
                    AssistChip(onClick = {}, label = { Text("Active") })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (supportsSelection) {
                    Button(onClick = { onSelect(row.id) }, modifier = Modifier.weight(1f)) {
                        Text("Use")
                    }
                }
                OutlinedButton(
                    onClick = { if (row.canEdit) onEdit(row.id) },
                    enabled = row.canEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Edit")
                }
                if (row.canCopy) {
                    TextButton(onClick = { onCopy(row.id) }, modifier = Modifier.weight(1f)) {
                        Text("Copy")
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (row.canRename) {
                    TextButton(onClick = { onRename(row.id) }) {
                        Text("Rename")
                    }
                }
                if (row.canDelete) {
                    TextButton(onClick = { onDelete(row.id) }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    title: String,
    values: Map<String, String>,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            values.forEach { (key, value) ->
                OutlinedTextField(
                    value = "$key: $value",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(key) },
                )
            }
            if (values.isEmpty()) {
                Text("No fields to display.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    data: ProfileEditorData,
    onBack: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    var values by remember {
        mutableStateOf(data.fields.associate { it.key to it.value })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(data.title) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(data.fields, key = { it.key }) { field ->
                OutlinedTextField(
                    value = values[field.key].orEmpty(),
                    onValueChange = { value ->
                        values = values + (field.key to value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(field.label) },
                    minLines = if (field.multiline) 4 else 1,
                    singleLine = !field.multiline,
                )
            }
            item {
                Button(
                    onClick = { onSave(values.mapValues { it.value.trim() }) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NtripMountpointScreen(
    initialState: NtripMountpointEditorState,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var state by remember { mutableStateOf(initialState) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NTRIP mountpoint") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.mountpointText,
                onValueChange = { state = state.copy(mountpointText = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mountpoint") },
                singleLine = true,
            )
            Button(
                onClick = { onSave(state.mountpointText.trim()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save mountpoint")
            }
            state.availableMountpoints.forEach { mountpoint ->
                OutlinedButton(
                    onClick = { state = state.selectMountpoint(mountpoint) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(mountpoint)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleSettingsScreen(
    title: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("$title settings are managed through native profile configuration.")
        }
    }
}
