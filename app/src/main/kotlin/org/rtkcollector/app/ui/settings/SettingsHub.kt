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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = title.uppercase(),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 24.dp)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = icon,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
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
        modifier = Modifier.padding(start = 58.dp, end = 14.dp),
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
            onBack = {},
        )
    }
}
