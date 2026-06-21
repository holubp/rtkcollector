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
import org.rtkcollector.app.ui.common.TidyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDiagnosticsScreen(
    status: DiagnosticsStatus,
    lastError: String?,
    onRuntimeLoggingChange: (Boolean) -> Unit,
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
            DiagnosticsCard("Runtime logging") {
                ToggleRow("Runtime logging", status.runtimeLoggingEnabled, onRuntimeLoggingChange)
                Text("Stored: ${status.runtimeFiles} files, ${status.runtimeBytes} bytes")
                Button(onClick = onShareRuntimeLogs, enabled = status.runtimeBytes > 0L) {
                    Text("Share runtime logs")
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
                    enabled = status.runtimeBytes > 0L || status.performanceBytes > 0L,
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
            text = { Text("This removes app logs and performance metrics. Recording sessions are not deleted.") },
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
