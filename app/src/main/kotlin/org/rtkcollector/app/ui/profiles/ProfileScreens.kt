package org.rtkcollector.app.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NtripMountpointScreen(
    initialState: NtripMountpointEditorState,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf(initialState) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("NTRIP mountpoint", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.mountpointText,
            onValueChange = { state = state.copy(mountpointText = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mountpoint") },
            singleLine = true,
        )
        Button(
            onClick = { state = state.withFetchedMountpoints(listOf("TUBO00CZE0", "DRES00DEU0")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Load sample fetched list")
        }
        state.availableMountpoints.forEach { mountpoint ->
            Button(
                onClick = { state = state.selectMountpoint(mountpoint) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(mountpoint)
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
fun SimpleSettingsScreen(title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text("Profile editor screen for $title")
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
