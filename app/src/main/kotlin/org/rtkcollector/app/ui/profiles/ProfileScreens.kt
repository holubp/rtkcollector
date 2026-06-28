package org.rtkcollector.app.ui.profiles

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent as AndroidKeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val ActiveProfileBackground = Color(0xFFE8F5E9)
private val ActiveProfileText = Color(0xFF145A18)
private val WarningOrange = Color(0xFFB26A00)
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
    onRename: (String, String) -> Boolean,
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
    onRename: (String, String) -> Boolean,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    supportsSelection: Boolean,
    showManagementActions: Boolean = true,
) {
    var renameTarget by remember { mutableStateOf<ProfileListRow?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProfileListRow?>(null) }

    renameTarget?.let { row ->
        ProfileRenameDialog(
            row = row,
            value = renameText,
            onValueChange = { renameText = it },
            onSave = {
                if (onRename(row.id, profileRenameSaveName(renameText))) {
                    renameTarget = null
                    renameText = ""
                }
            },
            onDiscard = {
                renameTarget = null
                renameText = ""
            },
            onCancel = {
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { row ->
        ProfileDeleteDialog(
            row = row,
            onConfirm = {
                onDelete(row.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

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
                    onRename = {
                        renameTarget = row
                        renameText = row.name
                    },
                    onDelete = { deleteTarget = row },
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
    onRename: () -> Unit,
    onDelete: () -> Unit,
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
            if (row.summary.isNotBlank()) {
                Text(
                    text = row.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.hasWarning) {
                Text(
                    text = "! ${row.warningText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningOrange,
                )
            }

            if (showUse || showEdit || showCopy || showRename || showDelete) {
                CompactProfileActions(
                    showUse = showUse,
                    showEdit = showEdit,
                    editEnabled = row.canViewDetails,
                    editLabel = row.editActionLabel,
                    showCopy = showCopy,
                    showRename = showRename,
                    showDelete = showDelete,
                    deleteLabel = profileDeleteActionLabel(row),
                    onUse = { onSelect(row.id) },
                    onEdit = { onEdit(row.id) },
                    onCopy = { onCopy(row.id) },
                    onRename = onRename,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactProfileActions(
    showUse: Boolean,
    showEdit: Boolean,
    editEnabled: Boolean,
    editLabel: String,
    showCopy: Boolean,
    showRename: Boolean,
    showDelete: Boolean,
    deleteLabel: String,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showUse) {
            Button(onClick = onUse) {
                Text("Use")
            }
        }
        if (showEdit) {
            OutlinedButton(onClick = onEdit, enabled = editEnabled) {
                Text(editLabel)
            }
        }
        if (showCopy) {
            TextButton(onClick = onCopy) {
                Text("Copy")
            }
        }
        if (showRename) {
            TextButton(onClick = onRename) {
                Text("Rename")
            }
        }
        if (showDelete) {
            TextButton(onClick = onDelete) {
                Text(deleteLabel)
            }
        }
    }
}

@Composable
private fun ProfileRenameDialog(
    row: ProfileListRow,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDiscard,
        title = { Text("Rename profile") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Profile name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = canSaveProfileRename(row.name, value),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDiscard,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Discard")
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun ProfileDeleteDialog(
    row: ProfileListRow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val action = profileDeleteActionLabel(row)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (row.isProtected) "Reset profile overrides" else "Delete profile") },
        text = {
            Text(
                if (row.isProtected) {
                    "Reset local overrides for ${row.displayName}?"
                } else {
                    "Delete ${row.displayName}? This cannot be undone."
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(action)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

internal fun profileRenameSaveName(value: String): String = value.trim()

internal fun canSaveProfileRename(currentName: String, value: String): Boolean {
    val saveName = profileRenameSaveName(value)
    return saveName.isNotBlank() && saveName != currentName
}

internal fun profileDeleteActionLabel(row: ProfileListRow): String =
    if (row.isProtected && row.hasLocalOverrides) "Reset local overrides" else "Delete"

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
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.id }) { row ->
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
            Text(
                text = if (row.hasWarning) "! ${row.displayName}" else row.displayName,
                color = if (row.hasWarning && !row.isSelected) WarningOrange else foreground,
                modifier = Modifier.weight(1f),
            )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorScreen(
    data: ProfileEditorData,
    onBack: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
    actions: List<ProfileEditorAction> = emptyList(),
) {
    val activeActions = if (data.readOnly) emptyList() else actions
    val destructiveActions = activeActions.filter { it.destructive }
    val utilityActions = activeActions.filterNot { it.destructive }
    var values by remember {
        mutableStateOf(data.fields.associate { it.key to it.value })
    }
    var visibleSecrets by remember { mutableStateOf(emptySet<String>()) }
    var expandedOptions by remember { mutableStateOf(emptySet<String>()) }
    var showUnsavedPrompt by remember { mutableStateOf(false) }
    var pendingDestructiveAction by remember { mutableStateOf<ProfileEditorAction?>(null) }
    val runtimeFields = data.fields
        .map { field -> field.withRuntimeProfileValidation(values) }
        .map { field -> if (data.readOnly) field.copy(readOnly = true) else field }
    val editorCanSave = !data.readOnly && canSaveProfileEditor(runtimeFields)
    val savedFingerprint = remember(data.fields) {
        profileEditorFingerprint(data.fields.associate { it.key to it.value })
    }
    val unsavedState = UnsavedEditorState(
        savedFingerprint = savedFingerprint,
        currentFingerprint = profileEditorFingerprint(values),
    )
    val saveValues = {
        if (editorCanSave) {
            onSave(values.mapValues { it.value.trim() })
        }
    }
    val leaveEditor = {
        if (data.readOnly || unsavedState.canLeaveWithoutPrompt) {
            onBack()
        } else {
            showUnsavedPrompt = true
        }
    }
    fun runAction(action: ProfileEditorAction) {
        action.onClickWithValues?.invoke(values.mapValues { it.value.trim() }) ?: action.onClick()
    }
    BackHandler(onBack = leaveEditor)
    if (showUnsavedPrompt) {
        AlertDialog(
            onDismissRequest = { showUnsavedPrompt = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save changes before leaving this profile editor?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedPrompt = false
                        saveValues()
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showUnsavedPrompt = false
                            onBack()
                        },
                    ) {
                        Text("Discard")
                    }
                    TextButton(onClick = { showUnsavedPrompt = false }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
    pendingDestructiveAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDestructiveAction = null },
            title = { Text(action.warningTitle ?: action.label) },
            text = {
                Text(action.warningBody ?: "Confirm ${action.label.lowercase()} for this profile?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDestructiveAction = null
                        runAction(action)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (action.destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                ) {
                    Text(action.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDestructiveAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(data.title) },
                    navigationIcon = {
                        TextButton(onClick = leaveEditor) {
                            Text("Back")
                        }
                    },
                    actions = {
                        if (!data.readOnly) {
                            Button(
                                onClick = saveValues,
                                enabled = editorCanSave,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text("Save")
                            }
                            TextButton(
                                onClick = onBack,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text("Discard")
                            }
                        }
                        destructiveActions.forEach { action ->
                            TextButton(
                                onClick = {
                                    pendingDestructiveAction = action
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text(action.label)
                            }
                        }
                    },
                )
                if (utilityActions.isNotEmpty()) {
                    Surface(
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            utilityActions.forEach { action ->
                                TextButton(
                                    onClick = {
                                        if (action.warningTitle != null || action.warningBody != null) {
                                            pendingDestructiveAction = action
                                        } else {
                                            runAction(action)
                                        }
                                    },
                                ) {
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
            items(runtimeFields, key = { it.key }) { field ->
                if (field.boolean) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = field.value.equals("true", ignoreCase = true),
                            onCheckedChange = { checked ->
                                values = values + (field.key to checked.toString())
                            },
                            enabled = !field.readOnly,
                        )
                        Text(field.label, modifier = Modifier.weight(1f))
                    }
                    ProfileFieldError(field)
                } else if (field.readOnlyList.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(field.label, style = MaterialTheme.typography.labelLarge)
                        field.readOnlyList.forEach { item ->
                            Text(item, style = MaterialTheme.typography.bodyMedium)
                        }
                        ProfileFieldError(field)
                    }
                } else if (field.editorOptions().isNotEmpty()) {
                    val optionItems = field.editorOptions()
                    val expanded = field.key in expandedOptions && !field.readOnly
                    val selectedLabel = optionItems.firstOrNull { it.value == field.value }?.label
                        ?: field.value
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = {
                            if (!field.readOnly) {
                                expandedOptions = if (expanded) {
                                    expandedOptions - field.key
                                } else {
                                    expandedOptions + field.key
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !field.readOnly),
                            label = { Text(field.label) },
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            isError = field.hasError,
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
                    ProfileFieldError(field)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(field.label, style = MaterialTheme.typography.labelLarge)
                        if (field.multiline) {
                            ScriptTextField(
                                value = field.value,
                                onValueChange = { value ->
                                    values = values + (field.key to value)
                                },
                                readOnly = field.readOnly,
                                isError = field.hasError,
                            )
                        } else {
                            OutlinedTextField(
                                value = field.value,
                                onValueChange = { value ->
                                    values = values + (field.key to value)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                readOnly = field.readOnly,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                isError = field.hasError,
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
                        ProfileFieldError(field)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptTextField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    isError: Boolean = false,
) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val focusManager = LocalFocusManager.current
    var savedSelection by remember { mutableStateOf(value.length to value.length) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val borderColor = if (isError) {
        MaterialTheme.colorScheme.error.toArgb()
    } else {
        MaterialTheme.colorScheme.outline.toArgb()
    }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp),
        factory = { context ->
            EditText(context).apply {
                val density = context.resources.displayMetrics.density
                setPadding(
                    (12 * density).toInt(),
                    (10 * density).toInt(),
                    (12 * density).toInt(),
                    (10 * density).toInt(),
                )
                setText(value)
                setSingleLine(false)
                minLines = 4
                gravity = Gravity.TOP or Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_NONE
                typeface = Typeface.MONOSPACE
                textSize = 14f
                setHorizontallyScrolling(false)
                isHorizontalScrollBarEnabled = false
                fun saveSelection() {
                    savedSelection = selectionStart.coerceAtLeast(0) to selectionEnd.coerceAtLeast(0)
                }
                fun restoreSelection() {
                    val start = savedSelection.first.coerceIn(0, text.length)
                    val end = savedSelection.second.coerceIn(0, text.length)
                    setSelection(start, end)
                }
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == AndroidKeyEvent.KEYCODE_TAB && event.action == AndroidKeyEvent.ACTION_DOWN) {
                        saveSelection()
                        focusManager.moveFocus(
                            if (event.isShiftPressed) {
                                FocusDirection.Previous
                            } else {
                                FocusDirection.Next
                            },
                        )
                        true
                    } else {
                        false
                    }
                }
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        restoreSelection()
                    } else {
                        saveSelection()
                    }
                }
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) {
                            val next = s?.toString().orEmpty()
                            if (next != value) {
                                latestOnValueChange(next)
                            }
                        }
                    },
                )
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.backgroundTintList = null
            view.background = GradientDrawable().apply {
                val density = view.resources.displayMetrics.density
                setColor(backgroundColor)
                cornerRadius = 4 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), borderColor)
            }
            view.isEnabled = true
            view.isFocusable = !readOnly
            view.isFocusableInTouchMode = !readOnly
            view.isCursorVisible = !readOnly
            view.setTextIsSelectable(readOnly)
            if (view.text.toString() != value) {
                savedSelection = savedSelection.first.coerceIn(0, value.length) to
                    savedSelection.second.coerceIn(0, value.length)
                view.setText(value)
                view.setSelection(savedSelection.first, savedSelection.second)
            }
        },
    )
}

@Composable
private fun ProfileFieldError(field: EditableProfileField) {
    if (field.hasError) {
        Text(
            text = "! ${field.errorText}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall,
        )
    } else if (field.hasHelper) {
        Text(
            text = field.helperText.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
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
