package org.rtkcollector.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.ui.common.HelpOverlay
import org.rtkcollector.app.ui.common.HelpTopic
import org.rtkcollector.app.ui.common.TidyColors

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
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var helpTopic by remember { mutableStateOf<HelpTopic?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    TextButton(onClick = { helpTopic = HelpTopic.SETTINGS_GROUPS }) {
                        Text("?")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsSection("Session setup") {
                    SettingsRow("◎", "Settings sets", onSettingsSets)
                    SettingsDivider()
                    SettingsRow("⇄", "Workflow selection", onWorkflowSelection)
                    SettingsDivider()
                    SettingsRow("▤", "Dashboard layout", onDashboardLayout, subtitle = dashboardLayoutLabel)
                    SettingsDivider()
                    SettingsRow("●", "Recording outputs", onRecordingOutputs)
                    SettingsDivider()
                    SettingsRow("▣", "Storage location profiles", onStorage)
                }

                SettingsSection("Receiver and USB") {
                    SettingsRow("USB", "USB device and baud", onUsbBaud)
                    SettingsDivider()
                    SettingsRow("⌁", "Command scripts", onCommands)
                    SettingsDivider()
                    SettingsRow("RX", "Receiver family/profile", onReceiverProfile)
                }

                SettingsSection("Corrections") {
                    SettingsRow("N", "NTRIP casters", onNtripCaster)
                    SettingsDivider()
                    SettingsRow("M", "NTRIP mountpoints", onNtripMountpoint)
                }

                SettingsSection("Sessions") {
                    SettingsRow("↗", "Recent sessions and sharing", onSessions)
                }

                SettingsSection("Settings transfer") {
                    SettingsRow("⇧", "Export settings backup", onExportSettings)
                    SettingsDivider()
                    SettingsRow("⇩", "Import settings backup", onImportSettings)
                }
            }
            HelpOverlay(topic = helpTopic, onDismiss = { helpTopic = null })
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TidyColors.Divider),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = title.uppercase(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: String,
    label: String,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    val description = if (subtitle.isNullOrBlank()) label else "$label: $subtitle"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 22.dp)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = icon,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp, end = 12.dp),
        color = TidyColors.Divider,
    )
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
            onExportSettings = {},
            onImportSettings = {},
            onBack = {},
        )
    }
}
