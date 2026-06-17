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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
private val CorrectionsDashboardCardHeight = 226.dp
private val RecordingDashboardCardHeight = 162.dp
private val SetupProfilesDashboardCardHeight = 160.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    state: DashboardState,
    layoutPreference: DashboardLayoutPreference = DashboardLayoutPreference.default,
    startInProgress: Boolean = false,
    onPrimaryAction: () -> Unit,
    onMenu: () -> Unit,
    onNtrip: () -> Unit,
    onUsbPermission: () -> Unit,
    onWorkflow: () -> Unit,
    onSettingsSet: () -> Unit,
    onReceiver: () -> Unit,
    onStorage: () -> Unit,
    onMark: () -> Unit,
    coordinateAveraging: CoordinateAveragingState = CoordinateAveragingState(),
    onStartCoordinateAveraging: (CoordinatePair, Double?) -> Unit = { _, _ -> },
    onStopCoordinateAveraging: () -> Unit = {},
    onUseCurrentCoordinateAsManualBase: (BaseCoordinateCandidate) -> Unit = {},
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
                onMark = onMark,
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
                    )
                } else {
                    CompactDashboard(
                        state = state,
                        status = state.status,
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
private fun BottomActionBar(
    state: DashboardState,
    startInProgress: Boolean,
    onPrimaryAction: () -> Unit,
    onNtrip: () -> Unit,
    onUsbPermission: () -> Unit,
    onMark: () -> Unit,
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
                        action.kind == DashboardActionKind.MARK ||
                        action.kind == DashboardActionKind.USB_PERMISSION
                }
                .forEach { action ->
                    Button(
                        onClick = when (action.kind) {
                            DashboardActionKind.NTRIP -> onNtrip
                            DashboardActionKind.USB_PERMISSION -> onUsbPermission
                            DashboardActionKind.MARK -> onMark
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
            onSettingsSet = onSettingsSet,
            onHelp = onHelp,
            coordinateAveraging = coordinateAveraging,
            onStartCoordinateAveraging = onStartCoordinateAveraging,
            onStopCoordinateAveraging = onStopCoordinateAveraging,
            onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
        )
    }
}

@Composable
private fun RailDashboard(
    state: DashboardState,
    status: DashboardStatus,
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
            onSettingsSet = onSettingsSet,
            onHelp = onHelp,
            modifier = Modifier.weight(1f),
            coordinateAveraging = coordinateAveraging,
            onStartCoordinateAveraging = onStartCoordinateAveraging,
            onStopCoordinateAveraging = onStopCoordinateAveraging,
            onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
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
    modifier: Modifier = Modifier,
    onSettingsSet: () -> Unit,
    onHelp: (HelpTopic) -> Unit,
    coordinateAveraging: CoordinateAveragingState,
    onStartCoordinateAveraging: (CoordinatePair, Double?) -> Unit,
    onStopCoordinateAveraging: () -> Unit,
    onUseCurrentCoordinateAsManualBase: (BaseCoordinateCandidate) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val useTwoColumns = compactDashboardCardColumnCount(maxWidth.value.toInt()) == 2
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
                        FixCard(state = state, onHelp = onHelp)
                        RecordingCard(state = state, onHelp = onHelp)
                    }
                }
            } else {
                PositionCard(
                    state = state,
                    coordinateAveraging = coordinateAveraging,
                    onStartCoordinateAveraging = onStartCoordinateAveraging,
                    onStopCoordinateAveraging = onStopCoordinateAveraging,
                    onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
                    onHelp = onHelp,
                )
                FixCard(state = state, onHelp = onHelp)
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
    coordinateAveraging: CoordinateAveragingState,
    onStartCoordinateAveraging: (CoordinatePair, Double?) -> Unit,
    onStopCoordinateAveraging: () -> Unit,
    onUseCurrentCoordinateAsManualBase: (BaseCoordinateCandidate) -> Unit,
    onHelp: (HelpTopic) -> Unit,
) {
    val coordinates = state.position.coordinatePairOrNull()
    val baseCandidate = coordinateAveraging.averageBaseCandidateOrNull()
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
                averaging = coordinateAveraging,
                onStartCoordinateAveraging = onStartCoordinateAveraging,
                onStopCoordinateAveraging = onStopCoordinateAveraging,
                onUseCurrentCoordinateAsManualBase = onUseCurrentCoordinateAsManualBase,
            )
        }
        Metric("UTC", state.position.utcTime)
        Metric("Ellipsoidal height", state.position.ellipsoidalHeight)
        Metric("Altitude", state.position.altitude)
        DashedSeparator()
        Metric("Latitude error", state.position.latError)
        Metric("Longitude error", state.position.lonError)
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
    onHelp: (HelpTopic) -> Unit,
) {
    DashboardCard(
        title = "Fix",
        cardHeight = FixDashboardCardHeight,
        helpTopic = HelpTopic.SATS_USED_VIEW,
        onHelp = onHelp,
    ) {
        MajorValue(state.fix.fixType)
        Metric("Sats used/view", state.fix.satellites)
        Metric("PDOP", state.fix.pdop)
        Metric("HDOP / VDOP", state.fix.hdopVdop)
        DashedSeparator()
        Metric("H accuracy", state.fix.horizontalAccuracy)
        Metric("V accuracy", state.fix.verticalAccuracy)
        Metric("Diff age", state.fix.differentialAge)
        Metric("Baseline", state.fix.baseline)
        DashedSeparator()
        Metric("PPP", state.fix.pppStatus)
        Metric("RTK", state.fix.rtkStatus)
        Metric("RTKLIB", state.fix.rtklibStatus)
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
        Metric("Inbound rate", state.ntrip.rates)
        Metric("Sent to receiver", state.files.txToReceiverBytes)
        Metric("Correction bytes", state.files.ntripBytes)
        Metric("NTRIP transferred", state.ntrip.transferred)
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
            onWorkflow = {},
            onSettingsSet = {},
            onReceiver = {},
            onStorage = {},
            onMark = {},
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
            onWorkflow = {},
            onSettingsSet = {},
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
            onUsbPermission = {},
            onWorkflow = {},
            onSettingsSet = {},
            onReceiver = {},
            onStorage = {},
            onMark = {},
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
            onWorkflow = {},
            onSettingsSet = {},
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
