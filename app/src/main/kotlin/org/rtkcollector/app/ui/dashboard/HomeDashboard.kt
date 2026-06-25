package org.rtkcollector.app.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.R
import org.rtkcollector.app.ui.common.HelpOverlay
import org.rtkcollector.app.ui.common.HelpTopic
import org.rtkcollector.app.ui.common.TidyColors
import org.rtkcollector.app.ui.common.TidyMetricRow
import kotlinx.coroutines.delay

private val CompactSetupTileHeight = 46.dp
private val RailSetupItemHeight = 50.dp
private val DashboardCardHeaderHeight = 22.dp
private val DashboardMajorValueHeight = 36.dp
private val PositionMajorValueHeight = 42.dp
private val DashboardMetricRowHeight = 16.dp
private val DashboardSeparatorHeight = 4.dp
private val PositionDashboardCardHeight = 180.dp
private val FixDashboardCardHeight = 282.dp
private val RtklibDashboardCardHeight = 206.dp
private val SatelliteDashboardCardHeight = 150.dp
private const val SatelliteMonitorCompactFrequencyColumns = 3
private val CorrectionsDashboardCardHeight = 292.dp
private val RecordingDashboardCardHeight = 162.dp
private val SetupProfilesDashboardCardHeight = 160.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    state: DashboardState,
    layoutPreference: DashboardLayoutPreference = DashboardLayoutPreference.default,
    distanceUnitPreference: DashboardDistanceUnitPreference = DashboardDistanceUnitPreference.default,
    satelliteMonitorThemePreference: SatelliteMonitorCardThemePreference = SatelliteMonitorCardThemePreference.default,
    startInProgress: Boolean = false,
    onPrimaryAction: () -> Unit,
    onMenu: () -> Unit,
    onNtrip: () -> Unit,
    onUsbPermission: () -> Unit,
    onMockGps: () -> Unit,
    onWorkflow: () -> Unit,
    onSettingsSet: () -> Unit,
    onReceiver: () -> Unit,
    onStorage: () -> Unit,
    coordinateAveraging: CoordinateAveragingState = CoordinateAveragingState(),
    onStartCoordinateAveraging: (CoordinatePair, Double?) -> Unit = { _, _ -> },
    onStopCoordinateAveraging: () -> Unit = {},
    onUseCurrentCoordinateAsManualBase: (BaseCoordinateCandidate) -> Unit = {},
    onSatelliteMonitorDetails: () -> Unit = {},
) {
    var helpTopic by remember { mutableStateOf<HelpTopic?>(null) }
    val context = LocalContext.current
    val copyErrorToClipboard = {
        state.errorClipboardText()?.let { text ->
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("RtkCollector error", text))
            Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        Unit
    }
    val errorSnapshot = DashboardErrorSnapshot(
        category = state.errorCategory,
        severity = state.errorSeverity,
        message = state.lastError,
    )
    var displayedErrorFingerprint by remember { mutableStateOf<String?>(null) }
    var errorFirstSeenAtMillis by remember { mutableStateOf(0L) }
    var errorNowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val currentErrorFingerprint = errorSnapshot.fingerprint

    LaunchedEffect(currentErrorFingerprint) {
        displayedErrorFingerprint = currentErrorFingerprint
        errorFirstSeenAtMillis = System.currentTimeMillis()
        errorNowMillis = errorFirstSeenAtMillis
    }
    LaunchedEffect(currentErrorFingerprint, errorFirstSeenAtMillis) {
        while (true) {
            delay(1_000)
            errorNowMillis = System.currentTimeMillis()
        }
    }
    val displayedError = if (
        displayedErrorFingerprint == currentErrorFingerprint &&
        errorSnapshot.shouldDisplay(errorNowMillis - errorFirstSeenAtMillis)
    ) {
        errorSnapshot
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        DashboardTitle(state = state)
                    }
                },
                actions = {
                    MockGpsStatusChip(
                        state = state.mockGps,
                        onClick = onMockGps,
                    )
                    RecordingStateBadge(isRecording = state.isRecording, startInProgress = startInProgress)
                    Button(
                        onClick = onMenu,
                        colors = dashboardSecondaryButtonColors(),
                    ) {
                        Text("Menu")
                    }
                },
            )
        },
        bottomBar = {
            BottomActionBar(
                state = state,
                startInProgress = startInProgress,
                onPrimaryAction = onPrimaryAction,
                onNtrip = onNtrip,
                onUsbPermission = onUsbPermission,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRailLayout = shouldUseRailDashboard(
                    layoutPreference = layoutPreference,
                    availableWidthDp = maxWidth.value.toInt(),
                    availableHeightDp = maxHeight.value.toInt(),
                )
                if (useRailLayout) {
                    RailDashboard(
                        state = state,
                        status = state.status,
                        distanceUnitPreference = distanceUnitPreference,
                        satelliteMonitorThemePreference = satelliteMonitorThemePreference,
                        onWorkflow = onWorkflow,
                        onSettingsSet = onSettingsSet,
                        onMountpoint = onNtrip,
                        onReceiver = onReceiver,
                        onStorage = onStorage,
                        onHelp = { helpTopic = it },
                        onCopyError = copyErrorToClipboard,
                        displayedError = displayedError,
                        coordinateAveraging = coordinateAveraging,
                        onStartCoordinateAveraging = onStartCoordinateAveraging,
                        onStopCoordinateAveraging = onStopCoordinateAveraging,
                        onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
                        onSatelliteMonitorDetails = onSatelliteMonitorDetails,
                    )
                } else {
                    CompactDashboard(
                        state = state,
                        status = state.status,
                        distanceUnitPreference = distanceUnitPreference,
                        satelliteMonitorThemePreference = satelliteMonitorThemePreference,
                        availableWidthDp = maxWidth.value.toInt(),
                        availableHeightDp = maxHeight.value.toInt(),
                        onWorkflow = onWorkflow,
                        onSettingsSet = onSettingsSet,
                        onMountpoint = onNtrip,
                        onReceiver = onReceiver,
                        onStorage = onStorage,
                        onHelp = { helpTopic = it },
                        onCopyError = copyErrorToClipboard,
                        displayedError = displayedError,
                        coordinateAveraging = coordinateAveraging,
                        onStartCoordinateAveraging = onStartCoordinateAveraging,
                        onStopCoordinateAveraging = onStopCoordinateAveraging,
                        onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
                        onSatelliteMonitorDetails = onSatelliteMonitorDetails,
                    )
                }
            }
            HelpOverlay(
                topic = helpTopic,
                onDismiss = { helpTopic = null },
            )
        }
    }
}

@Composable
private fun DashboardTitle(state: DashboardState) {
    Column {
        Text(
            text = "RtkCollector",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = dashboardContextLine(state.status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun dashboardContextLine(status: DashboardStatus): String =
    listOf(status.workflow, status.receiver)
        .filterNot { it.isMissingDashboardValue() }
        .joinToString(" · ")
        .ifBlank { "Setup required" }

@Composable
private fun RecordingStateBadge(
    isRecording: Boolean,
    startInProgress: Boolean,
) {
    val label = when {
        isRecording -> "RECORDING"
        startInProgress -> "STARTING"
        else -> "READY"
    }
    val background = when {
        isRecording -> MaterialTheme.colorScheme.primary
        startInProgress -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val foreground = if (isRecording) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.small,
        color = background,
        modifier = Modifier.padding(end = 2.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun MockGpsStatusChip(
    state: MockGpsDashboardState,
    onClick: () -> Unit,
) {
    val background = if (state.enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val foreground = if (state.enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .padding(end = 4.dp)
            .semantics {
                role = Role.Button
                contentDescription = state.label
            }
            .clickable(onClick = onClick),
    ) {
        Text(
            text = state.label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun BottomActionBar(
    state: DashboardState,
    startInProgress: Boolean,
    onPrimaryAction: () -> Unit,
    onNtrip: () -> Unit,
    onUsbPermission: () -> Unit,
) {
    Surface(shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPrimaryAction,
                enabled = !startInProgress,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (startInProgress) "Starting..." else state.primaryAction.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            state.secondaryActions
                .filter { action ->
                    action.kind == DashboardActionKind.NTRIP ||
                        action.kind == DashboardActionKind.USB_PERMISSION
                }
                .forEach { action ->
                    Button(
                        onClick = when (action.kind) {
                            DashboardActionKind.NTRIP -> onNtrip
                            DashboardActionKind.USB_PERMISSION -> onUsbPermission
                            else -> onPrimaryAction
                        },
                        colors = dashboardSecondaryButtonColors(),
                    ) {
                        Text(
                            text = action.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
        }
    }
}

@Composable
private fun dashboardSecondaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun CompactDashboard(
    state: DashboardState,
    status: DashboardStatus,
    distanceUnitPreference: DashboardDistanceUnitPreference,
    satelliteMonitorThemePreference: SatelliteMonitorCardThemePreference,
    availableWidthDp: Int,
    availableHeightDp: Int,
    onWorkflow: () -> Unit,
    onSettingsSet: () -> Unit,
    onMountpoint: () -> Unit,
    onReceiver: () -> Unit,
    onStorage: () -> Unit,
    onHelp: (HelpTopic) -> Unit,
    onCopyError: () -> Unit,
    displayedError: DashboardErrorSnapshot?,
    coordinateAveraging: CoordinateAveragingState,
    onStartCoordinateAveraging: (CoordinatePair, Double?) -> Unit,
    onStopCoordinateAveraging: () -> Unit,
    onUseCurrentCoordinateAsManualBase: (BaseCoordinateCandidate) -> Unit,
    onSatelliteMonitorDetails: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SetupStrip(
            status = status,
            onWorkflow = onWorkflow,
            onMountpoint = onMountpoint,
            onReceiver = onReceiver,
            onStorage = onStorage,
        )
        ErrorStrip(snapshot = displayedError, onCopy = onCopyError)
        DashboardCards(
            state = state,
            distanceUnitPreference = distanceUnitPreference,
            satelliteMonitorThemePreference = satelliteMonitorThemePreference,
            availableWidthDp = availableWidthDp,
            availableHeightDp = availableHeightDp,
            onSettingsSet = onSettingsSet,
            onHelp = onHelp,
            coordinateAveraging = coordinateAveraging,
            onStartCoordinateAveraging = onStartCoordinateAveraging,
            onStopCoordinateAveraging = onStopCoordinateAveraging,
            onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
            onSatelliteMonitorDetails = onSatelliteMonitorDetails,
        )
    }
}

@Composable
private fun RailDashboard(
    state: DashboardState,
    status: DashboardStatus,
    distanceUnitPreference: DashboardDistanceUnitPreference,
    satelliteMonitorThemePreference: SatelliteMonitorCardThemePreference,
    onWorkflow: () -> Unit,
    onSettingsSet: () -> Unit,
    onMountpoint: () -> Unit,
    onReceiver: () -> Unit,
    onStorage: () -> Unit,
    onHelp: (HelpTopic) -> Unit,
    onCopyError: () -> Unit,
    displayedError: DashboardErrorSnapshot?,
    coordinateAveraging: CoordinateAveragingState,
    onStartCoordinateAveraging: (CoordinatePair, Double?) -> Unit,
    onStopCoordinateAveraging: () -> Unit,
    onUseCurrentCoordinateAsManualBase: (BaseCoordinateCandidate) -> Unit,
    onSatelliteMonitorDetails: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 138.dp, max = 168.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Setup",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = dashboardContextLine(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ErrorStrip(snapshot = displayedError, onCopy = onCopyError)
                defaultDashboardSetupItems.forEach { item ->
                    SetupRailItem(
                        label = item.label,
                        value = status.valueFor(item),
                        active = item == DashboardSetupItem.WORKFLOW,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = when (item) {
                            DashboardSetupItem.WORKFLOW -> onWorkflow
                            DashboardSetupItem.MOUNTPOINT -> onMountpoint
                            DashboardSetupItem.RECEIVER -> onReceiver
                            DashboardSetupItem.STORAGE -> onStorage
                        },
                    )
                }
            }
        }
        DashboardCards(
            state = state,
            distanceUnitPreference = distanceUnitPreference,
            satelliteMonitorThemePreference = satelliteMonitorThemePreference,
            onSettingsSet = onSettingsSet,
            onHelp = onHelp,
            modifier = Modifier.weight(1f),
            coordinateAveraging = coordinateAveraging,
            onStartCoordinateAveraging = onStartCoordinateAveraging,
            onStopCoordinateAveraging = onStopCoordinateAveraging,
            onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
            onSatelliteMonitorDetails = onSatelliteMonitorDetails,
        )
    }
}

@Composable
private fun ErrorStrip(
    snapshot: DashboardErrorSnapshot?,
    onCopy: () -> Unit,
) {
    val text = snapshot?.message?.takeIf { it.isNotBlank() }?.let { "${snapshot.category}: $it" } ?: return
    Surface(
        color = TidyColors.MissingBackground,
        contentColor = TidyColors.MissingText,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, TidyColors.MissingText),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SetupStrip(
    status: DashboardStatus,
    onWorkflow: () -> Unit,
    onMountpoint: () -> Unit,
    onReceiver: () -> Unit,
    onStorage: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = compactDashboardCardColumnCount(maxWidth.value.toInt())
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            defaultDashboardSetupItems.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowItems.forEach { item ->
                        SetupTile(
                            label = item.label,
                            value = status.valueFor(item),
                            modifier = Modifier.weight(1f),
                            onClick = when (item) {
                                DashboardSetupItem.WORKFLOW -> onWorkflow
                                DashboardSetupItem.MOUNTPOINT -> onMountpoint
                                DashboardSetupItem.RECEIVER -> onReceiver
                                DashboardSetupItem.STORAGE -> onStorage
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun DashboardStatus.valueFor(item: DashboardSetupItem): String =
    when (item) {
        DashboardSetupItem.WORKFLOW -> workflow
        DashboardSetupItem.MOUNTPOINT -> mountpoint
        DashboardSetupItem.RECEIVER -> receiver
        DashboardSetupItem.STORAGE -> storage
    }

@Composable
private fun SetupTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val missing = value.isMissingDashboardValue()
    val background = if (missing) TidyColors.MissingBackground else MaterialTheme.colorScheme.surfaceContainerLow
    val foreground = if (missing) TidyColors.MissingText else MaterialTheme.colorScheme.onSurface
    val border = if (missing) TidyColors.MissingText else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = modifier
            .height(CompactSetupTileHeight)
            .semantics {
                role = Role.Button
                contentDescription = "$label: $value"
            }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (missing) TidyColors.MissingText else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SetupRailItem(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val missing = value.isMissingDashboardValue()
    val background = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val foreground = if (missing) TidyColors.MissingText else MaterialTheme.colorScheme.onSurface
    val border = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = modifier
            .height(RailSetupItemHeight)
            .semantics {
                role = Role.Button
                contentDescription = "$label: $value"
            }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
            Column(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (missing) TidyColors.MissingText else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = foreground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun String.isMissingDashboardValue(): Boolean {
    val normalized = trim()
    return normalized.isBlank() ||
        normalized.equals("n/a", ignoreCase = true) ||
        normalized.startsWith("Select ", ignoreCase = true) ||
        normalized.equals("Not selected", ignoreCase = true)
}

@Composable
private fun DashboardCards(
    state: DashboardState,
    distanceUnitPreference: DashboardDistanceUnitPreference,
    satelliteMonitorThemePreference: SatelliteMonitorCardThemePreference,
    modifier: Modifier = Modifier,
    availableWidthDp: Int? = null,
    availableHeightDp: Int? = null,
    onSettingsSet: () -> Unit,
    onHelp: (HelpTopic) -> Unit,
    coordinateAveraging: CoordinateAveragingState,
    onStartCoordinateAveraging: (CoordinatePair, Double?) -> Unit,
    onStopCoordinateAveraging: () -> Unit,
    onUseCurrentCoordinateAsManualBase: (BaseCoordinateCandidate) -> Unit,
    onSatelliteMonitorDetails: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val useTwoColumns = compactDashboardCardColumnCount(
            availableWidthDp = availableWidthDp ?: maxWidth.value.toInt(),
            availableHeightDp = availableHeightDp,
        ) == 2
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (useTwoColumns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PositionCard(
                            state = state,
                            distanceUnitPreference = distanceUnitPreference,
                            coordinateAveraging = coordinateAveraging,
                            onStartCoordinateAveraging = onStartCoordinateAveraging,
                            onStopCoordinateAveraging = onStopCoordinateAveraging,
                            onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
                            onHelp = onHelp,
                        )
                        CorrectionsCard(state = state, onHelp = onHelp)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FixCard(state = state, distanceUnitPreference = distanceUnitPreference, onHelp = onHelp)
                        if (state.isRecording) {
                            SatelliteMonitorCard(
                                state = state.satelliteMonitor,
                                themePreference = satelliteMonitorThemePreference,
                                onHelp = onHelp,
                                onOpenDetails = onSatelliteMonitorDetails,
                            )
                        }
                        state.rtklib?.let { RtklibCard(it, distanceUnitPreference) }
                        RecordingCard(state = state, onHelp = onHelp)
                    }
                }
            } else {
                PositionCard(
                    state = state,
                    distanceUnitPreference = distanceUnitPreference,
                    coordinateAveraging = coordinateAveraging,
                    onStartCoordinateAveraging = onStartCoordinateAveraging,
                    onStopCoordinateAveraging = onStopCoordinateAveraging,
                    onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
                    onHelp = onHelp,
                )
                FixCard(state = state, distanceUnitPreference = distanceUnitPreference, onHelp = onHelp)
                if (state.isRecording) {
                    SatelliteMonitorCard(
                        state = state.satelliteMonitor,
                        themePreference = satelliteMonitorThemePreference,
                        onHelp = onHelp,
                        onOpenDetails = onSatelliteMonitorDetails,
                    )
                }
                state.rtklib?.let { RtklibCard(it, distanceUnitPreference) }
                CorrectionsCard(state = state, onHelp = onHelp)
                RecordingCard(state = state, onHelp = onHelp)
            }
            SetupProfilesCard(state = state, onSettingsSet = onSettingsSet)
        }
    }
}

@Composable
private fun PositionCard(
    state: DashboardState,
    distanceUnitPreference: DashboardDistanceUnitPreference,
    coordinateAveraging: CoordinateAveragingState,
    onStartCoordinateAveraging: (CoordinatePair, Double?) -> Unit,
    onStopCoordinateAveraging: () -> Unit,
    onUseCurrentCoordinateAsManualBase: (BaseCoordinateCandidate) -> Unit,
    onHelp: (HelpTopic) -> Unit,
) {
    val coordinates = state.position.coordinatePairOrNull()
    val serviceAveraging = state.position.serviceCoordinateAveragingState()
    val effectiveAveraging = if (
        serviceAveraging.active ||
        serviceAveraging.sampleCount > 0 ||
        serviceAveraging.stoppedReason != null
    ) {
        serviceAveraging
    } else {
        coordinateAveraging
    }
    val baseCandidate = effectiveAveraging.averageBaseCandidateOrNull()
        ?: state.position.baseCoordinateCandidateOrNull()
    val ellipsoidalHeightM = state.position.ellipsoidalHeightMetersOrNull()
    val context = LocalContext.current
    var showCopyDialog by remember { mutableStateOf(false) }
    val baseControlsVisible = state.status.workflow.isBaseCoordinateWorkflow()
    DashboardCard(
        title = "Position",
        cardHeight = PositionDashboardCardHeight,
        helpTopic = HelpTopic.COORDINATE_ACTIONS,
        onHelp = onHelp,
    ) {
        PositionMajorValue(
            position = state.position,
            onClick = {
                if (coordinates != null) {
                    showCopyDialog = true
                } else {
                    Toast.makeText(context, "No coordinate available to copy.", Toast.LENGTH_SHORT).show()
                }
            },
        )
        if (baseControlsVisible) {
            CoordinateActionRow(
                coordinates = coordinates,
                baseCandidate = baseCandidate,
                ellipsoidalHeightM = ellipsoidalHeightM,
                averaging = effectiveAveraging,
                onStartCoordinateAveraging = onStartCoordinateAveraging,
                onStopCoordinateAveraging = onStopCoordinateAveraging,
                onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
            )
        }
        Metric("UTC", state.position.utcTime)
        Metric("Ellipsoidal height", state.position.ellipsoidalHeight)
        Metric("Altitude", state.position.altitude)
        DashedSeparator()
        Metric("Err lat", formatDashboardDistance(state.position.latError, distanceUnitPreference))
        Metric("Err lon", formatDashboardDistance(state.position.lonError, distanceUnitPreference))
    }
    if (showCopyDialog && coordinates != null) {
        CoordinateCopyDialog(
            coordinates = coordinates,
            onDismiss = { showCopyDialog = false },
        )
    }
}

@Composable
private fun PositionMajorValue(
    position: PositionCardState,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .height(PositionMajorValueHeight)
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "Position coordinates"
            }
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
    ) {
        position.latLonLinesForNarrowLayout().forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CoordinateActionRow(
    coordinates: CoordinatePair?,
    baseCandidate: BaseCoordinateCandidate?,
    ellipsoidalHeightM: Double?,
    averaging: CoordinateAveragingState,
    onStartCoordinateAveraging: (CoordinatePair, Double?) -> Unit,
    onStopCoordinateAveraging: () -> Unit,
    onUseCurrentCoordinateAsManualBase: (BaseCoordinateCandidate) -> Unit,
) {
    val enabled = coordinates != null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactCoordinateButton(
            label = "Base",
            enabled = baseCandidate != null,
            onClick = {
                baseCandidate?.let(onUseCurrentCoordinateAsManualBase)
            },
        )
        CompactCoordinateButton(
            label = if (averaging.active) "Stop" else "Avg",
            enabled = enabled || averaging.active,
            onClick = {
                if (averaging.active) {
                    onStopCoordinateAveraging()
                } else {
                    coordinates?.let { onStartCoordinateAveraging(it, ellipsoidalHeightM) }
                }
            },
        )
        Text(
            text = averaging.statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompactCoordinateButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun CoordinateCopyDialog(
    coordinates: CoordinatePair,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    fun copy(format: CoordinateCopyFormat) {
        val text = format.format(coordinates)
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("RtkCollector coordinate", text))
        Toast.makeText(context, "Coordinate copied.", Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy coordinate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CoordinateCopyFormat.entries.forEach { format ->
                    TextButton(onClick = { copy(format) }) {
                        Text(format.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun String.isBaseCoordinateWorkflow(): Boolean {
    val normalized = lowercase()
    return normalized.contains("temporary base") || normalized.contains("fixed base")
}

@Composable
private fun FixCard(
    state: DashboardState,
    distanceUnitPreference: DashboardDistanceUnitPreference,
    onHelp: (HelpTopic) -> Unit,
) {
    DashboardCard(
        title = "Fix",
        cardHeight = FixDashboardCardHeight,
        helpTopic = HelpTopic.SATS_USED_VIEW,
        onHelp = onHelp,
    ) {
        MajorValue(state.fix.fixType)
        Metric("Sats used / in view", state.fix.satellites)
        Metric("PDOP", state.fix.pdop)
        Metric("HDOP / VDOP", state.fix.hdopVdop)
        DashedSeparator()
        Metric(
            "Acc H/V",
            "${formatDashboardDistance(state.fix.horizontalAccuracy, distanceUnitPreference)} / " +
                formatDashboardDistance(state.fix.verticalAccuracy, distanceUnitPreference),
        )
        Metric("Diff age", state.fix.differentialAge)
        Metric("Baseline", state.fix.baseline)
        DashedSeparator()
        Metric("PPP", state.fix.pppStatus)
        Metric("RTK", state.fix.rtkStatus)
        Metric("Best", state.fix.bestSolution)
        Metric("Mock loc", state.fix.mockLocation)
        DashedSeparator()
        Metric("Mode", state.fix.receiverMode)
        Text(
            text = state.fix.receiverFrequency,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RtklibCard(
    state: RtklibCardState,
    distanceUnitPreference: DashboardDistanceUnitPreference,
) {
    DashboardCard(
        title = "RTKLIB",
        cardHeight = RtklibDashboardCardHeight,
        helpTopic = null,
        onHelp = {},
    ) {
        MajorValue(state.state)
        Metric("Fix", state.fixClass)
        Metric("Solution age", state.age)
        Metric("Lat/Lon", state.latLon)
        Metric("Ell h", state.ellipsoidalHeight)
        Metric("Acc H/V", formatDashboardDistancePair(state.accuracyHv, distanceUnitPreference))
        Metric("Route", state.routePlan)
        Metric("Snapshot", state.snapshotId)
        if (!state.lastError.equals("n/a", ignoreCase = true)) {
            Metric("Error", state.lastError)
        }
        DashedSeparator()
        Metric("Queue rover/corr", "${state.roverQueue} / ${state.correctionQueue}")
        Metric("Dropped rover/corr", state.dropped)
        Metric("Decoded", state.decoded)
        Metric("Output", state.outputs)
    }
}

@Composable
private fun SatelliteMonitorCard(
    state: SatelliteMonitorDashboardState,
    themePreference: SatelliteMonitorCardThemePreference,
    onHelp: (HelpTopic) -> Unit,
    onOpenDetails: () -> Unit,
) {
    val colors = satelliteMonitorCardColors(themePreference)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SatelliteDashboardCardHeight)
            .semantics {
                role = Role.Button
                contentDescription = "Open satellite monitor details"
            }
            .clickable(onClick = onOpenDetails),
        colors = CardDefaults.cardColors(containerColor = colors.background),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(DashboardCardHeaderHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Satellites",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.engineLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SatelliteMonitorInfoIcon(
                    colors = colors,
                    onClick = { onHelp(HelpTopic.SATELLITE_MONITOR) },
                )
            }
            SatelliteMonitorSourceDots(state.sources, colors)
            if (state.hasFrequencyGroups) {
                state.constellations.forEach { constellation ->
                    SatelliteConstellationGroupRow(
                        group = constellation,
                        colors = colors,
                    )
                }
            } else {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SatelliteMonitorInfoIcon(
    colors: SatelliteMonitorCardColors,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(24.dp)
            .height(18.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Satellite monitor help"
            }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = colors.infoBackground,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "(i)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.mutedText,
            )
        }
    }
}

@Composable
private fun SatelliteMonitorSourceDots(
    sources: SatelliteMonitorSourceStatuses,
    colors: SatelliteMonitorCardColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SatelliteMonitorSourceDot(sources.rover, colors)
        SatelliteMonitorSourceDot(sources.base, colors)
        SatelliteMonitorSourceDot(sources.solution, colors)
    }
}

@Composable
private fun SatelliteMonitorSourceDot(
    source: SatelliteMonitorSourceStatus,
    colors: SatelliteMonitorCardColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = source.label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Surface(
            modifier = Modifier.size(7.dp),
            shape = MaterialTheme.shapes.small,
            color = colors.dotColor(source.freshness),
            border = BorderStroke(1.dp, colors.border),
            content = {},
        )
    }
}

@Composable
private fun SatelliteConstellationGroupRow(
    group: SatelliteMonitorConstellationGroup,
    colors: SatelliteMonitorCardColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = group.label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        group.frequencyRows(SatelliteMonitorCompactFrequencyColumns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { frequency ->
                    SatelliteFrequencyBox(
                        frequency = frequency,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(SatelliteMonitorCompactFrequencyColumns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SatelliteFrequencyBox(
    frequency: SatelliteMonitorFrequencyRow,
    colors: SatelliteMonitorCardColors,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.frequencyBackground,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = frequency.bandLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SatelliteSignalCountRow(
                sourceLabel = "R",
                count = frequency.rover,
                visibleColor = colors.roverVisible,
                usedColor = colors.roverUsed,
                colors = colors,
            )
            SatelliteSignalCountRow(
                sourceLabel = "B",
                count = frequency.base,
                visibleColor = colors.baseVisible,
                usedColor = colors.baseUsed,
                colors = colors,
            )
        }
    }
}

@Composable
private fun SatelliteSignalCountRow(
    sourceLabel: String,
    count: SatelliteMonitorSignalCount,
    visibleColor: Color,
    usedColor: Color,
    colors: SatelliteMonitorCardColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = sourceLabel,
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        SatelliteBoxedBar(
            count = count,
            visibleColor = visibleColor,
            usedColor = usedColor,
            emptyColor = colors.emptySegment,
            borderColor = colors.segmentBorder,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.displayValue,
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.bodyText,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun SatelliteBoxedBar(
    count: SatelliteMonitorSignalCount,
    visibleColor: Color,
    usedColor: Color,
    emptyColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        count.boxedSegments(totalSegments = SatelliteMonitorBoxedBarSegments).forEach { segment ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = MaterialTheme.shapes.small,
                color = when (segment) {
                    SatelliteMonitorBarSegment.USED -> usedColor
                    SatelliteMonitorBarSegment.VISIBLE -> visibleColor
                    SatelliteMonitorBarSegment.EMPTY -> emptyColor
                },
                border = BorderStroke(1.dp, borderColor),
                content = {},
            )
        }
    }
}

@Composable
private fun CorrectionsCard(
    state: DashboardState,
    onHelp: (HelpTopic) -> Unit,
) {
    DashboardCard(
        title = "Corrections",
        cardHeight = CorrectionsDashboardCardHeight,
        helpTopic = HelpTopic.NTRIP_URL,
        onHelp = onHelp,
    ) {
        MajorValue(state.ntrip.status)
        Metric("Caster / mountpoint", state.ntrip.url)
        Metric("Mountpoint", state.status.mountpoint)
        Metric("Station ID", state.ntrip.stationId)
        Metric("Base position", state.ntrip.baseLatLon)
        DashedSeparator()
        Metric("Last update", state.ntrip.lastUpdated)
        Metric("Inbound rate", state.ntrip.rates)
        Metric("NTRIP received", state.ntrip.transferred)
        Metric("Saved corrections", state.files.ntripBytes)
        Metric("Forwarded to receiver", state.files.txToReceiverBytes)
        if (!state.ntrip.uploadUrl.equals("n/a", ignoreCase = true)) {
            DashedSeparator()
            Metric("Upload", state.ntrip.uploadStatus)
            Metric("Upload URL", state.ntrip.uploadUrl)
            Metric("Uploaded", state.ntrip.uploadBytes)
            Metric("Dropped upload", state.ntrip.uploadDroppedBytes)
            state.ntrip.uploadLastError?.takeIf { it.isNotBlank() }?.let {
                Metric("Upload error", it)
            }
        }
    }
}

private const val SatelliteMonitorBoxedBarSegments = 10

private data class SatelliteMonitorCardColors(
    val background: Color,
    val frequencyBackground: Color,
    val infoBackground: Color,
    val border: Color,
    val segmentBorder: Color,
    val emptySegment: Color,
    val title: Color,
    val bodyText: Color,
    val mutedText: Color,
    val roverVisible: Color,
    val roverUsed: Color,
    val baseVisible: Color,
    val baseUsed: Color,
    val freshDot: Color,
    val staleDot: Color,
    val unavailableDot: Color,
) {
    fun dotColor(freshness: SatelliteMonitorSourceFreshness): Color =
        when (freshness) {
            SatelliteMonitorSourceFreshness.FRESH -> freshDot
            SatelliteMonitorSourceFreshness.STALE -> staleDot
            SatelliteMonitorSourceFreshness.UNAVAILABLE -> unavailableDot
        }
}

@Composable
private fun satelliteMonitorCardColors(
    preference: SatelliteMonitorCardThemePreference,
): SatelliteMonitorCardColors {
    val rover = Color(0xFF2F7FD8)
    val base = Color(0xFFE6B73D)
    return when (preference) {
        SatelliteMonitorCardThemePreference.LIGHT -> SatelliteMonitorCardColors(
            background = MaterialTheme.colorScheme.surfaceContainerLow,
            frequencyBackground = MaterialTheme.colorScheme.surface,
            infoBackground = MaterialTheme.colorScheme.surface,
            border = MaterialTheme.colorScheme.outlineVariant,
            segmentBorder = MaterialTheme.colorScheme.outlineVariant,
            emptySegment = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.48f),
            title = MaterialTheme.colorScheme.primary,
            bodyText = MaterialTheme.colorScheme.onSurface,
            mutedText = MaterialTheme.colorScheme.onSurfaceVariant,
            roverVisible = rover.copy(alpha = 0.32f),
            roverUsed = rover,
            baseVisible = base.copy(alpha = 0.38f),
            baseUsed = base,
            freshDot = Color(0xFF2E7D32),
            staleDot = Color(0xFFE0A100),
            unavailableDot = MaterialTheme.colorScheme.outline,
        )
        SatelliteMonitorCardThemePreference.DARK -> SatelliteMonitorCardColors(
            background = Color(0xFF111827),
            frequencyBackground = Color(0xFF172235),
            infoBackground = Color(0xFF101827),
            border = Color(0xFF2C3B53),
            segmentBorder = Color(0xFF26344A),
            emptySegment = Color(0xFF202B3D),
            title = Color(0xFF9CC9FF),
            bodyText = Color(0xFFE5EDF8),
            mutedText = Color(0xFFAAB7C8),
            roverVisible = Color(0xFF2F7FD8).copy(alpha = 0.42f),
            roverUsed = Color(0xFF5AA9FF),
            baseVisible = Color(0xFFE6B73D).copy(alpha = 0.44f),
            baseUsed = Color(0xFFFFD45A),
            freshDot = Color(0xFF65D46E),
            staleDot = Color(0xFFFFC64D),
            unavailableDot = Color(0xFF64748B),
        )
    }
}

@Composable
private fun RecordingCard(
    state: DashboardState,
    onHelp: (HelpTopic) -> Unit,
) {
    DashboardCard(
        title = "Recording",
        cardHeight = RecordingDashboardCardHeight,
        helpTopic = HelpTopic.TX_TO_RECEIVER,
        onHelp = onHelp,
    ) {
        MajorValue(state.files.sessionLocation)
        Metric("Session total", state.files.sessionTotalBytes)
        Metric("receiver-rx.raw", state.files.receiverRxBytes)
        Metric("TX to receiver", state.files.txToReceiverBytes)
        Metric("correction-input.raw", state.files.ntripBytes)
        Metric("NMEA export", state.files.nmeaBytes)
        Metric("ZIP share", state.files.zipShareLabel)
    }
}

@Composable
private fun SetupProfilesCard(
    state: DashboardState,
    onSettingsSet: () -> Unit,
) {
    DashboardCard(
        title = "Setup profiles",
        cardHeight = SetupProfilesDashboardCardHeight,
        helpTopic = null,
        onHelp = {},
    ) {
        ClickableMetric("Settings set", state.profiles.settingsSet, onClick = onSettingsSet)
        Metric("Command profile", state.profiles.commandProfile)
        Metric("Baud", state.profiles.baudProfile)
        Metric("NTRIP caster", state.profiles.ntripCasterProfile)
        Metric("Recording policy", state.profiles.recordingOutputProfile)
        Metric("Storage profile", state.profiles.storageLocationProfile)
    }
}

@Composable
private fun DashboardCard(
    title: String,
    cardHeight: Dp,
    helpTopic: HelpTopic?,
    onHelp: (HelpTopic) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = cardHeight),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(DashboardCardHeaderHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (helpTopic != null) {
                    CompactHelpIcon(
                        contentDescription = "$title help",
                        onClick = { onHelp(helpTopic) },
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun CompactHelpIcon(
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(18.dp)
            .height(18.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "i",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MajorValue(value: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .height(DashboardMajorValueHeight),
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Metric(label: String, value: String) {
    TidyMetricRow(
        label = label,
        value = value,
        modifier = Modifier.heightIn(min = DashboardMetricRowHeight),
    )
}

@Composable
private fun ClickableMetric(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    TidyMetricRow(
        label = label,
        value = value,
        modifier = Modifier
            .heightIn(min = DashboardMetricRowHeight)
            .semantics {
                role = Role.Button
                contentDescription = "$label: $value"
            }
            .clickable(onClick = onClick),
    )
}

@Composable
private fun DashedSeparator() {
    val color = TidyColors.Divider
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(DashboardSeparatorHeight),
    ) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
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
            onUsbPermission = {},
            onMockGps = {},
            onWorkflow = {},
            onSettingsSet = {},
            onReceiver = {},
            onStorage = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun HomeDashboardReadyMissingPreview() {
    MaterialTheme {
        HomeDashboard(
            state = previewReadyMissingState(),
            onPrimaryAction = {},
            onMenu = {},
            onNtrip = {},
            onUsbPermission = {},
            onMockGps = {},
            onWorkflow = {},
            onSettingsSet = {},
            onReceiver = {},
            onStorage = {},
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
            onUsbPermission = {},
            onMockGps = {},
            onWorkflow = {},
            onSettingsSet = {},
            onReceiver = {},
            onStorage = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 820, heightDp = 390)
@Composable
private fun HomeDashboardRailPreview() {
    MaterialTheme {
        HomeDashboard(
            state = previewRunningState(),
            layoutPreference = DashboardLayoutPreference.RAIL,
            onPrimaryAction = {},
            onMenu = {},
            onNtrip = {},
            onUsbPermission = {},
            onMockGps = {},
            onWorkflow = {},
            onSettingsSet = {},
            onReceiver = {},
            onStorage = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun HomeDashboardSatelliteDarkPreview() {
    MaterialTheme {
        HomeDashboard(
            state = previewRunningState(),
            satelliteMonitorThemePreference = SatelliteMonitorCardThemePreference.DARK,
            onPrimaryAction = {},
            onMenu = {},
            onNtrip = {},
            onUsbPermission = {},
            onMockGps = {},
            onWorkflow = {},
            onSettingsSet = {},
            onReceiver = {},
            onStorage = {},
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
            rtkStatus = "RTK float",
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
        satelliteMonitor = SatelliteMonitorDashboardState.preview(),
    )

private fun previewReadyMissingState(): DashboardState =
    DashboardState.planned(
        workflow = "n/a",
        mountpoint = "n/a",
        receiver = "n/a",
        storage = "Storage location profile",
        position = PositionCardState(
            latLon = "n/a",
            ellipsoidalHeight = "n/a",
            altitude = "n/a",
            utcTime = "n/a",
            latError = "n/a",
            lonError = "n/a",
        ),
        fix = FixCardState(
            fixType = "Not recording",
            satellites = "0 / 0",
            pdop = "n/a",
            hdopVdop = "n/a",
            horizontalAccuracy = "n/a",
            verticalAccuracy = "n/a",
            differentialAge = "n/a",
            baseline = "n/a",
            pppStatus = "n/a",
            rtkStatus = "n/a",
            rtklibStatus = "Not configured",
        ),
        ntrip = NtripCardState(
            url = "n/a",
            status = "Disconnected",
            transferred = "0 B",
            stationId = "n/a",
            baseLatLon = "n/a",
            rates = "0 / 0 B/s",
        ),
        files = FilesCardState(
            sessionLocation = "n/a",
            receiverRxBytes = "0 B",
            txToReceiverBytes = "0 B",
            ntripBytes = "0 B",
            nmeaBytes = "0 B",
            zipShareEnabled = false,
        ),
    )
