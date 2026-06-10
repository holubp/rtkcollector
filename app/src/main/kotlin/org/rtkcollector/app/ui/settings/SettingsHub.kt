package org.rtkcollector.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHub(
    onSettingsSets: () -> Unit,
    dashboardLayoutLabel: String,
    onDashboardLayout: () -> Unit,
    onNtripCaster: () -> Unit,
    onNtripMountpoint: () -> Unit,
    onUsbBaud: () -> Unit,
    onCommands: () -> Unit,
    onRecordingOutputs: () -> Unit,
    onStorage: () -> Unit,
    onSessions: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        SettingsButton("Settings sets", onSettingsSets)
        SettingsButton("Dashboard layout: $dashboardLayoutLabel", onDashboardLayout)
        SettingsButton("NTRIP casters", onNtripCaster)
        SettingsButton("NTRIP mountpoints", onNtripMountpoint)
        SettingsButton("USB and baud", onUsbBaud)
        SettingsButton("Command scripts", onCommands)
        SettingsButton("Recording outputs", onRecordingOutputs)
        SettingsButton("Storage", onStorage)
        SettingsButton("Sessions", onSessions)
        SettingsButton("Back", onBack)
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
