package org.rtkcollector.app.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.profile.SettingsImportSummary
import org.rtkcollector.app.profile.SettingsImportValidationResult

@Composable
fun SettingsImportScreen(
    source: String,
    result: SettingsImportValidationResult,
    recordingActive: Boolean,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Import settings backup", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = source,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        when (result) {
            SettingsImportValidationResult.Loading -> {
                Text("Reading and validating settings backup...")
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
            is SettingsImportValidationResult.Valid -> {
                Summary(result.summary)
                if (result.summary.containsPlaintextPasswords) {
                    Warning("This backup contains plaintext NTRIP passwords. Import only if you trust the source.")
                }
                if (result.summary.omittedProfileFamilies.isNotEmpty()) {
                    Warning(
                        "This older backup does not contain ${result.summary.omittedProfileFamilies.joinToString()}. " +
                            "The currently installed profiles in those categories will be kept.",
                    )
                }
                if (result.summary.safTreeUriReselectionCount > 0) {
                    val count = result.summary.safTreeUriReselectionCount
                    Warning(
                        "$count imported Android folder ${if (count == 1) "needs" else "need"} to be selected again before use.",
                    )
                }
                if (recordingActive) {
                    Warning("Stop recording before importing settings.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onImport, enabled = !recordingActive) { Text("Import") }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            is SettingsImportValidationResult.Invalid -> {
                Warning(result.message)
                OutlinedButton(onClick = onCancel) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun Summary(summary: SettingsImportSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Command profiles: ${summary.commandProfileCount}")
        Text("USB/baud profiles: ${summary.usbBaudProfileCount}")
        Text("NTRIP casters: ${summary.ntripCasterProfileCount}")
        Text("NTRIP caster uploads: ${summary.ntripCasterUploadProfileCount}")
        Text("NTRIP mountpoints: ${summary.ntripMountpointProfileCount}")
        Text("Recording outputs: ${summary.recordingPolicyProfileCount}")
        Text("RTKLIB profiles: ${summary.rtklibProfileCount}")
        Text("Solution policies: ${summary.solutionPolicyProfileCount}")
        Text("Storage locations: ${summary.storageProfileCount}")
        Text("Settings sets: ${summary.settingsSetCount}")
        Text("Selected settings: ${summary.selectedSettingsSetId ?: "n/a"}")
        Text("Selected workflow: ${summary.selectedWorkflowId ?: "n/a"}")
        Text("Last mountpoint: ${summary.lastActiveNtripMountpointProfileId ?: "n/a"}")
    }
}

@Composable
private fun Warning(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFEBEE),
        contentColor = Color(0xFF7F1D1D),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(text, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
