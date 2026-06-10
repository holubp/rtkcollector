package org.rtkcollector.app.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private val ActiveProfileBackground = Color(0xFFE8F5E9)
private val ActiveProfileText = Color(0xFF145A18)
private val ReceiverFamilyOptions = listOf(
    EditableProfileOption("um980-n4", "Unicore UM980 / N4"),
    EditableProfileOption("ublox-m8p0", "u-blox M8P-0"),
    EditableProfileOption("ublox-m8p2", "u-blox M8P-2"),
    EditableProfileOption("ublox-m8t", "u-blox M8T"),
    EditableProfileOption("generic-nmea-rtcm", "Generic NMEA + RTCM"),
)

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
    onAdd: () -> Unit,
    onBack: () -> Unit,
    showManagementActions: Boolean = true,
) {
    ProfileListScreen(
        title = title,
        rows = state.rows,
        onSelect = onSelect,
        onEdit = onEdit,
        onCopy = onCopy,
        onRename = onRename,
        onDelete = onDelete,
        onAdd = onAdd,
        onBack = onBack,
        supportsSelection = true,
        showManagementActions = showManagementActions,
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
    onAdd: () -> Unit,
    onBack: () -> Unit,
    supportsSelection: Boolean,
    showManagementActions: Boolean = true,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (showManagementActions) {
                        TextButton(onClick = onAdd) {
                            Text("Add")
                        }
                    }
                },
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
                if (showManagementActions) {
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Text("Add")
                    }
                }
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
                    showManagementActions = showManagementActions,
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
    showManagementActions: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (row.isSelected) {
                ActiveProfileBackground
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
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
                        BuiltInLabel()
                    }
                }
                if (supportsSelection && row.isSelected) {
                    ActiveLabel()
                }
            }

            val showUse = supportsSelection && !row.isSelected
            val showEdit = showManagementActions
            val showCopy = showManagementActions && row.canCopy
            val showRename = showManagementActions && row.canRename
            val showDelete = showManagementActions && row.canDelete
            if (showUse || showEdit || showCopy || showRename || showDelete) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showUse || showEdit || showCopy) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (showUse) {
                                Button(onClick = { onSelect(row.id) }, modifier = Modifier.weight(1f)) {
                                    Text("Use")
                                }
                            }
                            if (showEdit) {
                                OutlinedButton(
                                    onClick = { if (row.canEdit) onEdit(row.id) },
                                    enabled = row.canEdit,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Edit")
                                }
                            }
                            if (showCopy) {
                                TextButton(onClick = { onCopy(row.id) }, modifier = Modifier.weight(1f)) {
                                    Text("Copy")
                                }
                            }
                        }
                    }

                    if (showRename || showDelete) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (showRename) {
                                TextButton(onClick = { onRename(row.id) }, modifier = Modifier.weight(1f)) {
                                    Text("Rename")
                                }
                            }
                            if (showDelete) {
                                TextButton(onClick = { onDelete(row.id) }, modifier = Modifier.weight(1f)) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltInLabel() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "Built-in",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ProfileSelectorDialog(
    title: String,
    rows: List<ProfileListRow>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text(title) },
        text = {
            if (rows.isEmpty()) {
                Text("No selectable profiles.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        ProfileSelectorRow(row = row, onSelect = onSelect)
                    }
                }
            }
        },
    )
}

@Composable
private fun ProfileSelectorRow(row: ProfileListRow, onSelect: (String) -> Unit) {
    val background = if (row.isSelected) ActiveProfileBackground else MaterialTheme.colorScheme.surface
    val foreground = if (row.isSelected) ActiveProfileText else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(row.id) },
        color = background,
        shape = MaterialTheme.shapes.small,
        tonalElevation = if (row.isSelected) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(row.displayName, color = foreground, modifier = Modifier.weight(1f))
            if (row.isSelected) {
                ActiveLabel()
            }
        }
    }
}

@Composable
private fun ActiveLabel() {
    Surface(
        color = ActiveProfileText,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "Active",
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
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
    actions: List<ProfileEditorAction> = emptyList(),
) {
    var values by remember {
        mutableStateOf(data.fields.associate { it.key to it.value })
    }
    var visibleSecrets by remember { mutableStateOf(emptySet<String>()) }
    var expandedOptions by remember { mutableStateOf(emptySet<String>()) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(data.title) },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text("Back")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = onBack,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Discard")
                        }
                        Button(
                            onClick = { onSave(values.mapValues { it.value.trim() }) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Save")
                        }
                    },
                )
                if (actions.isNotEmpty()) {
                    Surface(
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            actions.forEach { action ->
                                TextButton(onClick = action.onClick) {
                                    Text(action.label)
                                }
                            }
                        }
                    }
                }
            }
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
                if (field.boolean) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = values[field.key].equals("true", ignoreCase = true),
                            onCheckedChange = { checked ->
                                values = values + (field.key to checked.toString())
                            },
                        )
                        Text(field.label, modifier = Modifier.weight(1f))
                    }
                } else if (field.readOnlyList.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(field.label, style = MaterialTheme.typography.labelLarge)
                        field.readOnlyList.forEach { item ->
                            Text(item, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (field.editorOptions().isNotEmpty()) {
                    val optionItems = field.editorOptions()
                    val expanded = field.key in expandedOptions
                    val selectedLabel = optionItems.firstOrNull { it.value == values[field.key] }?.label
                        ?: values[field.key].orEmpty()
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = {
                            expandedOptions = if (expanded) {
                                expandedOptions - field.key
                            } else {
                                expandedOptions + field.key
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                            label = { Text(field.label) },
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expandedOptions = expandedOptions - field.key },
                        ) {
                            optionItems.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        values = values + (field.key to option.value)
                                        expandedOptions = expandedOptions - field.key
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(field.label, style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = values[field.key].orEmpty(),
                            onValueChange = { value ->
                                values = values + (field.key to value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = if (field.multiline) 4 else 1,
                            readOnly = field.readOnly,
                            singleLine = !field.multiline,
                            textStyle = if (field.multiline) {
                                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            visualTransformation = if (field.secret && field.key !in visibleSecrets) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                            trailingIcon = if (field.secret) {
                                {
                                    TextButton(
                                        onClick = {
                                            visibleSecrets = if (field.key in visibleSecrets) {
                                                visibleSecrets - field.key
                                            } else {
                                                visibleSecrets + field.key
                                            }
                                        },
                                    ) {
                                        Text(if (field.key in visibleSecrets) "Hide" else "Show")
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun EditableProfileField.editorOptions(): List<EditableProfileOption> =
    optionItems.ifEmpty {
        if (key == "receiverFamily") {
            ReceiverFamilyOptions
        } else {
            emptyList()
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
