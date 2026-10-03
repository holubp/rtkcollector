package org.rtkcollector.app.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.PendingIntent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.console.DeviceConsoleController
import org.rtkcollector.app.console.DeviceConsoleLineEnding
import org.rtkcollector.app.console.DeviceConsoleState
import org.rtkcollector.app.diagnostics.DiagnosticCategory
import org.rtkcollector.app.diagnostics.DiagnosticsSettings
import org.rtkcollector.app.diagnostics.DiagnosticsStore
import org.rtkcollector.app.diagnostics.DiagnosticsZipExporter
import org.rtkcollector.app.diagnostics.RuntimeDiagnosticRecord
import org.rtkcollector.app.diagnostics.RuntimeDiagnostics
import org.rtkcollector.app.diagnostics.recordSessionTaskFailure
import org.rtkcollector.app.base.AcceptedBaseCoordinate
import org.rtkcollector.app.base.AcceptedBaseCoordinateStore
import org.rtkcollector.app.base.BaseCoordinateForm
import org.rtkcollector.app.base.BasePositionJsonCodec
import org.rtkcollector.app.base.FixedBaseCommandValidator
import org.rtkcollector.app.base.FixedBaseCommandProfileSelection
import org.rtkcollector.app.base.FixedBaseHandoffPlanner
import org.rtkcollector.app.base.FixedBaseCommandAction
import org.rtkcollector.app.base.FixedBaseSettingsSetAction
import org.rtkcollector.app.base.FixedBaseSettingsSetCandidate
import org.rtkcollector.app.base.FixedBaseProfileMaterializer
import org.rtkcollector.app.permissions.batteryOptimisationWarning
import org.rtkcollector.app.permissions.runtimePermissionsRequiredBeforeRecording
import org.rtkcollector.app.profile.ActiveRecordingConfig
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.NtripCasterUploadProfile
import org.rtkcollector.app.profile.NtripCasterUploadRetryMode
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.NtripMountpointOverride
import org.rtkcollector.app.profile.ProfileDeviceFilter
import org.rtkcollector.app.profile.ProfileStores
import org.rtkcollector.app.profile.storageValue
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.PreferenceCommitException
import org.rtkcollector.app.profile.PreferenceRollbackStatus
import org.rtkcollector.app.profile.RecordingPolicyProfile
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.RetainedSettingsProfileIds
import org.rtkcollector.app.profile.RtklibProfile
import org.rtkcollector.app.profile.SatelliteTelemetryCapability
import org.rtkcollector.app.profile.SettingsBackupFile
import org.rtkcollector.app.profile.SettingsBackupProfileFamily
import org.rtkcollector.app.profile.SettingsImportValidationResult
import org.rtkcollector.app.profile.SettingsSetExportOptions
import org.rtkcollector.app.profile.SolutionPolicyProfile
import org.rtkcollector.app.profile.StorageProfile
import org.rtkcollector.app.profile.UsbBaudProfile
import org.rtkcollector.app.profile.WorkflowApplicationPolicy
import org.rtkcollector.app.profile.WorkflowActivationMode
import org.rtkcollector.app.profile.canChangeActiveSetup
import org.rtkcollector.app.profile.displayMountpoint
import org.rtkcollector.app.profile.effectiveBaseCoordinateId
import org.rtkcollector.app.profile.effectiveBaseCasterUploadEnabled
import org.rtkcollector.app.profile.effectiveCommandProfileRef
import org.rtkcollector.app.profile.effectiveNtripCasterProfileRef
import org.rtkcollector.app.profile.effectiveNtripCasterUploadProfileRef
import org.rtkcollector.app.profile.effectiveNtripMountpointProfileRef
import org.rtkcollector.app.profile.effectiveRecordingOutputProfileRef
import org.rtkcollector.app.profile.effectiveStorageProfileRef
import org.rtkcollector.app.profile.ActiveSetup
import org.rtkcollector.app.profile.ActiveSetupProfileGraph
import org.rtkcollector.app.profile.ActiveSetupResolver
import org.rtkcollector.app.profile.ActiveSetupSelections
import org.rtkcollector.app.profile.hasPersistedSafTreeAuthority
import org.rtkcollector.app.profile.withEditedCommandPhases
import org.rtkcollector.app.profile.SelectionChoice
import org.rtkcollector.app.profile.UploadSelection
import org.rtkcollector.app.profile.ConfigurationReadiness
import org.rtkcollector.app.profile.correctionCasterRestrictionRef
import org.rtkcollector.app.ui.profiles.settingsSetSelectionPolicyFields
import org.rtkcollector.app.ui.profiles.withSettingsSetSelectionPolicies
import org.rtkcollector.app.recording.RecordingSetupBridge
import org.rtkcollector.app.recording.RunningSetupSnapshot
import org.rtkcollector.app.recording.SetupPatchRequest
import org.rtkcollector.app.profile.isOptionLocked
import org.rtkcollector.app.profile.ntripCasterUploadSecretId
import org.rtkcollector.app.profile.ntripCasterSecretId
import org.rtkcollector.app.profile.ntripTlsVerificationFromStorage
import org.rtkcollector.app.profile.readSettingsImportText
import org.rtkcollector.app.profile.renameProfile
import org.rtkcollector.app.profile.requireProfileReference
import org.rtkcollector.app.profile.reapplied
import org.rtkcollector.app.profile.sanitizedForPersistedSafWriteAccess
import org.rtkcollector.app.profile.settingsBackupImportPlan
import org.rtkcollector.app.profile.validateSettingsImportJson
import org.rtkcollector.app.profile.withWorkflowActivationMode
import org.rtkcollector.app.profile.withAcceptedBaseCoordinate
import org.rtkcollector.app.profile.workflowActivationMode
import org.rtkcollector.app.profile.workflowIdAfterSettingsSetActivation
import org.rtkcollector.app.profile.workflowIdForActiveSetup
import org.rtkcollector.core.solution.SolutionSourcePolicy
import org.rtkcollector.app.receiver.PersistentReceiverWriteRoute
import org.rtkcollector.app.receiver.isPlausibleUm980MaintenanceResponse
import org.rtkcollector.app.receiver.isUm980CommandOkResponse
import org.rtkcollector.app.receiver.persistentBaudCommands
import org.rtkcollector.app.receiver.persistentReceiverCommands
import org.rtkcollector.app.receiver.persistentReceiverWriteRoute
import org.rtkcollector.app.receiver.um980VersionProbeBytes
import org.rtkcollector.app.secrets.NtripSecretStore
import org.rtkcollector.app.recording.RecordingForegroundService
import org.rtkcollector.app.recording.ReceiverNmeaReexporter
import org.rtkcollector.app.recording.RtklibSessionPostprocessor
import org.rtkcollector.app.recording.SessionNmeaExporter
import org.rtkcollector.app.recording.SessionNmeaShareSelection
import org.rtkcollector.app.recording.SessionNmeaSource
import org.rtkcollector.app.sessions.FilesystemSessionBrowser
import org.rtkcollector.app.sessions.ActiveRecordingSessionRegistry
import org.rtkcollector.app.sessions.SafSessionActions
import org.rtkcollector.app.sessions.SafSessionBrowser
import org.rtkcollector.app.sessions.SessionArchiveManager
import org.rtkcollector.app.sessions.SessionBrowserEntry
import org.rtkcollector.app.sessions.SessionBrowserState
import org.rtkcollector.app.sessions.SessionEntryKind
import org.rtkcollector.app.sessions.sessionBrowserStateOf
import org.rtkcollector.app.usb.AndroidUsbSerialTransport
import org.rtkcollector.app.usb.UsbSerialOpenOptions
import org.rtkcollector.app.ui.dashboard.DashboardState
import org.rtkcollector.app.ui.dashboard.DashboardStatus
import org.rtkcollector.app.ui.dashboard.DashboardLayoutPreference
import org.rtkcollector.app.ui.dashboard.DashboardUiPreferences
import org.rtkcollector.app.ui.dashboard.DashboardDistanceUnitPreference
import org.rtkcollector.app.ui.dashboard.DashboardSetupItem
import org.rtkcollector.app.ui.dashboard.SatelliteMonitorCardThemePreference
import org.rtkcollector.app.ui.dashboard.BaseCoordinateCandidate
import org.rtkcollector.app.ui.dashboard.CasterUploadCardState
import org.rtkcollector.app.ui.dashboard.CasterUploadMonitorScreen
import org.rtkcollector.app.ui.dashboard.CoordinatePair
import org.rtkcollector.app.ui.dashboard.FixCardState
import org.rtkcollector.app.ui.dashboard.ProfilesCardState
import org.rtkcollector.app.ui.dashboard.RtklibCardState
import org.rtkcollector.app.ui.dashboard.HomeDashboard
import org.rtkcollector.app.ui.dashboard.MockGpsDashboardState
import org.rtkcollector.app.ui.dashboard.baseCoordinateCandidateOrNull
import org.rtkcollector.app.ui.dashboard.coordinatePairOrNull
import org.rtkcollector.app.ui.dashboard.dashboardUploadSelectorRows
import org.rtkcollector.app.ui.dashboard.UploadSelectorOffProfileId
import org.rtkcollector.app.ui.dashboard.dashboardStateFromRecordingIntent
import org.rtkcollector.app.ui.dashboard.dashboardStorageConfigurationResolved
import org.rtkcollector.app.ui.dashboard.fixedDashboardSetupItems
import org.rtkcollector.app.ui.dashboard.formatBytes
import org.rtkcollector.app.ui.dashboard.isNtripCasterUploadProfileSelected
import org.rtkcollector.app.ui.dashboard.isMissingDashboardValue
import org.rtkcollector.app.ui.dashboard.receiverFrequencyForFamily
import org.rtkcollector.app.ui.dashboard.serviceCoordinateAveragingState
import org.rtkcollector.app.ui.console.DeviceConsoleOption
import org.rtkcollector.app.ui.console.DeviceConsoleScreen
import org.rtkcollector.app.ui.common.ProfileSingleLineTextField
import org.rtkcollector.app.ui.diagnostics.AppDiagnosticsScreen
import org.rtkcollector.app.ui.profiles.SettingsSetListScreen
import org.rtkcollector.app.ui.profiles.SettingsSetListState
import org.rtkcollector.app.ui.profiles.stageNtripCredentialEdit
import org.rtkcollector.app.ui.profiles.settingsSetLockFields
import org.rtkcollector.app.ui.profiles.withSettingsSetLockSelections
import org.rtkcollector.app.ui.profiles.NtripMountpointEditorState
import org.rtkcollector.app.ui.profiles.NtripMountpointScreen
import org.rtkcollector.app.ui.profiles.LivePatchOwner
import org.rtkcollector.app.ui.profiles.LiveSetupOwner
import org.rtkcollector.app.ui.profiles.LiveSetupReceipt
import org.rtkcollector.app.ui.profiles.LiveSetupRequest
import org.rtkcollector.app.ui.profiles.LiveSetupRetryDecision
import org.rtkcollector.app.ui.profiles.mayPublishLivePatchSelection
import org.rtkcollector.app.ui.profiles.planLiveSetupRetry
import org.rtkcollector.app.ui.profiles.uploadSelectionLabel
import org.rtkcollector.app.ui.profiles.EditableProfileField
import org.rtkcollector.app.ui.profiles.EditableProfileOption
import org.rtkcollector.app.ui.profiles.ProfileEditorAction
import org.rtkcollector.app.ui.profiles.ProfileEditorData
import org.rtkcollector.app.ui.profiles.ProfileEditorScreen
import org.rtkcollector.app.ui.profiles.ProfileListScreen
import org.rtkcollector.app.ui.profiles.ProfileListRow
import org.rtkcollector.app.ui.profiles.ProfileSelectorDialog
import org.rtkcollector.app.ui.profiles.RefreshNtripCasterMountpointsLabel
import org.rtkcollector.app.ui.profiles.SuspectInvalidMountpointWarning
import org.rtkcollector.app.ui.profiles.ntripSecurityEditorFields
import org.rtkcollector.app.ui.profiles.persistentBaudWriteAction
import org.rtkcollector.app.ui.profiles.persistentReceiverWriteAction
import org.rtkcollector.app.ui.profiles.profileDeleteActionLabel
import org.rtkcollector.app.ui.imports.SettingsImportScreen
import org.rtkcollector.app.ui.imports.settingsImportUriFromIntent
import org.rtkcollector.app.ui.sessions.SessionsScreen
import org.rtkcollector.app.ui.settings.SettingsHub
import org.rtkcollector.app.ui.satellite.SatelliteMonitorScreen
import org.rtkcollector.app.ui.usb.UsbDeviceChoice
import org.rtkcollector.app.ui.usb.UsbStartAccessAction
import org.rtkcollector.app.ui.usb.UsbStartAccessDecision
import org.rtkcollector.receiver.unicore.Um980PersistentBaudPlan
import org.rtkcollector.receiver.unicore.Um980PersistentBaudStep
import org.rtkcollector.receiver.unicore.Um980NmeaExportOptions
import org.rtkcollector.receiver.unicore.Um980RuntimeCommandValidator
import org.rtkcollector.core.correction.NtripCredentials
import org.rtkcollector.core.correction.NtripSourcetableClient
import org.rtkcollector.core.correction.NtripSourcetableRequest
import org.rtkcollector.core.correction.NtripTlsVerification
import org.rtkcollector.core.correction.NtripTransportMode
import org.rtkcollector.core.rtklib.RtklibNativeBridge
import org.rtkcollector.core.rtklib.RtklibPostprocessMode
import org.rtkcollector.app.share.isSettingsBackupCacheFile
import org.rtkcollector.app.share.settingsBackupFileName
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

private const val TEMP_SHARE_ZIP_CLEANUP_DELAY_MILLIS = 60L * 60L * 1000L
private const val SETTINGS_BACKUP_SHARE_CLEANUP_DELAY_MILLIS = 5L * 60L * 1000L
private const val SETTINGS_BACKUP_CACHE_TTL_MILLIS = 30L * 60L * 1000L
private const val PERSISTENT_RECEIVER_COMMAND_DELAY_MILLIS = 100L
private const val PERSISTENT_RECEIVER_SAVE_OK_TIMEOUT_MILLIS = 3_000L
private const val DEVICE_CONSOLE_PERMISSION_REQUIRED = "USB permission is required before opening the device console."
private val persistentReceiverWriteInProgress = AtomicBoolean(false)

internal fun tryStartRecordingServiceStateQuery(startService: () -> Unit): Boolean =
    try {
        startService()
        true
    } catch (_: IllegalStateException) {
        false
    }

class MainActivity : ComponentActivity() {
    private var latestImportIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestImportIntent = intent
        setContent {
            RtkCollectorApp(
                externalIntent = latestImportIntent,
                onExternalIntentConsumed = {
                    latestImportIntent = null
                    setIntent(Intent(Intent.ACTION_MAIN))
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestImportIntent = intent
    }
}

private data class PendingSettingsImport(
    val source: String,
    val result: SettingsImportValidationResult,
    val returnScreen: AppScreen,
)

private data class SettingsImportOutcome(
    val safTreeUriReselectionCount: Int,
    val secretStoreOutcome: SecretStoreImportOutcome,
)

internal enum class SecretStoreImportOutcome(
    val userMessage: String?,
) {
    STORED(null),
    PRIMARY_COMMIT_FAILED_ROLLBACK_SUCCEEDED(
        "NTRIP passwords could not be stored securely; previous password values were restored. Enter them again.",
    ),
    PRIMARY_AND_ROLLBACK_FAILED(
        "NTRIP passwords could not be stored securely; persisted password state may be inconsistent. Check and enter them again.",
    ),
    FAILED("NTRIP passwords could not be stored securely; enter them again."),
}

internal fun classifySecretStoreImportFailure(error: Throwable): SecretStoreImportOutcome =
    when (error.preferenceCommitFailure()?.rollbackStatus) {
        PreferenceRollbackStatus.FAILED ->
            SecretStoreImportOutcome.PRIMARY_AND_ROLLBACK_FAILED
        PreferenceRollbackStatus.RESTORED ->
            SecretStoreImportOutcome.PRIMARY_COMMIT_FAILED_ROLLBACK_SUCCEEDED
        null -> SecretStoreImportOutcome.FAILED
    }

private fun Throwable.preferenceCommitFailure(): PreferenceCommitException? =
    generateSequence(this) { it.cause }
        .filterIsInstance<PreferenceCommitException>()
        .firstOrNull()

private data class PendingStorageFolderSelection(
    val target: ProfileEditorTarget,
    val values: Map<String, String>,
)

private data class PendingSetupSelection(
    val request: SetupPatchRequest,
    val settingsSetId: String,
    val option: ActiveSetupOptionKey,
    val selections: ActiveSetupSelections,
    val targetLabel: String,
    val intent: Intent,
    val retry: (SetupPatchRequest) -> Intent,
    val canPublishSelection: () -> Boolean,
    val failure: String? = null,
)

@Composable
fun RtkCollectorApp(
    externalIntent: Intent? = null,
    onExternalIntentConsumed: () -> Unit = {},
) {
    var screen by rememberSaveable(stateSaver = AppScreenSaver) { mutableStateOf(AppScreen.HOME) }
    var sessionBrowserEntryPoint by rememberSaveable(stateSaver = SessionBrowserEntryPointSaver) {
        mutableStateOf(SessionBrowserEntryPoint.SETTINGS)
    }
    val context = LocalContext.current
    val diagnosticsSettings = remember(context) { DiagnosticsSettings(context) }
    val diagnosticsStore = remember(context) { DiagnosticsStore(context.filesDir) }
    val diagnosticsZipExporter = remember(context) { DiagnosticsZipExporter(context.cacheDir) }
    val runtimeDiagnostics = remember(context) {
        RuntimeDiagnostics(diagnosticsStore) { diagnosticsSettings.runtimeLoggingEnabled }
    }
    var diagnosticsRevision by remember { mutableStateOf(0) }
    var diagnosticsError by remember { mutableStateOf<String?>(null) }
    val profileStore = remember(context) { ProfileStores(context) }
    val secretStore = remember(context) { NtripSecretStore(context) }
    var configurationRecoveryAttempt by remember { mutableStateOf(0) }
    val configurationReadiness = remember(profileStore, configurationRecoveryAttempt) {
        if (ActiveRecordingSessionRegistry.isAnyActive()) {
            runCatching { profileStore.requireConfigurationRecovered(); ConfigurationReadiness.Ready }
                .getOrElse { ConfigurationReadiness.Blocked(ConfigurationReadiness.Blocked.Reason.RECOVERY,
                    "Stored settings recovery could not complete. Recording remains independent.") }
        } else profileStore.initializeOwnershipWhileIdle(secretStore)
    }
    if (configurationReadiness is ConfigurationReadiness.Blocked) {
        MaterialTheme {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("RtkCollector", style = MaterialTheme.typography.titleLarge)
                Text(configurationReadiness.message, color = MaterialTheme.colorScheme.error)
                if (ActiveRecordingSessionRegistry.isAnyActive() || RecordingSetupBridge.current() != null) {
                    Button(onClick = { context.startService(RecordingForegroundService.stopIntent(context)) }) {
                        Text("Stop recording")
                    }
                }
                Button(onClick = { configurationRecoveryAttempt++ }) { Text("Retry settings recovery") }
            }
        }
        return
    }
    val dashboardUiPreferences = remember(context) { DashboardUiPreferences(context) }
    val baseCoordinateStore = remember(context) { AcceptedBaseCoordinateStore(context) }
    val initialSelectedSettingsSetId = remember(profileStore) { profileStore.selectedSettingsSetId() }
    var settingsSets by remember {
        mutableStateOf(profileStore.settingsSets())
    }
    var selectedSettingsSetId by remember { mutableStateOf(initialSelectedSettingsSetId) }
    var profileEditorTarget by rememberSaveable(stateSaver = ProfileEditorTargetSaver) {
        mutableStateOf<ProfileEditorTarget?>(null)
    }
    var dashboardSelector by rememberSaveable(stateSaver = DashboardSelectorSaver) {
        mutableStateOf<DashboardSelector?>(null)
    }
    var dashboardLayout by rememberSaveable(stateSaver = DashboardLayoutPreferenceSaver) {
        mutableStateOf(DashboardLayoutPreference.default)
    }
    var dashboardDistanceUnits by rememberSaveable(stateSaver = DashboardDistanceUnitPreferenceSaver) {
        mutableStateOf(DashboardDistanceUnitPreference.default)
    }
    var satelliteMonitorCardTheme by rememberSaveable(stateSaver = SatelliteMonitorCardThemePreferenceSaver) {
        mutableStateOf(SatelliteMonitorCardThemePreference.default)
    }
    var dashboardSetupExpanded by remember {
        mutableStateOf(dashboardUiPreferences.setupExpanded())
    }
    var showDashboardLayoutDialog by remember { mutableStateOf(false) }
    var showMockGpsDialog by remember { mutableStateOf(false) }
    var showActiveChoicesDialog by remember { mutableStateOf(false) }
    var activeOptionSelector by remember { mutableStateOf<ActiveSetupOptionKey?>(null) }
    var pendingSetupSelection by remember { mutableStateOf<PendingSetupSelection?>(null) }
    var activeAttemptSettingsSetId by rememberSaveable {
        mutableStateOf(RecordingSetupBridge.current()?.settingsSetId)
    }
    var pendingSharedEdit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSharedEditDescription by remember { mutableStateOf("") }
    var showSettingsExportDialog by remember { mutableStateOf(false) }
    var includePlaintextPasswordsInBackup by remember { mutableStateOf(false) }
    var zipProgressText by remember { mutableStateOf<String?>(null) }
    var sessionProgressFraction by remember { mutableStateOf<Float?>(null) }
    var pendingSettingsImport by remember { mutableStateOf<PendingSettingsImport?>(null) }
    var pendingStorageFolderSelection by remember { mutableStateOf<PendingStorageFolderSelection?>(null) }
    var settingsImportRequestId by remember { mutableStateOf(0) }
    val settingsImportScope = rememberCoroutineScope()
    var sessionBrowserState by remember { mutableStateOf(SessionBrowserState()) }
    var pendingNmeaSources by remember { mutableStateOf<List<SessionNmeaSource>>(emptyList()) }
    var pendingNmeaEntries by remember { mutableStateOf<List<SessionBrowserEntry>>(emptyList()) }
    var pendingRtklibPostprocessEntries by remember { mutableStateOf<List<SessionBrowserEntry>>(emptyList()) }
    var sessionTaskErrorDetail by remember { mutableStateOf<String?>(null) }
    var profileRevision by remember { mutableStateOf(0) }
    var consoleState by remember { mutableStateOf(DeviceConsoleState()) }
    var consoleInput by rememberSaveable { mutableStateOf("") }
    var selectedConsoleUsbProfileId by rememberSaveable {
        mutableStateOf(profileStore.usbBaudProfiles().firstOrNull()?.id)
    }
    var selectedConsoleCommandProfileId by rememberSaveable {
        mutableStateOf(profileStore.commandProfiles().firstOrNull()?.id)
    }
    var consoleLineEnding by rememberSaveable { mutableStateOf(DeviceConsoleLineEnding.CRLF) }
    var consoleController by remember { mutableStateOf<DeviceConsoleController?>(null) }
    var baseCoordinateEditorId by rememberSaveable { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_VARIABLE")
    val currentProfileRevision = profileRevision
    val usbDeviceChoices = remember(currentProfileRevision) { context.currentUsbDeviceChoices() }
    var selectedWorkflowId by remember {
        val initialWorkflowId = settingsSets.firstOrNull { it.id == selectedSettingsSetId }?.let {
            profileStore.resolvedActiveSetup(it, profileStore.selectedWorkflowId())
                .option(ActiveSetupOptionKey.WORKFLOW).effectiveValueId
        }
        mutableStateOf(initialWorkflowId)
    }
    var selectedDeviceFilter by rememberSaveable {
        mutableStateOf(profileStore.selectedDeviceFilter())
    }
    var plannedState by remember {
        mutableStateOf(profileStore.plannedDashboardState(settingsSets, selectedSettingsSetId, selectedWorkflowId,
            baseCoordinateStore.coordinates()))
    }
    var state by remember { mutableStateOf(plannedState) }
    val latestState = rememberUpdatedState(state)
    var startInProgress by rememberSaveable { mutableStateOf(false) }
    var startingLocks by remember { mutableStateOf<DashboardStatus?>(null) }
    var pendingStartAfterNotificationPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    var manualBaseCoordinate by remember { mutableStateOf<BaseCoordinateCandidate?>(null) }
    var pendingFixedBaseCoordinateChoices by remember { mutableStateOf<FixedBaseCoordinateChoices?>(null) }
    var pendingFixedBaseCoordinate by remember { mutableStateOf<AcceptedBaseCoordinate?>(null) }
    var pendingFixedBasePublication by remember { mutableStateOf<org.rtkcollector.app.base.FixedBaseHandoffPlan?>(null) }
    var pendingFixedBaseSettingsSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFixedBaseCommandAction by remember { mutableStateOf(FixedBaseCommandAction.UPDATE_EXISTING) }
    var fixedBaseProfileSelectionMode by remember { mutableStateOf<FixedBaseProfileSelectionMode?>(null) }
    var showReapplySettingsDialog by remember { mutableStateOf(false) }
    fun stageSettingsImport(uri: Uri) {
        val requestId = settingsImportRequestId + 1
        val returnScreen = screen
        settingsImportRequestId = requestId
        pendingSettingsImport = PendingSettingsImport(
            source = uri.toString(),
            result = SettingsImportValidationResult.Loading,
            returnScreen = returnScreen,
        )
        settingsImportScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    validateSettingsImportJson(readSettingsImportText(context.contentResolver, uri))
                        .sanitizedForPersistedSafWriteAccess(
                            persistedSafTreeUrisWithWriteAccess = context.persistedSafTreeUrisWithWriteAccess(),
                            retainedProfileIds = profileStore.retainedSettingsProfileIds(),
                        )
                }.getOrElse { error ->
                    SettingsImportValidationResult.Invalid(error.message ?: "Settings backup could not be read.")
                }
            }
            if (settingsImportRequestId == requestId) {
                pendingSettingsImport = PendingSettingsImport(
                    source = uri.toString(),
                    result = result,
                    returnScreen = returnScreen,
                )
            }
        }
    }
    LaunchedEffect(externalIntent) {
        if (externalIntent != null) {
            settingsImportUriFromIntent(externalIntent)?.let { uri ->
                stageSettingsImport(uri)
            }
            onExternalIntentConsumed()
        }
    }
    fun refreshSessions() {
        sessionBrowserState = buildSessionBrowserState(
            context = context,
            dashboardState = state,
            profileStore = profileStore,
            settingsSets = settingsSets,
            selectedSettingsSetId = selectedSettingsSetId,
        )
    }
    fun openSessions(entryPoint: SessionBrowserEntryPoint) {
        sessionBrowserEntryPoint = entryPoint
        refreshSessions()
        screen = AppScreen.SESSIONS
    }
    fun runSessionTask(label: String, category: DiagnosticCategory = DiagnosticCategory.APP, task: () -> Unit) {
        zipProgressText = "$label..."
        sessionProgressFraction = null
        Thread {
            runCatching(task)
                .onSuccess {
                    runOnMain(context) {
                        zipProgressText = null
                        sessionProgressFraction = null
                        refreshSessions()
                    }
                }
                .onFailure { error ->
                    runOnMain(context) {
                        zipProgressText = null
                        sessionProgressFraction = null
                        refreshSessions()
                        val detail = "$label failed: ${error.message ?: error::class.java.name}\n${error.stackTraceToString()}"
                        sessionTaskErrorDetail = detail
                        recordSessionTaskFailure(
                            store = diagnosticsStore,
                            enabled = diagnosticsSettings.runtimeLoggingEnabled,
                            label = label,
                            category = category,
                            error = error,
                        )
                        Toast.makeText(context, "$label failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }.start()
    }
    fun runRtklibPostprocess(entries: List<SessionBrowserEntry>, mode: RtklibPostprocessMode) {
        val selected = entries.filter { it.canRegenerateRtklib && !it.isSafLocation }
        if (selected.isEmpty()) {
            Toast.makeText(context, "Select at least one filesystem-backed completed recording.", Toast.LENGTH_LONG).show()
            return
        }
        val modeLabel = when (mode) {
            RtklibPostprocessMode.FORWARD -> "forward"
            RtklibPostprocessMode.FORWARD_BACKWARD -> "forward/backward"
        }
        runSessionTask("Regenerate RTKLIB", DiagnosticCategory.RTKLIB) {
            val backend = RtklibNativeBridge()
            selected.forEachIndexed { index, entry ->
                runOnMain(context) {
                    zipProgressText = "RTKLIB ${index + 1}/${selected.size}: $modeLabel"
                    sessionProgressFraction = index.toFloat() / selected.size.toFloat()
                }
                val result = RtklibSessionPostprocessor.postprocessFilesystemSession(
                    sessionDirectory = Paths.get(entry.location),
                    mode = mode,
                    backend = backend,
                )
                if (!result.success) {
                    error(result.message ?: "RTKLIB postprocess failed for ${entry.title}.")
                }
            }
        }
    }
    fun refreshProfileUi(updatedSettingsSets: List<RecordingSettingsSet> = settingsSets) {
        settingsSets = updatedSettingsSets
        val planned = profileStore.plannedDashboardState(updatedSettingsSets, selectedSettingsSetId, selectedWorkflowId,
            baseCoordinateStore.coordinates())
        plannedState = planned
        state = state.withPlannedConfiguration(planned)
        profileRevision++
    }
    fun confirmSharedEdit(label: String, affected: List<RecordingSettingsSet>, apply: () -> Unit) {
        if (affected.isEmpty()) apply() else {
            pendingSharedEditDescription = "$label affects next Start for: ${affected.joinToString { it.name }}. " +
                "An existing recording keeps its accepted configuration."
            pendingSharedEdit = apply
        }
    }
    fun coordinateUsers(id: String): List<RecordingSettingsSet> = settingsSets.filter { set ->
        set.basePositionProfileRef?.id == id ||
            profileStore.activeSelections(set).let { choices ->
                listOf(choices.activeChoices, choices.rememberedChoices, choices.transientChoices)
                    .any { it[ActiveSetupOptionKey.BASE_COORDINATE]?.profileId == id }
            }
    }
    fun profileUsers(target: ProfileEditorTarget): List<RecordingSettingsSet> = settingsSets.filter { set ->
        if (target.kind == ProfileKind.SETTINGS_SET) false else {
            val setup = profileStore.resolvedActiveSetup(set, selectedWorkflowId)
            val key = when (target.kind) {
                ProfileKind.COMMANDS -> ActiveSetupOptionKey.RECEIVER_COMMAND
                ProfileKind.USB_BAUD -> ActiveSetupOptionKey.USB_BAUD
                ProfileKind.NTRIP_CASTER -> ActiveSetupOptionKey.NTRIP_CASTER
                ProfileKind.NTRIP_MOUNTPOINT -> ActiveSetupOptionKey.NTRIP_MOUNTPOINT
                ProfileKind.NTRIP_CASTER_UPLOAD -> ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD
                ProfileKind.RECORDING_OUTPUTS -> ActiveSetupOptionKey.RECORDING_OUTPUT
                ProfileKind.RTKLIB -> ActiveSetupOptionKey.RTKLIB
                ProfileKind.SOLUTION_POLICY -> ActiveSetupOptionKey.SOLUTION_POLICY
                ProfileKind.STORAGE -> ActiveSetupOptionKey.STORAGE
                ProfileKind.SETTINGS_SET -> error("Settings sets are not shared profile contents.")
            }
            set.let { listOf(it).referenceProfile(target.kind, target.id) } ||
                setup.snapshot().profileId(key) == target.id ||
                profileStore.activeSelections(set).let { choices ->
                    listOf(choices.activeChoices, choices.rememberedChoices, choices.transientChoices)
                        .any { it[key]?.profileId == target.id }
                } ||
                (key == ActiveSetupOptionKey.NTRIP_CASTER && set.ntripMountpointProfileRef?.id?.let { id ->
                    profileStore.ntripMountpointProfiles().firstOrNull { it.id == id }?.casterProfileId
                } == target.id)
        }
    }
    fun chooseActiveOption(key: ActiveSetupOptionKey, id: String?) {
        val set = requireNotNull(settingsSets.firstOrNull { it.id == selectedSettingsSetId }) {
            "Selected settings set is missing."
        }
        val choice = id?.let(SelectionChoice::profile) ?: SelectionChoice.none()
        profileStore.saveActiveSelections(profileStore.activeSelections(set).choose(set, key, choice))
        if (key == ActiveSetupOptionKey.WORKFLOW) selectedWorkflowId = id
        refreshProfileUi()
    }
    fun chooseActiveUpload(id: String?) {
        val set = requireNotNull(settingsSets.firstOrNull { it.id == selectedSettingsSetId })
        val previous = profileStore.activeSelections(set)
        val retained = previous.uploadSelection?.profile ?: set.ntripCasterUploadProfileRef?.id
            ?.let(SelectionChoice::profile) ?: SelectionChoice.none()
        profileStore.saveActiveSelections(previous.chooseUpload(set, UploadSelection(
            enabled = id != null,
            profile = id?.let(SelectionChoice::profile) ?: retained,
        )))
        refreshProfileUi()
    }
    fun activateSettingsSet(id: String) {
        val set = requireNotNull(settingsSets.firstOrNull { it.id == id }) { "Settings set is missing." }
        val choices = profileStore.activeSelections(set)
        if (set.workflowApplicationPolicy == org.rtkcollector.app.profile.WorkflowApplicationPolicy.LEAVE_INTACT) {
            profileStore.saveActiveSelections(choices.copy(workflowBaselineId = selectedWorkflowId))
        }
        selectedSettingsSetId = id
        profileStore.saveSelectedSettingsSetId(id)
        selectedWorkflowId = profileStore.resolvedActiveSetup(set, selectedWorkflowId)
            .option(ActiveSetupOptionKey.WORKFLOW).effectiveValueId
        manualBaseCoordinate = null
        refreshProfileUi()
    }
    fun rejectFixedOption(vararg keys: ActiveSetupOptionKey): Boolean {
        val set = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
        val running = RecordingSetupBridge.current().takeIf { state.isRecording }
        val fixedForRecording = state.isRecording && (running == null || keys.any { it in running.lockedOptions })
        val fixedForNextStart = !state.isRecording && set != null && keys.any(set::isOptionLocked)
        if (!fixedForRecording && !fixedForNextStart) return false
        val message = if (fixedForRecording) {
            "Fixed for this recording. Stop recording to change it."
        } else {
            "Fixed by settings set. Copy or edit the set to change it."
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        return true
    }
    fun selectNtripMountpoint(profile: NtripMountpointProfile) {
        if (rejectFixedOption(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)) return
        runCatching {
            val selectedSet = requireNotNull(settingsSets.firstOrNull { it.id == selectedSettingsSetId }) {
                "Selected settings set was not found."
            }
            val caster = requireNotNull(profileStore.ntripCasterProfiles().singleOrNull { it.id == profile.casterProfileId }) {
                "Selected source's caster profile is missing."
            }
            val choices = profileStore.activeSelections(selectedSet)
                .choose(selectedSet, ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SelectionChoice.profile(profile.id))
            if (state.isRecording) {
                require(pendingSetupSelection == null) { "Wait for the pending change or discard it before selecting another source." }
                val running = requireNotNull(RecordingSetupBridge.current()) { "Recording configuration is unavailable; refresh and retry." }
                require(running.settingsSetId == selectedSet.id) { "The recording belongs to another settings set." }
                val request = SetupPatchRequest(java.util.UUID.randomUUID().toString(), running.sessionId,
                    running.revision, profileStore.selectionRevision(selectedSet.id))
                val expectedOwner = LivePatchOwner(profile.id, profile.toJson().toString(), caster.id, caster.toJson().toString())
                val currentOwner = {
                    val currentSource = profileStore.ntripMountpointProfiles().singleOrNull { it.id == profile.id }
                    val currentCaster = profileStore.ntripCasterProfiles().singleOrNull { it.id == caster.id }
                    if (currentSource == null || currentCaster == null) null else LivePatchOwner(
                        currentSource.id, currentSource.toJson().toString(),
                        currentCaster.id, currentCaster.toJson().toString(),
                    )
                }
                val retry: (SetupPatchRequest) -> Intent = { attempt ->
                    val password = caster.secretId.takeIf(String::isNotBlank)?.let(secretStore::getPassword)
                    val token = RecordingSetupBridge.stageSourceUpdate(attempt, selectedSet.id, profile, caster, password)
                    Intent(context, RecordingForegroundService::class.java)
                        .setAction(RecordingForegroundService.ACTION_UPDATE_NTRIP)
                        .putExtra(RecordingForegroundService.EXTRA_SETUP_TOKEN, token)
                }
                val selectionStillOwned = {
                    mayPublishLivePatchSelection(
                        expectedOwner, currentOwner(), request.selectionRevision,
                        profileStore.selectionRevision(selectedSet.id),
                    )
                }
                val canPublishAtDispatch = selectionStillOwned()
                val intent = retry(request)
                pendingSetupSelection = PendingSetupSelection(
                    request = request,
                    settingsSetId = selectedSet.id,
                    option = ActiveSetupOptionKey.NTRIP_MOUNTPOINT,
                    selections = choices,
                    targetLabel = profile.name,
                    intent = intent,
                    retry = retry,
                    canPublishSelection = { canPublishAtDispatch && selectionStillOwned() },
                )
                context.startService(intent)
            } else {
                profileStore.saveActiveSelections(choices)
                profileStore.saveLastActiveNtripMountpointProfileId(profile.id)
                refreshProfileUi()
            }
        }.onSuccess {
        }.onFailure { error ->
            val message = if (error is IllegalArgumentException) error.message ?: "Cannot select NTRIP mountpoint."
                else "Source selection could not be applied. Check its profile and credentials."
            pendingSetupSelection = pendingSetupSelection?.copy(failure = message)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    fun reapplySelectedSettingsSet() {
        val set = settingsSets.firstOrNull { it.id == selectedSettingsSetId } ?: return
        profileStore.resetActiveSelections(set, selectedWorkflowId)
        selectedWorkflowId = profileStore.resolvedActiveSetup(set, selectedWorkflowId)
            .option(ActiveSetupOptionKey.WORKFLOW).effectiveValueId
        manualBaseCoordinate = null
        refreshProfileUi()
    }
    fun selectedCommandProfileOrToast(): CommandProfile? {
        val selectedSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
        if (selectedSet == null) {
            Toast.makeText(context, "Selected settings set was not found.", Toast.LENGTH_LONG).show()
            return null
        }
        val profile = profileStore.commandProfiles().firstOrNull {
            it.id == profileStore.activeProfileId(selectedSet, ActiveSetupOptionKey.RECEIVER_COMMAND)
        }
        if (profile == null) {
            Toast.makeText(context, "Selected command profile was not found.", Toast.LENGTH_LONG).show()
        }
        return profile
    }
    fun resetFixedBaseHandoff() {
        pendingFixedBasePublication = null
        pendingFixedBaseCoordinateChoices = null
        pendingFixedBaseCoordinate = null
        pendingFixedBaseSettingsSetId = null
        pendingFixedBaseCommandAction = FixedBaseCommandAction.UPDATE_EXISTING
        fixedBaseProfileSelectionMode = null
    }
    fun commandUsageIds(set: RecordingSettingsSet): Set<String> {
        val choices = profileStore.activeSelections(set)
        return listOfNotNull(
            set.commandProfileRef.id,
            choices.activeChoices[ActiveSetupOptionKey.RECEIVER_COMMAND]?.profileId,
            choices.rememberedChoices[ActiveSetupOptionKey.RECEIVER_COMMAND]?.profileId,
            choices.transientChoices[ActiveSetupOptionKey.RECEIVER_COMMAND]?.profileId,
            profileStore.activeProfileId(set, ActiveSetupOptionKey.RECEIVER_COMMAND),
        ).toSet()
    }
    fun fixedBaseCandidates(): List<FixedBaseSettingsSetCandidate> =
        FixedBaseHandoffPlanner.eligibleSettingsSets(
            settingsSets = settingsSets,
            commandProfiles = profileStore.commandProfiles(),
            filter = selectedDeviceFilter,
            effectiveCommandId = { set ->
                profileStore.activeProfileId(set, ActiveSetupOptionKey.RECEIVER_COMMAND).orEmpty()
            },
            commandUsageIds = ::commandUsageIds,
        )
    fun beginFixedBaseHandoff(candidate: BaseCoordinateCandidate) {
        val acceptedCoordinate = candidate.toAcceptedBaseCoordinate(
            id = profileStore.duplicateId("base"),
            name = if (candidate.source == "AVERAGE") {
                "Temporary base average"
            } else {
                "Temporary base instant"
            },
        )
        if (acceptedCoordinate == null) {
            Toast.makeText(
                context,
                "Base candidate requires a valid latitude, longitude and MSL altitude.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val candidates = fixedBaseCandidates()
        if (candidates.isEmpty()) {
            Toast.makeText(
                context,
                "No fixed-base settings set with a MODE BASE profile is available for ${selectedDeviceFilter.displayName}.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        manualBaseCoordinate = candidate
        pendingFixedBaseCoordinate = acceptedCoordinate
        pendingFixedBaseSettingsSetId = FixedBaseHandoffPlanner.preferredSettingsSetId(
            candidates = candidates,
            lastSettingsSetId = profileStore.lastFixedBaseSettingsSetId(selectedDeviceFilter),
        )
        fixedBaseProfileSelectionMode = FixedBaseProfileSelectionMode.SETTINGS_SET
    }
    fun publishPreparedFixedBaseHandoff(plan: org.rtkcollector.app.base.FixedBaseHandoffPlan) {
        runCatching {
            require(!state.isRecording) { "Wait for recording to stop before applying fixed-base settings." }
            profileStore.publishFixedBaseHandoff(plan, selectedDeviceFilter)
            selectedSettingsSetId = plan.targetSet.id
            selectedWorkflowId = WORKFLOW_FIXED_BASE
            manualBaseCoordinate = null
            resetFixedBaseHandoff()
            refreshProfileUi(plan.settingsSets)
            Toast.makeText(context,
                "Fixed-base settings selected. Choose Upload if needed, then press Start.",
                Toast.LENGTH_LONG).show()
        }.onFailure { error ->
            pendingFixedBasePublication = null
            fixedBaseProfileSelectionMode = FixedBaseProfileSelectionMode.CONFIRM
            Toast.makeText(context, error.message ?: "Cannot publish fixed-base settings.", Toast.LENGTH_LONG).show()
        }
    }
    fun commitFixedBaseHandoff(coordinate: AcceptedBaseCoordinate) {
        runCatching {
            val sets = profileStore.settingsSets()
            val commands = profileStore.commandProfiles()
            val candidate = FixedBaseHandoffPlanner.eligibleSettingsSets(
                sets, commands, selectedDeviceFilter,
                effectiveCommandId = { set ->
                    profileStore.activeProfileId(set, ActiveSetupOptionKey.RECEIVER_COMMAND).orEmpty()
                },
                commandUsageIds = ::commandUsageIds,
            ).firstOrNull { it.settingsSet.id == pendingFixedBaseSettingsSetId }
                ?: error("Selected fixed-base settings set is no longer available.")
            FixedBaseHandoffPlanner.prepare(candidate, sets, commands, coordinate,
                profileStore.duplicateId("settings"), profileStore.duplicateId("commands"),
                pendingFixedBaseCommandAction)
        }.onSuccess { plan ->
            if (state.isRecording || ActiveRecordingSessionRegistry.isAnyActive()) {
                pendingFixedBasePublication = plan
                fixedBaseProfileSelectionMode = null
                runCatching { context.startService(RecordingForegroundService.stopIntent(context)) }
                    .onFailure { error ->
                        pendingFixedBasePublication = null
                        fixedBaseProfileSelectionMode = FixedBaseProfileSelectionMode.CONFIRM
                        Toast.makeText(context, error.message ?: "Could not request recording Stop.", Toast.LENGTH_LONG).show()
                    }
            } else publishPreparedFixedBaseHandoff(plan)
        }.onFailure { error ->
            Toast.makeText(context, error.message ?: "Cannot prepare fixed-base profile.", Toast.LENGTH_LONG).show()
        }
    }
    fun saveAcceptedBaseCoordinate(coordinate: AcceptedBaseCoordinate) {
        baseCoordinateStore.upsert(coordinate)
        baseCoordinateStore.saveSelectedCoordinateId(coordinate.id)
    }
    fun activateFixedBaseWithCommandProfile(commandProfile: CommandProfile, coordinate: AcceptedBaseCoordinate) {
        val shouldStopRecording = state.isRecording
        manualBaseCoordinate = null
        selectedWorkflowId = WORKFLOW_FIXED_BASE
        profileStore.saveSelectedWorkflowId(WORKFLOW_FIXED_BASE)
        val updatedSettingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
            set.copy(commandProfileRef = ProfileReference(commandProfile.id, commandProfile.name))
                .withAcceptedBaseCoordinate(coordinate.id, coordinate.name)
        }
        profileStore.saveSettingsSets(updatedSettingsSets)
        refreshProfileUi(updatedSettingsSets)
        if (shouldStopRecording) {
            context.startService(RecordingForegroundService.stopIntent(context))
        }
    }
    fun overwriteCommandProfileForFixedBase(
        coordinate: AcceptedBaseCoordinate,
        selectedProfile: CommandProfile,
    ) {
        runCatching {
            FixedBaseCommandValidator.requireSupportedReceiverFamily(selectedProfile.receiverFamily)
        }.getOrElse { error ->
            Toast.makeText(context, error.message ?: "Cannot materialize MODE BASE.", Toast.LENGTH_LONG).show()
            return
        }
        if (selectedProfile.isProtected) {
            Toast.makeText(
                context,
                "Copy the built-in command profile before overwriting MODE BASE.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (
            FixedBaseCommandValidator.isCommandProfileUsedByOtherSettingsSet(
                settingsSets = settingsSets,
                selectedSettingsSetId = selectedSettingsSetId,
                commandProfileId = selectedProfile.id,
            )
        ) {
            Toast.makeText(
                context,
                "Create a new fixed-base profile because the selected command profile is used by another settings set.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val materializedProfile = runCatching {
            val modeBaseCommand = coordinate.toFixedBaseModeCommand()
            val result = FixedBaseProfileMaterializer.materialize(selectedProfile.runtimeScript, modeBaseCommand)
            selectedProfile.copy(runtimeScript = result.runtimeScript)
        }.getOrElse { error ->
            Toast.makeText(context, error.message ?: "Cannot materialize MODE BASE.", Toast.LENGTH_LONG).show()
            return
        }
        profileStore.saveCommandProfiles(
            profileStore.commandProfiles().map { profile ->
                if (profile.id == materializedProfile.id) materializedProfile else profile
            },
        )
        saveAcceptedBaseCoordinate(coordinate)
        activateFixedBaseWithCommandProfile(materializedProfile, coordinate)
        pendingFixedBaseCoordinate = null
        fixedBaseProfileSelectionMode = null
        Toast.makeText(
            context,
            "Updated ${materializedProfile.name} and selected fixed-base workflow.",
            Toast.LENGTH_LONG,
        ).show()
    }
    fun createFixedBaseCommandProfile(
        coordinate: AcceptedBaseCoordinate,
        sourceProfile: CommandProfile,
    ) {
        runCatching {
            FixedBaseCommandValidator.requireSupportedReceiverFamily(sourceProfile.receiverFamily)
        }.getOrElse { error ->
            Toast.makeText(context, error.message ?: "Cannot create fixed-base profile.", Toast.LENGTH_LONG).show()
            return
        }
        val newProfile = runCatching {
            val modeBaseCommand = coordinate.toFixedBaseModeCommand()
            val result = FixedBaseProfileMaterializer.materialize(sourceProfile.runtimeScript, modeBaseCommand)
            sourceProfile.copyProfile(
                id = profileStore.duplicateId("commands"),
                name = "Fixed base ${coordinate.name}",
            ).copy(runtimeScript = result.runtimeScript)
        }.getOrElse { error ->
            Toast.makeText(context, error.message ?: "Cannot create fixed-base profile.", Toast.LENGTH_LONG).show()
            return
        }
        profileStore.saveCommandProfiles(profileStore.commandProfiles() + newProfile)
        saveAcceptedBaseCoordinate(coordinate)
        activateFixedBaseWithCommandProfile(newProfile, coordinate)
        pendingFixedBaseCoordinate = null
        fixedBaseProfileSelectionMode = null
        Toast.makeText(
            context,
            "Created ${newProfile.name} and selected fixed-base workflow.",
            Toast.LENGTH_LONG,
        ).show()
    }
    fun updateMockGpsSelection(enabled: Boolean, rateHz: Int) {
        if (rejectFixedOption(ActiveSetupOptionKey.RECORDING_OUTPUT)) return
        runCatching {
            val set = requireNotNull(settingsSets.firstOrNull { it.id == selectedSettingsSetId })
            val running = RecordingSetupBridge.current().takeIf { state.isRecording }
            val owner = running?.recordingOutputProfile ?: requireNotNull(
                profileStore.resolvedActiveSetup(set, selectedWorkflowId).resolvedProfiles?.output)
            val candidate = owner.copy(enableMockLocation = enabled, mockLocationRateHz = rateHz)
            candidate.validate()
            val profiles = profileStore.recordingPolicyProfiles()
            val reusable = profiles.firstOrNull { !it.isProtected &&
                it.copy(id = candidate.id, name = candidate.name, isProtected = candidate.isProtected) == candidate }
            val derived = reusable ?: candidate.copy(id = profileStore.duplicateId("outputs"),
                name = "${owner.name} · Mock ${if (enabled) "$rateHz Hz" else "Off"}", isProtected = false)
            if (reusable == null) profileStore.saveRecordingPolicyProfiles(profiles + derived)
            val choices = profileStore.activeSelections(set)
                .choose(set, ActiveSetupOptionKey.RECORDING_OUTPUT, SelectionChoice.profile(derived.id))
            if (state.isRecording) {
                require(pendingSetupSelection == null) { "Wait for or discard the pending change first." }
                requireNotNull(running) { "Recording configuration is unavailable." }
                require(running.settingsSetId == set.id) { "The selected settings set does not own this recording." }
                val request = SetupPatchRequest(java.util.UUID.randomUUID().toString(), running.sessionId,
                    running.revision, profileStore.selectionRevision(set.id))
                val expectedRunningOwner = LivePatchOwner(owner.id, owner.toJson().toString(), null, null)
                val expectedPatchedOwner = LivePatchOwner(derived.id, derived.toJson().toString(), null, null)
                val currentNextStartOwner = {
                    profileStore.resolvedActiveSetup(set, selectedWorkflowId).resolvedProfiles?.output?.let {
                        LivePatchOwner(it.id, it.toJson().toString(), null, null)
                    }
                }
                val currentPatchedOwner = {
                    profileStore.recordingPolicyProfiles().singleOrNull { it.id == derived.id }?.let {
                        LivePatchOwner(it.id, it.toJson().toString(), null, null)
                    }
                }
                val retry: (SetupPatchRequest) -> Intent = { attempt ->
                    Intent(context, RecordingForegroundService::class.java)
                        .setAction(RecordingForegroundService.ACTION_UPDATE_MOCK_LOCATION)
                        .putExtra(RecordingForegroundService.EXTRA_SETUP_TOKEN,
                            RecordingSetupBridge.stageMockUpdate(attempt, set.id, derived))
                }
                val selectionStillOwned = {
                    val currentRevision = profileStore.selectionRevision(set.id)
                    mayPublishLivePatchSelection(
                        expectedRunningOwner, currentNextStartOwner(), request.selectionRevision, currentRevision,
                    ) && mayPublishLivePatchSelection(
                        expectedPatchedOwner, currentPatchedOwner(), request.selectionRevision, currentRevision,
                    )
                }
                val canPublishAtDispatch = selectionStillOwned()
                val intent = retry(request)
                pendingSetupSelection = PendingSetupSelection(
                    request = request,
                    settingsSetId = set.id,
                    option = ActiveSetupOptionKey.RECORDING_OUTPUT,
                    selections = choices,
                    targetLabel = derived.name,
                    intent = intent,
                    retry = retry,
                    canPublishSelection = { canPublishAtDispatch && selectionStillOwned() },
                )
                context.startService(intent)
            } else {
                profileStore.saveActiveSelections(choices)
                refreshProfileUi()
            }
        }.onFailure {
            val message = "Mock output change could not be dispatched. Query recording state before retrying."
            pendingSetupSelection = pendingSetupSelection?.copy(failure = message)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingStartAfterNotificationPermission
        pendingStartAfterNotificationPermission = null
        if (granted) {
            pending?.invoke()
        } else {
            Toast.makeText(
                context,
                "Recording needs notification permission so Android can show the foreground recording notification.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    fun startAfterRuntimePermissionCheck(action: () -> Unit) {
        val missingPermissions = runtimePermissionsRequiredBeforeRecording(Build.VERSION.SDK_INT)
            .filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
        if (missingPermissions.contains(Manifest.permission.POST_NOTIFICATIONS)) {
            pendingStartAfterNotificationPermission = {
                val currentState = latestState.value
                when {
                    currentState.isRecording -> {
                        startInProgress = false
                    }
                    persistentReceiverWriteInProgress.get() -> {
                        Toast.makeText(
                            context,
                            "Wait for persistent receiver configuration write to finish before recording.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    else -> action()
                }
            }
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            stageSettingsImport(uri)
        }
    }
    val importBasePositionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Base-position JSON could not be read.")
                val coordinate = BasePositionJsonCodec.decode(
                    json = text,
                    fallbackId = profileStore.duplicateId("base"),
                    fallbackName = "Imported base coordinate",
                )
                val affected = coordinateUsers(coordinate.id)
                confirmSharedEdit(coordinate.name, affected) {
                    profileStore.invalidateNextStartConfiguration(affected.mapTo(linkedSetOf()) { it.id })
                    baseCoordinateStore.upsert(coordinate)
                    refreshProfileUi()
                    Toast.makeText(context, "Base coordinate imported.", Toast.LENGTH_LONG).show()
                }
            }.onFailure { error ->
                Toast.makeText(context, "Cannot import base coordinate: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val storageFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val pendingSelection = pendingStorageFolderSelection
        pendingStorageFolderSelection = null
        if (uri != null && pendingSelection != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                profileStore.saveProfileEditorData(
                    target = pendingSelection.target,
                    values = pendingSelection.values +
                        ("kind" to "SAF_TREE") +
                        ("treeUri" to uri.toString()),
                    settingsSets = settingsSets,
                    savePassword = secretStore::putPassword,
                )
            }.onSuccess { updatedSettingsSets ->
                refreshProfileUi(updatedSettingsSets)
                screen = pendingSelection.target.kind.backScreen()
                Toast.makeText(context, "Selected Android folder saved for recording storage.", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "Cannot save Android folder: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun renameProfile(kind: ProfileKind, id: String, name: String): Boolean {
        if (kind == ProfileKind.SETTINGS_SET && id == selectedSettingsSetId &&
            !canChangeActiveSetup(state.isRecording, startInProgress)) {
            Toast.makeText(context, "Stop recording before renaming the active settings set.", Toast.LENGTH_LONG).show()
            return false
        }
        return runCatching {
            profileStore.renameProfileData(kind, id, name, settingsSets)
        }.fold(
            onSuccess = { updatedSettingsSets ->
                refreshProfileUi(updatedSettingsSets)
                true
            },
            onFailure = { error ->
                Toast.makeText(context, "Cannot rename profile: ${error.message}", Toast.LENGTH_LONG).show()
                false
            },
        )
    }
    fun deleteProfileIfUnused(kind: ProfileKind, id: String, delete: () -> Unit) {
        if (profileUsers(ProfileEditorTarget(kind, id)).isNotEmpty() ||
            kind == ProfileKind.NTRIP_CASTER && profileStore.ntripMountpointProfiles().any { it.casterProfileId == id }) {
            Toast.makeText(context, "Cannot delete: profile is used by a settings set.", Toast.LENGTH_LONG).show()
            return
        }
        delete()
        refreshProfileUi()
    }
    fun deleteSettingsSet(id: String) {
        if (!canChangeActiveSetup(state.isRecording, startInProgress) && id == selectedSettingsSetId) {
            Toast.makeText(context, "Stop recording before deleting the active settings set.", Toast.LENGTH_LONG).show()
            return
        }
        val item = settingsSets.firstOrNull { it.id == id } ?: return
        val updated = if (item.isProtected) {
            settingsSets.map { set ->
                if (set.id == id) set.copy(overrides = org.rtkcollector.app.profile.SettingsSetOverrides()) else set
            }
        } else {
            settingsSets.filterNot { it.id == id }
        }
        settingsSets = updated
        profileStore.saveSettingsSets(updated)
        if (!item.isProtected && selectedSettingsSetId == id) {
            selectedSettingsSetId = updated.firstOrNull()?.id.orEmpty()
            if (selectedSettingsSetId.isNotBlank()) {
                profileStore.saveSelectedSettingsSetId(selectedSettingsSetId)
            }
        }
        refreshProfileUi(updated)
    }
    fun deleteProfile(kind: ProfileKind, id: String) {
        when (kind) {
            ProfileKind.SETTINGS_SET -> deleteSettingsSet(id)
            ProfileKind.NTRIP_CASTER -> deleteProfileIfUnused(kind, id) {
                val profiles = profileStore.ntripCasterProfiles()
                profileStore.saveNtripCasterProfiles(profiles.filterNot { it.id == id && !it.isProtected })
            }
            ProfileKind.NTRIP_CASTER_UPLOAD -> deleteProfileIfUnused(kind, id) {
                val profiles = profileStore.ntripCasterUploadProfiles()
                profileStore.saveNtripCasterUploadProfiles(profiles.filterNot { it.id == id && !it.isProtected })
            }
            ProfileKind.NTRIP_MOUNTPOINT -> deleteProfileIfUnused(kind, id) {
                val profiles = profileStore.ntripMountpointProfiles()
                profileStore.saveNtripMountpointProfiles(profiles.filterNot { it.id == id && !it.isProtected })
            }
            ProfileKind.COMMANDS -> deleteProfileIfUnused(kind, id) {
                val profiles = profileStore.commandProfiles()
                profileStore.saveCommandProfiles(profiles.filterNot { it.id == id && !it.isProtected })
            }
            ProfileKind.RECORDING_OUTPUTS -> deleteProfileIfUnused(kind, id) {
                val profiles = profileStore.recordingPolicyProfiles()
                profileStore.saveRecordingPolicyProfiles(profiles.filterNot { it.id == id && !it.isProtected })
            }
            ProfileKind.RTKLIB -> deleteProfileIfUnused(kind, id) {
                val profiles = profileStore.rtklibProfiles()
                profileStore.saveRtklibProfiles(profiles.filterNot { it.id == id && !it.isProtected })
            }
            ProfileKind.SOLUTION_POLICY -> deleteProfileIfUnused(kind, id) {
                val profiles = profileStore.solutionPolicyProfiles()
                profileStore.saveSolutionPolicyProfiles(profiles.filterNot { it.id == id && !it.isProtected })
            }
            ProfileKind.STORAGE -> deleteProfileIfUnused(kind, id) {
                val profiles = profileStore.storageProfiles()
                profileStore.saveStorageProfiles(profiles.filterNot { it.id == id && !it.isProtected })
            }
            ProfileKind.USB_BAUD -> deleteProfileIfUnused(kind, id) {
                val profiles = profileStore.usbBaudProfiles()
                profileStore.saveUsbBaudProfiles(profiles.filterNot { it.id == id && !it.isProtected })
            }
        }
    }
    fun profileEditorDeleteAction(target: ProfileEditorTarget): ProfileEditorAction? {
        val row = when (target.kind) {
            ProfileKind.SETTINGS_SET -> SettingsSetListState.from(settingsSets, selectedSettingsSetId).rows.firstOrNull { it.id == target.id }
            ProfileKind.NTRIP_CASTER -> profileStore.ntripCasterProfiles().firstOrNull { it.id == target.id }?.profileRow()
            ProfileKind.NTRIP_CASTER_UPLOAD -> profileStore.ntripCasterUploadProfiles()
                .firstOrNull { it.id == target.id }
                ?.profileRow()
            ProfileKind.NTRIP_MOUNTPOINT -> profileStore.ntripMountpointProfiles().firstOrNull { it.id == target.id }?.profileRow()
            ProfileKind.USB_BAUD -> profileStore.usbBaudProfiles().firstOrNull { it.id == target.id }?.profileRow()
            ProfileKind.COMMANDS -> profileStore.commandProfiles().firstOrNull { it.id == target.id }?.profileRow()
            ProfileKind.RECORDING_OUTPUTS -> profileStore.recordingPolicyProfiles().firstOrNull { it.id == target.id }?.profileRow()
            ProfileKind.RTKLIB -> profileStore.rtklibProfiles().firstOrNull { it.id == target.id }?.profileRow()
            ProfileKind.SOLUTION_POLICY -> profileStore.solutionPolicyProfiles().firstOrNull { it.id == target.id }?.profileRow()
            ProfileKind.STORAGE -> profileStore.storageProfiles().firstOrNull { it.id == target.id }?.profileRow()
        } ?: return null
        if (!row.canDelete) return null
        if (target.kind != ProfileKind.SETTINGS_SET && profileUsers(target).isNotEmpty()) return null
        return ProfileEditorAction(
            label = profileDeleteActionLabel(row),
            onClick = {
                deleteProfile(target.kind, target.id)
                screen = target.kind.backScreen()
            },
            destructive = true,
        )
    }
    BackHandler(enabled = screen != AppScreen.HOME) {
        screen = if (screen == AppScreen.SESSIONS) {
            sessionBrowserEntryPoint.returnScreen()
        } else {
            screen.backScreen(profileEditorTarget)
        }
    }
    fun reconcilePendingSetup(pending: PendingSetupSelection, receipt: org.rtkcollector.app.recording.SetupBridgeReceipt) {
        if (!receipt.accepted || receipt.sessionId != pending.request.sessionId ||
            receipt.settingsSetId != pending.settingsSetId) {
            pendingSetupSelection = pending.copy(failure = receipt.message ?: "Recording update was rejected.")
            return
        }
        if (pending.canPublishSelection()) {
            runCatching {
                val consumed = profileStore.savePublishedLiveSelections(
                    pending.selections, pending.option, receipt.selectionRevision)
                RecordingSetupBridge.consumePublishedSelections(receipt, consumed)
            }.onFailure {
                Toast.makeText(context,
                    "Recording changed; newer next-start choices were retained.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context,
                "Recording change accepted; newer next-start choices were retained.", Toast.LENGTH_LONG).show()
        }
        pendingSetupSelection = null
        refreshProfileUi()
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    RecordingForegroundService.ACTION_STATE -> {
                        val wasRecording = state.isRecording
                        val wasStarting = startInProgress
                        val nextState = dashboardStateFromRecordingIntent(intent)
                            .withPlannedConfiguration(plannedState)
                            .withRecordingLocks(state, startingLocks?.let { plannedState.copy(status = it) } ?: plannedState)
                        state = nextState
                        if (nextState.isRecording) activeAttemptSettingsSetId = RecordingSetupBridge.current()?.settingsSetId
                            ?: activeAttemptSettingsSetId
                        pendingSetupSelection?.let { pending ->
                            RecordingSetupBridge.result(pending.request.requestId)?.let { receipt ->
                                reconcilePendingSetup(pending, receipt)
                            }
                        }
                        if (!nextState.isRecording && (wasRecording || wasStarting && nextState.lastError != null)) {
                            activeAttemptSettingsSetId = null
                            pendingSetupSelection = null
                            refreshProfileUi()
                        }
                        if (intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_LIFECYCLE) in
                            setOf("STOPPED", "FAILED")) {
                            pendingFixedBasePublication?.let { plan -> publishPreparedFixedBaseHandoff(plan) }
                        }
                        if (nextState.isRecording || nextState.lastError != null || nextState.errorSeverity != "NONE") {
                            startInProgress = false
                            startingLocks = null
                        }
                        if (screen == AppScreen.SESSIONS) {
                            refreshSessions()
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        profileRevision++
                    }
                    ACTION_USB_PERMISSION -> {
                        profileRevision++
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        val message = if (granted) {
                            UsbStartAccessDecision.permissionGrantedMessage()
                        } else {
                            UsbStartAccessDecision.permissionDeniedMessage()
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(RecordingForegroundService.ACTION_STATE)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val activity = context as? ComponentActivity
        fun queryRecordingServiceState() {
            val queryStarted = tryStartRecordingServiceStateQuery {
                context.startService(
                    Intent(context, RecordingForegroundService::class.java)
                        .setAction(RecordingForegroundService.ACTION_QUERY),
                )
            }
            if (!queryStarted) {
                recordAppRuntimeDiagnosticIfEnabled(
                    context = context,
                    severity = "WARNING",
                    message = "Recording-service state query was blocked by Android background restrictions.",
                )
            }
        }
        var queryDispatchedForCurrentResume = false
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!queryDispatchedForCurrentResume) {
                    queryDispatchedForCurrentResume = true
                    queryRecordingServiceState()
                }
                Lifecycle.Event.ON_PAUSE -> queryDispatchedForCurrentResume = false
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(lifecycleObserver)
        if (
            activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true &&
            !queryDispatchedForCurrentResume
        ) {
            queryDispatchedForCurrentResume = true
            queryRecordingServiceState()
        }
        onDispose {
            activity?.lifecycle?.removeObserver(lifecycleObserver)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    LaunchedEffect(pendingSetupSelection?.request?.requestId, pendingSetupSelection?.failure) {
        val pending = pendingSetupSelection ?: return@LaunchedEffect
        if (pending.failure != null) return@LaunchedEffect
        delay(2_000)
        runCatching {
            context.startService(Intent(context, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_QUERY))
        }
        delay(8_000)
        if (pendingSetupSelection?.request?.requestId == pending.request.requestId &&
            pendingSetupSelection?.failure == null && RecordingSetupBridge.result(pending.request.requestId) == null) {
            pendingSetupSelection = pending.copy(failure = "No acknowledgement received. Retry checks the accepted recording state first.")
        }
    }
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    val batteryWarning = batteryOptimisationWarning(
        isIgnoringBatteryOptimisations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true,
        isRecording = state.isRecording,
    )

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            pendingSettingsImport?.let { pendingImport ->
                SettingsImportScreen(
                    source = pendingImport.source,
                    result = pendingImport.result,
                    recordingActive = state.isRecording || startInProgress,
                    onImport = {
                        if (!canChangeActiveSetup(state.isRecording, startInProgress)) {
                            Toast.makeText(context, "Stop recording before importing settings.", Toast.LENGTH_LONG).show()
                        } else {
                            val valid = pendingImport.result as? SettingsImportValidationResult.Valid
                            if (valid != null) {
                                runCatching {
                                    importSettingsBackup(context, valid.backup)
                                }.onSuccess { outcome ->
                                    val updatedSelectedSettingsSetId = profileStore.selectedSettingsSetId()
                                    val updatedSettingsSets = profileStore.settingsSets()
                                    val updatedSelectedWorkflowId = profileStore.selectedWorkflowId()
                                    settingsSets = updatedSettingsSets
                                    selectedSettingsSetId = updatedSelectedSettingsSetId
                                    selectedWorkflowId = updatedSelectedWorkflowId
                                    val planned = profileStore.plannedDashboardState(
                                        updatedSettingsSets,
                                        updatedSelectedSettingsSetId,
                                        updatedSelectedWorkflowId,
                                        baseCoordinateStore.coordinates(),
                                    )
                                    plannedState = planned
                                    state = state.withPlannedConfiguration(planned)
                                    profileRevision++
                                    pendingSettingsImport = null
                                    screen = pendingImport.returnScreen
                                    val totalSafTreeUriReselectionCount = maxOf(
                                        valid.summary.safTreeUriReselectionCount,
                                        outcome.safTreeUriReselectionCount,
                                    )
                                    val storageMessage = if (totalSafTreeUriReselectionCount == 0) {
                                        "Settings backup imported."
                                    } else {
                                        "Settings backup imported. Select ${
                                            if (totalSafTreeUriReselectionCount == 1) "the Android folder" else "Android folders"
                                        } again before use."
                                    }
                                    val message = storageMessage + outcome.secretStoreOutcome.userMessage
                                        ?.let { " $it" }
                                        .orEmpty()
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }.onFailure { error ->
                                    pendingSettingsImport = pendingImport.copy(
                                        result = SettingsImportValidationResult.Invalid(
                                            error.message ?: "Settings backup could not be imported.",
                                        ),
                                    )
                                }
                            }
                        }
                    },
                    onCancel = {
                        settingsImportRequestId++
                        pendingSettingsImport = null
                        screen = pendingImport.returnScreen
                    },
                )
                return@Surface
            }
            if (pendingNmeaSources.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = {
                        pendingNmeaSources = emptyList()
                        pendingNmeaEntries = emptyList()
                    },
                    title = { Text("Share NMEA") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Choose which available NMEA output to share.")
                            pendingNmeaSources.forEach { source ->
                                TextButton(
                                    onClick = {
                                        val entries = pendingNmeaEntries
                                        pendingNmeaSources = emptyList()
                                        pendingNmeaEntries = emptyList()
                                        shareNmeaFromEntries(
                                            context = context,
                                            entries = entries,
                                            requestedSources = setOf(source),
                                            refreshSessions = ::refreshSessions,
                                            setProgress = { text, fraction ->
                                                zipProgressText = text
                                                sessionProgressFraction = fraction
                                            },
                                        )
                                    },
                                ) {
                                    Text(source.displayName)
                                }
                            }
                            if (pendingNmeaSources.size > 1) {
                                TextButton(
                                    onClick = {
                                        val entries = pendingNmeaEntries
                                        val sources = pendingNmeaSources.toSet()
                                        pendingNmeaSources = emptyList()
                                        pendingNmeaEntries = emptyList()
                                        shareNmeaFromEntries(
                                            context = context,
                                            entries = entries,
                                            requestedSources = sources,
                                            refreshSessions = ::refreshSessions,
                                            setProgress = { text, fraction ->
                                                zipProgressText = text
                                                sessionProgressFraction = fraction
                                            },
                                        )
                                    },
                                ) {
                                    Text("All available")
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(
                            onClick = {
                                pendingNmeaSources = emptyList()
                                pendingNmeaEntries = emptyList()
                            },
                        ) {
                            Text("Cancel")
                        }
                    },
                )
            }
            if (pendingRtklibPostprocessEntries.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = {
                        pendingRtklibPostprocessEntries = emptyList()
                    },
                    title = { Text("Regenerate RTKLIB") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Choose the native RTKLIB-EX postprocessing mode for the selected recording(s).")
                            TextButton(
                                onClick = {
                                    val entries = pendingRtklibPostprocessEntries
                                    pendingRtklibPostprocessEntries = emptyList()
                                    runRtklibPostprocess(entries, RtklibPostprocessMode.FORWARD)
                                },
                            ) {
                                Text("Forward only")
                            }
                            TextButton(
                                onClick = {
                                    val entries = pendingRtklibPostprocessEntries
                                    pendingRtklibPostprocessEntries = emptyList()
                                    runRtklibPostprocess(entries, RtklibPostprocessMode.FORWARD_BACKWARD)
                                },
                            ) {
                                Text("Forward + backward")
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(
                            onClick = {
                                pendingRtklibPostprocessEntries = emptyList()
                            },
                        ) {
                            Text("Cancel")
                        }
                    },
                )
            }
            sessionTaskErrorDetail?.let { detail ->
                AlertDialog(
                    onDismissRequest = { sessionTaskErrorDetail = null },
                    title = { Text("Session operation failed") },
                    text = {
                        Text(
                            text = detail,
                            modifier = Modifier
                                .height(260.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("RtkCollector session error", detail))
                                Toast.makeText(context, "Error details copied.", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Text("Copy")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { sessionTaskErrorDetail = null }) {
                            Text("Close")
                        }
                    },
                )
            }
            when (screen) {
                AppScreen.HOME -> Column(modifier = Modifier.fillMaxSize()) {
                    val homeState = when (pendingSetupSelection?.option) {
                        ActiveSetupOptionKey.NTRIP_MOUNTPOINT -> state.copy(status = state.status.copy(
                            mountpoint = "${state.status.mountpoint} · Change pending",
                        ))
                        ActiveSetupOptionKey.RECORDING_OUTPUT -> state.copy(mockGps = state.mockGps.copy(
                            changePending = true,
                        ))
                        else -> state
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        HomeDashboard(
                            state = homeState,
                            layoutPreference = dashboardLayout,
                            distanceUnitPreference = dashboardDistanceUnits,
                            satelliteMonitorThemePreference = satelliteMonitorCardTheme,
                            setupExpandedPreference = dashboardSetupExpanded,
                            startInProgress = startInProgress,
                            recordingReliabilityWarning = batteryWarning.message.takeIf { batteryWarning.show },
                            onSetupExpandedPreferenceChange = { expanded ->
                                dashboardSetupExpanded = expanded
                                dashboardUiPreferences.saveSetupExpanded(expanded)
                            },
                            onPrimaryAction = {
                                if (state.isRecording) {
                                    startInProgress = false
                                    startingLocks = null
                                    context.startService(RecordingForegroundService.stopIntent(context))
                                } else if (persistentReceiverWriteInProgress.get()) {
                                    Toast.makeText(
                                        context,
                                        "Wait for persistent receiver configuration write to finish before recording.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    startAfterRuntimePermissionCheck {
                                        val startIntent = buildDashboardStartIntent(
                                            context = context,
                                            settingsSets = settingsSets,
                                            selectedSettingsSetId = selectedSettingsSetId,
                                            selectedWorkflowId = selectedWorkflowId,
                                            selectedBaseCoordinate = baseCoordinateStore.selectedCoordinate(),
                                        )
                                        if (startIntent == null) {
                                            refreshProfileUi()
                                        }
                                        startIntent?.let { intent ->
                                            activeAttemptSettingsSetId = selectedSettingsSetId
                                            startingLocks = state.status
                                            startInProgress = true
                                            runCatching {
                                                RecordingSetupBridge.dispatchStart(intent.getStringExtra(
                                                    RecordingForegroundService.EXTRA_SETUP_TOKEN)) {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                        context.startForegroundService(intent)
                                                    } else context.startService(intent)
                                                }
                                            }.onFailure {
                                                startInProgress = false
                                                activeAttemptSettingsSetId = null
                                                refreshProfileUi()
                                                showCannotStart(context, "Recording service could not be started.")
                                            }
                                        }
                                    }
                                }
                            },
                            onMenu = { screen = AppScreen.SETTINGS },
                            onNtrip = {
                                if (!rejectFixedOption(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)) {
                                    dashboardSelector = DashboardSelector.MOUNTPOINT
                                }
                            },
                            onUsbPermission = {
                                requestSelectedUsbPermission(context, selectedSettingsSetId)
                                profileRevision++
                            },
                            onMockGps = {
                                if (!rejectFixedOption(ActiveSetupOptionKey.RECORDING_OUTPUT)) {
                                    showMockGpsDialog = true
                                }
                            },
                            onSettingsSet = {
                                if (!canChangeActiveSetup(state.isRecording, startInProgress)) {
                                    Toast.makeText(context, "Stop recording before changing settings set.", Toast.LENGTH_LONG).show()
                                } else {
                                    dashboardSelector = DashboardSelector.SETTINGS_SET
                                }
                            },
                            onWorkflow = {
                                if (state.isRecording) {
                                    Toast.makeText(context, "Stop recording before changing workflow.", Toast.LENGTH_LONG).show()
                                } else if (rejectFixedOption(ActiveSetupOptionKey.WORKFLOW)) {
                                    Unit
                                } else {
                                    dashboardSelector = DashboardSelector.WORKFLOW
                                }
                            },
                            onDevice = {
                                if (state.isRecording) {
                                    Toast.makeText(context, "Stop recording before changing device filter.", Toast.LENGTH_LONG).show()
                                } else {
                                    dashboardSelector = DashboardSelector.DEVICE
                                }
                            },
                            onInitProfiles = {
                                if (state.isRecording) {
                                    Toast.makeText(context, "Stop recording before changing init/shutdown profiles.", Toast.LENGTH_LONG).show()
                                } else if (rejectFixedOption(ActiveSetupOptionKey.RECEIVER_COMMAND)) {
                                    Unit
                                } else {
                                    dashboardSelector = DashboardSelector.INIT_PROFILES
                                }
                            },
                            onUpload = {
                                if (state.isRecording) {
                                    Toast.makeText(context, "Stop recording before changing NTRIP upload.", Toast.LENGTH_LONG).show()
                                } else if (rejectFixedOption(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD)) {
                                    Unit
                                } else {
                                    dashboardSelector = DashboardSelector.UPLOAD
                                }
                            },
                            onStorage = {
                                if (state.isRecording) {
                                    Toast.makeText(context, "Stop recording before changing storage.", Toast.LENGTH_LONG).show()
                                } else if (rejectFixedOption(ActiveSetupOptionKey.STORAGE)) {
                                    Unit
                                } else {
                                    dashboardSelector = DashboardSelector.STORAGE
                                }
                            },
                            onSessions = { openSessions(SessionBrowserEntryPoint.HOME) },
                            coordinateAveraging = state.position.serviceCoordinateAveragingState(),
                            onStartCoordinateAveraging = { _, _ ->
                                if (!state.isRecording) {
                                    Toast.makeText(context, "Start recording before averaging.", Toast.LENGTH_SHORT).show()
                                } else {
                                    context.startService(
                                        Intent(context, RecordingForegroundService::class.java)
                                            .setAction(RecordingForegroundService.ACTION_START_COORDINATE_AVERAGING),
                                    )
                                }
                            },
                            onStopCoordinateAveraging = {
                                context.startService(
                                    Intent(context, RecordingForegroundService::class.java)
                                        .setAction(RecordingForegroundService.ACTION_STOP_COORDINATE_AVERAGING),
                                )
                            },
                            onUseCurrentCoordinateAsManualBase = { candidate ->
                                val averageCandidate = state.position.serviceCoordinateAveragingState()
                                    .averageBaseCandidateOrNull()
                                val instantCandidate = state.position.baseCoordinateCandidateOrNull()
                                if (averageCandidate != null && instantCandidate != null) {
                                    pendingFixedBaseCoordinateChoices = FixedBaseCoordinateChoices(
                                        average = averageCandidate,
                                        instant = instantCandidate,
                                    )
                                } else {
                                    beginFixedBaseHandoff(averageCandidate ?: instantCandidate ?: candidate)
                                }
                            },
                            onSatelliteMonitorDetails = { screen = AppScreen.SATELLITE_MONITOR },
                            onCasterUploadDetails = { screen = AppScreen.CASTER_UPLOAD_MONITOR },
                        )
                    }
                }
                AppScreen.SATELLITE_MONITOR -> SatelliteMonitorScreen(
                    state = state.satelliteMonitor,
                    onBack = { screen = AppScreen.HOME },
                )
                AppScreen.CASTER_UPLOAD_MONITOR -> CasterUploadMonitorScreen(
                    state = state.casterUpload,
                    onBack = { screen = AppScreen.HOME },
                )
                AppScreen.SETTINGS ->
                    SettingsHub(
                        activeSettingsSetLabel = plannedState.profiles.settingsSet,
                        activeWorkflowLabel = plannedState.status.workflow,
                        onActiveChoices = { showActiveChoicesDialog = true },
                        deviceFilterLabel = selectedDeviceFilter.displayName,
                        activeSettingsSetOutsideDeviceFilter = state.status.settingsSetOutsideDeviceFilter,
                        initProfileOutsideDeviceFilter = state.status.initProfileOutsideDeviceFilter,
                        canReapplySettingsSet = settingsSets.any { it.id == selectedSettingsSetId },
                        onActiveSettingsSet = {
                            if (!canChangeActiveSetup(state.isRecording, startInProgress)) {
                                Toast.makeText(context, "Stop recording before loading a different settings set.", Toast.LENGTH_LONG).show()
                            } else {
                                screen = AppScreen.SETTINGS_SET_SELECTOR
                            }
                        },
                        onDeviceFilter = {
                            if (state.isRecording) {
                                Toast.makeText(context, "Stop recording before changing device filter.", Toast.LENGTH_LONG).show()
                            } else {
                                dashboardSelector = DashboardSelector.DEVICE
                            }
                        },
                        onReapplySettingsSet = {
                            if (!canChangeActiveSetup(state.isRecording, startInProgress)) {
                                Toast.makeText(context, "Stop recording before re-applying the settings set.", Toast.LENGTH_LONG).show()
                            } else {
                                showReapplySettingsDialog = true
                            }
                        },
                        onSettingsSets = { screen = AppScreen.SETTINGS_SETS },
                        onWorkflowSelection = {
                            if (state.isRecording) {
                                Toast.makeText(context, "Stop recording before changing workflow.", Toast.LENGTH_LONG).show()
                            } else if (rejectFixedOption(ActiveSetupOptionKey.WORKFLOW)) {
                                Unit
                            } else {
                                dashboardSelector = DashboardSelector.WORKFLOW
                                screen = AppScreen.HOME
                            }
                        },
                        onBaseCoordinates = { screen = AppScreen.BASE_COORDINATES },
                        dashboardLayoutLabel = "${dashboardLayout.displayName}; ${dashboardDistanceUnits.displayName}; ${satelliteMonitorCardTheme.displayName}",
                        onDashboardLayout = { showDashboardLayoutDialog = true },
                        onNtripCaster = { screen = AppScreen.NTRIP_CASTER },
                        onNtripCasterUpload = { screen = AppScreen.NTRIP_CASTER_UPLOAD },
                        onNtripMountpoint = { screen = AppScreen.NTRIP_MOUNTPOINT_PROFILES },
                        onUsbBaud = { screen = AppScreen.USB_BAUD },
                        onCommands = { screen = AppScreen.COMMANDS },
                        onReceiverProfile = {
                            if (state.isRecording) {
                                Toast.makeText(context, "Stop recording before changing init/shutdown profiles.", Toast.LENGTH_LONG).show()
                            } else {
                                dashboardSelector = DashboardSelector.INIT_PROFILES
                                screen = AppScreen.HOME
                            }
                        },
                        onRecordingOutputs = { screen = AppScreen.RECORDING_OUTPUTS },
                        onRtklibProfiles = { screen = AppScreen.RTKLIB_PROFILES },
                        onSolutionPolicy = { screen = AppScreen.SOLUTION_POLICIES },
                        onStorage = { screen = AppScreen.STORAGE },
                        onSessions = { openSessions(SessionBrowserEntryPoint.SETTINGS) },
                        onExportSettings = {
                            includePlaintextPasswordsInBackup = false
                            showSettingsExportDialog = true
                        },
                        onImportSettings = {
                            importSettingsLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                        },
                        onDeviceConsole = { screen = AppScreen.DEVICE_CONSOLE },
                        onAppDiagnostics = { screen = AppScreen.APP_DIAGNOSTICS },
                        onBack = { screen = AppScreen.HOME },
                    )
                AppScreen.NTRIP_MOUNTPOINT -> NtripMountpointScreen(
                    initialState = NtripMountpointEditorState(
                        mountpointText = profileStore.selectedMountpointLabel(selectedSettingsSetId).takeUnless { it == "n/a" }.orEmpty(),
                        availableMountpoints = profileStore.selectedCasterMountpoints(selectedSettingsSetId),
                    ),
                    onBack = { screen = AppScreen.SETTINGS },
                    onSave = { mountpoint ->
                        if (startInProgress || rejectFixedOption(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)) {
                            if (startInProgress) {
                                Toast.makeText(context, "Wait for recording to start before changing setup.", Toast.LENGTH_LONG).show()
                            }
                            screen = AppScreen.SETTINGS
                        } else {
                        runCatching {
                            val set = requireNotNull(settingsSets.firstOrNull { it.id == selectedSettingsSetId })
                            val selected = profileStore.resolvedActiveSetup(set, selectedWorkflowId).resolvedProfiles?.source
                            requireNotNull(selected) { "Select a source profile with its caster before typing a mountpoint." }
                            val candidate = selected.copy(mountpoint = mountpoint.trim())
                            candidate.validate()
                            val profiles = profileStore.ntripMountpointProfiles()
                            val reusable = profiles.firstOrNull { !it.isProtected &&
                                it.copy(id = candidate.id, name = candidate.name, isProtected = candidate.isProtected) == candidate }
                            val derived = reusable ?: candidate.copy(id = profileStore.duplicateId("source"),
                                name = "${selected.name} · ${candidate.mountpoint}", isProtected = false)
                            if (reusable == null) profileStore.saveNtripMountpointProfiles(profiles + derived)
                            selectNtripMountpoint(derived)
                            screen = AppScreen.SETTINGS
                        }.onFailure {
                            Toast.makeText(context, it.message ?: "Cannot derive a source profile.", Toast.LENGTH_LONG).show()
                        }
                        }
                    },
                )
                AppScreen.SETTINGS_SETS -> SettingsSetListScreen(
                    title = "Settings sets",
                    state = SettingsSetListState(
                        rows = filteredSettingsSetRows(settingsSets, selectedSettingsSetId, selectedDeviceFilter, profileStore),
                    ),
                    onSelect = { id ->
                        if (!canChangeActiveSetup(state.isRecording, startInProgress)) {
                            Toast.makeText(context, "Stop recording before changing settings set.", Toast.LENGTH_LONG).show()
                        } else {
                            activateSettingsSet(id)
                        }
                    },
                    onEdit = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.SETTINGS_SET, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onCopy = { id ->
                        val source = settingsSets.firstOrNull { it.id == id }
                        if (source != null) {
                            val copy = source.copySet(profileStore.duplicateId("settings"), "${source.name} copy")
                            val updated = settingsSets + copy
                            settingsSets = updated
                            profileStore.saveSettingsSets(updated)
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.SETTINGS_SET, copy.id)
                            profileRevision++
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onRename = { id, name -> renameProfile(ProfileKind.SETTINGS_SET, id, name) },
                    onDelete = { id -> deleteProfile(ProfileKind.SETTINGS_SET, id) },
                    onAdd = {
                        val newSet = RecordingSettingsSet.builtInRoverNtrip().copySet(
                            id = profileStore.duplicateId("settings"),
                            name = "New settings set",
                        )
                        val updated = settingsSets + newSet
                        settingsSets = updated
                        profileStore.saveSettingsSets(updated)
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.SETTINGS_SET, newSet.id)
                        profileRevision++
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                )
                AppScreen.NTRIP_CASTER -> ProfileListScreen(
                    title = "NTRIP casters",
                    rows = profileStore.ntripCasterProfiles().map { it.profileRow() },
                    onSelect = {},
                    onEdit = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_CASTER, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onCopy = { id ->
                        val profiles = profileStore.ntripCasterProfiles()
                        profiles.firstOrNull { it.id == id }?.let { source ->
                            val copy = source.copyProfile(profileStore.duplicateId("caster"), "${source.name} copy")
                            profileStore.saveNtripCasterProfiles(
                                profiles + copy,
                            )
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_CASTER, copy.id)
                            profileRevision++
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onRename = { id, name -> renameProfile(ProfileKind.NTRIP_CASTER, id, name) },
                    onDelete = { id -> deleteProfile(ProfileKind.NTRIP_CASTER, id) },
                    onAdd = {
                        val profile = NtripCasterProfile(
                            id = profileStore.duplicateId("caster"),
                            name = "New NTRIP caster",
                        )
                        profileStore.saveNtripCasterProfiles(profileStore.ntripCasterProfiles() + profile)
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_CASTER, profile.id)
                        profileRevision++
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                    supportsSelection = false,
                )
                AppScreen.NTRIP_CASTER_UPLOAD -> ProfileListScreen(
                    title = "NTRIP caster upload",
                    rows = profileStore.ntripCasterUploadProfiles().map {
                        it.profileRow(
                            isSelected = it.id == profileStore.activeProfileId(
                                settingsSets.firstOrNull { set -> set.id == selectedSettingsSetId }, ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD),
                        )
                    },
                    onSelect = { id ->
                        val profile = profileStore.ntripCasterUploadProfiles().firstOrNull { it.id == id }
                        if (!rejectFixedOption(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD) && profile != null) {
                            chooseActiveUpload(profile.id)
                        }
                    },
                    onEdit = { id ->
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing caster upload profile.", Toast.LENGTH_LONG).show()
                        } else {
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_CASTER_UPLOAD, id)
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onCopy = { id ->
                        val profiles = profileStore.ntripCasterUploadProfiles()
                        profiles.firstOrNull { it.id == id }?.let { source ->
                            val copy = source.copyProfile(profileStore.duplicateId("caster-upload"), "${source.name} copy")
                            profileStore.saveNtripCasterUploadProfiles(profiles + copy)
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_CASTER_UPLOAD, copy.id)
                            profileRevision++
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onRename = { id, name -> renameProfile(ProfileKind.NTRIP_CASTER_UPLOAD, id, name) },
                    onDelete = { id -> deleteProfile(ProfileKind.NTRIP_CASTER_UPLOAD, id) },
                    onAdd = {
                        val profile = NtripCasterUploadProfile(
                            id = profileStore.duplicateId("caster-upload"),
                            name = "New caster upload",
                        )
                        profileStore.saveNtripCasterUploadProfiles(profileStore.ntripCasterUploadProfiles() + profile)
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_CASTER_UPLOAD, profile.id)
                        profileRevision++
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                    supportsSelection = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
                        ?.isOptionLocked(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD) != true,
                )
                AppScreen.NTRIP_MOUNTPOINT_PROFILES -> ProfileListScreen(
                    title = "NTRIP mountpoints",
                    rows = profileStore.ntripMountpointProfiles().map {
                        it.profileRow(profileStore.ntripCasterProfiles())
                    },
                    onSelect = {},
                    onEdit = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_MOUNTPOINT, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onCopy = { id ->
                        val profiles = profileStore.ntripMountpointProfiles()
                        profiles.firstOrNull { it.id == id }?.let { source ->
                            val copy = source.copyProfile(profileStore.duplicateId("mount"), "${source.name} copy")
                            profileStore.saveNtripMountpointProfiles(
                                profiles + copy,
                            )
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_MOUNTPOINT, copy.id)
                            profileRevision++
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onRename = { id, name -> renameProfile(ProfileKind.NTRIP_MOUNTPOINT, id, name) },
                    onDelete = { id -> deleteProfile(ProfileKind.NTRIP_MOUNTPOINT, id) },
                    onAdd = {
                        val casterId = profileStore.ntripCasterProfiles().firstOrNull()?.id ?: "ntrip-caster-default"
                        val profile = NtripMountpointProfile(
                            id = profileStore.duplicateId("mount"),
                            name = "New mountpoint",
                            casterProfileId = casterId,
                        )
                        profileStore.saveNtripMountpointProfiles(profileStore.ntripMountpointProfiles() + profile)
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_MOUNTPOINT, profile.id)
                        profileRevision++
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                    supportsSelection = false,
                )
                AppScreen.COMMANDS -> ProfileListScreen(
                    title = "Init/shutdown profiles",
                    rows = filteredCommandProfileRows(
                        profiles = profileStore.commandProfiles(),
                        selectedCommandProfileId = settingsSets
                            .firstOrNull { it.id == selectedSettingsSetId }
                            ?.effectiveCommandProfileRef()
                            ?.id,
                        filter = selectedDeviceFilter,
                    ),
                    onSelect = {},
                    onEdit = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.COMMANDS, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onCopy = { id ->
                        val profiles = profileStore.commandProfiles()
                        profiles.firstOrNull { it.id == id }?.let { source ->
                            val copy = source.copyProfile(profileStore.duplicateId("commands"), "${source.name} copy")
                            profileStore.saveCommandProfiles(
                                profiles + copy,
                            )
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.COMMANDS, copy.id)
                            profileRevision++
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onRename = { id, name -> renameProfile(ProfileKind.COMMANDS, id, name) },
                    onDelete = { id -> deleteProfile(ProfileKind.COMMANDS, id) },
                    onAdd = {
                        val profile = CommandProfile(
                            id = profileStore.duplicateId("commands"),
                            name = "New command profile",
                            runtimeScript = ProfileStores.UM980_BINARY_MULTI_HZ_SCRIPT,
                        )
                        profileStore.saveCommandProfiles(profileStore.commandProfiles() + profile)
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.COMMANDS, profile.id)
                        profileRevision++
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                    supportsSelection = false,
                )
                AppScreen.RECORDING_OUTPUTS -> ProfileListScreen(
                    title = "Recording outputs",
                    rows = profileStore.recordingPolicyProfiles().map { it.profileRow() },
                    onSelect = {},
                    onEdit = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.RECORDING_OUTPUTS, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onCopy = { id ->
                        val profiles = profileStore.recordingPolicyProfiles()
                        profiles.firstOrNull { it.id == id }?.let { source ->
                            val copy = source.copyProfile(profileStore.duplicateId("recording"), "${source.name} copy")
                            profileStore.saveRecordingPolicyProfiles(
                                profiles + copy,
                            )
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.RECORDING_OUTPUTS, copy.id)
                            profileRevision++
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onRename = { id, name -> renameProfile(ProfileKind.RECORDING_OUTPUTS, id, name) },
                    onDelete = { id -> deleteProfile(ProfileKind.RECORDING_OUTPUTS, id) },
                    onAdd = {
                        val profile = RecordingPolicyProfile(
                            id = profileStore.duplicateId("recording"),
                            name = "New recording output",
                        )
                        profileStore.saveRecordingPolicyProfiles(profileStore.recordingPolicyProfiles() + profile)
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.RECORDING_OUTPUTS, profile.id)
                        profileRevision++
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                    supportsSelection = false,
                )
                AppScreen.RTKLIB_PROFILES -> ProfileListScreen(
                    title = "RTKLIB profiles",
                    rows = profileStore.rtklibProfiles().map { it.profileRow() },
                    onSelect = {},
                    onEdit = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.RTKLIB, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onCopy = { id ->
                        val profiles = profileStore.rtklibProfiles()
                        profiles.firstOrNull { it.id == id }?.let { source ->
                            val copy = source.copyProfile(profileStore.duplicateId("rtklib"), "${source.name} copy")
                            profileStore.saveRtklibProfiles(profiles + copy)
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.RTKLIB, copy.id)
                            profileRevision++
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onRename = { id, name -> renameProfile(ProfileKind.RTKLIB, id, name) },
                    onDelete = { id -> deleteProfile(ProfileKind.RTKLIB, id) },
                    onAdd = {
                        val profile = RtklibProfile(
                            id = profileStore.duplicateId("rtklib"),
                            name = "New RTKLIB profile",
                        )
                        profileStore.saveRtklibProfiles(profileStore.rtklibProfiles() + profile)
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.RTKLIB, profile.id)
                        profileRevision++
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                    supportsSelection = false,
                )
                AppScreen.SOLUTION_POLICIES -> ProfileListScreen(
                    title = "Solution and mock policy",
                    rows = profileStore.solutionPolicyProfiles().map { it.profileRow() },
                    onSelect = {},
                    onEdit = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.SOLUTION_POLICY, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onCopy = { id ->
                        val profiles = profileStore.solutionPolicyProfiles()
                        profiles.firstOrNull { it.id == id }?.let { source ->
                            val copy = source.copyProfile(profileStore.duplicateId("solution"), "${source.name} copy")
                            profileStore.saveSolutionPolicyProfiles(profiles + copy)
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.SOLUTION_POLICY, copy.id)
                            profileRevision++
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onRename = { id, name -> renameProfile(ProfileKind.SOLUTION_POLICY, id, name) },
                    onDelete = { id -> deleteProfile(ProfileKind.SOLUTION_POLICY, id) },
                    onAdd = {
                        val profile = SolutionPolicyProfile(
                            id = profileStore.duplicateId("solution"),
                            name = "New solution policy",
                        )
                        profileStore.saveSolutionPolicyProfiles(profileStore.solutionPolicyProfiles() + profile)
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.SOLUTION_POLICY, profile.id)
                        profileRevision++
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                    supportsSelection = false,
                )
                AppScreen.STORAGE -> ProfileListScreen(
                    title = "Storage location profiles",
                    rows = profileStore.storageProfiles().map { it.profileRow() },
                    onSelect = {},
                    onEdit = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.STORAGE, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onCopy = { id ->
                        val profiles = profileStore.storageProfiles()
                        profiles.firstOrNull { it.id == id }?.let { source ->
                            val copy = source.copyProfile(profileStore.duplicateId("storage"), "${source.name} copy")
                            profileStore.saveStorageProfiles(
                                profiles + copy,
                            )
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.STORAGE, copy.id)
                            profileRevision++
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onRename = { id, name -> renameProfile(ProfileKind.STORAGE, id, name) },
                    onDelete = { id -> deleteProfile(ProfileKind.STORAGE, id) },
                    onAdd = {
                        val profile = StorageProfile(
                            id = profileStore.duplicateId("storage"),
                            name = "New storage location",
                        )
                        profileStore.saveStorageProfiles(profileStore.storageProfiles() + profile)
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.STORAGE, profile.id)
                        profileRevision++
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                    supportsSelection = false,
                )
                AppScreen.USB_BAUD -> ProfileListScreen(
                    title = "USB and baud profiles",
                    rows = profileStore.usbBaudProfiles().map { it.profileRow() },
                    onSelect = {},
                    onEdit = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.USB_BAUD, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onCopy = { id ->
                        val profiles = profileStore.usbBaudProfiles()
                        profiles.firstOrNull { it.id == id }?.let { source ->
                            val copy = source.copyProfile(profileStore.duplicateId("baud"), "${source.name} copy")
                            profileStore.saveUsbBaudProfiles(profiles + copy)
                            profileEditorTarget = ProfileEditorTarget(ProfileKind.USB_BAUD, copy.id)
                            profileRevision++
                            screen = AppScreen.PROFILE_EDITOR
                        }
                    },
                    onRename = { id, name -> renameProfile(ProfileKind.USB_BAUD, id, name) },
                    onDelete = { id -> deleteProfile(ProfileKind.USB_BAUD, id) },
                    onAdd = {
                        val profile = UsbBaudProfile(
                            id = profileStore.duplicateId("baud"),
                            name = "New USB/baud profile",
                        )
                        profileStore.saveUsbBaudProfiles(profileStore.usbBaudProfiles() + profile)
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.USB_BAUD, profile.id)
                        profileRevision++
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                    supportsSelection = false,
                )
                AppScreen.BASE_COORDINATES -> BaseCoordinatesScreen(
                    rows = baseCoordinateStore.coordinates().map { coordinate ->
                        val settingsSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
                        coordinate.profileRow(
                            isSelected = coordinate.id == profileStore.activeProfileId(settingsSet, ActiveSetupOptionKey.BASE_COORDINATE),
                        )
                    },
                    selectionLocked = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
                        ?.isOptionLocked(ActiveSetupOptionKey.BASE_COORDINATE) == true,
                    protectedCoordinateIds = emptySet(),
                    onSelect = { id ->
                        if (!rejectFixedOption(ActiveSetupOptionKey.BASE_COORDINATE)) {
                            chooseActiveOption(ActiveSetupOptionKey.BASE_COORDINATE, id)
                        }
                    },
                    onAdd = {
                        val coordinate = AcceptedBaseCoordinate(
                            id = profileStore.duplicateId("base"),
                            name = "New base coordinate",
                            latDeg = 0.0,
                            lonDeg = 0.0,
                            ellipsoidalHeightM = null,
                            mslAltitudeM = null,
                            geoidSeparationM = null,
                            frame = "UNKNOWN",
                            epoch = null,
                            method = "MANUAL_KNOWN_POINT",
                            durationSeconds = null,
                            horizontalUncertaintyM = null,
                            verticalUncertaintyM = null,
                            antennaHeightM = null,
                            antennaReferencePoint = null,
                            sourceSessionId = null,
                            sourceDescription = "Manual entry",
                        )
                        baseCoordinateStore.upsert(coordinate)
                        baseCoordinateEditorId = coordinate.id
                        screen = AppScreen.BASE_COORDINATE_EDITOR
                    },
                    onImport = {
                        importBasePositionLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                    },
                    onEdit = { id ->
                        baseCoordinateEditorId = id
                        screen = AppScreen.BASE_COORDINATE_EDITOR
                    },
                    onCopy = { id ->
                        baseCoordinateStore.coordinates().firstOrNull { it.id == id }?.let { source ->
                            val copy = source.copy(
                                id = profileStore.duplicateId("base"),
                                name = "${source.name} copy",
                            )
                            baseCoordinateStore.upsert(copy)
                            baseCoordinateEditorId = copy.id
                            screen = AppScreen.BASE_COORDINATE_EDITOR
                        }
                    },
                    onRename = { id, name ->
                        baseCoordinateStore.coordinates().firstOrNull { it.id == id }?.let { coordinate ->
                            confirmSharedEdit(coordinate.name, coordinateUsers(id)) {
                                runCatching {
                                    val renamed = coordinate.copy(name = name.trim()).also { it.validate() }
                                    profileStore.invalidateNextStartConfiguration(coordinateUsers(id).mapTo(linkedSetOf()) { it.id })
                                    baseCoordinateStore.upsert(renamed)
                                    refreshProfileUi()
                                }.onFailure {
                                    Toast.makeText(context, "Cannot rename base coordinate.", Toast.LENGTH_LONG).show()
                                }
                            }
                            true
                        } ?: false
                    },
                    onDelete = { id ->
                        if (coordinateUsers(id).isEmpty()) {
                            baseCoordinateStore.delete(id)
                            profileRevision++
                        } else {
                            Toast.makeText(context, "This coordinate is referenced by a settings set. Select another coordinate there before deleting it.", Toast.LENGTH_LONG).show()
                        }
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                )
                AppScreen.BASE_COORDINATE_EDITOR -> {
                    val coordinate = baseCoordinateEditorId?.let { id ->
                        baseCoordinateStore.coordinates().firstOrNull { it.id == id }
                    }
                    if (coordinate == null) {
                        screen = AppScreen.BASE_COORDINATES
                    } else {
                        BaseCoordinateEditorScreen(
                            coordinate = coordinate,
                            onBack = { screen = AppScreen.BASE_COORDINATES },
                            onSave = { updated ->
                                confirmSharedEdit(updated.name, coordinateUsers(updated.id)) {
                                  runCatching {
                                    updated.validate()
                                    profileStore.invalidateNextStartConfiguration(coordinateUsers(updated.id).mapTo(linkedSetOf()) { it.id })
                                    baseCoordinateStore.upsert(updated)
                                    refreshProfileUi()
                                    screen = AppScreen.BASE_COORDINATES
                                  }.onFailure { error ->
                                    Toast.makeText(context, "Cannot save base coordinate: ${error.message}", Toast.LENGTH_LONG).show()
                                  }
                                }
                            },
                            onExport = {
                                shareBasePositionJson(context, coordinate)
                            },
                        )
                    }
                }
                AppScreen.SETTINGS_SET_SELECTOR -> ProfileListScreen(
                    title = "Select workflow/settings",
                    rows = filteredSettingsSetRows(settingsSets, selectedSettingsSetId, selectedDeviceFilter, profileStore),
                    onSelect = { id ->
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing workflow.", Toast.LENGTH_LONG).show()
                            screen = AppScreen.HOME
                        } else {
                            activateSettingsSet(id)
                            screen = AppScreen.HOME
                        }
                    },
                    onEdit = {},
                    onCopy = {},
                    onRename = { _, _ -> false },
                    onDelete = {},
                    onAdd = {},
                    onBack = { screen = AppScreen.HOME },
                    supportsSelection = true,
                    showManagementActions = false,
                )
                AppScreen.MOUNTPOINT_SELECTOR -> ProfileListScreen(
                    title = "Select NTRIP mountpoint",
                    rows = settingsSets.firstOrNull { set -> set.id == selectedSettingsSetId }
                        ?.selectableNtripMountpoints(profileStore.ntripMountpointProfiles())
                        .orEmpty().map {
                        it.profileRow(
                            casters = profileStore.ntripCasterProfiles(),
                            isSelected = it.id == profileStore.activeProfileId(
                                settingsSets.firstOrNull { set -> set.id == selectedSettingsSetId }, ActiveSetupOptionKey.NTRIP_MOUNTPOINT),
                        )
                    },
                    onSelect = { id ->
                        if (startInProgress || rejectFixedOption(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)) {
                            if (startInProgress) {
                                Toast.makeText(context, "Wait for recording to start before changing setup.", Toast.LENGTH_LONG).show()
                            }
                            screen = AppScreen.HOME
                        } else {
                        profileStore.ntripMountpointProfiles().firstOrNull { it.id == id }?.let(::selectNtripMountpoint)
                        screen = AppScreen.HOME
                        }
                    },
                    onEdit = {},
                    onCopy = {},
                    onRename = { _, _ -> false },
                    onDelete = {},
                    onAdd = {},
                    onBack = { screen = AppScreen.HOME },
                    supportsSelection = true,
                    showManagementActions = false,
                )
                AppScreen.COMMAND_SELECTOR -> ProfileListScreen(
                    title = "Select init/shutdown profile",
                    rows = filteredCommandProfileRows(
                        profiles = profileStore.commandProfiles(),
                        selectedCommandProfileId = profileStore.activeProfileId(
                            settingsSets.firstOrNull { set -> set.id == selectedSettingsSetId }, ActiveSetupOptionKey.RECEIVER_COMMAND),
                        filter = selectedDeviceFilter,
                    ),
                    onSelect = { id ->
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing init/shutdown profiles.", Toast.LENGTH_LONG).show()
                            screen = AppScreen.HOME
                        } else if (rejectFixedOption(ActiveSetupOptionKey.RECEIVER_COMMAND)) {
                            screen = AppScreen.HOME
                        } else {
                            profileStore.commandProfiles().firstOrNull { it.id == id }?.let { profile ->
                                manualBaseCoordinate = null
                                chooseActiveOption(ActiveSetupOptionKey.RECEIVER_COMMAND, profile.id)
                            }
                            screen = AppScreen.HOME
                        }
                    },
                    onEdit = {},
                    onCopy = {},
                    onRename = { _, _ -> false },
                    onDelete = {},
                    onAdd = {},
                    onBack = { screen = AppScreen.HOME },
                    supportsSelection = true,
                    showManagementActions = false,
                )
                AppScreen.STORAGE_SELECTOR -> ProfileListScreen(
                    title = "Select storage profile",
                    rows = profileStore.storageProfiles().map {
                        it.profileRow(isSelected = it.id == profileStore.activeProfileId(
                            settingsSets.firstOrNull { set -> set.id == selectedSettingsSetId }, ActiveSetupOptionKey.STORAGE))
                    },
                    onSelect = { id ->
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing storage.", Toast.LENGTH_LONG).show()
                            screen = AppScreen.HOME
                        } else if (rejectFixedOption(ActiveSetupOptionKey.STORAGE)) {
                            screen = AppScreen.HOME
                        } else {
                            profileStore.storageProfiles().firstOrNull { it.id == id }?.let { profile ->
                                chooseActiveOption(ActiveSetupOptionKey.STORAGE, profile.id)
                            }
                            screen = AppScreen.HOME
                        }
                    },
                    onEdit = {},
                    onCopy = {},
                    onRename = { _, _ -> false },
                    onDelete = {},
                    onAdd = {},
                    onBack = { screen = AppScreen.HOME },
                    supportsSelection = true,
                    showManagementActions = false,
                )
                AppScreen.SESSIONS -> SessionsScreen(
                    state = sessionBrowserState,
                    progressText = zipProgressText,
                    progressFraction = sessionProgressFraction,
                    onToggle = { id -> sessionBrowserState = sessionBrowserState.toggle(id) },
                    onSelectCurrent = { sessionBrowserState = sessionBrowserState.selectCurrent() },
                    onSelectRecordings = { sessionBrowserState = sessionBrowserState.selectRecordings() },
                    onSelectArchives = { sessionBrowserState = sessionBrowserState.selectArchives() },
                    onSelectAll = { sessionBrowserState = sessionBrowserState.toggleSelectAll() },
                    onClearSelection = { sessionBrowserState = sessionBrowserState.clearSelection() },
                    onShareSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canShareZip)
                        if (selected.isEmpty()) {
                            Toast.makeText(context, "Select at least one completed recording.", Toast.LENGTH_LONG).show()
                        } else {
                            zipProgressText = "Preparing ZIP..."
                            sessionProgressFraction = 0f
                            Thread {
                                runCatching {
                                    val cacheRoot = context.cacheDir.resolve("session-share-zips").toPath()
                                    SessionArchiveManager.cleanupTemporaryShareZips(cacheRoot)
                                    selected.mapIndexed { index, entry ->
                                        if (entry.isSafLocation) {
                                            SafSessionActions.createTemporaryShareZip(
                                                resolver = context.contentResolver,
                                                sessionUri = Uri.parse(entry.location),
                                                cacheRoot = cacheRoot,
                                            ) { progress ->
                                                runOnMain(context) {
                                                    zipProgressText = "ZIP ${index + 1}/${selected.size}: ${progress.filesCompleted}/${progress.totalFiles}"
                                                    sessionProgressFraction = progress.fraction.toFloat()
                                                }
                                            }
                                        } else {
                                            val source = Paths.get(entry.location)
                                            SessionArchiveManager.createTemporaryShareZip(source, cacheRoot) { progress ->
                                                runOnMain(context) {
                                                    zipProgressText = "ZIP ${index + 1}/${selected.size}: ${progress.filesCompleted}/${progress.totalFiles}"
                                                    sessionProgressFraction = progress.fraction.toFloat()
                                                }
                                            }
                                        }
                                    }
                                }.onSuccess { zips ->
                                    runOnMain(context) {
                                        zipProgressText = null
                                        sessionProgressFraction = null
                                        shareZipFiles(context, zips.map { it.toFile() })
                                        refreshSessions()
                                    }
                                }.onFailure { error ->
                                    runOnMain(context) {
                                        zipProgressText = null
                                        sessionProgressFraction = null
                                        Toast.makeText(context, "Share ZIP failed: ${error.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }.start()
                        }
                    },
                    onShareNmeaSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canShareNmea)
                        if (selected.isEmpty()) {
                            Toast.makeText(context, "Select at least one completed recording.", Toast.LENGTH_LONG).show()
                        } else {
                            val filesystemSources = selected
                                .filterNot { it.isSafLocation }
                                .flatMap { entry ->
                                    runCatching {
                                        SessionNmeaShareSelection.availableSources(Paths.get(entry.location))
                                    }.getOrDefault(emptyList())
                                }
                            val safSources = selected
                                .filter { it.isSafLocation }
                                .flatMap { entry ->
                                    SessionNmeaSource.entries.filter { source ->
                                        SafSessionActions.hasNonEmptyChild(
                                            resolver = context.contentResolver,
                                            sessionUri = Uri.parse(entry.location),
                                            fileName = source.artifactFileName,
                                        )
                                    }
                                }
                            val sources = (filesystemSources + safSources).distinct()
                            when {
                                sources.isEmpty() -> Toast.makeText(
                                    context,
                                    "No recorded NMEA file is available for the selected session(s).",
                                    Toast.LENGTH_LONG,
                                ).show()
                                sources.size == 1 -> shareNmeaFromEntries(
                                    context = context,
                                    entries = selected,
                                    requestedSources = sources.toSet(),
                                    refreshSessions = ::refreshSessions,
                                    setProgress = { text, fraction ->
                                        zipProgressText = text
                                        sessionProgressFraction = fraction
                                    },
                                )
                                else -> {
                                    pendingNmeaEntries = selected
                                    pendingNmeaSources = sources
                                }
                            }
                        }
                    },
                    onReexportNmeaSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canReexportNmea)
                        if (selected.isEmpty()) {
                            Toast.makeText(context, "Select at least one completed recording.", Toast.LENGTH_LONG).show()
                        } else {
                            zipProgressText = "Re-exporting NMEA..."
                            sessionProgressFraction = 0f
                            Thread {
                                val pppGgaQuality = currentPppNmeaGgaQuality(
                                    profileStore = profileStore,
                                    settingsSets = settingsSets,
                                    selectedSettingsSetId = selectedSettingsSetId,
                                )
                                val options = Um980NmeaExportOptions(pppGgaQuality = pppGgaQuality)
                                var reexported = 0
                                var skipped = 0
                                var failed = 0
                                selected.forEachIndexed { index, entry ->
                                    runOnMain(context) {
                                        zipProgressText = "Re-export NMEA ${index + 1}/${selected.size}"
                                        sessionProgressFraction = index.toFloat() / selected.size.toFloat()
                                    }
                                    if (entry.isSafLocation) {
                                        runCatching {
                                            val sentences = SafSessionActions.reexportNmea(
                                                resolver = context.contentResolver,
                                                sessionUri = Uri.parse(entry.location),
                                                receiverFamily = entry.receiverFamily,
                                                options = options,
                                            ) { progress ->
                                                val withinSession = progress.fraction?.toFloat() ?: 0f
                                                runOnMain(context) {
                                                    zipProgressText =
                                                        "Re-export NMEA ${index + 1}/${selected.size}: ${formatBytes(progress.bytesRead)}"
                                                    sessionProgressFraction =
                                                        (index.toFloat() + withinSession) / selected.size.toFloat()
                                                }
                                            }
                                            if (sentences == 0L) skipped++ else reexported++
                                        }.onFailure {
                                            failed++
                                        }
                                    } else {
                                        val sessionDirectory = Paths.get(entry.location)
                                        val receiverRxRaw = sessionDirectory.resolve("receiver-rx.raw")
                                        if (!Files.isRegularFile(receiverRxRaw)) {
                                            skipped++
                                        } else {
                                            runCatching {
                                                ReceiverNmeaReexporter.reexportReceiverRxRaw(
                                                    receiverRxRaw = receiverRxRaw,
                                                    outputNmea = sessionDirectory.resolve("receiver-solution.nmea"),
                                                    receiverFamily = entry.receiverFamily,
                                                    options = options,
                                                )
                                            }.onSuccess {
                                                reexported++
                                            }.onFailure {
                                                failed++
                                            }
                                        }
                                    }
                                }
                                runOnMain(context) {
                                    zipProgressText = null
                                    sessionProgressFraction = null
                                    refreshSessions()
                                    Toast.makeText(
                                        context,
                                        "Re-exported NMEA for $reexported session(s); skipped $skipped; failed $failed.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }.start()
                        }
                    },
                    onRegenerateRtklibSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canRegenerateRtklib)
                        if (selected.isEmpty()) {
                            Toast.makeText(context, "Select at least one completed recording.", Toast.LENGTH_LONG).show()
                        } else {
                            pendingRtklibPostprocessEntries = selected
                        }
                    },
                    onArchiveSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canArchive)
                        runSessionTask("Archive") {
                            val safTreeUri = selectedSafTreeUri(profileStore, settingsSets, selectedSettingsSetId)
                            selected.forEachIndexed { index, entry ->
                                if (entry.isSafLocation) {
                                    SafSessionActions.archiveSession(
                                        resolver = context.contentResolver,
                                        sessionUri = Uri.parse(entry.location),
                                        rootTreeUri = safTreeUri ?: error("SAF storage tree is required for SAF archive."),
                                        cacheRoot = context.cacheDir.resolve("session-archive-saf").toPath(),
                                    ) { progress ->
                                        runOnMain(context) {
                                            zipProgressText = "Archive ${index + 1}/${selected.size}: ${progress.filesCompleted}/${progress.totalFiles}"
                                            sessionProgressFraction = progress.fraction.toFloat()
                                        }
                                    }
                                } else {
                                    val source = Paths.get(entry.location)
                                    SessionArchiveManager.archiveSession(source) { progress ->
                                        runOnMain(context) {
                                            zipProgressText = "Archive ${index + 1}/${selected.size}: ${progress.filesCompleted}/${progress.totalFiles}"
                                            sessionProgressFraction = progress.fraction.toFloat()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onRestoreSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canRestore)
                        runSessionTask("Restore") {
                            val safTreeUri = selectedSafTreeUri(profileStore, settingsSets, selectedSettingsSetId)
                            selected.forEachIndexed { index, entry ->
                                runOnMain(context) {
                                    zipProgressText = "Restore ${index + 1}/${selected.size}"
                                    sessionProgressFraction = index.toFloat() / selected.size.toFloat()
                                }
                                if (entry.isSafLocation) {
                                    SafSessionActions.restoreArchive(
                                        resolver = context.contentResolver,
                                        archiveUri = Uri.parse(entry.location),
                                        rootTreeUri = safTreeUri ?: error("SAF storage tree is required for SAF restore."),
                                    )
                                } else {
                                    SessionArchiveManager.restoreArchive(Paths.get(entry.location))
                                }
                            }
                        }
                    },
                    onDeleteSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canDelete)
                        runSessionTask("Delete") {
                            selected.forEachIndexed { index, entry ->
                                runOnMain(context) {
                                    zipProgressText = "Delete ${index + 1}/${selected.size}"
                                    sessionProgressFraction = index.toFloat() / selected.size.toFloat()
                                }
                                if (entry.isSafLocation) {
                                    when (entry.kind) {
                                        SessionEntryKind.ARCHIVE -> SafSessionActions.deleteArchive(context.contentResolver, Uri.parse(entry.location))
                                        SessionEntryKind.CURRENT_STOPPED,
                                        SessionEntryKind.RECORDING -> SafSessionActions.deleteRecording(context.contentResolver, Uri.parse(entry.location))
                                        SessionEntryKind.CURRENT_ACTIVE -> Unit
                                    }
                                } else {
                                    when (entry.kind) {
                                        SessionEntryKind.ARCHIVE -> FilesystemSessionBrowser.deleteArchive(Paths.get(entry.location))
                                        SessionEntryKind.CURRENT_STOPPED,
                                        SessionEntryKind.RECORDING -> FilesystemSessionBrowser.deleteRecording(Paths.get(entry.location))
                                        SessionEntryKind.CURRENT_ACTIVE -> Unit
                                    }
                                }
                            }
                        }
                    },
                    onCopyPath = { path ->
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("RtkCollector session path", path))
                        Toast.makeText(context, "Session path copied", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { screen = sessionBrowserEntryPoint.returnScreen() },
                )
                AppScreen.APP_DIAGNOSTICS -> {
                    @Suppress("UNUSED_VARIABLE")
                    val currentDiagnosticsRevision = diagnosticsRevision
                    val diagnosticsStatus = diagnosticsStore.status(
                        runtimeEnabled = diagnosticsSettings.runtimeLoggingEnabled,
                        performanceEnabled = diagnosticsSettings.performanceMonitoringEnabled,
                    )
                    AppDiagnosticsScreen(
                        status = diagnosticsStatus,
                        lastError = diagnosticsError,
                        onRuntimeLoggingChange = { enabled: Boolean ->
                            diagnosticsSettings.runtimeLoggingEnabled = enabled
                            diagnosticsRevision++
                        },
                        onPerformanceMonitoringChange = { enabled: Boolean ->
                            diagnosticsSettings.performanceMonitoringEnabled = enabled
                            diagnosticsRevision++
                        },
                        onShareRuntimeLogs = {
                            runCatching {
                                shareDiagnosticZip(
                                    context,
                                    diagnosticsZipExporter.exportRuntimeLogs(diagnosticsStore, diagnosticsStatus),
                                )
                            }.onSuccess {
                                diagnosticsError = null
                            }.onFailure { error ->
                                diagnosticsError = error.message ?: error.javaClass.simpleName
                                if (runtimeDiagnostics.isEnabled) {
                                    runtimeDiagnostics.record(
                                        RuntimeDiagnosticRecord(
                                            timestampMillis = System.currentTimeMillis(),
                                            category = DiagnosticCategory.APP,
                                            severity = "DEGRADED",
                                            message = "Runtime diagnostics sharing failed: ${diagnosticsError.orEmpty()}",
                                        ),
                                    )
                                }
                            }
                        },
                        onSharePerformanceLogs = {
                            runCatching {
                                shareDiagnosticZip(
                                    context,
                                    diagnosticsZipExporter.exportPerformanceLogs(diagnosticsStore, diagnosticsStatus),
                                )
                            }.onSuccess {
                                diagnosticsError = null
                            }.onFailure { error ->
                                diagnosticsError = error.message ?: error.javaClass.simpleName
                                if (runtimeDiagnostics.isEnabled) {
                                    runtimeDiagnostics.record(
                                        RuntimeDiagnosticRecord(
                                            timestampMillis = System.currentTimeMillis(),
                                            category = DiagnosticCategory.APP,
                                            severity = "DEGRADED",
                                            message = "Performance diagnostics sharing failed: ${diagnosticsError.orEmpty()}",
                                        ),
                                    )
                                }
                            }
                        },
                        onDeleteLogs = {
                            runCatching {
                                diagnosticsStore.deleteDiagnostics()
                                diagnosticsZipExporter.deleteTemporaryShares()
                                diagnosticsRevision++
                                diagnosticsError = null
                            }.onFailure { error ->
                                diagnosticsError = error.message ?: error.javaClass.simpleName
                                if (runtimeDiagnostics.isEnabled) {
                                    runtimeDiagnostics.record(
                                        RuntimeDiagnosticRecord(
                                            timestampMillis = System.currentTimeMillis(),
                                            category = DiagnosticCategory.APP,
                                            severity = "DEGRADED",
                                            message = "Diagnostics deletion failed: ${diagnosticsError.orEmpty()}",
                                        ),
                                    )
                                }
                            }
                        },
                        onBack = { screen = AppScreen.SETTINGS },
                    )
                }
                AppScreen.DEVICE_CONSOLE -> {
                    val usbProfiles = profileStore.usbBaudProfiles()
                    val commandProfiles = profileStore.commandProfiles()
                    val filteredCommandProfiles = commandProfiles
                        .filter { selectedDeviceFilter.matchesCommandProfile(it) }
                        .let { filtered ->
                            val selectedOutsideFilter = commandProfiles.firstOrNull {
                                it.id == selectedConsoleCommandProfileId && filtered.none { profile -> profile.id == it.id }
                            }
                            if (selectedOutsideFilter == null) filtered else listOf(selectedOutsideFilter) + filtered
                        }
                    val selectedUsbProfile =
                        usbProfiles.firstOrNull { it.id == selectedConsoleUsbProfileId } ?: usbProfiles.firstOrNull()
                    val selectedCommandProfile =
                        filteredCommandProfiles.firstOrNull { it.id == selectedConsoleCommandProfileId }
                            ?: filteredCommandProfiles.firstOrNull()

                    DisposableEffect(screen) {
                        onDispose {
                            consoleController?.disconnect()
                            consoleController = null
                        }
                    }

                    DeviceConsoleScreen(
                        state = consoleState.copy(
                            lineEnding = consoleLineEnding,
                            selectedUsbProfileId = selectedUsbProfile?.id,
                            selectedCommandProfileId = selectedCommandProfile?.id,
                        ),
                        recordingActive = state.isRecording,
                        deviceFilterLabel = selectedDeviceFilter.displayName,
                        usbProfiles = usbProfiles.map { DeviceConsoleOption(it.id, it.name) },
                        commandProfiles = filteredCommandProfiles.map { DeviceConsoleOption(it.id, it.name) },
                        selectedUsbProfileId = selectedUsbProfile?.id,
                        selectedCommandProfileId = selectedCommandProfile?.id,
                        inputText = consoleInput,
                        onInputChange = { consoleInput = it },
                        onUsbProfileSelected = { id ->
                            consoleController?.disconnect()
                            consoleController = null
                            selectedConsoleUsbProfileId = id
                            consoleState = DeviceConsoleState(selectedUsbProfileId = id, lineEnding = consoleLineEnding)
                        },
                        onCommandProfileSelected = { id -> selectedConsoleCommandProfileId = id },
                        onLineEndingSelected = { consoleLineEnding = it },
                        onBufferLimitSelected = { bytes ->
                            consoleController?.setBufferLimit(bytes)
                            consoleState = consoleState.copy(bufferLimitBytes = bytes)
                        },
                        onConnect = {
                            val profile = selectedUsbProfile
                            if (profile == null) {
                                Toast.makeText(context, "No USB/baud profile is available.", Toast.LENGTH_LONG).show()
                            } else {
                                val controller = consoleController ?: createDeviceConsoleController(
                                    context = context,
                                    isRecording = { state.isRecording },
                                    usbProfile = profile,
                                    onState = { nextState ->
                                        runOnMain(context) {
                                            consoleState = nextState
                                        }
                                    },
                                ).also { consoleController = it }
                                controller.connect().onFailure { error ->
                                    if (error.message == DEVICE_CONSOLE_PERMISSION_REQUIRED) {
                                        requestUsbPermissionForProfile(context, profile.id)
                                        Toast.makeText(
                                            context,
                                            "Approve USB access, then tap Connect again.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            error.message ?: "Device console could not connect.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            }
                        },
                        onDisconnect = { consoleController?.disconnect() },
                        onSend = {
                            consoleController?.send(consoleInput, consoleLineEnding)?.onFailure { error ->
                                Toast.makeText(context, error.message ?: "Device console send failed.", Toast.LENGTH_LONG).show()
                            }
                        },
                        onSendInit = {
                            val script = selectedCommandProfile?.runtimeScript.orEmpty()
                            if (script.isBlank()) {
                                Toast.makeText(context, "Selected init script is empty.", Toast.LENGTH_LONG).show()
                            } else {
                                consoleController?.send(script, consoleLineEnding)?.onFailure { error ->
                                    Toast.makeText(context, error.message ?: "Init script send failed.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onClearInput = { consoleInput = "" },
                        onPauseToggle = { consoleController?.setPaused(!consoleState.paused) },
                        onCopyOutput = {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("RtkCollector device console output", consoleState.output))
                            Toast.makeText(context, "Console output copied", Toast.LENGTH_SHORT).show()
                        },
                        onClearOutput = { consoleController?.clearOutput() },
                        onBack = {
                            consoleController?.disconnect()
                            consoleController = null
                            screen = AppScreen.SETTINGS
                        },
                    )
                }
                AppScreen.PROFILE_EDITOR -> {
                    val target = profileEditorTarget
                    if (target == null) {
                        screen = AppScreen.SETTINGS
                    } else {
                        val data = profileStore.profileEditorData(
                            target = target,
                            settingsSets = settingsSets,
                            passwordLookup = secretStore::getPassword,
                            usbDeviceChoices = usbDeviceChoices,
                            baseCoordinates = baseCoordinateStore.coordinates(),
                        )
                        ProfileEditorScreen(
                            data = data,
                            actions = buildList {
                                if (target.kind == ProfileKind.SETTINGS_SET && !data.readOnly &&
                                    profileStore.migrationRecovery()[target.id]?.issues?.any {
                                        it.resolution == null && it.reason?.blocksApplicableRoute == true &&
                                            it.disposition != org.rtkcollector.app.profile.LegacyFieldDisposition.DORMANT
                                    } == true) {
                                    add(ProfileEditorAction(label = "Validate and apply migration repairs", onClick = {},
                                        onClickWithValues = { values ->
                                            runCatching {
                                                require(canChangeActiveSetup(state.isRecording, startInProgress)) {
                                                    "Stop recording before applying migration repairs."
                                                }
                                                profileStore.saveProfileEditorData(target, values, settingsSets,
                                                    secretStore::putPassword, baseCoordinateStore.coordinates(), validateMigrationRepair = true)
                                            }.onSuccess { updated ->
                                                refreshProfileUi(updated)
                                                Toast.makeText(context, "Applicable migration repairs validated and saved.", Toast.LENGTH_LONG).show()
                                            }.onFailure { error ->
                                                Toast.makeText(context, "Cannot apply repairs: ${error.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }))
                                }
                                if (target.kind == ProfileKind.USB_BAUD) {
                                    add(
                                        ProfileEditorAction(label = "Refresh USB", onClick = {
                                            profileRevision++
                                        }),
                                    )
                                    add(
                                        ProfileEditorAction(label = "Request USB permission", onClick = {
                                            requestUsbPermissionForProfile(context, target.id)
                                            profileRevision++
                                        }),
                                    )
                                    val usbProfile = profileStore.usbBaudProfiles().firstOrNull { it.id == target.id }
                                    if (usbProfile != null) {
                                        add(
                                            persistentBaudWriteAction(
                                                initialBaud = usbProfile.profileBaud,
                                                targetBaud = usbProfile.serialBaud,
                                                usbDeviceLabel = usbProfile.usbProductName
                                                    ?: usbProfile.usbDeviceName
                                                    ?: "selected USB receiver",
                                                onClickWithValues = { values ->
                                                    writeUsbBaudPersistentlyToDevice(
                                                        context = context,
                                                        usbProfileId = target.id,
                                                        values = values,
                                                        isRecording = state.isRecording,
                                                    )
                                                },
                                            ),
                                        )
                                    }
                                }
                                if (target.kind == ProfileKind.NTRIP_CASTER) {
                                    add(
                                        ProfileEditorAction(
                                            label = RefreshNtripCasterMountpointsLabel,
                                            onClick = {},
                                            onClickWithValues = { values ->
                                                refreshNtripCasterMountpoints(
                                                    context = context,
                                                    targetId = target.id,
                                                    values = values,
                                                    profileStore = profileStore,
                                                    onSuccess = { count ->
                                                        refreshProfileUi()
                                                        Toast.makeText(context, "Fetched $count mountpoints.", Toast.LENGTH_LONG).show()
                                                    },
                                                    onFailure = { message ->
                                                        Toast.makeText(context, "Mountpoint refresh failed: $message", Toast.LENGTH_LONG).show()
                                                    },
                                                )
                                            },
                                        ),
                                    )
                                }
                                if (target.kind == ProfileKind.NTRIP_MOUNTPOINT) {
                                    add(
                                        ProfileEditorAction(
                                            label = RefreshNtripCasterMountpointsLabel,
                                            onClick = {},
                                            onClickWithValues = { values ->
                                                refreshNtripCasterMountpointsForProfileId(
                                                    context = context,
                                                    casterProfileId = values["casterProfileId"].orEmpty(),
                                                    profileStore = profileStore,
                                                    passwordLookup = secretStore::getPassword,
                                                    onSuccess = { count ->
                                                        refreshProfileUi()
                                                        Toast.makeText(context, "Fetched $count mountpoints.", Toast.LENGTH_LONG).show()
                                                    },
                                                    onFailure = { message ->
                                                        Toast.makeText(context, "Mountpoint refresh failed: $message", Toast.LENGTH_LONG).show()
                                                    },
                                                )
                                            },
                                        ),
                                    )
                                }
                                if (target.kind == ProfileKind.COMMANDS) {
                                    add(
                                        persistentReceiverWriteAction(
                                            onClickWithValues = { values ->
                                                writeCommandProfilePersistentlyToDevice(
                                                    context = context,
                                                    settingsSets = settingsSets,
                                                    selectedSettingsSetId = selectedSettingsSetId,
                                                    commandProfileId = target.id,
                                                    runtimeScript = values["runtimeScript"].orEmpty(),
                                                    isRecording = state.isRecording,
                                                )
                                            },
                                        ),
                                    )
                                }
                                if (target.kind == ProfileKind.STORAGE) {
                                    add(
                                        ProfileEditorAction(
                                            label = "Select Android folder",
                                            onClick = {},
                                            onClickWithValues = { values ->
                                                pendingStorageFolderSelection = PendingStorageFolderSelection(target, values)
                                                storageFolderLauncher.launch(null)
                                            },
                                        ),
                                    )
                                }
                                profileEditorDeleteAction(target)?.let { deleteAction ->
                                    add(
                                        deleteAction,
                                    )
                                }
                            },
                            onBack = { screen = target.kind.backScreen() },
                            onSave = { values ->
                                if (!canChangeActiveSetup(state.isRecording, startInProgress) &&
                                    target.kind == ProfileKind.SETTINGS_SET && target.id == selectedSettingsSetId) {
                                    Toast.makeText(context, "Stop recording before editing the active settings set.", Toast.LENGTH_LONG).show()
                                } else confirmSharedEdit(data.title, profileUsers(target)) { runCatching {
                                    profileStore.saveProfileEditorData(
                                        target = target,
                                        values = values,
                                        settingsSets = settingsSets,
                                        savePassword = secretStore::putPassword,
                                        baseCoordinates = baseCoordinateStore.coordinates(),
                                    )
                                }.onSuccess { updated ->
                                    refreshProfileUi(updated)
                                    screen = target.kind.backScreen()
                                }.onFailure { error ->
                                    Toast.makeText(context, "Cannot save profile: ${error.message}", Toast.LENGTH_LONG).show()
                                } }
                            },
                        )
                    }
                }
            }
            pendingSharedEdit?.let { apply ->
                AlertDialog(
                    onDismissRequest = { pendingSharedEdit = null },
                    title = { Text("Edit shared profile?") },
                    text = { Text(pendingSharedEditDescription) },
                    confirmButton = { TextButton(onClick = {
                        pendingSharedEdit = null
                        apply()
                    }) { Text("Save") } },
                    dismissButton = { TextButton(onClick = { pendingSharedEdit = null }) { Text("Cancel") } },
                )
            }
            if (showActiveChoicesDialog) {
                val set = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
                val setup = set?.let { profileStore.resolvedActiveSetup(it, selectedWorkflowId,
                    profileStore.ownershipProfileGraph(baseCoordinateStore.coordinates())) }
                AlertDialog(
                    onDismissRequest = { showActiveChoicesDialog = false },
                    title = { Text("Active profile choices") },
                    text = {
                        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                            setup?.options?.values?.filter { it.key != ActiveSetupOptionKey.NTRIP_CASTER }?.forEach { option ->
                                val graph = profileStore.ownershipProfileGraph(baseCoordinateStore.coordinates())
                                val profileName = option.effectiveValueId?.let { graph.referenceFor(option.key, it)?.name ?: it }
                                val selected = if (option.key == ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD) {
                                    uploadSelectionLabel(
                                        applicable = option.applicable,
                                        enabled = setup.uploadSelection?.enabled.takeUnless { option.selectionPresent == false },
                                        profileName = profileName,
                                    )
                                } else profileName ?: if (option.requiresUserSelection) "Select" else "None"
                                TextButton(
                                    enabled = option.applicable && option.policy != org.rtkcollector.app.profile.SettingsSetOptionPolicy.LOCKED &&
                                        !state.isRecording && !startInProgress,
                                    onClick = {
                                        showActiveChoicesDialog = false
                                        when (option.key) {
                                            ActiveSetupOptionKey.WORKFLOW -> dashboardSelector = DashboardSelector.WORKFLOW
                                            ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> dashboardSelector = DashboardSelector.UPLOAD
                                            else -> activeOptionSelector = option.key
                                        }
                                    },
                                ) {
                                    Text("${option.label}: $selected" + when {
                                        !option.applicable -> " (not applicable)"
                                        option.policy == org.rtkcollector.app.profile.SettingsSetOptionPolicy.LOCKED -> " 🔒"
                                        else -> ""
                                    })
                                }
                                option.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showActiveChoicesDialog = false }) { Text("Close") } },
                )
            }
            activeOptionSelector?.let { key ->
                val set = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
                val graph = profileStore.ownershipProfileGraph(baseCoordinateStore.coordinates())
                val setup = set?.let { profileStore.resolvedActiveSetup(it, selectedWorkflowId, graph) }
                val refs = when (key) {
                    ActiveSetupOptionKey.RECEIVER_COMMAND -> graph.commandProfiles.map { it.id }
                    ActiveSetupOptionKey.USB_BAUD -> graph.usbBaudProfiles.map { it.id }
                    ActiveSetupOptionKey.NTRIP_MOUNTPOINT -> set?.selectableNtripMountpoints(graph.ntripMountpointProfiles).orEmpty().map { it.id }
                    ActiveSetupOptionKey.RTKLIB -> graph.rtklibProfiles.map { it.id }
                    ActiveSetupOptionKey.SOLUTION_POLICY -> graph.solutionPolicyProfiles.map { it.id }
                    ActiveSetupOptionKey.RECORDING_OUTPUT -> graph.recordingOutputProfiles.map { it.id }
                    ActiveSetupOptionKey.STORAGE -> graph.storageProfiles.map { it.id }
                    ActiveSetupOptionKey.BASE_COORDINATE -> graph.baseCoordinates.map { it.id }
                    else -> emptyList()
                }.mapNotNull { graph.referenceFor(key, it) }
                val selected = setup?.option(key)?.effectiveValueId
                ProfileSelectorDialog(
                    title = setup?.option(key)?.label ?: "Select profile",
                    rows = buildList {
                        if (setup?.option(key)?.required == false) add(ProfileListRow("__none__", "None", false, false,
                            isSelected = selected == null && setup.option(key).selectedChoice?.kind == org.rtkcollector.app.profile.SelectionChoiceKind.NONE))
                        refs.forEach { ref -> add(ProfileListRow(ref.id, ref.name, false, false, isSelected = selected == ref.id)) }
                    },
                    onSelect = { id ->
                        runCatching { chooseActiveOption(key, id.takeUnless { it == "__none__" }) }
                            .onFailure { Toast.makeText(context, it.message ?: "Cannot change selection.", Toast.LENGTH_LONG).show() }
                        activeOptionSelector = null
                        showActiveChoicesDialog = true
                    },
                    onDismiss = { activeOptionSelector = null; showActiveChoicesDialog = true },
                )
            }
            pendingSetupSelection?.takeIf { it.failure != null }?.let { pending ->
                AlertDialog(
                    onDismissRequest = { pendingSetupSelection = null },
                    title = { Text("Recording change not applied") },
                    text = { Text("${pending.targetLabel}: ${pending.failure}") },
                    confirmButton = {
                        TextButton(onClick = {
                            val storedReceipt = RecordingSetupBridge.result(pending.request.requestId)
                            val retryDecision = planLiveSetupRetry(
                                original = LiveSetupRequest(
                                    pending.request.requestId,
                                    pending.request.sessionId,
                                    pending.request.expectedRevision,
                                    pending.request.selectionRevision,
                                ),
                                settingsSetId = pending.settingsSetId,
                                running = RecordingSetupBridge.current()?.let {
                                    LiveSetupOwner(it.sessionId, it.settingsSetId, it.revision)
                                },
                                receipt = storedReceipt?.let {
                                    LiveSetupReceipt(it.requestId, it.sessionId, it.settingsSetId.orEmpty(), it.accepted)
                                },
                                freshRequestId = java.util.UUID.randomUUID().toString(),
                                selectionRevision = profileStore.selectionRevision(pending.settingsSetId),
                            )
                            when (retryDecision) {
                                is LiveSetupRetryDecision.Acknowledged -> storedReceipt?.let {
                                    reconcilePendingSetup(pending, it)
                                }
                                is LiveSetupRetryDecision.Blocked -> {
                                    pendingSetupSelection = pending.copy(failure = retryDecision.reason)
                                }
                                is LiveSetupRetryDecision.Dispatch -> runCatching {
                                    val attempt = SetupPatchRequest(
                                        retryDecision.request.requestId,
                                        retryDecision.request.sessionId,
                                        retryDecision.request.expectedRevision,
                                        retryDecision.request.selectionRevision,
                                    )
                                    val retryIntent = pending.retry(attempt)
                                    context.startService(retryIntent)
                                    pendingSetupSelection = pending.copy(
                                        request = attempt,
                                        intent = retryIntent,
                                        failure = null,
                                    )
                                }.onFailure {
                                    pendingSetupSelection = pending.copy(failure = "Retry could not be dispatched.")
                                }
                            }
                        }) { Text("Retry") }
                    },
                    dismissButton = { TextButton(onClick = { pendingSetupSelection = null }) { Text("Discard") } },
                )
            }
            dashboardSelector?.let { selector ->
                val filteredProfileSelector = selector == DashboardSelector.SETTINGS_SET || selector == DashboardSelector.INIT_PROFILES
                ProfileSelectorDialog(
                    title = selector.title,
                    rows = dashboardSelectorRows(
                        selector = selector,
                        profileStore = profileStore,
                        settingsSets = settingsSets,
                        selectedSettingsSetId = selectedSettingsSetId,
                        deviceFilter = selectedDeviceFilter,
                    ),
                    onSelect = { id ->
                        if (startInProgress) {
                            Toast.makeText(context, "Wait for recording to start before changing setup.", Toast.LENGTH_LONG).show()
                            dashboardSelector = null
                        } else if (rejectFixedOption(*selector.lockedOptionKeys().toTypedArray())) {
                            dashboardSelector = null
                        } else {
                        when (selector) {
                            DashboardSelector.WORKFLOW -> {
                                manualBaseCoordinate = null
                                chooseActiveOption(ActiveSetupOptionKey.WORKFLOW, id)
                            }
                            DashboardSelector.DEVICE -> {
                                selectedDeviceFilter = ProfileDeviceFilter.fromStorageValue(id)
                                profileStore.saveSelectedDeviceFilter(selectedDeviceFilter)
                                refreshProfileUi(settingsSets)
                            }
                            DashboardSelector.SETTINGS_SET -> {
                                activateSettingsSet(id)
                            }
                            DashboardSelector.MOUNTPOINT -> {
                                profileStore.ntripMountpointProfiles().firstOrNull { it.id == id }?.let { profile ->
                                    selectNtripMountpoint(profile)
                                }
                            }
                            DashboardSelector.INIT_PROFILES -> {
                                profileStore.commandProfiles().firstOrNull { it.id == id }?.let { profile ->
                                    manualBaseCoordinate = null
                                    chooseActiveOption(ActiveSetupOptionKey.RECEIVER_COMMAND, profile.id)
                                }
                            }
                            DashboardSelector.UPLOAD -> {
                                chooseActiveUpload(id.takeUnless { it == UploadSelectorOffProfileId })
                            }
                            DashboardSelector.STORAGE -> {
                                profileStore.storageProfiles().firstOrNull { it.id == id }?.let { profile ->
                                    chooseActiveOption(ActiveSetupOptionKey.STORAGE, profile.id)
                                }
                            }
                        }
                        dashboardSelector = null
                        }
                    },
                    onDismiss = { dashboardSelector = null },
                    subtitle = if (filteredProfileSelector) {
                        "Device filter: ${selectedDeviceFilter.displayName}"
                    } else {
                        ""
                    },
                    emptyText = if (filteredProfileSelector) {
                        "No ${selectedDeviceFilter.displayName} profiles. Switch Device to Any or create/copy a matching profile."
                    } else {
                        "No selectable profiles."
                    },
                )
            }
            pendingFixedBaseCoordinateChoices?.let { choices ->
                AlertDialog(
                    onDismissRequest = { pendingFixedBaseCoordinateChoices = null },
                    title = { Text("Use which base coordinate?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Choose the coordinate to write into the fixed-base MODE BASE profile.")
                            Text("Average: ${choices.average.displayLabel()}")
                            Text("Current: ${choices.instant.displayLabel()}")
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingFixedBaseCoordinateChoices = null
                                beginFixedBaseHandoff(choices.average)
                            },
                        ) {
                            Text("Use average")
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    pendingFixedBaseCoordinateChoices = null
                                    beginFixedBaseHandoff(choices.instant)
                                },
                            ) {
                                Text("Use current")
                            }
                            TextButton(onClick = { pendingFixedBaseCoordinateChoices = null }) {
                                Text("Cancel")
                            }
                        }
                    },
                )
            }
            pendingFixedBaseCoordinate?.takeIf { pendingFixedBasePublication == null }?.let { coordinate ->
                val baseSettingsSetCandidates = fixedBaseCandidates()
                val selectedCandidate = baseSettingsSetCandidates.firstOrNull {
                    it.settingsSet.id == pendingFixedBaseSettingsSetId
                }
                when (fixedBaseProfileSelectionMode) {
                    FixedBaseProfileSelectionMode.SETTINGS_SET ->
                        ProfileSelectorDialog(
                            title = "Select fixed-base settings set",
                            rows = baseSettingsSetCandidates.map { candidate ->
                                candidate.profileRow(isSelected = candidate.settingsSet.id == pendingFixedBaseSettingsSetId)
                            },
                            onSelect = { id ->
                                pendingFixedBaseSettingsSetId = id
                                pendingFixedBaseCommandAction = baseSettingsSetCandidates.firstOrNull {
                                    it.settingsSet.id == id
                                }?.let { candidate ->
                                    if (candidate.settingsSet.isProtected || candidate.commandProfile?.isProtected == true) {
                                        FixedBaseCommandAction.COPY_COMMAND
                                    } else FixedBaseCommandAction.UPDATE_EXISTING
                                } ?: FixedBaseCommandAction.UPDATE_EXISTING
                                fixedBaseProfileSelectionMode = FixedBaseProfileSelectionMode.CONFIRM
                            },
                            onDismiss = { resetFixedBaseHandoff() },
                            subtitle = "Device filter: ${selectedDeviceFilter.displayName}",
                            emptyText = "No fixed-base settings set with a MODE BASE profile. Switch Device to Any or create/copy a fixed-base set.",
                        )
                    FixedBaseProfileSelectionMode.CONFIRM -> {
                        val candidate = selectedCandidate
                        AlertDialog(
                            onDismissRequest = { fixedBaseProfileSelectionMode = FixedBaseProfileSelectionMode.SETTINGS_SET },
                            title = { Text("Switch to fixed base?") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Coordinate: ${coordinate.displayLabel()}")
                                    if (candidate != null) {
                                        val commandCanUpdate = !candidate.settingsSet.isProtected &&
                                            candidate.commandProfile?.isProtected == false
                                        if (commandCanUpdate) {
                                            SingleChoiceSegmentedButtonRow {
                                                FixedBaseCommandAction.entries.forEachIndexed { index, action ->
                                                    SegmentedButton(
                                                        selected = pendingFixedBaseCommandAction == action,
                                                        onClick = { pendingFixedBaseCommandAction = action },
                                                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                                                    ) { Text(if (action == FixedBaseCommandAction.UPDATE_EXISTING) "Update" else "Copy") }
                                                }
                                            }
                                        }
                                        val actionText = if (candidate.defaultAction == FixedBaseSettingsSetAction.DERIVE_NEW) {
                                            "Derive a new settings set and MODE BASE profile from ${candidate.settingsSet.name}."
                                        } else if (pendingFixedBaseCommandAction == FixedBaseCommandAction.COPY_COMMAND) {
                                            "Copy the MODE BASE profile and update ${candidate.settingsSet.name}."
                                        } else {
                                            "Update ${candidate.settingsSet.name} and its MODE BASE profile."
                                        }
                                        Text("Switching to base settings set with ${candidate.commandProfile?.name.orEmpty()} profile.")
                                        Text(actionText)
                                        if (pendingFixedBaseCommandAction == FixedBaseCommandAction.UPDATE_EXISTING &&
                                            candidate.affectedSettingsSetIds.isNotEmpty()) {
                                            Text("This also changes the command used at next Start by: " +
                                                baseSettingsSetCandidates.map { it.settingsSet }
                                                    .plus(settingsSets)
                                                    .distinctBy { it.id }
                                                    .filter { it.id in candidate.affectedSettingsSetIds }
                                                    .joinToString { it.name })
                                        }
                                        Text(candidate.reason)
                                    } else {
                                        Text("Selected fixed-base settings set is no longer available.")
                                    }
                                    Text("Recording will stop. The app will be ready for Start; Upload is not started automatically.")
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { commitFixedBaseHandoff(coordinate) },
                                    enabled = candidate != null,
                                ) {
                                    Text(if (candidate?.affectedSettingsSetIds?.isNotEmpty() == true &&
                                        pendingFixedBaseCommandAction == FixedBaseCommandAction.UPDATE_EXISTING)
                                        "Update shared profile" else "OK")
                                }
                            },
                            dismissButton = {
                                Row {
                                    TextButton(
                                        onClick = { fixedBaseProfileSelectionMode = FixedBaseProfileSelectionMode.SETTINGS_SET },
                                    ) {
                                        Text("Back")
                                    }
                                    TextButton(onClick = { resetFixedBaseHandoff() }) {
                                        Text("Cancel")
                                    }
                                }
                            },
                        )
                    }
                    null -> {
                        fixedBaseProfileSelectionMode = FixedBaseProfileSelectionMode.SETTINGS_SET
                    }
                }
            }
            if (showMockGpsDialog) {
                MockGpsSelectorDialog(
                    selected = state.mockGps,
                    onSelect = { enabled, rateHz ->
                        updateMockGpsSelection(enabled = enabled, rateHz = rateHz)
                        showMockGpsDialog = false
                    },
                    onDismiss = { showMockGpsDialog = false },
                )
            }
            if (showDashboardLayoutDialog) {
                DashboardLayoutDialog(
                    selected = dashboardLayout,
                    selectedDistanceUnits = dashboardDistanceUnits,
                    selectedSatelliteTheme = satelliteMonitorCardTheme,
                    onSelect = { layout ->
                        dashboardLayout = layout
                        showDashboardLayoutDialog = false
                    },
                    onDistanceUnitsSelect = { unitPreference ->
                        dashboardDistanceUnits = unitPreference
                    },
                    onSatelliteThemeSelect = { theme ->
                        satelliteMonitorCardTheme = theme
                    },
                    onDismiss = { showDashboardLayoutDialog = false },
                )
            }
            if (showReapplySettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showReapplySettingsDialog = false },
                    title = { Text("Re-apply settings set?") },
                    text = {
                        Text("This discards local changes and restores the selected settings set definition.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (!canChangeActiveSetup(state.isRecording, startInProgress)) {
                                    Toast.makeText(context, "Stop recording before re-applying the settings set.", Toast.LENGTH_LONG).show()
                                } else {
                                    reapplySelectedSettingsSet()
                                }
                                showReapplySettingsDialog = false
                            },
                        ) {
                            Text("Re-apply")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showReapplySettingsDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
            if (showSettingsExportDialog) {
                SettingsExportDialog(
                    includePlaintextPasswords = includePlaintextPasswordsInBackup,
                    onIncludePlaintextPasswordsChange = { includePlaintextPasswordsInBackup = it },
                    onExport = {
                        showSettingsExportDialog = false
                        runCatching {
                            shareSettingsBackup(context, includePlaintextPasswordsInBackup)
                        }.onFailure { error ->
                            Toast.makeText(context, "Cannot export settings backup: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onDismiss = { showSettingsExportDialog = false },
                )
            }
        }
    }
}

@Composable
private fun SettingsExportDialog(
    includePlaintextPasswords: Boolean,
    onIncludePlaintextPasswordsChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export settings backup") },
        text = {
            Column {
                Text("Export profiles and active selections for transfer to another phone.")
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(
                        checked = includePlaintextPasswords,
                        onCheckedChange = onIncludePlaintextPasswordsChange,
                    )
                    Text("Include plaintext NTRIP passwords")
                }
                if (includePlaintextPasswords) {
                    Text(
                        text = SettingsSetExportOptions(includePlaintextPasswords = true).passwordWarning.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExport) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DashboardLayoutDialog(
    selected: DashboardLayoutPreference,
    selectedDistanceUnits: DashboardDistanceUnitPreference,
    selectedSatelliteTheme: SatelliteMonitorCardThemePreference,
    onSelect: (DashboardLayoutPreference) -> Unit,
    onDistanceUnitsSelect: (DashboardDistanceUnitPreference) -> Unit,
    onSatelliteThemeSelect: (SatelliteMonitorCardThemePreference) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dashboard layout") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = "Layout",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                DashboardLayoutPreference.entries.forEach { layout ->
                    TextButton(
                        onClick = { onSelect(layout) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val suffix = if (layout == selected) " (selected)" else ""
                        Text("${layout.displayName}$suffix")
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Distance units",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                DashboardDistanceUnitPreference.entries.forEach { unitPreference ->
                    TextButton(
                        onClick = { onDistanceUnitsSelect(unitPreference) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val suffix = if (unitPreference == selectedDistanceUnits) " (selected)" else ""
                        Text("${unitPreference.displayName}$suffix")
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Satellite card",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Dark mode",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = selectedSatelliteTheme == SatelliteMonitorCardThemePreference.DARK,
                        onCheckedChange = { enabled ->
                            onSatelliteThemeSelect(
                                if (enabled) {
                                    SatelliteMonitorCardThemePreference.DARK
                                } else {
                                    SatelliteMonitorCardThemePreference.LIGHT
                                },
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

private fun buildSessionBrowserState(
    context: Context,
    dashboardState: DashboardState,
    profileStore: ProfileStores,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
): SessionBrowserState {
    val selectedSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
    val storageProfile = profileStore.activeProfileId(selectedSet, ActiveSetupOptionKey.STORAGE)
        ?.let { id -> profileStore.storageProfiles().firstOrNull { it.id == id } }
    val sessionLocation = dashboardState.files.sessionLocation.takeUnless { it == "n/a" }
    val appPrivateRoot = context.getExternalFilesDir("sessions") ?: context.filesDir.resolve("sessions")
    return if (storageProfile == null || storageProfile.kind == "APP_PRIVATE") {
        FilesystemSessionBrowser.discover(
            storageRoot = appPrivateRoot.toPath(),
            currentSessionLocation = sessionLocation,
            currentSessionActive = dashboardState.isRecording,
        )
    } else {
        storageProfile.treeUri
            ?.takeIf { it.isNotBlank() }
            ?.let { treeUri ->
                SafSessionBrowser.discover(
                    resolver = context.contentResolver,
                    treeUri = Uri.parse(treeUri),
                    currentSessionLocation = sessionLocation,
                    currentSessionActive = dashboardState.isRecording,
                )
            }
            ?: SessionBrowserState()
    }
}

private fun currentPppNmeaGgaQuality(
    profileStore: ProfileStores,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
): Int {
    val selectedSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
        ?: profileStore.settingsSets().firstOrNull { it.id == selectedSettingsSetId }
    val profileQuality = profileStore.activeProfileId(selectedSet, ActiveSetupOptionKey.RECORDING_OUTPUT)
        ?.let { id -> profileStore.recordingPolicyProfiles().firstOrNull { it.id == id } }
        ?.pppNmeaGgaQuality
        ?: RecordingPolicyProfile.DEFAULT_PPP_NMEA_GGA_QUALITY
    return profileQuality
}

private val SessionBrowserEntry.isSafLocation: Boolean
    get() = location.startsWith("content://")

private fun selectedSafTreeUri(
    profileStore: ProfileStores,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
): Uri? {
    val selectedSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
        ?: profileStore.settingsSets().firstOrNull { it.id == selectedSettingsSetId }
    return profileStore.activeProfileId(selectedSet, ActiveSetupOptionKey.STORAGE)
        ?.let { id -> profileStore.storageProfiles().firstOrNull { it.id == id } }
        ?.takeIf { it.kind == "SAF_TREE" }
        ?.treeUri
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)
}

private fun runOnMain(context: Context, action: () -> Unit) {
    (context as? ComponentActivity)?.runOnUiThread(action) ?: action()
}

private fun refreshNtripCasterMountpoints(
    context: Context,
    targetId: String,
    values: Map<String, String>,
    profileStore: ProfileStores,
    onSuccess: (Int) -> Unit,
    onFailure: (String) -> Unit,
) {
    val host = values["host"].orEmpty().trim()
    val port = values["port"]?.toIntOrNull() ?: 2101
    val username = values["username"].orEmpty().trim()
    val password = values["password"].orEmpty()
    if (host.isBlank()) {
        onFailure("NTRIP host is blank.")
        return
    }
    if (port !in 1..65535) {
        onFailure("NTRIP port must be 1..65535.")
        return
    }
    val existing = profileStore.ntripCasterProfiles().firstOrNull { it.id == targetId }
    if (existing == null) {
        onFailure("NTRIP caster profile no longer exists.")
        return
    }
    val policy = runCatching { existing.securityForEditorRefresh(values, false) }
        .getOrElse { error ->
            onFailure(error.message ?: "NTRIP TLS policy is invalid.")
            return
        }
    val resolvedPassword = password
    val credentials = username.takeIf(String::isNotBlank)?.let {
        NtripCredentials(username = it, password = resolvedPassword)
    }

    Thread(
        {
            runCatching {
                NtripSourcetableClient(
                    NtripSourcetableRequest(
                        policy = policy,
                        credentials = credentials,
                    ),
                ).fetch()
            }.onSuccess { result ->
                runOnMain(context) {
                    val updatedProfiles = profileStore.ntripCasterProfiles().map { profile ->
                        if (profile.id == targetId) {
                            profile.copy(
                                sourcetableMountpoints = result.mountpoints,
                            ).also(NtripCasterProfile::validate)
                        } else {
                            profile
                        }
                    }
                    profileStore.saveNtripCasterProfiles(updatedProfiles)
                    onSuccess(result.mountpoints.size)
                }
            }.onFailure { error ->
                runOnMain(context) {
                    onFailure(error.message ?: error.javaClass.simpleName)
                }
            }
        },
        "rtkcollector-ntrip-caster-refresh",
    ).start()
}

private fun refreshNtripCasterMountpointsForProfileId(
    context: Context,
    casterProfileId: String,
    profileStore: ProfileStores,
    passwordLookup: (String) -> String?,
    onSuccess: (Int) -> Unit,
    onFailure: (String) -> Unit,
) {
    val profile = profileStore.ntripCasterProfiles().firstOrNull { it.id == casterProfileId }
    if (profile == null) {
        onFailure("Select an NTRIP caster profile first.")
        return
    }
    val password = profile.secretId
        .takeIf(String::isNotBlank)
        ?.let(passwordLookup)
        .orEmpty()
    refreshNtripCasterMountpoints(
        context = context,
        targetId = profile.id,
        values = mapOf(
            "host" to profile.host,
            "port" to profile.port.toString(),
            "username" to profile.username,
            "password" to password,
            "transportMode" to profile.transportMode.name,
            "tlsVerification" to profile.tlsVerification.storageValue,
            "unsafeTlsAcknowledged" to profile.unsafeTlsAcknowledged.toString(),
            "requiresTlsVerificationChoice" to profile.requiresTlsVerificationChoice.toString(),
        ),
        profileStore = profileStore,
        onSuccess = onSuccess,
        onFailure = onFailure,
    )
}

private fun buildSettingsBackup(
    context: Context,
    includePlaintextPasswords: Boolean,
): SettingsBackupFile {
    val profileStore = ProfileStores(context)
    val secretStore = NtripSecretStore(context)
    return profileStore.buildCommittedBackup(
        options = SettingsSetExportOptions(includePlaintextPasswords),
        secretStore = secretStore,
    )
}

private fun shareSettingsBackup(context: Context, includePlaintextPasswords: Boolean) {
    val backup = buildSettingsBackup(context, includePlaintextPasswords)
    val directory = context.cacheDir.resolve("settings-backups").also { it.mkdirs() }
    val file = directory.resolve(
        settingsBackupFileName(
            epochMillis = System.currentTimeMillis(),
            includesPlaintextPasswords = includePlaintextPasswords,
        ),
    )
    file.writeText(backup.toJson().toString(2), Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share RtkCollector settings"))
    scheduleSettingsBackupCacheCleanup(directory, file)
}

private fun importSettingsBackup(context: Context, backup: SettingsBackupFile): SettingsImportOutcome {
    ActiveRecordingSessionRegistry.requireNoActiveRecording("import settings")
    val profileStore = ProfileStores(context)
    val importPlan = profileStore.planImportedSettings(
        backup = backup,
        persistedSafTreeUrisWithWriteAccess = context.persistedSafTreeUrisWithWriteAccess(),
    )
    val sanitizedBackup = importPlan.backup
    val importedSettingsSets = sanitizedImportedSettingsSets(sanitizedBackup.settingsSets)
    val importedSelectedSettingsSetId = sanitizedBackup.selectedSettingsSetId
        ?.takeIf { id -> importedSettingsSets.any { it.id == id } }
        ?: importedSettingsSets.first().id
    val secretStore = NtripSecretStore(context)
    profileStore.publishImportedSettings(
        backup = sanitizedBackup,
        settingsSets = importedSettingsSets,
        selectedSettingsSetId = importedSelectedSettingsSetId,
        selectedWorkflowId = restoredWorkflowIdOrNull(sanitizedBackup.selectedWorkflowId),
        lastActiveNtripMountpointProfileId = sanitizedBackup.lastActiveNtripMountpointProfileId
            ?.takeIf { id -> sanitizedBackup.ntripMountpointProfiles.any { it.id == id } },
        secretStore = secretStore,
    )
    return SettingsImportOutcome(
        safTreeUriReselectionCount = importPlan.safTreeUriReselectionCount,
        secretStoreOutcome = SecretStoreImportOutcome.STORED,
    )
}

private fun Context.persistedSafTreeUrisWithWriteAccess(): Set<String> =
    contentResolver.persistedUriPermissions
        .asSequence()
        .filter { permission -> hasPersistedSafTreeAuthority(permission.isReadPermission, permission.isWritePermission) }
        .map { permission -> permission.uri.toString() }
        .toSet()

private fun ProfileStores.retainedSettingsProfileIds(): RetainedSettingsProfileIds =
    RetainedSettingsProfileIds(
        ntripCasterUploadProfileIds = ntripCasterUploadProfiles().mapTo(mutableSetOf()) { it.id },
        rtklibProfileIds = rtklibProfiles().mapTo(mutableSetOf()) { it.id },
        solutionPolicyProfileIds = solutionPolicyProfiles().mapTo(mutableSetOf()) { it.id },
    )

private fun shareZip(context: Context, zipFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share recording ZIP"))
    scheduleTemporaryZipCleanup(listOf(zipFile))
}

private fun scheduleSettingsBackupCacheCleanup(cacheDirectory: File, justShared: File) {
    Handler(Looper.getMainLooper()).postDelayed(
        {
            val now = System.currentTimeMillis()
            runCatching {
                cacheDirectory.listFiles { file ->
                    file.isFile && isSettingsBackupCacheFile(file.name)
                }?.forEach { file ->
                    if (file == justShared || file.lastModified() < now - SETTINGS_BACKUP_CACHE_TTL_MILLIS) {
                        runCatching { file.delete() }
                    }
                }
            }
        },
        SETTINGS_BACKUP_SHARE_CLEANUP_DELAY_MILLIS,
    )
}

private fun shareZipFiles(context: Context, zipFiles: List<File>) {
    if (zipFiles.isEmpty()) return
    if (zipFiles.size == 1) {
        shareZip(context, zipFiles.first())
        return
    }
    val uris = ArrayList(
        zipFiles.map { zipFile ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        },
    )
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "application/zip"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share recording ZIPs"))
    scheduleTemporaryZipCleanup(zipFiles)
}

private fun shareNmeaFiles(context: Context, nmeaFiles: List<File>) {
    if (nmeaFiles.isEmpty()) return
    if (nmeaFiles.size == 1) {
        shareNmea(context, nmeaFiles.first())
        return
    }
    val uris = ArrayList(
        nmeaFiles.map { nmeaFile ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", nmeaFile)
        },
    )
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "text/plain"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share recording NMEA files"))
    scheduleTemporaryNmeaCleanup(nmeaFiles)
}

private fun shareNmeaFromEntries(
    context: Context,
    entries: List<SessionBrowserEntry>,
    requestedSources: Set<SessionNmeaSource>?,
    refreshSessions: () -> Unit,
    setProgress: (String?, Float?) -> Unit,
) {
    if (entries.isEmpty()) return
    setProgress("Preparing NMEA...", 0f)
    Thread {
        runCatching {
            val cacheRoot = context.cacheDir.resolve("session-share-nmea").toPath()
            cleanupTemporaryNmeaShares(cacheRoot)
            val filesystemEntries = entries.filterNot { it.isSafLocation }
            val safEntries = entries.filter { it.isSafLocation }
            val filesystemSelection = SessionNmeaShareSelection.fromSessionDirectories(
                sessionDirectories = filesystemEntries.map { Paths.get(it.location) },
                outputDirectory = cacheRoot,
                requestedSources = requestedSources,
            )
            val outputs = mutableListOf<Path>()
            try {
                outputs.addAll(
                    SessionNmeaExporter.exportAll(filesystemSelection.plans) { index ->
                        runOnMain(context) {
                            setProgress(
                                "NMEA ${index + 1}/${entries.size}",
                                (index + 1).toFloat() / entries.size.toFloat(),
                            )
                        }
                    },
                )
                var skipped = filesystemSelection.skippedCount
                safEntries.forEachIndexed { index, entry ->
                    val safOutputs = SafSessionActions.createTemporaryNmeaShares(
                        resolver = context.contentResolver,
                        sessionUri = Uri.parse(entry.location),
                        cacheRoot = cacheRoot,
                        requestedSources = requestedSources,
                        useLegacyReceiverName =
                            requestedSources == null || requestedSources == setOf(SessionNmeaSource.RECEIVER_SOLUTION),
                    )
                    if (safOutputs.isEmpty()) {
                        skipped++
                    } else {
                        outputs.addAll(safOutputs)
                    }
                    runOnMain(context) {
                        val completed = filesystemEntries.size + index + 1
                        setProgress("NMEA $completed/${entries.size}", completed.toFloat() / entries.size.toFloat())
                    }
                }
                skipped to outputs
            } catch (error: Throwable) {
                outputs.forEach { path -> runCatching { Files.deleteIfExists(path) } }
                throw error
            }
        }.onSuccess { (skipped, outputs) ->
            runOnMain(context) {
                setProgress(null, null)
                if (outputs.isEmpty()) {
                    Toast.makeText(
                        context,
                        "No recorded NMEA file is available for the selected session(s).",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    shareNmeaFiles(context, outputs.map { it.toFile() })
                    val message = if (skipped > 0) {
                        "Shared NMEA for ${outputs.size} file(s); $skipped selected session(s) had no matching NMEA export."
                    } else {
                        "Shared NMEA for ${outputs.size} file(s)."
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                refreshSessions()
            }
        }.onFailure { error ->
            runOnMain(context) {
                setProgress(null, null)
                Toast.makeText(context, "Share NMEA failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }.start()
}

private fun shareNmea(context: Context, nmeaFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", nmeaFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share recording NMEA"))
    scheduleTemporaryNmeaCleanup(listOf(nmeaFile))
}

private fun shareBasePositionJson(context: Context, coordinate: AcceptedBaseCoordinate) {
    val directory = context.cacheDir.resolve("base-position-share")
    directory.mkdirs()
    val file = directory.resolve("${coordinate.id}.base-position.json")
    file.writeText(BasePositionJsonCodec.encode(coordinate), StandardCharsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share base position JSON"))
}

private fun shareDiagnosticZip(context: Context, zipFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share RtkCollector diagnostics"))
}

private fun showCannotStart(
    context: Context,
    detail: String,
    attributes: Map<String, String> = emptyMap(),
) {
    val message = "Cannot start: $detail"
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    recordAppRuntimeDiagnosticIfEnabled(
        context = context,
        severity = "ERROR",
        message = "Start preflight failed: $detail",
        attributes = attributes,
    )
}

private fun recordAppRuntimeDiagnosticIfEnabled(
    context: Context,
    severity: String,
    message: String,
    attributes: Map<String, String> = emptyMap(),
) {
    runCatching {
        val settings = DiagnosticsSettings(context)
        if (!settings.runtimeLoggingEnabled) return
        DiagnosticsStore(context.filesDir).appendRuntime(
            enabled = true,
            record = RuntimeDiagnosticRecord(
                timestampMillis = System.currentTimeMillis(),
                category = DiagnosticCategory.APP,
                severity = severity,
                message = message,
                attributes = attributes,
            ),
        )
    }
}

private fun scheduleTemporaryZipCleanup(zipFiles: List<File>) {
    Handler(Looper.getMainLooper()).postDelayed(
        {
            runCatching {
                zipFiles.forEach { file ->
                    if (file.parentFile?.name == "session-share-zips") {
                        file.delete()
                    }
                }
            }
        },
        TEMP_SHARE_ZIP_CLEANUP_DELAY_MILLIS,
    )
}

private fun scheduleTemporaryNmeaCleanup(nmeaFiles: List<File>) {
    Handler(Looper.getMainLooper()).postDelayed(
        {
            runCatching {
                nmeaFiles.forEach { file ->
                    if (file.parentFile?.name == "session-share-nmea") {
                        file.delete()
                    }
                }
            }
        },
        TEMP_SHARE_ZIP_CLEANUP_DELAY_MILLIS,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaseCoordinatesScreen(
    rows: List<ProfileListRow>,
    selectionLocked: Boolean,
    protectedCoordinateIds: Set<String>,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onEdit: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRename: (String, String) -> Boolean,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var renameTarget by remember { mutableStateOf<ProfileListRow?>(null) }
    var renameText by remember { mutableStateOf("") }
    renameTarget?.let { row ->
        AlertDialog(
            onDismissRequest = {
                renameTarget = null
                renameText = ""
            },
            title = { Text("Rename base coordinate") },
            text = {
                ProfileSingleLineTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Name",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onRename(row.id, renameText)) {
                            renameTarget = null
                            renameText = ""
                        }
                    },
                    enabled = renameText.trim().isNotBlank() && renameText.trim() != row.name,
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        renameTarget = null
                        renameText = ""
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Base coordinates") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    TextButton(onClick = onImport) {
                        Text("Import")
                    }
                    TextButton(onClick = onAdd) {
                        Text("Add")
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No accepted base coordinates yet.")
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("Add")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.id }) { row ->
                    BaseCoordinateRow(
                        row = row,
                        selectionLocked = selectionLocked,
                        protected = row.id in protectedCoordinateIds,
                        onSelect = { onSelect(row.id) },
                        onEdit = { onEdit(row.id) },
                        onCopy = { onCopy(row.id) },
                        onRename = {
                            renameTarget = row
                            renameText = row.name
                        },
                        onDelete = { onDelete(row.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BaseCoordinateRow(
    row: ProfileListRow,
    selectionLocked: Boolean,
    protected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (row.isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (protected) "${row.displayName} 🔒" else row.displayName,
                style = MaterialTheme.typography.titleSmall)
            Text(row.displaySummary, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!row.isSelected && !selectionLocked) {
                    Button(onClick = onSelect) {
                        Text("Use")
                    }
                }
                TextButton(onClick = onEdit, enabled = !protected) {
                    Text("Edit")
                }
                TextButton(onClick = onCopy) {
                    Text("Copy")
                }
                TextButton(onClick = onRename, enabled = !protected) {
                    Text("Rename")
                }
                TextButton(onClick = onDelete, enabled = !protected) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun BaseCoordinateEditorScreen(
    coordinate: AcceptedBaseCoordinate,
    onBack: () -> Unit,
    onSave: (AcceptedBaseCoordinate) -> Unit,
    onExport: () -> Unit,
) {
    val data = ProfileEditorData(
        title = "Edit base coordinate",
        fields = listOf(
            EditableProfileField("name", "Name", coordinate.name),
            EditableProfileField("latDeg", "Latitude degrees", coordinate.latDeg.toString()),
            EditableProfileField("lonDeg", "Longitude degrees", coordinate.lonDeg.toString()),
            EditableProfileField(
                "ellipsoidalHeightM",
                "Ellipsoidal height meters",
                coordinate.ellipsoidalHeightM?.toString().orEmpty(),
            ),
            EditableProfileField(
                "mslAltitudeM",
                "MSL altitude meters",
                coordinate.mslAltitudeM?.toString().orEmpty(),
            ),
            EditableProfileField(
                "geoidSeparationM",
                "Geoid separation meters",
                coordinate.geoidSeparationM?.toString().orEmpty(),
            ),
            EditableProfileField("frame", "Frame/datum", coordinate.frame),
            EditableProfileField("epoch", "Epoch", coordinate.epoch.orEmpty()),
            EditableProfileField(
                key = "method",
                label = "Method",
                value = coordinate.method,
                optionItems = BASE_POSITION_METHOD_OPTIONS,
            ),
            EditableProfileField("durationSeconds", "Duration seconds", coordinate.durationSeconds?.toString().orEmpty()),
            EditableProfileField(
                "horizontalUncertaintyM",
                "Horizontal uncertainty meters",
                coordinate.horizontalUncertaintyM?.toString().orEmpty(),
            ),
            EditableProfileField(
                "verticalUncertaintyM",
                "Vertical uncertainty meters",
                coordinate.verticalUncertaintyM?.toString().orEmpty(),
            ),
            EditableProfileField("antennaHeightM", "Antenna height meters", coordinate.antennaHeightM?.toString().orEmpty()),
            EditableProfileField("antennaReferencePoint", "Antenna reference point", coordinate.antennaReferencePoint.orEmpty()),
            EditableProfileField("sourceSessionId", "Source session UUID", coordinate.sourceSessionId.orEmpty()),
            EditableProfileField("sourceDescription", "Source description", coordinate.sourceDescription),
        ),
    )
    ProfileEditorScreen(
        data = data,
        onBack = onBack,
        onSave = { values ->
            val form = BaseCoordinateForm(
                id = coordinate.id,
                name = values["name"].orEmpty(),
                latDeg = values["latDeg"].orEmpty(),
                lonDeg = values["lonDeg"].orEmpty(),
                ellipsoidalHeightM = values["ellipsoidalHeightM"].orEmpty(),
                mslAltitudeM = values["mslAltitudeM"].orEmpty(),
                geoidSeparationM = values["geoidSeparationM"].orEmpty(),
                frame = values["frame"].orEmpty(),
                epoch = values["epoch"].orEmpty(),
                method = values["method"].orEmpty(),
                durationSeconds = values["durationSeconds"].orEmpty(),
                horizontalUncertaintyM = values["horizontalUncertaintyM"].orEmpty(),
                verticalUncertaintyM = values["verticalUncertaintyM"].orEmpty(),
                antennaHeightM = values["antennaHeightM"].orEmpty(),
                antennaReferencePoint = values["antennaReferencePoint"].orEmpty(),
                sourceSessionId = values["sourceSessionId"].orEmpty(),
                sourceDescription = values["sourceDescription"].orEmpty(),
            )
            form.toAcceptedBaseCoordinate()
                .onSuccess(onSave)
                .onFailure { error -> throw IllegalArgumentException(error.message ?: "Base coordinate is invalid.") }
        },
        actions = listOf(
            ProfileEditorAction(
                label = "Export JSON",
                onClick = onExport,
            ),
        ),
    )
}

private fun cleanupTemporaryNmeaShares(cacheRoot: Path) {
    runCatching {
        if (Files.isDirectory(cacheRoot)) {
            Files.list(cacheRoot).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().endsWith(".nmea") }
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }
}

private fun ProfileStores.profileEditorData(
    target: ProfileEditorTarget,
    settingsSets: List<RecordingSettingsSet>,
    passwordLookup: (String) -> String?,
    usbDeviceChoices: List<UsbDeviceChoice> = emptyList(),
    baseCoordinates: List<AcceptedBaseCoordinate> = emptyList(),
): ProfileEditorData =
    when (target.kind) {
        ProfileKind.SETTINGS_SET -> settingsSets.first { it.id == target.id }.let { set ->
            ProfileEditorData(
                title = "Edit settings set",
                fields = listOf(
                    EditableProfileField("name", "Name", set.name),
                    EditableProfileField(
                        key = "workflowActivationMode",
                        label = "Workflow when activating this settings set",
                        value = set.workflowActivationMode(),
                        optionItems = WORKFLOW_ACTIVATION_MODE_OPTIONS,
                    ),
                    EditableProfileField(
                        key = "workflowId",
                        label = "Specific workflow",
                        value = set.workflowId,
                        optionItems = WORKFLOW_MODE_OPTIONS,
                    ),
                    EditableProfileField(
                        key = "commandProfileId",
                        label = "Command profile",
                        value = set.commandProfileRef.id,
                        optionItems = commandProfiles().profileOptions(CommandProfile::id, CommandProfile::name),
                    ),
                    EditableProfileField(
                        key = "usbBaudProfileId",
                        label = "USB/baud profile",
                        value = set.usbBaudProfileRef.id,
                        optionItems = usbBaudProfiles().profileOptions(UsbBaudProfile::id, UsbBaudProfile::name),
                    ),
                    EditableProfileField(
                        key = "ntripCasterRestrictionId",
                        label = "Restrict correction sources to caster (optional)",
                        value = set.correctionCasterRestrictionRef()?.id.orEmpty(),
                        optionItems = nullableProfileOptions(ntripCasterProfiles().profileOptions(NtripCasterProfile::id, NtripCasterProfile::name)),
                    ),
                    EditableProfileField(
                        key = "ntripMountpointProfileId",
                        label = "NTRIP mountpoint profile",
                        value = set.ntripMountpointProfileRef?.id.orEmpty(),
                        optionItems = nullableProfileOptions(ntripMountpointProfiles().profileOptions(NtripMountpointProfile::id, NtripMountpointProfile::name)),
                    ),
                    EditableProfileField(
                        key = "baseCasterUploadEnabled",
                        label = "Enable base RTCM caster upload",
                        value = set.baseCasterUploadEnabled.toString(),
                        boolean = true,
                    ),
                    EditableProfileField(
                        key = "ntripCasterUploadProfileId",
                        label = "NTRIP caster upload profile",
                        value = set.ntripCasterUploadProfileRef?.id.orEmpty(),
                        optionItems = nullableProfileOptions(ntripCasterUploadProfiles().profileOptions(NtripCasterUploadProfile::id, NtripCasterUploadProfile::name)),
                    ),
                    EditableProfileField(
                        key = "rtklibProfileId",
                        label = "RTKLIB profile",
                        value = set.rtklibProfileRef?.id.orEmpty(),
                        optionItems = nullableProfileOptions(rtklibProfiles().profileOptions(RtklibProfile::id, RtklibProfile::name)),
                    ),
                    EditableProfileField(
                        key = "solutionPolicyProfileId",
                        label = "Solution and mock policy",
                        value = set.solutionPolicyProfileRef?.id.orEmpty(),
                        optionItems = nullableProfileOptions(solutionPolicyProfiles().profileOptions(SolutionPolicyProfile::id, SolutionPolicyProfile::name)),
                    ),
                    EditableProfileField(
                        key = "recordingOutputProfileId",
                        label = "Recording output profile",
                        value = set.recordingOutputProfileRef.id,
                        optionItems = recordingPolicyProfiles().profileOptions(RecordingPolicyProfile::id, RecordingPolicyProfile::name),
                    ),
                    EditableProfileField(
                        key = "storageProfileId",
                        label = "Storage location profile",
                        value = set.storageProfileRef.id,
                        optionItems = storageProfiles().profileOptions(StorageProfile::id, StorageProfile::name),
                    ),
                    EditableProfileField(
                        key = "baseCoordinateId",
                        label = "Base coordinate",
                        value = set.basePositionProfileRef?.id.orEmpty(),
                        optionItems = nullableProfileOptions(
                            baseCoordinates.profileOptions(AcceptedBaseCoordinate::id, AcceptedBaseCoordinate::name),
                        ),
                    ),
                ) + settingsSetSelectionPolicyFields(set),
            ).asProtectedProfileView(set.isProtected)
        }
        ProfileKind.NTRIP_CASTER -> ntripCasterProfiles().first { it.id == target.id }.let { profile ->
            val storedPassword = profile.secretId.takeIf(String::isNotBlank)?.let(passwordLookup).orEmpty()
            ProfileEditorData(
                title = "Edit NTRIP caster",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("host", "Host", profile.host),
                    EditableProfileField("port", "Port", profile.port.toString()),
                    *ntripSecurityEditorFields(
                        profile.transportMode,
                        profile.tlsVerification,
                        profile.unsafeTlsAcknowledged,
                        false,
                        profile.requiresTlsVerificationChoice,
                        ggaUploadEnabled = ntripMountpointProfiles().any {
                            it.casterProfileId == profile.id && it.ggaUploadPolicy.isNotBlank()
                        },
                    ).toTypedArray(),
                    EditableProfileField("requiresTlsVerificationChoice", "", profile.requiresTlsVerificationChoice.toString(), hidden = true),
                    EditableProfileField("username", "Username", profile.username),
                    EditableProfileField("password", "Password", storedPassword, secret = true),
                    EditableProfileField(
                        key = "protocolPolicy",
                        label = "Protocol policy",
                        value = profile.protocolPolicy,
                        optionItems = NTRIP_PROTOCOL_POLICY_OPTIONS,
                    ),
                    EditableProfileField(
                        key = "sourcetableMountpoints",
                        label = "Known mountpoints",
                        value = "",
                        readOnlyList = profile.sourcetableMountpoints.ifEmpty { listOf("No cached mountpoints") },
                    ),
                ),
            ).asProtectedProfileView(profile.isProtected)
        }
        ProfileKind.NTRIP_CASTER_UPLOAD -> ntripCasterUploadProfiles().first { it.id == target.id }.let { profile ->
            val storedPassword = profile.secretId.takeIf(String::isNotBlank)?.let(passwordLookup).orEmpty()
            ProfileEditorData(
                title = "Edit NTRIP caster upload",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("host", "Host", profile.host),
                    EditableProfileField("port", "Port", profile.port.toString()),
                    *ntripSecurityEditorFields(
                        profile.transportMode,
                        profile.tlsVerification,
                        profile.unsafeTlsAcknowledged,
                        false,
                        profile.requiresTlsVerificationChoice,
                    ).toTypedArray(),
                    EditableProfileField("requiresTlsVerificationChoice", "", profile.requiresTlsVerificationChoice.toString(), hidden = true),
                    EditableProfileField("mountpoint", "Mountpoint", profile.mountpoint),
                    EditableProfileField(
                        "username",
                        "Username",
                        profile.username,
                        sourceUploadUsername = true,
                    ),
                    EditableProfileField(
                        "password",
                        "Source password",
                        storedPassword,
                        secret = true,
                    ),
                    EditableProfileField(
                        key = "protocolPolicy",
                        label = "Source upload protocol",
                        value = profile.protocolPolicy,
                        optionItems = NTRIP_SOURCE_UPLOAD_PROTOCOL_OPTIONS,
                    ),
                    EditableProfileField(
                        key = "retryMode",
                        label = "Retry mode",
                        value = profile.retryMode.name,
                        optionItems = listOf(
                            EditableProfileOption(NtripCasterUploadRetryMode.ADAPTIVE.name, "Adaptive"),
                            EditableProfileOption(NtripCasterUploadRetryMode.FIXED.name, "Fixed"),
                        ),
                    ),
                    EditableProfileField(
                        "fixedReconnectDelaySeconds",
                        "Fixed reconnect delay seconds (minimum 10; values under 10 cannot be entered)",
                        profile.fixedReconnectDelaySeconds.toString(),
                        errorText = "Minimum 10 seconds.".takeIf { profile.fixedReconnectDelaySeconds < 10 },
                    ),
                    EditableProfileField(
                        "adaptiveInitialDelaySeconds",
                        "Adaptive initial delay seconds (minimum 10)",
                        profile.adaptiveInitialDelaySeconds.toString(),
                        errorText = "Minimum 10 seconds.".takeIf { profile.adaptiveInitialDelaySeconds < 10 },
                    ),
                    EditableProfileField(
                        "adaptiveMaxDelaySeconds",
                        "Adaptive maximum delay seconds",
                        profile.adaptiveMaxDelaySeconds.toString(),
                    ),
                    EditableProfileField(
                        key = "stopAfterFailuresEnabled",
                        label = "Stop after repeated failures",
                        value = profile.stopAfterFailuresEnabled.toString(),
                        boolean = true,
                    ),
                    EditableProfileField(
                        "stopAfterConsecutiveFailures",
                        "Failure count before stop",
                        profile.stopAfterConsecutiveFailures.toString(),
                    ),
                    EditableProfileField(
                        key = "safetyRulesEnabled",
                        label = if (profile.isRtk2goHost) {
                            "RTK2go safety rules (required for RTK2go)"
                        } else {
                            "RTK2go safety rules"
                        },
                        value = profile.effectiveSafetyRulesEnabled.toString(),
                        boolean = true,
                        readOnly = profile.isRtk2goHost,
                        casterUploadSafety = true,
                    ),
                    EditableProfileField(
                        "safetyMaxBitrateKbps",
                        "Safety max bitrate kbps",
                        profile.safetyMaxBitrateKbps.toString(),
                    ),
                    EditableProfileField(
                        "safetyBitrateWindowSeconds",
                        "Safety bitrate window seconds",
                        profile.safetyBitrateWindowSeconds.toString(),
                    ),
                    EditableProfileField(
                        "safetyMaxSessionUploadMb",
                        "Safety session upload limit MB",
                        profile.safetyMaxSessionUploadMb.toString(),
                    ),
                    EditableProfileField(
                        key = "enabledByDefault",
                        label = "Enabled by default",
                        value = profile.enabledByDefault.toString(),
                        boolean = true,
                    ),
                ),
            ).asProtectedProfileView(profile.isProtected)
        }
        ProfileKind.NTRIP_MOUNTPOINT -> ntripMountpointProfiles().first { it.id == target.id }.let { profile ->
            val mountpointOptionsByCaster = ntripCasterProfiles().associate { caster ->
                caster.id to caster.sourcetableMountpoints.map { EditableProfileOption(it, it) }
            }
            val selectedCasterMountpoints = mountpointOptionsByCaster[profile.casterProfileId].orEmpty()
            val mountpointError = profile.mountpoint
                .takeIf(String::isNotBlank)
                ?.takeIf { mountpoint ->
                    selectedCasterMountpoints.isNotEmpty() && selectedCasterMountpoints.none { it.value == mountpoint }
                }
                ?.let { "Mountpoint is not in the selected caster sourcetable." }
            ProfileEditorData(
                title = "Edit NTRIP mountpoint",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField(
                        key = "casterProfileId",
                        label = "Caster profile",
                        value = profile.casterProfileId,
                        optionItems = ntripCasterProfiles().profileOptions(NtripCasterProfile::id, NtripCasterProfile::name),
                    ),
                    EditableProfileField(
                        key = "mountpoint",
                        label = "Mountpoint",
                        value = profile.mountpoint,
                        optionItems = selectedCasterMountpoints,
                        optionGroups = mountpointOptionsByCaster,
                        errorText = mountpointError,
                    ),
                    EditableProfileField("ggaUploadPolicy", "GGA upload policy", profile.ggaUploadPolicy),
                    EditableProfileField(
                        key = "expectedFormat",
                        label = "Expected format",
                        value = profile.expectedFormat,
                        optionItems = listOf(
                            EditableProfileOption("RTCM3", "RTCM3"),
                            EditableProfileOption("UNKNOWN", "Unknown"),
                        ),
                    ),
                    EditableProfileField(
                        key = "remoteBaseRawAvailable",
                        label = "Remote base raw available",
                        value = profile.remoteBaseRawAvailable.toString(),
                        boolean = true,
                    ),
                ),
            ).asProtectedProfileView(profile.isProtected)
        }
        ProfileKind.USB_BAUD -> usbBaudProfiles().first { it.id == target.id }.let { profile ->
            val profileUsbChoice = profile.usbDeviceChoice()
            val usbOptions = usbDeviceOptionItems(usbDeviceChoices, profileUsbChoice)
            ProfileEditorData(
                title = "Edit USB and baud profile",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField(
                        key = "profileBaud",
                        label = "Initial receiver baud",
                        value = profile.profileBaud.toString(),
                        options = SELECTABLE_BAUD_RATES,
                    ),
                    EditableProfileField(
                        key = "serialBaud",
                        label = "Target receiver and host baud",
                        value = profile.serialBaud.toString(),
                        options = SELECTABLE_BAUD_RATES,
                    ),
                    EditableProfileField(
                        key = "usbDeviceChoice",
                        label = "USB device",
                        value = profileUsbChoice?.toProfileValue().orEmpty(),
                        optionItems = usbOptions,
                    ),
                ),
            ).asProtectedProfileView(profile.isProtected)
        }
        ProfileKind.COMMANDS -> commandProfiles().first { it.id == target.id }.let { profile ->
            ProfileEditorData(
                title = "Edit command script",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("receiverFamily", "Receiver family", profile.receiverFamily),
                    EditableProfileField(
                        key = "satelliteTelemetry",
                        label = "Satellite telemetry",
                        value = profile.satelliteTelemetry.storageId,
                        optionItems = SATELLITE_TELEMETRY_OPTIONS,
                    ),
                    EditableProfileField("initScript", "Pre-baud init script", profile.initScript, multiline = true),
                    EditableProfileField("runtimeScript", "Post-baud runtime script", profile.runtimeScript, multiline = true),
                    EditableProfileField("shutdownScript", "Shutdown script", profile.shutdownScript, multiline = true),
                ),
            ).asProtectedProfileView(profile.isProtected)
        }
        ProfileKind.RECORDING_OUTPUTS -> recordingPolicyProfiles().first { it.id == target.id }.let { profile ->
            ProfileEditorData(
                title = "Edit recording outputs",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("recordTxToReceiver", "Record app TX to receiver", profile.recordTxToReceiver.toString(), boolean = true),
                    EditableProfileField("recordNtripCorrectionInput", "Record NTRIP correction input", profile.recordNtripCorrectionInput.toString(), boolean = true),
                    EditableProfileField("exportNmea", "Export derived NMEA", profile.exportNmea.toString(), boolean = true),
                    EditableProfileField(
                        key = "pppNmeaGgaQuality",
                        label = "PPP GGA quality in generated NMEA",
                        value = profile.pppNmeaGgaQuality.toString(),
                        optionItems = PPP_NMEA_GGA_QUALITY_OPTIONS,
                    ),
                    EditableProfileField("exportJsonSolution", "Export JSON solution", profile.exportJsonSolution.toString(), boolean = true),
                    EditableProfileField("exportGpx", "Export GPX", profile.exportGpx.toString(), boolean = true),
                    EditableProfileField("enableMockLocation", "Publish Android mock location while recording", profile.enableMockLocation.toString(), boolean = true),
                    EditableProfileField(
                        key = "mockLocationRateHz",
                        label = "Mock location update rate",
                        value = profile.mockLocationRateHz.toString(),
                        optionItems = MOCK_GPS_RATE_OPTIONS,
                    ),
                    EditableProfileField("recordRemoteBaseRaw", "Record remote base raw", profile.recordRemoteBaseRaw.toString(), boolean = true),
                ),
            ).asProtectedProfileView(profile.isProtected)
        }
        ProfileKind.RTKLIB -> rtklibProfiles().first { it.id == target.id }.let { profile ->
            ProfileEditorData(
                title = "Edit RTKLIB profile",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("enabled", "Enable RTKLIB real-time solution", profile.enabled.toString(), boolean = true),
                    EditableProfileField(
                        key = "preset",
                        label = "RTKLIB preset",
                        value = profile.preset,
                        optionItems = listOf(
                            EditableProfileOption(RtklibProfile.PRESET_ROVER_KINEMATIC_RTK, "Rover kinematic RTK"),
                            EditableProfileOption(RtklibProfile.PRESET_TEMPORARY_BASE_STATIC_RTK, "Temporary-base static RTK"),
                        ),
                    ),
                    EditableProfileField("outputNmea", "Write RTKLIB NMEA", profile.outputNmea.toString(), boolean = true),
                    EditableProfileField("outputPos", "Write RTKLIB POS", profile.outputPos.toString(), boolean = true),
                    EditableProfileField("maxRoverQueueBytes", "Rover input queue bytes", profile.maxRoverQueueBytes.toString()),
                    EditableProfileField("maxCorrectionQueueBytes", "Correction input queue bytes", profile.maxCorrectionQueueBytes.toString()),
                    EditableProfileField(
                        key = "frequencyCount",
                        label = "Frequency count",
                        value = profile.frequencyCount.toString(),
                        optionItems = listOf(
                            EditableProfileOption("1", "1"),
                            EditableProfileOption("2", "2"),
                            EditableProfileOption("3", "3"),
                        ),
                    ),
                    EditableProfileField("serverCycleMillis", "Server cycle [ms]", profile.serverCycleMillis.toString()),
                    EditableProfileField("serverBufferBytes", "Server buffer [bytes]", profile.serverBufferBytes.toString()),
                    EditableProfileField("solutionBufferBytes", "Solution buffer [bytes]", profile.solutionBufferBytes.toString()),
                ),
            ).asProtectedProfileView(profile.isProtected)
        }
        ProfileKind.SOLUTION_POLICY -> solutionPolicyProfiles().first { it.id == target.id }.let { profile ->
            val options = SolutionSourcePolicy.entries.map { policy ->
                EditableProfileOption(policy.name, policy.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase))
            }
            ProfileEditorData(
                title = "Edit solution policy",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField(
                        key = "screenPolicy",
                        label = "Dashboard solution source",
                        value = profile.screenPolicy.name,
                        optionItems = options,
                    ),
                    EditableProfileField(
                        key = "mockPolicy",
                        label = "Mock GPS solution source",
                        value = profile.mockPolicy.name,
                        optionItems = options,
                    ),
                ),
            ).asProtectedProfileView(profile.isProtected)
        }
        ProfileKind.STORAGE -> storageProfiles().first { it.id == target.id }.let { profile ->
            ProfileEditorData(
                title = "Edit storage location profile",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField(
                        key = "kind",
                        label = "Storage kind",
                        value = profile.kind,
                        optionItems = listOf(
                            EditableProfileOption("APP_PRIVATE", "App-private storage"),
                            EditableProfileOption("SAF_TREE", "Selected Android folder"),
                        ),
                    ),
                    EditableProfileField("treeUri", "Selected folder URI", profile.treeUri.orEmpty(), readOnly = true),
                ),
            ).asProtectedProfileView(profile.isProtected)
        }
    }

private fun ProfileEditorData.asProtectedProfileView(isProtected: Boolean): ProfileEditorData =
    if (!isProtected) {
        this
    } else {
        copy(
            title = title.replaceFirst("Edit", "View"),
            readOnly = true,
        )
    }

@Composable
private fun MockGpsSelectorDialog(
    selected: MockGpsDashboardState,
    onSelect: (enabled: Boolean, rateHz: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mock GPS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Publish the selected receiver solution through Android mock location while recording.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                MockGpsOptionButton(
                    label = "Off",
                    selected = !selected.enabled,
                    onClick = { onSelect(false, RecordingPolicyProfile.DEFAULT_MOCK_LOCATION_RATE_HZ) },
                )
                MOCK_GPS_RATE_OPTIONS.forEach { option ->
                    val rateHz = option.value.toInt()
                    MockGpsOptionButton(
                        label = option.label,
                        selected = selected.enabled && selected.rateHz == rateHz,
                        onClick = { onSelect(true, rateHz) },
                    )
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

@Composable
private fun MockGpsOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            androidx.compose.material3.ButtonDefaults.buttonColors()
        },
    ) {
        Text(if (selected) "$label · Active" else label)
    }
}

private fun ProfileStores.saveProfileEditorData(
    target: ProfileEditorTarget,
    values: Map<String, String>,
    settingsSets: List<RecordingSettingsSet>,
    savePassword: (String, String) -> Unit,
    baseCoordinates: List<AcceptedBaseCoordinate> = emptyList(),
    validateMigrationRepair: Boolean = false,
): List<RecordingSettingsSet> {
    when (target.kind) {
        ProfileKind.SETTINGS_SET -> {
            val updated = settingsSets.map { set ->
                if (set.id != target.id) {
                    set
                } else {
                    require(!set.isProtected) { "Protected settings sets cannot be edited." }
                    set.copy(
                        name = values.required("name"),
                        workflowId = values.required("workflowId"),
                        commandProfileRef = reference(values.required("commandProfileId"), commandProfiles().map { it.id to it.name }),
                        usbBaudProfileRef = reference(values.required("usbBaudProfileId"), usbBaudProfiles().map { it.id to it.name }),
                        ntripCasterProfileRef = null,
                        ntripCasterRestrictionRef = values.optional("ntripCasterRestrictionId")?.let {
                            reference(it, ntripCasterProfiles().map { profile -> profile.id to profile.name })
                        },
                        ntripMountpointProfileRef = values.optional("ntripMountpointProfileId")?.let {
                            reference(it, ntripMountpointProfiles().map { profile -> profile.id to profile.name })
                        },
                        ntripCasterUploadProfileRef = values.optional("ntripCasterUploadProfileId")?.let {
                            reference(it, ntripCasterUploadProfiles().map { profile -> profile.id to profile.name })
                        },
                        rtklibProfileRef = values.optional("rtklibProfileId")?.let {
                            reference(it, rtklibProfiles().map { profile -> profile.id to profile.name })
                        },
                        solutionPolicyProfileRef = values.optional("solutionPolicyProfileId")?.let {
                            reference(it, solutionPolicyProfiles().map { profile -> profile.id to profile.name })
                        },
                        baseCasterUploadEnabled = values.optional("baseCasterUploadEnabled").toBooleanStrictOrFalse(),
                        recordingOutputProfileRef = reference(
                            values.required("recordingOutputProfileId"),
                            recordingPolicyProfiles().map { it.id to it.name },
                        ),
                        storageProfileRef = reference(values.required("storageProfileId"), storageProfiles().map { it.id to it.name }),
                        basePositionProfileRef = values.optional("baseCoordinateId")?.let {
                            reference(it, baseCoordinates.map { coordinate -> coordinate.id to coordinate.name })
                        },
                    ).withWorkflowActivationMode(values.required("workflowActivationMode"))
                        .withSettingsSetSelectionPolicies(values)
                }
            }
            if (validateMigrationRepair) return saveSettingsSetsWithValidatedRepair(updated, target.id, baseCoordinates)
            saveSettingsSets(updated)
            return updated
        }
        ProfileKind.NTRIP_CASTER -> saveNtripCasterProfiles(
            ntripCasterProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected NTRIP caster profiles cannot be edited." }
                    val secretId = profile.secretId.ifBlank { ntripCasterSecretId(target.id) }
                    val transportMode = values.optional("transportMode")?.let { value ->
                        runCatching { NtripTransportMode.valueOf(value) }.getOrDefault(profile.transportMode)
                    } ?: profile.transportMode
                    val selectedTlsVerification = values.optional("tlsVerification")?.let { value ->
                        runCatching { ntripTlsVerificationFromStorage(value) }.getOrDefault(profile.tlsVerification)
                    } ?: profile.tlsVerification
                    val tlsVerification = if (transportMode == NtripTransportMode.PLAINTEXT) {
                        NtripTlsVerification.SystemTrust
                    } else {
                        selectedTlsVerification
                    }
                    val host = values.optional("host").orEmpty()
                    val port = values.optional("port")?.toIntOrNull() ?: 2101
                    profile.copy(
                        name = values.required("name"),
                        host = host,
                        port = port,
                        username = values.optional("username").orEmpty(),
                        secretId = secretId,
                        transportMode = transportMode,
                        tlsVerification = tlsVerification,
                        unsafeTlsAcknowledged = false,
                        requiresTlsVerificationChoice = profile.requiresTlsVerificationChoice &&
                            values.optional("requiresTlsVerificationChoice") != "false",
                        protocolPolicy = values.optional("protocolPolicy").orEmpty().ifBlank {
                            "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY"
                        },
                        sourcetableMountpoints = values.optional("sourcetableMountpoints")
                            ?.lines()
                            ?.map(String::trim)
                            ?.filter(String::isNotBlank)
                            ?: profile.sourcetableMountpoints,
                    ).let { stageNtripCredentialEdit(it, values["password"], savePassword) }
                } else {
                    profile
                }
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, target.kind, target.id, values.required("name"))
        }
        ProfileKind.NTRIP_CASTER_UPLOAD -> saveNtripCasterUploadProfiles(
            ntripCasterUploadProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected NTRIP caster upload profiles cannot be edited." }
                    val secretId = profile.secretId.ifBlank { ntripCasterUploadSecretId(target.id) }
                    val transportMode = values.optional("transportMode")?.let { value ->
                        runCatching { NtripTransportMode.valueOf(value) }.getOrDefault(profile.transportMode)
                    } ?: profile.transportMode
                    val selectedTlsVerification = values.optional("tlsVerification")?.let { value ->
                        runCatching { ntripTlsVerificationFromStorage(value) }.getOrDefault(profile.tlsVerification)
                    } ?: profile.tlsVerification
                    val tlsVerification = if (transportMode == NtripTransportMode.PLAINTEXT) {
                        NtripTlsVerification.SystemTrust
                    } else {
                        selectedTlsVerification
                    }
                    val host = values.optional("host").orEmpty()
                    val port = values.optional("port")?.toIntOrNull() ?: 2101
                    profile.copy(
                        name = values.required("name"),
                        host = host,
                        port = port,
                        mountpoint = values.optional("mountpoint").orEmpty(),
                        username = values.optional("username").orEmpty(),
                        secretId = secretId,
                        transportMode = transportMode,
                        tlsVerification = tlsVerification,
                        unsafeTlsAcknowledged = false,
                        requiresTlsVerificationChoice = profile.requiresTlsVerificationChoice &&
                            values.optional("requiresTlsVerificationChoice") != "false",
                        protocolPolicy = values.optional("protocolPolicy").orEmpty().ifBlank {
                            "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY"
                        },
                        retryMode = NtripCasterUploadRetryMode.fromStorageValue(values.optional("retryMode")),
                        fixedReconnectDelaySeconds = values.optional("fixedReconnectDelaySeconds")
                            ?.toIntOrNull()
                            ?.coerceAtLeast(10)
                            ?: 10,
                        adaptiveInitialDelaySeconds = values.optional("adaptiveInitialDelaySeconds")
                            ?.toIntOrNull()
                            ?.coerceAtLeast(10)
                            ?: 10,
                        adaptiveMaxDelaySeconds = values.optional("adaptiveMaxDelaySeconds")
                            ?.toIntOrNull()
                            ?: 300,
                        stopAfterFailuresEnabled = values.optional("stopAfterFailuresEnabled").toBooleanStrictOrFalse(),
                        stopAfterConsecutiveFailures = values.optional("stopAfterConsecutiveFailures")
                            ?.toIntOrNull()
                            ?.coerceAtLeast(1)
                            ?: 5,
                        safetyRulesEnabled = values.optional("safetyRulesEnabled").toBooleanStrictOrFalse(),
                        safetyMaxBitrateKbps = values.optional("safetyMaxBitrateKbps")
                            ?.toIntOrNull()
                            ?.coerceAtLeast(1)
                            ?: 35,
                        safetyBitrateWindowSeconds = values.optional("safetyBitrateWindowSeconds")
                            ?.toIntOrNull()
                            ?.coerceAtLeast(1)
                            ?: 60,
                        safetyMaxSessionUploadMb = values.optional("safetyMaxSessionUploadMb")
                            ?.toIntOrNull()
                            ?.coerceAtLeast(1)
                            ?: 500,
                        enabledByDefault = values.optional("enabledByDefault").toBooleanStrictOrFalse(),
                    ).let { stageNtripCredentialEdit(it, values["password"], savePassword) }
                } else {
                    profile
                }
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, target.kind, target.id, values.required("name"))
        }
        ProfileKind.NTRIP_MOUNTPOINT -> {
            val newName = values.required("name")
            val casterProfileId = values.required("casterProfileId")
            require(ntripCasterProfiles().any { it.id == casterProfileId }) { "Selected caster profile is missing." }
            saveNtripMountpointProfiles(
                ntripMountpointProfiles().map { profile ->
                    if (profile.id == target.id) {
                        require(!profile.isProtected) { "Protected NTRIP mountpoint profiles cannot be edited." }
                        profile.copy(
                            name = newName,
                            casterProfileId = casterProfileId,
                            mountpoint = values.optional("mountpoint").orEmpty(),
                            ggaUploadPolicy = values.optional("ggaUploadPolicy").orEmpty(),
                            expectedFormat = values.optional("expectedFormat").orEmpty().ifBlank { "RTCM3" },
                            remoteBaseRawAvailable = values.optional("remoteBaseRawAvailable").toBooleanStrictOrFalse(),
                        )
                    } else {
                        profile
                    }
                },
            )
            return updateSettingsSetReferenceNames(settingsSets, target.kind, target.id, newName)
        }
        ProfileKind.COMMANDS -> saveCommandProfiles(
            commandProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected command profiles cannot be edited." }
                    profile.copy(
                        name = values.required("name"),
                        receiverFamily = values.required("receiverFamily"),
                        satelliteTelemetry = SatelliteTelemetryCapability.fromStorageId(values.optional("satelliteTelemetry")),
                    ).withEditedCommandPhases(values)
                } else {
                    profile
                }
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, target.kind, target.id, values.required("name"))
        }
        ProfileKind.RECORDING_OUTPUTS -> saveRecordingPolicyProfiles(
            recordingPolicyProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected recording profiles cannot be edited." }
                    profile.copy(
                        name = values.required("name"),
                        recordTxToReceiver = values.optional("recordTxToReceiver").toBooleanStrictOrFalse(),
                        recordNtripCorrectionInput = values.optional("recordNtripCorrectionInput").toBooleanStrictOrFalse(),
                        exportNmea = values.optional("exportNmea").toBooleanStrictOrFalse(),
                        pppNmeaGgaQuality = values.optional("pppNmeaGgaQuality")?.toIntOrNull()
                            ?: RecordingPolicyProfile.DEFAULT_PPP_NMEA_GGA_QUALITY,
                        exportJsonSolution = values.optional("exportJsonSolution").toBooleanStrictOrFalse(),
                        exportGpx = values.optional("exportGpx").toBooleanStrictOrFalse(),
                        recordRemoteBaseRaw = values.optional("recordRemoteBaseRaw").toBooleanStrictOrFalse(),
                        enableMockLocation = values.optional("enableMockLocation").toBooleanStrictOrFalse(),
                        mockLocationRateHz = values.optional("mockLocationRateHz")?.toIntOrNull()
                            ?: RecordingPolicyProfile.DEFAULT_MOCK_LOCATION_RATE_HZ,
                    )
                } else {
                    profile
                }
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, target.kind, target.id, values.required("name"))
        }
        ProfileKind.RTKLIB -> saveRtklibProfiles(
            rtklibProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected RTKLIB profiles cannot be edited." }
                    profile.copy(
                        name = values.required("name"),
                        enabled = values.optional("enabled").toBooleanStrictOrFalse(),
                        preset = values.required("preset"),
                        outputNmea = values.optional("outputNmea").toBooleanStrictOrFalse(),
                        outputPos = values.optional("outputPos").toBooleanStrictOrFalse(),
                        maxRoverQueueBytes = values.optional("maxRoverQueueBytes")?.toIntOrNull()
                            ?: RtklibProfile.DEFAULT_MAX_ROVER_QUEUE_BYTES,
                        maxCorrectionQueueBytes = values.optional("maxCorrectionQueueBytes")?.toIntOrNull()
                            ?: RtklibProfile.DEFAULT_MAX_CORRECTION_QUEUE_BYTES,
                        frequencyCount = values.optional("frequencyCount")?.toIntOrNull()
                            ?: RtklibProfile.DEFAULT_FREQUENCY_COUNT,
                        serverCycleMillis = values.optional("serverCycleMillis")?.toIntOrNull()
                            ?: RtklibProfile.DEFAULT_SERVER_CYCLE_MILLIS,
                        serverBufferBytes = values.optional("serverBufferBytes")?.toIntOrNull()
                            ?: RtklibProfile.DEFAULT_SERVER_BUFFER_BYTES,
                        solutionBufferBytes = values.optional("solutionBufferBytes")?.toIntOrNull()
                            ?: RtklibProfile.DEFAULT_SOLUTION_BUFFER_BYTES,
                    )
                } else {
                    profile
                }
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, target.kind, target.id, values.required("name"))
        }
        ProfileKind.SOLUTION_POLICY -> saveSolutionPolicyProfiles(
            solutionPolicyProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected solution policy profiles cannot be edited." }
                    profile.copy(
                        name = values.required("name"),
                        screenPolicy = values.optional("screenPolicy")?.let(SolutionSourcePolicy::valueOf)
                            ?: SolutionSourcePolicy.AUTO_BEST,
                        mockPolicy = values.optional("mockPolicy")?.let(SolutionSourcePolicy::valueOf)
                            ?: SolutionSourcePolicy.AUTO_BEST,
                    )
                } else {
                    profile
                }
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, target.kind, target.id, values.required("name"))
        }
        ProfileKind.STORAGE -> saveStorageProfiles(
            storageProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected storage profiles cannot be edited." }
                    profile.copy(
                        name = values.required("name"),
                        kind = values.required("kind"),
                        treeUri = values.optional("treeUri"),
                        requiresTreeReselection = false,
                    )
                } else {
                    profile
                }
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, target.kind, target.id, values.required("name"))
        }
        ProfileKind.USB_BAUD -> saveUsbBaudProfiles(
            usbBaudProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected USB/baud profiles cannot be edited." }
                    val usbChoice = UsbDeviceChoice.fromProfileValue(values.optional("usbDeviceChoice").orEmpty())
                    profile.copy(
                        name = values.required("name"),
                        profileBaud = values.optional("profileBaud")?.toIntOrNull() ?: 230400,
                        serialBaud = values.optional("serialBaud")?.toIntOrNull() ?: 230400,
                        usbVid = usbChoice?.vendorId,
                        usbPid = usbChoice?.productId,
                        usbDeviceName = usbChoice?.deviceName,
                        usbProductName = usbChoice?.productName,
                    )
                } else {
                    profile
                }
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, target.kind, target.id, values.required("name"))
        }
    }
    return settingsSets
}

private fun ProfileStores.renameProfileData(
    kind: ProfileKind,
    id: String,
    name: String,
    settingsSets: List<RecordingSettingsSet>,
): List<RecordingSettingsSet> {
    val saveName = name.trim()
    when (kind) {
        ProfileKind.SETTINGS_SET -> {
            val updated = renameProfile(
                profiles = settingsSets,
                profileId = id,
                newName = saveName,
                idOf = RecordingSettingsSet::id,
                isProtectedOf = RecordingSettingsSet::isProtected,
                rename = { set, newName -> set.copy(name = newName).also(RecordingSettingsSet::validate) },
            )
            saveSettingsSets(updated)
            return updated
        }
        ProfileKind.NTRIP_CASTER -> saveNtripCasterProfiles(
            renameProfile(ntripCasterProfiles(), id, saveName, NtripCasterProfile::id, NtripCasterProfile::isProtected) { profile, newName ->
                profile.copy(name = newName).also(NtripCasterProfile::validate)
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, kind, id, saveName)
        }
        ProfileKind.NTRIP_CASTER_UPLOAD -> saveNtripCasterUploadProfiles(
            renameProfile(
                ntripCasterUploadProfiles(),
                id,
                saveName,
                NtripCasterUploadProfile::id,
                NtripCasterUploadProfile::isProtected,
            ) { profile, newName ->
                profile.copy(name = newName).also(NtripCasterUploadProfile::validate)
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, kind, id, saveName)
        }
        ProfileKind.NTRIP_MOUNTPOINT -> saveNtripMountpointProfiles(
            renameProfile(ntripMountpointProfiles(), id, saveName, NtripMountpointProfile::id, NtripMountpointProfile::isProtected) { profile, newName ->
                profile.copy(name = newName).also(NtripMountpointProfile::validate)
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, kind, id, saveName)
        }
        ProfileKind.COMMANDS -> saveCommandProfiles(
            renameProfile(commandProfiles(), id, saveName, CommandProfile::id, CommandProfile::isProtected) { profile, newName ->
                profile.copy(name = newName).also(CommandProfile::validate)
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, kind, id, saveName)
        }
        ProfileKind.RECORDING_OUTPUTS -> saveRecordingPolicyProfiles(
            renameProfile(recordingPolicyProfiles(), id, saveName, RecordingPolicyProfile::id, RecordingPolicyProfile::isProtected) { profile, newName ->
                profile.copy(name = newName).also(RecordingPolicyProfile::validate)
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, kind, id, saveName)
        }
        ProfileKind.RTKLIB -> saveRtklibProfiles(
            renameProfile(rtklibProfiles(), id, saveName, RtklibProfile::id, RtklibProfile::isProtected) { profile, newName ->
                profile.copy(name = newName).also(RtklibProfile::validate)
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, kind, id, saveName)
        }
        ProfileKind.SOLUTION_POLICY -> saveSolutionPolicyProfiles(
            renameProfile(solutionPolicyProfiles(), id, saveName, SolutionPolicyProfile::id, SolutionPolicyProfile::isProtected) { profile, newName ->
                profile.copy(name = newName).also(SolutionPolicyProfile::validate)
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, kind, id, saveName)
        }
        ProfileKind.STORAGE -> saveStorageProfiles(
            renameProfile(storageProfiles(), id, saveName, StorageProfile::id, StorageProfile::isProtected) { profile, newName ->
                profile.copy(name = newName).also(StorageProfile::validate)
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, kind, id, saveName)
        }
        ProfileKind.USB_BAUD -> saveUsbBaudProfiles(
            renameProfile(usbBaudProfiles(), id, saveName, UsbBaudProfile::id, UsbBaudProfile::isProtected) { profile, newName ->
                profile.copy(name = newName).also(UsbBaudProfile::validate)
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, kind, id, saveName)
        }
    }
    return settingsSets
}

private fun ProfileStores.updateSettingsSetReferenceNames(
    settingsSets: List<RecordingSettingsSet>,
    kind: ProfileKind,
    id: String,
    name: String,
): List<RecordingSettingsSet> {
    val updated = settingsSets.map { set ->
        if (set.isProtected) return@map set
        when (kind) {
            ProfileKind.COMMANDS -> set.copy(
                commandProfileRef = set.commandProfileRef.renameIfId(id, name),
                overrides = set.overrides.copy(commandProfileRef = set.overrides.commandProfileRef.renameNullableIfId(id, name)),
            )
            ProfileKind.USB_BAUD -> set.copy(
                usbBaudProfileRef = set.usbBaudProfileRef.renameIfId(id, name),
                overrides = set.overrides.copy(usbBaudProfileRef = set.overrides.usbBaudProfileRef.renameNullableIfId(id, name)),
            )
            ProfileKind.NTRIP_CASTER -> set.copy(
                ntripCasterProfileRef = set.ntripCasterProfileRef.renameNullableIfId(id, name),
                overrides = set.overrides.copy(ntripCasterProfileRef = set.overrides.ntripCasterProfileRef.renameNullableIfId(id, name)),
            )
            ProfileKind.NTRIP_CASTER_UPLOAD -> set.copy(
                ntripCasterUploadProfileRef = set.ntripCasterUploadProfileRef.renameNullableIfId(id, name),
                overrides = set.overrides.copy(
                    ntripCasterUploadProfileRef = set.overrides.ntripCasterUploadProfileRef.renameNullableIfId(id, name),
                ),
            )
            ProfileKind.NTRIP_MOUNTPOINT -> set.copy(
                ntripMountpointProfileRef = set.ntripMountpointProfileRef.renameNullableIfId(id, name),
                overrides = set.overrides.copy(
                    ntripMountpointProfileRef = set.overrides.ntripMountpointProfileRef.renameNullableIfId(id, name),
                ),
            )
            ProfileKind.RECORDING_OUTPUTS -> set.copy(
                recordingOutputProfileRef = set.recordingOutputProfileRef.renameIfId(id, name),
                overrides = set.overrides.copy(
                    recordingOutputProfileRef = set.overrides.recordingOutputProfileRef.renameNullableIfId(id, name),
                ),
            )
            ProfileKind.RTKLIB -> set.copy(rtklibProfileRef = set.rtklibProfileRef.renameNullableIfId(id, name))
            ProfileKind.SOLUTION_POLICY -> set.copy(solutionPolicyProfileRef = set.solutionPolicyProfileRef.renameNullableIfId(id, name))
            ProfileKind.STORAGE -> set.copy(
                storageProfileRef = set.storageProfileRef.renameIfId(id, name),
                overrides = set.overrides.copy(storageProfileRef = set.overrides.storageProfileRef.renameNullableIfId(id, name)),
            )
            ProfileKind.SETTINGS_SET -> set
        }.also(RecordingSettingsSet::validate)
    }
    if (updated != settingsSets) saveSettingsSets(updated)
    return updated
}

private fun ProfileReference.renameIfId(id: String, name: String): ProfileReference =
    if (this.id == id) copy(name = name) else this

private fun ProfileReference?.renameNullableIfId(id: String, name: String): ProfileReference? =
    if (this?.id == id) copy(name = name) else this

private fun List<RecordingSettingsSet>.referenceProfile(kind: ProfileKind, id: String): Boolean =
    any { set ->
        when (kind) {
            ProfileKind.COMMANDS -> set.commandProfileRef.id == id
                || set.overrides.commandProfileRef?.id == id
            ProfileKind.USB_BAUD -> set.usbBaudProfileRef.id == id
                || set.overrides.usbBaudProfileRef?.id == id
            ProfileKind.NTRIP_CASTER -> set.ntripCasterProfileRef?.id == id
                || set.overrides.ntripCasterProfileRef?.id == id
            ProfileKind.NTRIP_CASTER_UPLOAD -> set.ntripCasterUploadProfileRef?.id == id
                || set.overrides.ntripCasterUploadProfileRef?.id == id
            ProfileKind.NTRIP_MOUNTPOINT -> set.ntripMountpointProfileRef?.id == id
                || set.overrides.ntripMountpointProfileRef?.id == id
            ProfileKind.RECORDING_OUTPUTS -> set.recordingOutputProfileRef.id == id
                || set.overrides.recordingOutputProfileRef?.id == id
            ProfileKind.RTKLIB -> set.rtklibProfileRef?.id == id
            ProfileKind.SOLUTION_POLICY -> set.solutionPolicyProfileRef?.id == id
            ProfileKind.STORAGE -> set.storageProfileRef.id == id
                || set.overrides.storageProfileRef?.id == id
            ProfileKind.SETTINGS_SET -> false
        }
    }

internal enum class AppScreen {
    HOME,
    SATELLITE_MONITOR,
    CASTER_UPLOAD_MONITOR,
    SETTINGS,
    NTRIP_CASTER,
    NTRIP_CASTER_UPLOAD,
    NTRIP_MOUNTPOINT,
    NTRIP_MOUNTPOINT_PROFILES,
    COMMANDS,
    USB_BAUD,
    RECORDING_OUTPUTS,
    RTKLIB_PROFILES,
    SOLUTION_POLICIES,
    STORAGE,
    BASE_COORDINATES,
    BASE_COORDINATE_EDITOR,
    SESSIONS,
    DEVICE_CONSOLE,
    APP_DIAGNOSTICS,
    SETTINGS_SETS,
    SETTINGS_SET_SELECTOR,
    MOUNTPOINT_SELECTOR,
    COMMAND_SELECTOR,
    STORAGE_SELECTOR,
    PROFILE_EDITOR,
}

private val AppScreenSaver: Saver<AppScreen, String> = Saver(
    save = { it.name },
    restore = { name ->
        runCatching { AppScreen.valueOf(name) }.getOrDefault(AppScreen.HOME)
    },
)

internal enum class SessionBrowserEntryPoint {
    HOME,
    SETTINGS,
}

internal fun SessionBrowserEntryPoint.returnScreen(): AppScreen =
    when (this) {
        SessionBrowserEntryPoint.HOME -> AppScreen.HOME
        SessionBrowserEntryPoint.SETTINGS -> AppScreen.SETTINGS
    }

internal val SessionBrowserEntryPointSaver: Saver<SessionBrowserEntryPoint, String> = Saver(
    save = { it.name },
    restore = { name -> runCatching { SessionBrowserEntryPoint.valueOf(name) }.getOrDefault(SessionBrowserEntryPoint.SETTINGS) },
)

private val SELECTABLE_BAUD_RATES = listOf(
    "4800",
    "9600",
    "14400",
    "19200",
    "38400",
    "57600",
    "115200",
    "128000",
    "230400",
    "256000",
    "460800",
    "921600",
)

private const val WORKFLOW_PLAIN_ROVER = "plain-rover"
private const val WORKFLOW_ROVER_NTRIP = "rover-ntrip"
private const val WORKFLOW_ROVER_RTKLIB = "rover-rtklib"
private const val WORKFLOW_ROVER_NTRIP_RTKLIB = "rover-ntrip-rtklib"
private const val WORKFLOW_BASE_CALIBRATION = "base-calibration"
private const val WORKFLOW_FIXED_BASE = "fixed-base"

private val WORKFLOW_MODE_OPTIONS = listOf(
    EditableProfileOption(WORKFLOW_PLAIN_ROVER, "Plain rover"),
    EditableProfileOption(WORKFLOW_ROVER_NTRIP, "Rover with NTRIP"),
    EditableProfileOption(WORKFLOW_ROVER_RTKLIB, "Rover with RTKLIB"),
    EditableProfileOption(WORKFLOW_ROVER_NTRIP_RTKLIB, "Rover with NTRIP + RTKLIB"),
    EditableProfileOption(WORKFLOW_BASE_CALIBRATION, "Temporary base"),
    EditableProfileOption(WORKFLOW_FIXED_BASE, "Fixed base"),
)

private val SATELLITE_TELEMETRY_OPTIONS = SatelliteTelemetryCapability.entries.map { capability ->
    EditableProfileOption(capability.storageId, capability.displayName)
}

private val WORKFLOW_ACTIVATION_MODE_OPTIONS = listOf(
    EditableProfileOption(
        WorkflowActivationMode.SELECT_CHANGEABLE,
        "Select specific workflow, user can change",
    ),
    EditableProfileOption(
        WorkflowActivationMode.SELECT_LOCKED,
        "Select specific workflow, locked",
    ),
    EditableProfileOption(
        WorkflowActivationMode.LET_USER_SELECT_BEFORE_START,
        "Choose once, remember workflow",
    ),
    EditableProfileOption(
        WorkflowActivationMode.LET_USER_SELECT_EACH_RECORDING,
        "Ask for workflow each recording",
    ),
    EditableProfileOption(
        WorkflowActivationMode.LEAVE_CURRENT_INTACT,
        "Leave current workflow intact",
    ),
)

private val PPP_NMEA_GGA_QUALITY_OPTIONS = listOf(
    EditableProfileOption("2", "2 - DGPS/DGNSS compatibility (default)"),
    EditableProfileOption("5", "5 - RTK float compatibility"),
    EditableProfileOption("9", "9 - GNSS fix compatibility"),
)

private val MOCK_GPS_RATE_OPTIONS = RecordingPolicyProfile.ALLOWED_MOCK_LOCATION_RATES_HZ
    .sorted()
    .map { rateHz -> EditableProfileOption(rateHz.toString(), "$rateHz Hz") }

private val BASE_POSITION_METHOD_OPTIONS = listOf(
    EditableProfileOption("MANUAL_KNOWN_POINT", "Manual / known point"),
    EditableProfileOption("STATIC_RTK", "Static RTK"),
    EditableProfileOption("PPP_STATIC", "PPP/static processing"),
    EditableProfileOption("RECEIVER_PPP", "Receiver PPP"),
    EditableProfileOption("LONG_AVERAGE", "Long average"),
    EditableProfileOption("RECEIVER_SURVEY_IN", "Receiver survey-in"),
    EditableProfileOption("EXTERNAL_BASE_POSITION_JSON", "External base-position.json"),
)

private val NTRIP_PROTOCOL_POLICY_OPTIONS = listOf(
    EditableProfileOption("NTRIP_V2_PREFERRED_WITH_COMPATIBILITY", "NTRIP v2 preferred, v1 fallback"),
    EditableProfileOption("NTRIP_V2_ONLY", "NTRIP v2 only"),
    EditableProfileOption("NTRIP_V1_ONLY", "NTRIP v1 only"),
)

private val NTRIP_SOURCE_UPLOAD_PROTOCOL_OPTIONS = listOf(
    EditableProfileOption("NTRIP_V1_ONLY", "NTRIP v1 source upload (classic)"),
    EditableProfileOption("NTRIP_V2_ONLY", "NTRIP v2 source upload (POST)"),
    EditableProfileOption("NTRIP_V2_PREFERRED_WITH_COMPATIBILITY", "Legacy v2 preferred setting"),
)

private const val ACTION_USB_PERMISSION = "org.rtkcollector.app.USB_PERMISSION"

private enum class ProfileKind {
    SETTINGS_SET,
    NTRIP_CASTER,
    NTRIP_CASTER_UPLOAD,
    NTRIP_MOUNTPOINT,
    USB_BAUD,
    COMMANDS,
    RECORDING_OUTPUTS,
    RTKLIB,
    SOLUTION_POLICY,
    STORAGE,
}

private data class ProfileEditorTarget(
    val kind: ProfileKind,
    val id: String,
)

private val ProfileEditorTargetSaver: Saver<ProfileEditorTarget?, String> = Saver(
    save = { target ->
        target?.let { "${it.kind.name}:${it.id}" } ?: ""
    },
    restore = { saved ->
        val separator = saved.indexOf(':')
        if (separator <= 0 || separator == saved.lastIndex) {
            null
        } else {
            val kind = runCatching { ProfileKind.valueOf(saved.substring(0, separator)) }.getOrNull()
            kind?.let { ProfileEditorTarget(it, saved.substring(separator + 1)) }
        }
    },
)

private enum class DashboardSelector(val title: String) {
    SETTINGS_SET("Load settings set"),
    WORKFLOW("Select workflow"),
    DEVICE("Select device filter"),
    MOUNTPOINT("Select NTRIP mountpoint"),
    INIT_PROFILES("Select init/shutdown profile"),
    UPLOAD("Select NTRIP upload"),
    STORAGE("Select storage profile"),
}

private fun DashboardSelector.lockedOptionKeys(): List<ActiveSetupOptionKey> = when (this) {
    DashboardSelector.WORKFLOW -> listOf(ActiveSetupOptionKey.WORKFLOW)
    DashboardSelector.MOUNTPOINT -> listOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)
    DashboardSelector.INIT_PROFILES -> listOf(ActiveSetupOptionKey.RECEIVER_COMMAND)
    DashboardSelector.UPLOAD -> listOf(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD)
    DashboardSelector.STORAGE -> listOf(ActiveSetupOptionKey.STORAGE)
    DashboardSelector.SETTINGS_SET, DashboardSelector.DEVICE -> emptyList()
}

private enum class FixedBaseProfileSelectionMode {
    SETTINGS_SET,
    CONFIRM,
}

private data class FixedBaseCoordinateChoices(
    val average: BaseCoordinateCandidate,
    val instant: BaseCoordinateCandidate,
)

private val DashboardSelectorSaver: Saver<DashboardSelector?, String> = Saver(
    save = { it?.name ?: "" },
    restore = { name ->
        name.takeIf(String::isNotBlank)?.let {
            runCatching { DashboardSelector.valueOf(it) }.getOrNull()
        }
    },
)

private val DashboardLayoutPreferenceSaver: Saver<DashboardLayoutPreference, String> = Saver(
    save = { it.storageId },
    restore = { DashboardLayoutPreference.fromStorageId(it) },
)

private val DashboardDistanceUnitPreferenceSaver: Saver<DashboardDistanceUnitPreference, String> = Saver(
    save = { it.storageId },
    restore = { DashboardDistanceUnitPreference.fromStorageId(it) },
)

private val SatelliteMonitorCardThemePreferenceSaver: Saver<SatelliteMonitorCardThemePreference, String> = Saver(
    save = { it.storageId },
    restore = { SatelliteMonitorCardThemePreference.fromStorageId(it) },
)

private fun ProfileKind.backScreen(): AppScreen =
    when (this) {
        ProfileKind.SETTINGS_SET -> AppScreen.SETTINGS_SETS
        ProfileKind.NTRIP_CASTER -> AppScreen.NTRIP_CASTER
        ProfileKind.NTRIP_CASTER_UPLOAD -> AppScreen.NTRIP_CASTER_UPLOAD
        ProfileKind.NTRIP_MOUNTPOINT -> AppScreen.NTRIP_MOUNTPOINT_PROFILES
        ProfileKind.USB_BAUD -> AppScreen.USB_BAUD
        ProfileKind.COMMANDS -> AppScreen.COMMANDS
        ProfileKind.RECORDING_OUTPUTS -> AppScreen.RECORDING_OUTPUTS
        ProfileKind.RTKLIB -> AppScreen.RTKLIB_PROFILES
        ProfileKind.SOLUTION_POLICY -> AppScreen.SOLUTION_POLICIES
        ProfileKind.STORAGE -> AppScreen.STORAGE
    }

private fun AppScreen.backScreen(editorTarget: ProfileEditorTarget?): AppScreen =
    when (this) {
        AppScreen.HOME -> AppScreen.HOME
        AppScreen.SATELLITE_MONITOR -> AppScreen.HOME
        AppScreen.CASTER_UPLOAD_MONITOR -> AppScreen.HOME
        AppScreen.SETTINGS -> AppScreen.HOME
        AppScreen.NTRIP_MOUNTPOINT,
        AppScreen.SETTINGS_SET_SELECTOR,
        AppScreen.MOUNTPOINT_SELECTOR,
        AppScreen.COMMAND_SELECTOR,
        AppScreen.STORAGE_SELECTOR -> AppScreen.HOME
        AppScreen.PROFILE_EDITOR -> editorTarget?.kind?.backScreen() ?: AppScreen.SETTINGS
        AppScreen.BASE_COORDINATE_EDITOR -> AppScreen.BASE_COORDINATES
        else -> AppScreen.SETTINGS
    }

@Preview(showBackground = true)
@Composable
private fun RtkCollectorAppPreview() {
    RtkCollectorApp()
}

private data class ResolvedDashboardProfiles(
    val settingsSet: RecordingSettingsSet,
    val commandProfile: CommandProfile,
    val usbProfile: UsbBaudProfile,
    val ntripCaster: NtripCasterProfile?,
    val ntripMountpoint: NtripMountpointProfile?,
    val ntripCasterUploadProfile: NtripCasterUploadProfile?,
    val recordingPolicy: RecordingPolicyProfile,
    val storageProfile: StorageProfile,
    val rtklibProfile: RtklibProfile?,
    val solutionPolicyProfile: SolutionPolicyProfile?,
)

private fun buildDashboardStartIntent(
    context: Context,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    selectedWorkflowId: String?,
    selectedBaseCoordinate: AcceptedBaseCoordinate?,
): Intent? {
    val store = ProfileStores(context)
    var lease: org.rtkcollector.app.profile.StartSelectionLease? = null
    return runCatching {
        store.withRecoveredConfiguration {
            val set = requireNotNull(store.settingsSets().firstOrNull { it.id == selectedSettingsSetId })
            lease = store.captureStartSelections(set, selectedWorkflowId)
            buildValidatedDashboardStartIntent(context, settingsSets, selectedSettingsSetId,
                selectedWorkflowId, store, requireNotNull(lease)).also {
                if (it == null) lease?.finish()
            }
        }
    }.getOrElse {
        lease?.finish()
        showCannotStart(context, "Stored configuration could not be validated. Review profiles and credentials.")
        null
    }
}

private fun buildValidatedDashboardStartIntent(
    context: Context,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    selectedWorkflowId: String?,
    profileStore: ProfileStores,
    selectionLease: org.rtkcollector.app.profile.StartSelectionLease,
): Intent? {
    val settingsSet = profileStore.settingsSets().firstOrNull { it.id == selectedSettingsSetId }
        ?: run { showCannotStart(context, "Selected settings set is missing."); return null }
    val graph = profileStore.ownershipProfileGraph(AcceptedBaseCoordinateStore(context).coordinates())
    val setup = org.rtkcollector.app.profile.ActiveSetupResolver.resolve(
        settingsSet, selectionLease.selections, selectedWorkflowId, profileGraph = graph)
    val resolvedProfiles = try {
        require(setup.canStart) { setup.messages.joinToString(" ") { it.message } }
        val review = profileStore.migrationReviewIssues(settingsSet)
        require(review.isEmpty()) { "Settings migration requires review before this recording." }
        val owners = requireNotNull(setup.resolvedProfiles)
        ResolvedDashboardProfiles(
            settingsSet = setup.projectSettingsSet(settingsSet, graph::referenceFor),
            commandProfile = requireNotNull(owners.command),
            usbProfile = requireNotNull(owners.usbBaud),
            ntripCaster = owners.caster,
            ntripMountpoint = owners.source,
            ntripCasterUploadProfile = owners.upload,
            recordingPolicy = requireNotNull(owners.output),
            storageProfile = requireNotNull(owners.storage),
            rtklibProfile = owners.rtklib,
            solutionPolicyProfile = owners.solution,
        )
    } catch (error: IllegalArgumentException) {
        showCannotStart(context, error.message ?: error.javaClass.simpleName)
        return null
    }
    val workflowId = setup.option(ActiveSetupOptionKey.WORKFLOW).effectiveValueId
    if (workflowId.isNullOrBlank()) {
        showCannotStart(context, "workflow is not selected.")
        return null
    }
    val activeBaseCoordinate = setup.resolvedProfiles?.baseCoordinate
    if (workflowId == WORKFLOW_FIXED_BASE && activeBaseCoordinate == null) {
        showCannotStart(context, "fixed base requires an accepted base coordinate.")
        return null
    }
    if (workflowId == WORKFLOW_FIXED_BASE) {
        val fixedBaseCoordinate = checkNotNull(activeBaseCoordinate)
        val fixedBaseStartError = runCatching {
            FixedBaseCommandValidator.validateSelectedCoordinateMatchesProfile(
                commandProfile = resolvedProfiles.commandProfile,
                selectedBaseCoordinate = fixedBaseCoordinate,
            )
        }.exceptionOrNull()
        if (fixedBaseStartError != null) {
            showCannotStart(context, fixedBaseStartError.message ?: fixedBaseStartError.javaClass.simpleName)
            return null
        }
    }
    val workflowUsesNtrip = workflowId.workflowUsesNtrip()
    val activeConfig = try {
        ActiveRecordingConfig.resolve(
            settingsSet = settingsSet,
            selections = selectionLease.selections,
            profileGraph = graph,
            workflowName = workflowId.workflowName(),
            currentWorkflowId = selectedWorkflowId,
            passwordLookup = NtripSecretStore(context)::getPassword,
        )
    } catch (error: IllegalArgumentException) {
        showCannotStart(
            context = context,
            detail = error.message ?: error.javaClass.simpleName,
            attributes = resolvedProfiles.startPreflightAttributes(workflowId = workflowId, activeConfig = null),
        )
        return null
    }
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val usbDevice = usbManager.selectUsbDevice(resolvedProfiles.usbProfile)
    if (usbDevice == null) {
        val message = if (resolvedProfiles.usbProfile.usbVid != null || resolvedProfiles.usbProfile.usbPid != null) {
            "Cannot start: selected USB receiver is not connected."
        } else {
            "Cannot start: no USB receiver is connected or selected."
        }
        showCannotStart(context, message.removePrefix("Cannot start: "))
        return null
    }
    val usbAccess = UsbStartAccessDecision.evaluate(
        deviceConnected = true,
        permissionReportedGranted = usbManager.hasPermission(usbDevice),
    )
    when (usbAccess.action) {
        UsbStartAccessAction.NO_DEVICE -> {
            showCannotStart(context, usbAccess.message.removePrefix("Cannot start: "))
            return null
        }
        UsbStartAccessAction.REQUEST_PERMISSION -> {
            requestUsbPermissionForDevice(context, usbDevice)
            showCannotStart(context, usbAccess.message.removePrefix("Cannot start: "))
            return null
        }
        UsbStartAccessAction.VERIFY_AND_START -> Unit
    }
    try {
        activeConfig.validateForStart()
    } catch (error: IllegalArgumentException) {
        showCannotStart(
            context = context,
            detail = error.message ?: error.javaClass.simpleName,
            attributes = resolvedProfiles.startPreflightAttributes(workflowId = workflowId, activeConfig = activeConfig),
        )
        return null
    }
    val setupToken = runCatching {
        RecordingSetupBridge.stageStart(RunningSetupSnapshot(
            sessionId = java.util.UUID.randomUUID().toString(), revision = 0,
            settingsSetId = settingsSet.id, config = activeConfig,
            recordingOutputProfile = resolvedProfiles.recordingPolicy,
            profileIds = setup.snapshot().profileIds,
            lockedOptions = setup.options.values.filter { it.applicable &&
                it.policy == org.rtkcollector.app.profile.SettingsSetOptionPolicy.LOCKED }.mapTo(linkedSetOf()) { it.key },
            casterRestrictionId = settingsSet.correctionCasterRestrictionRef()?.id,
        ), basePosition = activeBaseCoordinate.takeIf { workflowId == WORKFLOW_FIXED_BASE },
            selectionLease = selectionLease)
    }.getOrElse { showCannotStart(context, "Validated recording configuration could not be staged."); return null }
    return Intent(context, RecordingForegroundService::class.java).apply {
        action = RecordingForegroundService.ACTION_START
        putExtra(RecordingForegroundService.EXTRA_SETUP_TOKEN, setupToken)
        putExtra(RecordingForegroundService.EXTRA_USB_DEVICE, usbDevice)
        putExtra(RecordingForegroundService.EXTRA_PROFILE_BAUD, activeConfig.profileBaud)
        putExtra(RecordingForegroundService.EXTRA_SERIAL_BAUD, activeConfig.serialBaud)
        putStringArrayListExtra(RecordingForegroundService.EXTRA_INIT_COMMANDS, ArrayList(activeConfig.initCommands))
        putStringArrayListExtra(RecordingForegroundService.EXTRA_BAUD_SWITCH_COMMANDS, ArrayList(activeConfig.baudSwitchCommands))
        putStringArrayListExtra(
            RecordingForegroundService.EXTRA_MODE_COMMANDS,
            ArrayList(activeConfig.modeCommands),
        )
        putStringArrayListExtra(RecordingForegroundService.EXTRA_SHUTDOWN_COMMANDS, ArrayList(activeConfig.shutdownCommands))
        putExtra(RecordingForegroundService.EXTRA_NTRIP_ENABLED, activeConfig.ntrip.enabled)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_HOST, activeConfig.ntrip.host)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PORT, activeConfig.ntrip.port)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_TRANSPORT_MODE, activeConfig.ntrip.transportMode.name)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_TLS_VERIFICATION, activeConfig.ntrip.tlsVerification.storageValue)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_UNSAFE_TLS_ACKNOWLEDGED, activeConfig.ntrip.unsafeTlsAcknowledged)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT, activeConfig.ntrip.mountpoint)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_USERNAME, activeConfig.ntrip.username)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PASSWORD, activeConfig.ntrip.password.orEmpty())
        putExtra(RecordingForegroundService.EXTRA_NTRIP_SECRET_REF, activeConfig.ntrip.secretRef.orEmpty())
        putExtra(RecordingForegroundService.EXTRA_NTRIP_GGA, "")
        putExtra(RecordingForegroundService.EXTRA_NTRIP_SEND_TO_RECEIVER, workflowId.workflowSendsNtripToReceiver())
        putExtra(RecordingForegroundService.EXTRA_NTRIP_STATION_ID, activeConfig.ntrip.stationId.orEmpty())
        activeConfig.ntrip.baseLatDeg?.let { putExtra(RecordingForegroundService.EXTRA_NTRIP_BASE_LAT, it) }
        activeConfig.ntrip.baseLonDeg?.let { putExtra(RecordingForegroundService.EXTRA_NTRIP_BASE_LON, it) }
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_ENABLED, activeConfig.casterUpload.enabled)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_HOST, activeConfig.casterUpload.host)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_PORT, activeConfig.casterUpload.port)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_TRANSPORT_MODE,
            activeConfig.casterUpload.transportMode.name)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_TLS_VERIFICATION,
            activeConfig.casterUpload.tlsVerification.storageValue)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_UNSAFE_TLS_ACKNOWLEDGED,
            activeConfig.casterUpload.unsafeTlsAcknowledged)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_MOUNTPOINT, activeConfig.casterUpload.mountpoint)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_USERNAME, activeConfig.casterUpload.username)
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_USERNAME_PRESENT,
            activeConfig.casterUpload.username.isNotBlank(),
        )
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_SECRET_REF,
            activeConfig.casterUpload.secretRef.orEmpty(),
        )
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_PASSWORD, activeConfig.casterUpload.password.orEmpty())
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_PROTOCOL_POLICY, activeConfig.casterUpload.protocolPolicy)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_RETRY_MODE, activeConfig.casterUpload.retryMode.name)
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_FIXED_RECONNECT_DELAY_SECONDS,
            activeConfig.casterUpload.fixedReconnectDelaySeconds,
        )
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_ADAPTIVE_INITIAL_DELAY_SECONDS,
            activeConfig.casterUpload.adaptiveInitialDelaySeconds,
        )
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_ADAPTIVE_MAX_DELAY_SECONDS,
            activeConfig.casterUpload.adaptiveMaxDelaySeconds,
        )
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_STOP_AFTER_FAILURES_ENABLED,
            activeConfig.casterUpload.stopAfterFailuresEnabled,
        )
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_STOP_AFTER_CONSECUTIVE_FAILURES,
            activeConfig.casterUpload.stopAfterConsecutiveFailures,
        )
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_SAFETY_RULES_ENABLED,
            activeConfig.casterUpload.safetyRulesEnabled,
        )
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_SAFETY_RULES_FORCED,
            activeConfig.casterUpload.effectiveSafetyRulesEnabled && !activeConfig.casterUpload.safetyRulesEnabled,
        )
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_SAFETY_MAX_BITRATE_KBPS,
            activeConfig.casterUpload.safetyMaxBitrateKbps,
        )
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_SAFETY_BITRATE_WINDOW_SECONDS,
            activeConfig.casterUpload.safetyBitrateWindowSeconds,
        )
        putExtra(
            RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_SAFETY_MAX_SESSION_UPLOAD_MB,
            activeConfig.casterUpload.safetyMaxSessionUploadMb,
        )
        putExtra(RecordingForegroundService.EXTRA_WORKFLOW_ID, activeConfig.workflowId)
        putExtra(RecordingForegroundService.EXTRA_WORKFLOW_NAME, activeConfig.workflowName)
        putExtra(RecordingForegroundService.EXTRA_RECEIVER_ROLE, workflowId.receiverRoleForSession())
        putExtra(RecordingForegroundService.EXTRA_RECEIVER_PROFILE_ID, activeConfig.receiverProfileId)
        putExtra(RecordingForegroundService.EXTRA_UM980_PROFILE_ID, activeConfig.commandProfileId)
        putExtra(RecordingForegroundService.EXTRA_COMMAND_PROFILE_ID, activeConfig.commandProfileId)
        putExtra(RecordingForegroundService.EXTRA_COMMAND_RECEIVER_FAMILY, activeConfig.commandReceiverFamily)
        putExtra(RecordingForegroundService.EXTRA_COMMAND_SATELLITE_TELEMETRY, activeConfig.satelliteTelemetry.storageId)
        putExtra(RecordingForegroundService.EXTRA_USB_BAUD_PROFILE_ID, activeConfig.usbBaudProfileId)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_CASTER_PROFILE_ID, resolvedProfiles.ntripCaster?.id)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT_PROFILE_ID, resolvedProfiles.ntripMountpoint?.id)
        putExtra(RecordingForegroundService.EXTRA_RECORDING_POLICY_ID, resolvedProfiles.recordingPolicy.id)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_PROFILE_ID, activeConfig.rtklib.profileId)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_ENABLED, activeConfig.rtklib.enabled)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_PRESET, activeConfig.rtklib.preset)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_SNAPSHOT_ID, activeConfig.rtklib.snapshotId)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_ROUTE_PLAN, activeConfig.rtklib.routePlan)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_VALIDATION_SUMMARY, activeConfig.rtklib.validationSummary)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_OUTPUT_NMEA, activeConfig.rtklib.outputNmea)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_OUTPUT_POS, activeConfig.rtklib.outputPos)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_MAX_ROVER_QUEUE_BYTES, activeConfig.rtklib.maxRoverQueueBytes)
        putExtra(
            RecordingForegroundService.EXTRA_RTKLIB_MAX_CORRECTION_QUEUE_BYTES,
            activeConfig.rtklib.maxCorrectionQueueBytes,
        )
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_FREQUENCY_COUNT, activeConfig.rtklib.frequencyCount)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_SERVER_CYCLE_MILLIS, activeConfig.rtklib.serverCycleMillis)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_SERVER_BUFFER_BYTES, activeConfig.rtklib.serverBufferBytes)
        putExtra(RecordingForegroundService.EXTRA_RTKLIB_SOLUTION_BUFFER_BYTES, activeConfig.rtklib.solutionBufferBytes)
        putExtra(RecordingForegroundService.EXTRA_STORAGE_PROFILE_ID, activeConfig.storage.id)
        putExtra(RecordingForegroundService.EXTRA_STORAGE_KIND, activeConfig.storage.kind)
        putExtra(RecordingForegroundService.EXTRA_STORAGE_TREE_URI, activeConfig.storage.treeUri)
        putExtra(RecordingForegroundService.EXTRA_RECORD_NTRIP_CORRECTION_INPUT, activeConfig.recording.recordNtripCorrectionInput)
        putExtra(RecordingForegroundService.EXTRA_EXPORT_NMEA, activeConfig.recording.exportNmea)
        putExtra(RecordingForegroundService.EXTRA_PPP_NMEA_GGA_QUALITY, activeConfig.recording.pppNmeaGgaQuality)
        putExtra(RecordingForegroundService.EXTRA_EXPORT_JSON_SOLUTION, activeConfig.recording.exportJsonSolution)
        putExtra(RecordingForegroundService.EXTRA_RECORD_REMOTE_BASE_RAW, activeConfig.recording.recordRemoteBaseRaw)
        putExtra(RecordingForegroundService.EXTRA_ENABLE_MOCK_LOCATION, activeConfig.recording.enableMockLocation)
        putExtra(RecordingForegroundService.EXTRA_MOCK_LOCATION_RATE_HZ, activeConfig.recording.mockLocationRateHz)
        putExtra(RecordingForegroundService.EXTRA_SOLUTION_POLICY_PROFILE_ID, activeConfig.solutionPolicy.profileId)
        putExtra(RecordingForegroundService.EXTRA_SOLUTION_SCREEN_POLICY, activeConfig.solutionPolicy.screenPolicy.name)
        putExtra(RecordingForegroundService.EXTRA_SOLUTION_MOCK_POLICY, activeConfig.solutionPolicy.mockPolicy.name)
        val basePositionJson = if (workflowId == WORKFLOW_FIXED_BASE && activeBaseCoordinate != null) {
            BasePositionJsonCodec.encode(activeBaseCoordinate)
        } else {
            ""
        }
        putExtra(
            RecordingForegroundService.EXTRA_COORDINATE_SOURCE,
            if (basePositionJson.isNotBlank()) activeBaseCoordinate?.sourceDescription.orEmpty() else "NONE",
        )
        putExtra(RecordingForegroundService.EXTRA_BASE_POSITION_JSON, basePositionJson)
        putExtra(RecordingForegroundService.EXTRA_BASE_COORDINATE_ID, activeBaseCoordinate?.id)
        putExtra(RecordingForegroundService.EXTRA_BASE_COORDINATE_NAME, activeBaseCoordinate?.name)
        putExtra(RecordingForegroundService.EXTRA_BASE_COORDINATE_METHOD, activeBaseCoordinate?.method)
        putStringArrayListExtra(RecordingForegroundService.EXTRA_EXPECTED_ARTIFACTS, ArrayList(activeConfig.expectedSessionArtifactNames))
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_SET_NAME, resolvedProfiles.settingsSet.displayNameWithOverrides())
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_COMMAND_PROFILE_NAME, resolvedProfiles.commandProfile.name)
        putExtra(
            RecordingForegroundService.EXTRA_SETTINGS_USB_BAUD_PROFILE_NAME,
            dashboardBaudLabel(resolvedProfiles.usbProfile.name, activeConfig.serialBaud),
        )
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_NTRIP_CASTER_PROFILE_NAME, resolvedProfiles.ntripCaster?.name ?: "NTRIP disabled")
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_RECORDING_OUTPUT_PROFILE_NAME, resolvedProfiles.recordingPolicy.name)
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_STORAGE_PROFILE_NAME, resolvedProfiles.storageProfile.name)
    }
}

private fun ResolvedDashboardProfiles.startPreflightAttributes(
    workflowId: String,
    activeConfig: ActiveRecordingConfig?,
): Map<String, String> =
    buildMap {
        put("workflowId", workflowId)
        put("workflowUsesNtrip", workflowId.workflowUsesNtrip().toString())
        put("settingsSetId", settingsSet.id)
        put("settingsSetName", settingsSet.name)
        put("receiverProfileId", settingsSet.receiverProfileId)
        put("commandProfileId", commandProfile.id)
        put("commandReceiverFamily", commandProfile.receiverFamily)
        put("ntripCasterProfileId", ntripCaster?.id.orEmpty())
        put("ntripCasterProfileName", ntripCaster?.name.orEmpty())
        put("ntripCasterHostPresent", (!ntripCaster?.host.isNullOrBlank()).toString())
        put("ntripMountpointProfileId", ntripMountpoint?.id.orEmpty())
        put("ntripMountpointProfileName", ntripMountpoint?.name.orEmpty())
        put("ntripMountpointPresent", (!ntripMountpoint?.mountpoint.isNullOrBlank()).toString())
        put("rtklibProfileId", rtklibProfile?.id.orEmpty())
        put("rtklibEnabled", (rtklibProfile?.enabled == true).toString())
        activeConfig?.let { config ->
            put("activeNtripEnabled", config.ntrip.enabled.toString())
            put("activeNtripHostPresent", config.ntrip.host.isNotBlank().toString())
            put("activeNtripMountpointPresent", config.ntrip.mountpoint.isNotBlank().toString())
            put("activeNtripConfigured", config.ntrip.isConfigured.toString())
            put("rtklibValidationSummary", config.rtklib.validationSummary.orEmpty())
        }
    }

private fun persistentReceiverServiceIntent(
    context: Context,
    label: String,
    commands: List<String>,
    targetBaud: Int? = null,
): Intent =
    Intent(context, RecordingForegroundService::class.java).apply {
        action = RecordingForegroundService.ACTION_WRITE_PERSISTENT_RECEIVER_CONFIG
        putExtra(RecordingForegroundService.EXTRA_PERSISTENT_WRITE_LABEL, label)
        putStringArrayListExtra(RecordingForegroundService.EXTRA_PERSISTENT_COMMANDS, ArrayList(commands))
        targetBaud?.let { putExtra(RecordingForegroundService.EXTRA_PERSISTENT_TARGET_BAUD, it) }
    }

private fun requestSelectedUsbPermission(context: Context, selectedSettingsSetId: String) {
    val profileStore = ProfileStores(context)
    val settingsSet = profileStore.settingsSets().firstOrNull { it.id == selectedSettingsSetId }
    requestUsbPermissionForProfile(context, profileStore.activeProfileId(settingsSet, ActiveSetupOptionKey.USB_BAUD))
}

private fun requestUsbPermissionForProfile(context: Context, usbProfileId: String?) {
    val profileStore = ProfileStores(context)
    val usbProfile = usbProfileId?.let { id ->
        profileStore.usbBaudProfiles().firstOrNull { it.id == id }
    } ?: profileStore.usbBaudProfiles().firstOrNull()
    if (usbProfile == null) {
        Toast.makeText(context, "No USB/baud profile is selected.", Toast.LENGTH_LONG).show()
        return
    }
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val device = usbManager.selectUsbDevice(usbProfile)
    if (device == null) {
        val message = if (usbProfile.usbVid != null || usbProfile.usbPid != null) {
            "Selected USB receiver is not connected."
        } else {
            "No USB receiver is connected."
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        return
    }
    if (usbManager.hasPermission(device)) {
        Toast.makeText(context, "USB permission is already granted.", Toast.LENGTH_SHORT).show()
        return
    }
    requestUsbPermissionForDevice(context, device)
    Toast.makeText(
        context,
        "USB permission requested. Approve the Android permission dialog, then press Start again.",
        Toast.LENGTH_LONG,
    ).show()
}

private fun requestUsbPermissionForDevice(context: Context, device: UsbDevice) {
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }
    val permissionIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
        flags,
    )
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    usbManager.requestPermission(device, permissionIntent)
}

private fun createDeviceConsoleController(
    context: Context,
    isRecording: () -> Boolean,
    usbProfile: UsbBaudProfile,
    onState: (DeviceConsoleState) -> Unit,
): DeviceConsoleController {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    return DeviceConsoleController(
        recordingActive = isRecording,
        transportFactory = {
            val device = usbManager.selectUsbDevice(usbProfile)
                ?: error("Selected USB receiver is not connected.")
            if (!usbManager.hasPermission(device)) {
                error(DEVICE_CONSOLE_PERMISSION_REQUIRED)
            }
            AndroidUsbSerialTransport(
                usbManager = usbManager,
                device = device,
                options = UsbSerialOpenOptions(usbProfile.profileBaud),
            )
        },
        stateListener = onState,
    )
}

private fun writeCommandProfilePersistentlyToDevice(
    context: Context,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    commandProfileId: String,
    runtimeScript: String,
    isRecording: Boolean,
) {
    val profileStore = ProfileStores(context)
    val commandProfile = profileStore.commandProfiles().firstOrNull { it.id == commandProfileId }
    if (commandProfile == null) {
        Toast.makeText(context, "Command profile is not available.", Toast.LENGTH_LONG).show()
        return
    }
    val commands = persistentReceiverCommands(runtimeScript)
    when (
        persistentReceiverWriteRoute(
            recordingActive = isRecording,
            usbProfileAvailable = true,
            receiverConnected = true,
            usbPermissionGranted = true,
        )
    ) {
        PersistentReceiverWriteRoute.ActiveRecordingService -> {
            context.startService(
                persistentReceiverServiceIntent(
                    context = context,
                    label = "Command profile persistent write",
                    commands = commands,
                ),
            )
            Toast.makeText(
                context,
                "Writing receiver configuration through active recording connection...",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        PersistentReceiverWriteRoute.IdleMaintenanceConnection -> Unit
        is PersistentReceiverWriteRoute.Rejected -> Unit
    }
    val settingsSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
        ?: profileStore.settingsSets().firstOrNull { it.id == selectedSettingsSetId }
    if (settingsSet == null) {
        Toast.makeText(context, "No settings set is selected.", Toast.LENGTH_LONG).show()
        return
    }
    val usbProfile = profileStore.usbBaudProfiles().firstOrNull { it.id == profileStore.activeProfileId(settingsSet, ActiveSetupOptionKey.USB_BAUD) }
    if (usbProfile == null) {
        Toast.makeText(context, "USB/baud profile is not available.", Toast.LENGTH_LONG).show()
        return
    }
    writePersistentCommandsViaMaintenanceConnection(
        context = context,
        usbProfile = usbProfile,
        commandProfileName = commandProfile.name,
        commands = commands,
    )
}

private fun writeUsbBaudPersistentlyToDevice(
    context: Context,
    usbProfileId: String,
    values: Map<String, String>,
    isRecording: Boolean,
) {
    val profileStore = ProfileStores(context)
    val savedProfile = profileStore.usbBaudProfiles().firstOrNull { it.id == usbProfileId }
    if (savedProfile == null) {
        Toast.makeText(context, "USB/baud profile is not available.", Toast.LENGTH_LONG).show()
        return
    }
    val usbProfile = try {
        savedProfile.copy(
            profileBaud = values["profileBaud"]?.toIntOrNull() ?: savedProfile.profileBaud,
            serialBaud = values["serialBaud"]?.toIntOrNull() ?: savedProfile.serialBaud,
        ).also(UsbBaudProfile::validate)
    } catch (error: IllegalArgumentException) {
        Toast.makeText(context, "Cannot write receiver baud: ${error.message}", Toast.LENGTH_LONG).show()
        return
    }
    val commands = persistentBaudCommands(usbProfile.serialBaud)
    if (isRecording) {
        context.startService(
            persistentReceiverServiceIntent(
                context = context,
                label = "USB target baud persistent write",
                commands = commands,
                targetBaud = usbProfile.serialBaud,
            ),
        )
        Toast.makeText(context, "Writing receiver target baud through active recording connection...", Toast.LENGTH_SHORT).show()
        return
    }
    writePersistentCommandsViaMaintenanceConnection(
        context = context,
        usbProfile = usbProfile,
        commandProfileName = "USB target baud ${usbProfile.serialBaud}",
        commands = commands,
        targetBaud = usbProfile.serialBaud,
    )
}

private fun writePersistentCommandsViaMaintenanceConnection(
    context: Context,
    usbProfile: UsbBaudProfile,
    commandProfileName: String,
    commands: List<String>,
    targetBaud: Int? = null,
) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val device = usbManager.selectUsbDevice(usbProfile)
    when (
        val route = persistentReceiverWriteRoute(
            recordingActive = false,
            usbProfileAvailable = true,
            receiverConnected = device != null,
            usbPermissionGranted = device?.let(usbManager::hasPermission) == true,
        )
    ) {
        PersistentReceiverWriteRoute.ActiveRecordingService -> error("Maintenance writer cannot use active service route.")
        PersistentReceiverWriteRoute.IdleMaintenanceConnection -> Unit
        is PersistentReceiverWriteRoute.Rejected -> {
            Toast.makeText(context, route.message, Toast.LENGTH_LONG).show()
            return
        }
    }
    if (!persistentReceiverWriteInProgress.compareAndSet(false, true)) {
        Toast.makeText(context, "Persistent receiver configuration write is already in progress.", Toast.LENGTH_LONG).show()
        return
    }
    Toast.makeText(context, "Writing persistent receiver configuration...", Toast.LENGTH_SHORT).show()
    Thread {
        val transport = AndroidUsbSerialTransport(
            usbManager = usbManager,
            device = requireNotNull(device),
            options = UsbSerialOpenOptions(baudRate = usbProfile.profileBaud),
        )
        try {
            runCatching {
                transport.open()
                verifyMaintenanceReceiverConnection(transport)
                if (targetBaud == null) {
                    sendPersistentMaintenanceCommands(transport, commands)
                } else {
                    executePersistentBaudPlanOnMaintenanceTransport(
                        transport = transport,
                        currentHostBaud = usbProfile.profileBaud,
                        targetBaud = targetBaud,
                    )
                }
            }.onSuccess {
                runOnMain(context) {
                    Toast.makeText(
                        context,
                        "Persistent receiver configuration written for $commandProfileName.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }.onFailure { error ->
                runOnMain(context) {
                    Toast.makeText(
                        context,
                        "Persistent receiver configuration failed: ${error.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        } finally {
            runCatching { transport.close() }
            persistentReceiverWriteInProgress.set(false)
        }
    }.start()
}

private fun executePersistentBaudPlanOnMaintenanceTransport(
    transport: AndroidUsbSerialTransport,
    currentHostBaud: Int,
    targetBaud: Int,
) {
    val plan = Um980PersistentBaudPlan.build(
        currentHostBaud = currentHostBaud,
        targetBaud = targetBaud,
    )
    plan.steps.forEach { step ->
        when (step) {
            is Um980PersistentBaudStep.SendCommands -> sendMaintenanceCommandLines(transport, step.commands)
            Um980PersistentBaudStep.PauseAfterDeviceBaudCommands -> Thread.sleep(500)
            is Um980PersistentBaudStep.ReconfigureHostBaud -> transport.reconfigureBaud(step.baud)
            Um980PersistentBaudStep.VerifyReceiverAtTargetBaud -> verifyMaintenanceReceiverConnection(transport)
            Um980PersistentBaudStep.ExpectSaveConfigOk -> waitForMaintenanceCommandOk(transport, "SAVECONFIG")
        }
    }
}

private fun sendPersistentMaintenanceCommands(
    transport: AndroidUsbSerialTransport,
    commands: List<String>,
) {
    commands.forEach { command ->
        sendMaintenanceCommandLines(transport, listOf(command))
        if (command.trim().equals("SAVECONFIG", ignoreCase = true)) {
            waitForMaintenanceCommandOk(transport, "SAVECONFIG")
        }
    }
}

private fun sendMaintenanceCommandLines(
    transport: AndroidUsbSerialTransport,
    commands: List<String>,
) {
    commands.forEach { command ->
        transport.write("$command\r\n".toByteArray(Charsets.US_ASCII))
        Thread.sleep(PERSISTENT_RECEIVER_COMMAND_DELAY_MILLIS)
    }
}

private fun waitForMaintenanceCommandOk(
    transport: AndroidUsbSerialTransport,
    commandLabel: String,
) {
    val response = collectMaintenanceCommandOkBytes(transport, PERSISTENT_RECEIVER_SAVE_OK_TIMEOUT_MILLIS)
    require(isUm980CommandOkResponse(response)) {
        "$commandLabel was not acknowledged by receiver."
    }
}

private fun collectMaintenanceCommandOkBytes(
    transport: AndroidUsbSerialTransport,
    timeoutMillis: Long,
): ByteArray {
    val deadline = System.currentTimeMillis() + timeoutMillis
    val response = ByteArrayOutputStream()
    while (System.currentTimeMillis() < deadline) {
        val bytes = transport.readAvailable(4096)
        if (bytes.isNotEmpty()) {
            response.write(bytes)
            val collected = response.toByteArray()
            if (isUm980CommandOkResponse(collected)) {
                return collected
            }
        } else {
            Thread.sleep(50)
        }
    }
    return response.toByteArray()
}

private fun collectMaintenanceReceiverBytes(
    transport: AndroidUsbSerialTransport,
    timeoutMillis: Long,
): ByteArray {
    val deadline = System.currentTimeMillis() + timeoutMillis
    val response = ByteArrayOutputStream()
    while (System.currentTimeMillis() < deadline) {
        val bytes = transport.readAvailable(4096)
        if (bytes.isNotEmpty()) {
            response.write(bytes)
            val collected = response.toByteArray()
            if (isPlausibleUm980MaintenanceResponse(collected) || isUm980CommandOkResponse(collected)) {
                return collected
            }
        } else {
            Thread.sleep(50)
        }
    }
    return response.toByteArray()
}

private fun verifyMaintenanceReceiverConnection(transport: AndroidUsbSerialTransport) {
    val initialBytes = transport.readAvailable(4096)
    if (isPlausibleUm980MaintenanceResponse(initialBytes)) return
    transport.write(um980VersionProbeBytes())
    Thread.sleep(300)
    val response = transport.readAvailable(4096)
    require(isPlausibleUm980MaintenanceResponse(response)) {
        "Receiver did not respond at the selected initial baud."
    }
}

private fun ProfileStores.selectedSettingsSet(): RecordingSettingsSet {
    val sets = settingsSets()
    return sets.firstOrNull { it.id == selectedSettingsSetId() } ?: sets.first()
}

private fun ProfileStores.ownershipProfileGraph(
    baseCoordinates: List<AcceptedBaseCoordinate> = emptyList(),
): ActiveSetupProfileGraph = ActiveSetupProfileGraph(
    commandProfiles = commandProfiles(),
    usbBaudProfiles = usbBaudProfiles(),
    ntripCasterProfiles = ntripCasterProfiles(),
    ntripMountpointProfiles = ntripMountpointProfiles(),
    ntripCasterUploadProfiles = ntripCasterUploadProfiles(),
    rtklibProfiles = rtklibProfiles(),
    solutionPolicyProfiles = solutionPolicyProfiles(),
    recordingOutputProfiles = recordingPolicyProfiles(),
    storageProfiles = storageProfiles(),
    baseCoordinates = baseCoordinates,
)

private fun ProfileStores.resolvedActiveSetup(
    set: RecordingSettingsSet,
    currentWorkflowId: String? = selectedWorkflowId(),
    graph: ActiveSetupProfileGraph = ownershipProfileGraph(),
): ActiveSetup = ActiveSetupResolver.resolve(
    set, activeSelections(set), currentWorkflowId = currentWorkflowId, profileGraph = graph,
)

private fun ProfileStores.activeProfileId(set: RecordingSettingsSet?, key: ActiveSetupOptionKey): String? =
    set?.let { resolvedActiveSetup(it).option(key).effectiveValueId }

private fun ProfileStores.selectedMountpointLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return "n/a"
    val profile = activeProfileId(settingsSet, ActiveSetupOptionKey.NTRIP_MOUNTPOINT)?.let { id ->
        ntripMountpointProfiles().firstOrNull { it.id == id }
    } ?: return "n/a"
    return profile.displayMountpoint()
}

private fun ProfileStores.selectedStorageLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId }
        ?: return "n/a"
    val id = activeProfileId(settingsSet, ActiveSetupOptionKey.STORAGE)
    return storageProfiles().firstOrNull { it.id == id }?.name ?: "n/a"
}

private fun ProfileStores.selectedReceiverLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId }
        ?: return "n/a"
    val id = activeProfileId(settingsSet, ActiveSetupOptionKey.RECEIVER_COMMAND)
    return commandProfiles().firstOrNull { it.id == id }?.name ?: "n/a"
}

private fun ProfileStores.selectedBaudProfileLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId }
        ?: return "n/a"
    val id = activeProfileId(settingsSet, ActiveSetupOptionKey.USB_BAUD)
    val profile = usbBaudProfiles().firstOrNull { it.id == id } ?: return "n/a"
    return dashboardBaudLabel(profile.name, profile.serialBaud)
}

internal fun UsbBaudProfile.dashboardBaudLabel(): String =
    dashboardBaudLabel(name, serialBaud)

internal fun dashboardBaudLabel(profileName: String, targetBaud: Int): String =
    "$profileName · target $targetBaud baud"

private fun ProfileStores.resolveSelectedNtripProfiles(selectedSettingsSetId: String): ResolvedNtripProfiles? {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId }
        ?: return null
    return settingsSet.resolveNtripProfiles(
        setup = resolvedActiveSetup(settingsSet),
        casterProfiles = ntripCasterProfiles(),
        mountpointProfiles = ntripMountpointProfiles(),
    )
}

private fun ProfileStores.selectedNtripCasterProfileLabel(selectedSettingsSetId: String): String {
    val resolution = resolveSelectedNtripProfiles(selectedSettingsSetId) ?: return "n/a"
    return resolution.caster?.name ?: "n/a"
}

private fun ProfileStores.selectedRecordingOutputProfileLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId }
        ?: return "n/a"
    val id = activeProfileId(settingsSet, ActiveSetupOptionKey.RECORDING_OUTPUT)
    return recordingPolicyProfiles().firstOrNull { it.id == id }?.name ?: "n/a"
}

private fun ProfileStores.selectedCasterMountpoints(selectedSettingsSetId: String): List<String> {
    val resolution = resolveSelectedNtripProfiles(selectedSettingsSetId) ?: return emptyList()
    return resolution.caster?.sourcetableMountpoints.orEmpty()
}

private fun ProfileStores.plannedDashboardState(
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    selectedWorkflowId: String?,
    baseCoordinates: List<AcceptedBaseCoordinate>,
): DashboardState {
    val selectedOriginal = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
    val graph = ownershipProfileGraph(baseCoordinates)
    val setup = selectedOriginal?.let { resolvedActiveSetup(it, selectedWorkflowId, graph) }
    val owners = setup?.resolvedProfiles
    val workflowId = setup?.option(ActiveSetupOptionKey.WORKFLOW)?.effectiveValueId
    val deviceFilter = selectedDeviceFilter()
    val mountpoint = owners?.source?.displayMountpoint() ?: "n/a"
    val mountpointRequired = workflowId?.workflowUsesNtrip() == true
    fun ready(vararg keys: ActiveSetupOptionKey): Boolean = setup != null &&
        keys.none { key -> setup.messages.any { it.key == key } }
    val mountpointConfigurationResolved = ready(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, ActiveSetupOptionKey.NTRIP_CASTER)
    val selectedCommandProfile = owners?.command
    val recordingPolicyProfile = owners?.output
    val storageProfile = owners?.storage
    val storageConfigurationResolved = dashboardStorageConfigurationResolved(
        profile = storageProfile,
        override = null,
    )
    val rtklibProfile = owners?.rtklib
    val casterUploadProfile = owners?.upload
    val casterUploadRequested = setup?.snapshot()?.uploadEnabled == true
    val casterUploadEnabled = casterUploadRequested && casterUploadProfile != null
    val uploadConfigurationResolved = ready(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD)
    val mockEnabled = recordingPolicyProfile?.enableMockLocation ?: false
    val mockRateHz = recordingPolicyProfile?.mockLocationRateHz
        ?: RecordingPolicyProfile.DEFAULT_MOCK_LOCATION_RATE_HZ
    val configProblems = setup?.messages.orEmpty().map { it.message } + selectedOriginal?.let {
        migrationReviewIssues(it).map { "Settings migration requires review: ${it.option.name}." }
    }.orEmpty()
    val settingsLabel = selectedOriginal?.name?.let { if (setup?.isModified == true) "$it +" else it } ?: "n/a"
    return DashboardState.planned(
        workflow = workflowId.workflowLabel(),
        device = deviceFilter.displayName,
        mountpoint = mountpoint,
        initProfile = selectedCommandProfile?.name ?: "n/a",
        upload = uploadSelectionLabel(
            applicable = setup?.option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD)?.applicable == true,
            enabled = setup?.uploadSelection?.enabled.takeUnless {
                setup?.option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD)?.selectionPresent == false
            },
            profileName = casterUploadProfile?.name,
        ),
        uploadAvailable = workflowId == WORKFLOW_FIXED_BASE || workflowId == WORKFLOW_BASE_CALIBRATION,
        fixedSetupItems = selectedOriginal?.fixedDashboardSetupItems().orEmpty(),
        fixedMockGps = selectedOriginal?.isOptionLocked(ActiveSetupOptionKey.RECORDING_OUTPUT) == true,
        mountpointRequired = mountpointRequired,
        uploadEnabled = casterUploadRequested,
        correctionTransport = owners?.caster?.transportMode.takeIf { mountpointRequired },
        uploadTransport = casterUploadProfile?.transportMode.takeIf { casterUploadRequested },
        settingsSetResolved = selectedOriginal != null,
        workflowResolved = ready(ActiveSetupOptionKey.WORKFLOW) && workflowId != null,
        mountpointConfigurationResolved = mountpointConfigurationResolved,
        initProfileResolved = selectedCommandProfile != null,
        uploadConfigurationResolved = uploadConfigurationResolved,
        storageProfileResolved = storageProfile != null,
        storageConfigurationResolved = storageConfigurationResolved,
        configurationProblems = configProblems,
        settingsSetOutsideDeviceFilter = selectedOriginal != null && !deviceFilter.matchesSettingsSet(selectedOriginal),
        initProfileOutsideDeviceFilter = selectedCommandProfile != null && !deviceFilter.matchesCommandProfile(selectedCommandProfile),
        storage = storageProfile?.name ?: "n/a",
        fix = FixCardState(
            receiverFrequency = receiverFrequencyForFamily(selectedCommandProfile?.receiverFamily),
        ),
        rtklib = rtklibProfile
            ?.takeIf { it.enabled }
            ?.let {
                RtklibCardState(
                    state = "Configured",
                    routePlan = "validated when recording starts",
                    snapshotId = "rtklib-ex-2.5.0",
                    outputs = listOfNotNull(
                        "NMEA".takeIf { rtklibProfile.outputNmea },
                        "POS".takeIf { rtklibProfile.outputPos },
                    ).ifEmpty { listOf("no output") }.joinToString(" / "),
                )
            },
        profiles = ProfilesCardState(
            settingsSet = settingsLabel,
            commandProfile = selectedCommandProfile?.name ?: "n/a",
            baudProfile = owners?.usbBaud?.dashboardBaudLabel() ?: "n/a",
            ntripCasterProfile = owners?.caster?.name ?: "n/a",
            recordingOutputProfile = recordingPolicyProfile?.name ?: "n/a",
            storageLocationProfile = storageProfile?.name ?: "n/a",
        ),
        mockGps = MockGpsDashboardState(enabled = mockEnabled, rateHz = mockRateHz),
        casterUpload = casterUploadProfile
            ?.takeIf { casterUploadEnabled }
            ?.let { profile ->
                CasterUploadCardState(
                    enabled = true,
                    statusLabel = "Configured",
                    mountpointLabel = "${profile.host}:${profile.port}/${profile.mountpoint.trimStart('/')}",
                    retryLabel = profile.retryMode.name.lowercase().replaceFirstChar(Char::uppercaseChar),
                    safetyLabel = when {
                        profile.effectiveSafetyRulesEnabled && !profile.safetyRulesEnabled -> "Safety forced"
                        profile.safetyRulesEnabled -> "Safety on"
                        else -> "Safety off"
                    },
                    safetyThresholdLabels = listOf(
                        "${profile.safetyMaxBitrateKbps} kbps over ${profile.safetyBitrateWindowSeconds} s",
                        "${profile.safetyMaxSessionUploadMb} MB session cap",
                        "12 s no-data watchdog",
                    ),
                    retryPolicyLabel = "${profile.retryMode.name.lowercase().replaceFirstChar(Char::uppercaseChar)}, " +
                        "stop after ${profile.stopAfterConsecutiveFailures} failure" +
                        (if (profile.stopAfterConsecutiveFailures == 1) "" else "s"),
                )
            }
            ?: CasterUploadCardState(),
    )
}

internal fun RecordingSettingsSet?.selectedMountpointLabel(
    mountpointProfiles: List<NtripMountpointProfile>,
): String {
    val settingsSet = this ?: return "n/a"
    settingsSet.overrides.ntripMountpoint?.mountpoint?.let { return it }
    val profile = settingsSet.effectiveNtripMountpointProfileRef()?.id?.let { id ->
        mountpointProfiles.firstOrNull { it.id == id }
    } ?: return "n/a"
    return profile.displayMountpoint()
}

internal fun selectedMountpointLabelFromProfileId(
    profileId: String?,
    mountpointProfiles: List<NtripMountpointProfile>,
): String =
    profileId
        ?.takeIf { it.isNotBlank() && !it.equals("a", ignoreCase = true) }
        ?.let { id -> mountpointProfiles.firstOrNull { it.id == id } }
        ?.displayMountpoint()
        ?.takeUnless { it.equals("a", ignoreCase = true) }
        ?: "n/a"

private fun String.isMissingMountpointValue(): Boolean {
    val value = trim()
    return value.isBlank() ||
        value.equals("n/a", ignoreCase = true) ||
        value.equals("a", ignoreCase = true)
}

private fun Context.currentUsbDeviceChoices(): List<UsbDeviceChoice> {
    val usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
    return usbManager.deviceList.values.map { device ->
        UsbDeviceChoice(
            vendorId = device.vendorId,
            productId = device.productId,
            deviceName = device.deviceName,
            productName = runCatching { device.productName }.getOrNull(),
        )
    }.sortedWith(compareBy<UsbDeviceChoice> { it.productName.orEmpty() }.thenBy { it.deviceName })
}

private fun UsbManager.selectUsbDevice(profile: UsbBaudProfile): UsbDevice? {
    val devices = deviceList.values
    val selectedVid = profile.usbVid
    val selectedPid = profile.usbPid
    if (selectedVid == null && selectedPid == null) {
        return devices.firstOrNull()
    }
    return devices.firstOrNull { device ->
        (selectedVid == null || device.vendorId == selectedVid) &&
            (selectedPid == null || device.productId == selectedPid)
    }
}

private fun UsbBaudProfile.usbDeviceChoice(): UsbDeviceChoice? {
    val vid = usbVid ?: return null
    val pid = usbPid ?: return null
    return UsbDeviceChoice(
        vendorId = vid,
        productId = pid,
        deviceName = usbDeviceName.orEmpty(),
        productName = usbProductName,
    )
}

private fun usbDeviceOptionItems(
    connectedChoices: List<UsbDeviceChoice>,
    selectedChoice: UsbDeviceChoice?,
): List<EditableProfileOption> {
    val connectedValues = connectedChoices.map { it.toProfileValue() }.toSet()
    val remembered = selectedChoice
        ?.takeUnless { it.toProfileValue() in connectedValues }
        ?.let { choice ->
            EditableProfileOption(
                value = choice.toProfileValue(),
                label = "${choice.label} (not connected)",
            )
        }
    return listOf(EditableProfileOption("", "Any USB device")) +
        connectedChoices.map { EditableProfileOption(it.toProfileValue(), it.label) } +
        listOfNotNull(remembered)
}

internal fun String.workflowUsesNtrip(): Boolean =
    this == WORKFLOW_ROVER_NTRIP ||
        this == WORKFLOW_ROVER_RTKLIB ||
        this == WORKFLOW_ROVER_NTRIP_RTKLIB ||
        this == WORKFLOW_BASE_CALIBRATION

internal fun String.workflowSendsNtripToReceiver(): Boolean =
    this == WORKFLOW_ROVER_NTRIP ||
        this == WORKFLOW_ROVER_NTRIP_RTKLIB ||
        this == WORKFLOW_BASE_CALIBRATION

private fun String?.workflowLabel(): String =
    this?.workflowName() ?: "Select workflow"

private fun String.workflowName(): String =
    when (this) {
        WORKFLOW_PLAIN_ROVER -> "Plain rover recording"
        WORKFLOW_ROVER_NTRIP -> "Rover + NTRIP to receiver"
        WORKFLOW_ROVER_RTKLIB -> "Rover + RTKLIB"
        WORKFLOW_ROVER_NTRIP_RTKLIB -> "Rover + NTRIP to receiver + RTKLIB"
        WORKFLOW_BASE_CALIBRATION -> "Temporary base"
        WORKFLOW_FIXED_BASE -> "Fixed base operation"
        else -> replace('-', ' ').replaceFirstChar { it.titlecase() }
    }

private fun String.receiverRoleForSession(): String =
    when (this) {
        WORKFLOW_BASE_CALIBRATION -> "BASE_CALIBRATION"
        WORKFLOW_FIXED_BASE -> "FIXED_BASE"
        else -> "ROVER"
    }

internal fun restoredWorkflowIdOrNull(workflowId: String?): String? =
    workflowId?.takeIf { id -> WORKFLOW_MODE_OPTIONS.any { it.value == id } }

internal fun sanitizedImportedSettingsSets(settingsSets: List<RecordingSettingsSet>): List<RecordingSettingsSet> =
    settingsSets.map { settingsSet ->
        if (restoredWorkflowIdOrNull(settingsSet.workflowId) != null) {
            settingsSet
        } else {
            settingsSet.copy(
                workflowId = WORKFLOW_PLAIN_ROVER,
                workflowApplicationPolicy = WorkflowApplicationPolicy.LET_USER_SELECT,
            )
        }
    }

private fun RecordingSettingsSet?.applyWorkflowPolicy(currentWorkflowId: String?): String? =
    this?.workflowIdAfterSettingsSetActivation(currentWorkflowId) ?: currentWorkflowId

private fun RecordingSettingsSet?.workflowIdForDashboard(selectedWorkflowId: String?): String? =
    this?.workflowIdForActiveSetup(selectedWorkflowId) ?: selectedWorkflowId

private fun CommandProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = listOfNotNull(
            receiverFamily.takeIf(String::isNotBlank),
            satelliteTelemetry.displayName,
            "command scripts",
        ).joinToString(" · "),
    )

private fun FixedBaseSettingsSetCandidate.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = settingsSet.id,
        name = settingsSet.name,
        isProtected = settingsSet.isProtected,
        hasLocalOverrides = settingsSet.hasLocalOverrides,
        isSelected = isSelected,
        summary = listOf(
            "fixed base",
            commandProfile?.name ?: settingsSet.effectiveCommandProfileRef().name,
            if (requiresDerivedSettingsSet) "derive new" else "update in place",
            reason,
        ).joinToString(" · "),
    )

private fun UsbBaudProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = listOfNotNull(
            "baud $profileBaud",
            usbProductName?.takeIf(String::isNotBlank) ?: usbDeviceName?.takeIf(String::isNotBlank),
        ).joinToString(" · "),
    )

private fun NtripCasterProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = listOfNotNull("$host:$port", protocolPolicy,
            "\u26A0 PLAINTEXT".takeIf { transportMode == NtripTransportMode.PLAINTEXT }).joinToString(" · "),
    )

private fun NtripCasterUploadProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = listOf(
            "${host.ifBlank { "host not set" }}:$port/${mountpoint.ifBlank { "mountpoint not set" }}",
            protocolPolicy,
            "\u26A0 PLAINTEXT".takeIf { transportMode == NtripTransportMode.PLAINTEXT },
            if (enabledByDefault) "enabled by default" else null,
        ).filterNotNull().joinToString(" · "),
    )

private fun NtripMountpointProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    profileRow(emptyList(), isSelected)

private fun NtripMountpointProfile.profileRow(
    casters: List<NtripCasterProfile>,
    isSelected: Boolean = false,
): ProfileListRow {
    val caster = casters.firstOrNull { it.id == casterProfileId }
    val suspect = mountpoint.isNotBlank() &&
        caster?.sourcetableMountpoints?.isNotEmpty() == true &&
        mountpoint !in caster.sourcetableMountpoints
    return ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = listOfNotNull(
            caster?.name ?: casterProfileId,
            mountpoint.ifBlank { "mountpoint not set" },
            "\u26A0 PLAINTEXT".takeIf { caster?.transportMode == NtripTransportMode.PLAINTEXT },
        ).joinToString(" · "),
        warningText = if (suspect) SuspectInvalidMountpointWarning else null,
    )
}

private fun AcceptedBaseCoordinate.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = false,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = "%.10f, %.10f · h %.3f m · %s".format(
            java.util.Locale.US,
            latDeg,
            lonDeg,
            heightForDisplayMeters(),
            method,
        ),
    )

private fun AcceptedBaseCoordinate.displayLabel(): String =
    "%.10f, %.10f, h %.3f m".format(
        java.util.Locale.US,
        latDeg,
        lonDeg,
        heightForDisplayMeters(),
    )

private fun AcceptedBaseCoordinate.heightForDisplayMeters(): Double =
    mslAltitudeM ?: ellipsoidalHeightM ?: 0.0

private fun BaseCoordinateCandidate.toAcceptedBaseCoordinate(
    id: String,
    name: String,
): AcceptedBaseCoordinate? {
    val lat = coordinates.latDouble ?: return null
    val lon = coordinates.lonDouble ?: return null
    val transferSource = if (source == "AVERAGE") "TEMPORARY_BASE_AVERAGE" else "TEMPORARY_BASE_INSTANT"
    return AcceptedBaseCoordinate(
        id = id,
        name = name,
        latDeg = lat,
        lonDeg = lon,
        ellipsoidalHeightM = ellipsoidalHeightM,
        mslAltitudeM = mslAltitudeM,
        geoidSeparationM = geoidSeparationM,
        frame = "UNKNOWN",
        epoch = null,
        method = if (source == "AVERAGE") "LONG_AVERAGE" else "UNKNOWN",
        durationSeconds = sampleCount.takeIf { it > 0 }?.toLong(),
        horizontalUncertaintyM = null,
        verticalUncertaintyM = null,
        antennaHeightM = null,
        antennaReferencePoint = null,
        sourceSessionId = null,
        sourceDescription = transferSource,
    )
}

private fun RecordingPolicyProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = buildList {
            if (recordTxToReceiver) add("TX")
            if (recordNtripCorrectionInput) add("corrections")
            if (exportNmea) add("NMEA")
            if (exportNmea) add("PPP->$pppNmeaGgaQuality")
            if (exportJsonSolution) add("JSON")
            if (exportGpx) add("GPX")
            if (recordRemoteBaseRaw) add("remote base raw")
        }.ifEmpty { listOf("receiver RX only") }.joinToString(" · "),
    )

private fun RtklibProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = if (enabled) {
            buildList {
                add(preset)
                if (outputNmea) add("NMEA")
                if (outputPos) add("POS")
            }.joinToString(" · ")
        } else {
            "disabled"
        },
    )

private fun SolutionPolicyProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = "screen ${screenPolicy.name} · mock ${mockPolicy.name}",
    )

private fun StorageProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = if (kind == "SAF") "SAF folder" else "App-private storage",
    )

private fun filteredSettingsSetRows(
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    filter: ProfileDeviceFilter,
    profileStore: ProfileStores? = null,
): List<ProfileListRow> {
    val visible = settingsSets.filter { filter.matchesSettingsSet(it) }
    val selected = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
    val withSelected = if (selected != null && visible.none { it.id == selected.id }) {
        visible + selected
    } else {
        visible
    }
    return SettingsSetListState.from(withSelected, selectedSettingsSetId).rows.map { original ->
        val set = withSelected.first { it.id == original.id }
        val setup = profileStore?.resolvedActiveSetup(set)
        val row = original.copy(hasLocalOverrides = setup?.isModified ?: original.hasLocalOverrides)
        if (selected != null && row.id == selected.id && !filter.matchesSettingsSet(selected)) {
            row.copy(outsideFilter = true)
        } else {
            row
        }
    }
}

private fun filteredCommandProfileRows(
    profiles: List<CommandProfile>,
    selectedCommandProfileId: String?,
    filter: ProfileDeviceFilter,
): List<ProfileListRow> {
    val visible = profiles.filter { filter.matchesCommandProfile(it) }
    val selected = profiles.firstOrNull { it.id == selectedCommandProfileId }
    val withSelected = if (selected != null && visible.none { it.id == selected.id }) {
        visible + selected
    } else {
        visible
    }
    return withSelected.map { profile ->
        profile.profileRow(
            isSelected = profile.id == selectedCommandProfileId,
        ).copy(outsideFilter = selected?.id == profile.id && !filter.matchesCommandProfile(profile))
    }
}

private fun dashboardSelectorRows(
    selector: DashboardSelector,
    profileStore: ProfileStores,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    deviceFilter: ProfileDeviceFilter,
): List<ProfileListRow> {
    val selectedSettingsSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
    return when (selector) {
        DashboardSelector.SETTINGS_SET -> filteredSettingsSetRows(settingsSets, selectedSettingsSetId, deviceFilter, profileStore)
        DashboardSelector.WORKFLOW -> WORKFLOW_MODE_OPTIONS.map { option ->
            ProfileListRow(
                id = option.value,
                name = option.label,
                isProtected = false,
                hasLocalOverrides = false,
                isSelected = option.value == profileStore.activeProfileId(selectedSettingsSet, ActiveSetupOptionKey.WORKFLOW),
            )
        }
        DashboardSelector.DEVICE -> ProfileDeviceFilter.entries.map { filter ->
            ProfileListRow(
                id = filter.storageValue,
                name = filter.displayName,
                isProtected = false,
                hasLocalOverrides = false,
                isSelected = filter == deviceFilter,
            )
        }
        DashboardSelector.MOUNTPOINT -> (selectedSettingsSet
            ?.selectableNtripMountpoints(profileStore.ntripMountpointProfiles())
            ?: profileStore.ntripMountpointProfiles()).map { profile ->
            profile.profileRow(
                casters = profileStore.ntripCasterProfiles(),
                isSelected = profile.id == profileStore.activeProfileId(selectedSettingsSet, ActiveSetupOptionKey.NTRIP_MOUNTPOINT),
            )
        }
        DashboardSelector.INIT_PROFILES -> filteredCommandProfileRows(
            profiles = profileStore.commandProfiles(),
            selectedCommandProfileId = profileStore.activeProfileId(selectedSettingsSet, ActiveSetupOptionKey.RECEIVER_COMMAND),
            filter = deviceFilter,
        )
        DashboardSelector.UPLOAD -> {
            val setup = selectedSettingsSet?.let { profileStore.resolvedActiveSetup(it) }
            val option = setup?.option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD)
            listOf(ProfileListRow(UploadSelectorOffProfileId, "Off", false, false,
                isSelected = setup?.uploadSelection?.enabled == false && option?.selectionPresent != false)) +
                profileStore.ntripCasterUploadProfiles().map { profile ->
                    profile.profileRow(isSelected = setup?.uploadSelection?.enabled == true && option?.effectiveValueId == profile.id)
                }
        }
        DashboardSelector.STORAGE -> profileStore.storageProfiles().map { profile ->
            profile.profileRow(isSelected = profile.id == profileStore.activeProfileId(selectedSettingsSet, ActiveSetupOptionKey.STORAGE))
        }
    }
}

private fun <T> List<T>.profileOptions(idOf: (T) -> String, nameOf: (T) -> String): List<EditableProfileOption> =
    map { EditableProfileOption(idOf(it), nameOf(it)) }

private fun nullableProfileOptions(options: List<EditableProfileOption>): List<EditableProfileOption> =
    listOf(EditableProfileOption("", "None")) + options

private inline fun List<RecordingSettingsSet>.updateSelected(
    selectedId: String,
    transform: (RecordingSettingsSet) -> RecordingSettingsSet,
): List<RecordingSettingsSet> =
    map { set -> if (set.id == selectedId) transform(set) else set }

private fun Map<String, String>.required(key: String): String =
    optional(key) ?: error("$key is required.")

private fun Map<String, String>.optional(key: String): String? =
    get(key)?.trim()?.takeIf(String::isNotBlank)

private fun String?.toBooleanStrictOrFalse(): Boolean =
    equals("true", ignoreCase = true)

private fun reference(id: String, known: List<Pair<String, String>>): ProfileReference =
    ProfileReference(id, known.firstOrNull { it.first == id }?.second ?: id)
