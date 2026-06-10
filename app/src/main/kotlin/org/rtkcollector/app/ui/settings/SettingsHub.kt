package org.rtkcollector.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHub(
    onSettingsSets: () -> Unit,
    onWorkflowSelection: () -> Unit,
    dashboardLayoutLabel: String,
    onDashboardLayout: () -> Unit,
    onNtripCaster: () -> Unit,
    onNtripMountpoint: () -> Unit,
    onUsbBaud: () -> Unit,
    onCommands: () -> Unit,
    onReceiverProfile: () -> Unit,
    onRecordingOutputs: () -> Unit,
    onStorage: () -> Unit,
    onSessions: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSection("Session setup") {
                SettingsButton("◎", "Settings sets", onSettingsSets)
                SettingsButton("⇄", "Workflow selection", onWorkflowSelection)
                SettingsButton("▤", "Dashboard layout: $dashboardLayoutLabel", onDashboardLayout)
                SettingsButton("●", "Recording outputs", onRecordingOutputs)
                SettingsButton("▣", "Storage location profiles", onStorage)
            }

            SettingsSection("Receiver and USB") {
                SettingsButton("USB", "USB device and baud", onUsbBaud)
                SettingsButton("⌁", "Command scripts", onCommands)
                SettingsButton("RX", "Receiver family/profile", onReceiverProfile)
            }

            SettingsSection("Corrections") {
                SettingsButton("N", "NTRIP casters", onNtripCaster)
                SettingsButton("M", "NTRIP mountpoints", onNtripMountpoint)
            }

            SettingsSection("Sessions") {
                SettingsButton("↗", "Recent sessions and sharing", onSessions)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SettingsButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 24.dp)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun SettingsHubPreview() {
    MaterialTheme {
        SettingsHub(
            onSettingsSets = {},
            onWorkflowSelection = {},
            dashboardLayoutLabel = "Compact field dashboard",
            onDashboardLayout = {},
            onNtripCaster = {},
            onNtripMountpoint = {},
            onUsbBaud = {},
            onCommands = {},
            onReceiverProfile = {},
            onRecordingOutputs = {},
            onStorage = {},
            onSessions = {},
            onBack = {},
        )
    }
}
