package org.rtkcollector.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    state: DashboardState,
    onPrimaryAction: () -> Unit,
    onMenu: () -> Unit,
    onNtrip: () -> Unit,
    onWorkflow: () -> Unit,
    onReceiver: () -> Unit,
    onStorage: () -> Unit,
    onMark: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RtkCollector")
                        Text(
                            text = if (state.isRecording) "Recording" else "Ready",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onMenu) {
                        Text("Menu")
                    }
                },
            )
        },
        bottomBar = {
            BottomActionBar(
                state = state,
                onPrimaryAction = onPrimaryAction,
                onNtrip = onNtrip,
                onMark = onMark,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusStrip(
                status = state.status,
                onWorkflow = onWorkflow,
                onMountpoint = onNtrip,
                onReceiver = onReceiver,
                onStorage = onStorage,
            )
            DashboardCards(state)
        }
    }
}

@Composable
private fun BottomActionBar(
    state: DashboardState,
    onPrimaryAction: () -> Unit,
    onNtrip: () -> Unit,
    onMark: () -> Unit,
) {
    Surface(shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.weight(1f),
            ) {
                Text(state.primaryAction.label, maxLines = 1)
            }
            state.secondaryActions
                .filter { action ->
                    action.kind == DashboardActionKind.NTRIP || action.kind == DashboardActionKind.MARK
                }
                .forEach { action ->
                    TextButton(
                        onClick = when (action.kind) {
                            DashboardActionKind.NTRIP -> onNtrip
                            DashboardActionKind.MARK -> onMark
                            else -> onPrimaryAction
                        },
                    ) {
                        Text(action.label, maxLines = 1)
                    }
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusStrip(
    status: DashboardStatus,
    onWorkflow: () -> Unit,
    onMountpoint: () -> Unit,
    onReceiver: () -> Unit,
    onStorage: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusChip("Workflow", status.workflow, onWorkflow)
        StatusChip("Mountpoint", status.mountpoint, onMountpoint)
        StatusChip("Receiver", status.receiver, onReceiver)
        StatusChip("Storage", status.storage, onStorage)
    }
}

@Composable
private fun StatusChip(label: String, value: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = "$label: $value",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardCards(state: DashboardState) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardCard("Position") {
            MajorValue(state.position.latLon)
            Metric("UTC", state.position.utcTime)
            Metric("Ell. height", state.position.ellipsoidalHeight)
            Metric("Altitude", state.position.altitude)
            Metric("Lat error", state.position.latError)
            Metric("Lon error", state.position.lonError)
        }
        DashboardCard("Fix") {
            MajorValue(state.fix.fixType)
            Metric("Sats", state.fix.satellites)
            Metric("PDOP", state.fix.pdop)
            Metric("HDOP / VDOP", state.fix.hdopVdop)
            Metric("H accuracy", state.fix.horizontalAccuracy)
            Metric("V accuracy", state.fix.verticalAccuracy)
            Metric("Diff age", state.fix.differentialAge)
            Metric("Baseline", state.fix.baseline)
            Metric("PPP", state.fix.pppStatus)
            Metric("RTKLIB", state.fix.rtklibStatus)
        }
        DashboardCard("NTRIP") {
            MajorValue(state.ntrip.status)
            Metric("URL", state.ntrip.url)
            Metric("Transferred", state.ntrip.transferred)
            Metric("Station ID", state.ntrip.stationId)
            Metric("Base lat/lon", state.ntrip.baseLatLon)
            Metric("Rates", state.ntrip.rates)
        }
        DashboardCard("Files") {
            MajorValue(state.files.sessionLocation)
            Metric("RX raw", state.files.receiverRxBytes)
            Metric("TX to receiver", state.files.txToReceiverBytes)
            Metric("NTRIP raw", state.files.ntripBytes)
            Metric("NMEA", state.files.nmeaBytes)
            Metric("ZIP share", if (state.files.zipShareEnabled) "Available" else "After stop")
        }
        DashboardCard("Profiles") {
            MajorValue(state.profiles.settingsSet)
            Metric("Commands", state.profiles.commandProfile)
            Metric("Baud", state.profiles.baudProfile)
            Metric("NTRIP caster", state.profiles.ntripCasterProfile)
            Metric("Recording policy", state.profiles.recordingOutputProfile)
            Metric("Storage", state.profiles.storageLocationProfile)
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.widthIn(min = 230.dp, max = 360.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun MajorValue(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun HomeDashboardPortraitPreview() {
    MaterialTheme {
        HomeDashboard(
            state = previewRunningState(),
            onPrimaryAction = {},
            onMenu = {},
            onNtrip = {},
            onWorkflow = {},
            onReceiver = {},
            onStorage = {},
            onMark = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 820, heightDp = 390)
@Composable
private fun HomeDashboardLandscapePreview() {
    MaterialTheme {
        HomeDashboard(
            state = previewRunningState(),
            onPrimaryAction = {},
            onMenu = {},
            onNtrip = {},
            onWorkflow = {},
            onReceiver = {},
            onStorage = {},
            onMark = {},
        )
    }
}

private fun previewRunningState(): DashboardState =
    DashboardState.running(
        status = DashboardStatus(
            workflow = "Rover + NTRIP",
            mountpoint = "TUBO00CZE0",
            receiver = "UM980",
            storage = "SAF folder",
        ),
        position = PositionCardState(
            latLon = "50.087451234, 14.421253456",
            ellipsoidalHeight = "287.423 m",
            altitude = "243.812 m",
            utcTime = "2026-06-08 15:34:12",
            latError = "8 mm",
            lonError = "7 mm",
        ),
        fix = FixCardState(
            fixType = "RTK float",
            satellites = "18 / 31",
            pdop = "1.2",
            hdopVdop = "0.7 / 1.0",
            horizontalAccuracy = "3 cm",
            verticalAccuracy = "6 cm",
            differentialAge = "0.8 s",
            baseline = "42.8 km",
            pppStatus = "Converging",
            rtklibStatus = "Not configured",
        ),
        ntrip = NtripCardState(
            url = "www.euref-ip.be:2101/TUBO00CZE0",
            status = "Streaming",
            transferred = "2.1 MB",
            stationId = "1234",
            baseLatLon = "49.123456, 14.987654",
            rates = "1.8 / 1.8 kB/s",
        ),
        files = FilesCardState(
            sessionLocation = ".../RtkCollector/2026-06-08",
            receiverRxBytes = "18.4 MB",
            txToReceiverBytes = "2.1 MB",
            ntripBytes = "2.1 MB",
            nmeaBytes = "431 kB",
            zipShareEnabled = false,
        ),
    )
