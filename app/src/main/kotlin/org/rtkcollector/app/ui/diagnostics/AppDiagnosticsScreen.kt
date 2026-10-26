package org.rtkcollector.app.ui.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.diagnostics.DiagnosticsStatus
import org.rtkcollector.app.diagnostics.DiagnosticLevel
import org.rtkcollector.app.diagnostics.DiagnosticsRetention
import org.rtkcollector.app.ui.common.TidyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDiagnosticsScreen(
    status: DiagnosticsStatus,
    lastError: String?,
    onLevelChange: (DiagnosticLevel) -> Unit,
    onRetentionChange: (DiagnosticsRetention) -> Unit,
    onPerformanceMonitoringChange: (Boolean) -> Unit,
    onShareRuntimeLogs: () -> Unit,
    onSharePerformanceLogs: () -> Unit,
    onDeleteLogs: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App logs and performance monitoring") },
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
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiagnosticsCard("Logging") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    DiagnosticLevel.entries.forEachIndexed { index, level ->
                        SegmentedButton(selected = status.level == level, onClick = { onLevelChange(level) },
                            icon = {},
                            shape = SegmentedButtonDefaults.itemShape(index, DiagnosticLevel.entries.size)) {
                            Text(level.name.lowercase().replaceFirstChar(Char::uppercaseChar))
                        }
                    }
                }
                Text("Essential: ${status.essentialFiles} files, ${status.essentialBytes} bytes")
                Text("Optional: ${status.runtimeFiles} files, ${status.runtimeBytes} bytes")
                LogChoice("Optional log cap", "${status.retention.maxOptionalBytes / 1_000_000} MB",
                    DiagnosticsRetention.SIZE_CHOICES_MB.map { "$it MB" }) { index ->
                    onRetentionChange(status.retention.copy(maxOptionalBytes = DiagnosticsRetention.SIZE_CHOICES_MB[index] * 1_000_000L))
                }
                LogChoice("Optional log retention", "${status.retention.maxOptionalAgeDays} days",
                    DiagnosticsRetention.AGE_CHOICES_DAYS.map { "$it days" }) { index ->
                    onRetentionChange(status.retention.copy(maxOptionalAgeDays = DiagnosticsRetention.AGE_CHOICES_DAYS[index]))
                }
                if (status.droppedRecords > 0 || status.writeFailures > 0) {
                    Text("Diagnostic losses: ${status.droppedRecords} dropped, ${status.writeFailures} write failures",
                        color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onShareRuntimeLogs, enabled = status.runtimeBytes + status.essentialBytes > 0L) {
                    Text("Share app logs")
                }
            }
            DiagnosticsCard("Performance monitoring") {
                ToggleRow("Performance monitoring", status.performanceMonitoringEnabled, onPerformanceMonitoringChange)
                Text("Stored: ${status.performanceFiles} files, ${status.performanceBytes} bytes")
                Button(onClick = onSharePerformanceLogs, enabled = status.performanceBytes > 0L) {
                    Text("Share performance logs")
                }
            }
            DiagnosticsCard("Maintenance") {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = status.runtimeBytes + status.essentialBytes + status.performanceBytes > 0L,
                ) {
                    Text("Delete logs")
                }
                if (!lastError.isNullOrBlank()) {
                    Text(lastError, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete diagnostic logs?") },
            text = { Text("This removes app logs and performance metrics, not recording sessions. Recently shared ZIP copies remain available to recipients for one hour.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteLogs()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun LogChoice(label: String, current: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $current") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, value ->
                DropdownMenuItem(text = { Text(value) }, onClick = { expanded = false; onSelect(index) })
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, TidyColors.Divider),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
