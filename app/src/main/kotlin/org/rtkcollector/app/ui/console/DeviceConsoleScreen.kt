package org.rtkcollector.app.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.console.DeviceConsoleLineEnding
import org.rtkcollector.app.console.DeviceConsoleState

data class DeviceConsoleOption(
    val id: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConsoleScreen(
    state: DeviceConsoleState,
    recordingActive: Boolean,
    deviceFilterLabel: String,
    usbProfiles: List<DeviceConsoleOption>,
    commandProfiles: List<DeviceConsoleOption>,
    selectedUsbProfileId: String?,
    selectedCommandProfileId: String?,
    inputText: String,
    onInputChange: (String) -> Unit,
    onUsbProfileSelected: (String) -> Unit,
    onCommandProfileSelected: (String) -> Unit,
    onLineEndingSelected: (DeviceConsoleLineEnding) -> Unit,
    onBufferLimitSelected: (Int) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSend: () -> Unit,
    onSendInit: () -> Unit,
    onClearInput: () -> Unit,
    onPauseToggle: () -> Unit,
    onCopyOutput: () -> Unit,
    onClearOutput: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device console") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = onConnect, enabled = !recordingActive && !state.connected) { Text("Connect") }
                    TextButton(onClick = onDisconnect, enabled = state.connected) { Text("Disconnect") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (recordingActive) {
                Text(
                    text = "Stop recording before opening the device console.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ConsoleDropdown(
                    label = "USB/baud",
                    value = usbProfiles.firstOrNull { it.id == selectedUsbProfileId }?.label ?: "n/a",
                    options = usbProfiles,
                    onSelect = onUsbProfileSelected,
                    modifier = Modifier.weight(1f),
                )
                ConsoleDropdown(
                    label = "Line ending",
                    value = state.lineEnding.label,
                    options = DeviceConsoleLineEnding.entries.map { DeviceConsoleOption(it.name, it.label) },
                    onSelect = { id -> onLineEndingSelected(DeviceConsoleLineEnding.valueOf(id)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ConsoleDropdown(
                    label = "Init profile ($deviceFilterLabel)",
                    value = commandProfiles.firstOrNull { it.id == selectedCommandProfileId }?.label ?: "n/a",
                    options = commandProfiles,
                    onSelect = onCommandProfileSelected,
                    modifier = Modifier.weight(1f),
                )
                ConsoleDropdown(
                    label = "Buffer",
                    value = bufferLabel(state.bufferLimitBytes),
                    options = listOf(
                        DeviceConsoleOption("262144", "256 KB"),
                        DeviceConsoleOption("1048576", "1 MB"),
                        DeviceConsoleOption("4194304", "4 MB"),
                    ),
                    onSelect = { id -> onBufferLimitSelected(id.toInt()) },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onSendInit, enabled = state.connected && selectedCommandProfileId != null) {
                    Text("Send init")
                }
            }
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = state.output,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                label = { Text("Command input") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                minLines = 3,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Button(onClick = onSend, enabled = state.connected && inputText.isNotBlank()) { Text("Send") }
                TextButton(onClick = onClearInput) { Text("Clear input") }
                TextButton(onClick = onPauseToggle) { Text(if (state.paused) "Resume output" else "Pause output") }
                TextButton(onClick = onCopyOutput, enabled = state.output.isNotBlank()) { Text("Copy output") }
                TextButton(onClick = onClearOutput, enabled = state.output.isNotBlank()) { Text("Clear output") }
            }
            state.lastError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsoleDropdown(
    label: String,
    value: String,
    options: List<DeviceConsoleOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun bufferLabel(bytes: Int): String =
    when (bytes) {
        262_144 -> "256 KB"
        1_048_576 -> "1 MB"
        4_194_304 -> "4 MB"
        else -> "$bytes B"
    }
