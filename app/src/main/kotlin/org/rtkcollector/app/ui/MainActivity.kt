package org.rtkcollector.app.ui

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.console.DeviceConsoleController
import org.rtkcollector.app.console.DeviceConsoleLineEnding
import org.rtkcollector.app.console.DeviceConsoleState
import org.rtkcollector.app.base.AcceptedBaseCoordinate
import org.rtkcollector.app.base.AcceptedBaseCoordinateStore
import org.rtkcollector.app.base.BaseCoordinateForm
import org.rtkcollector.app.base.BasePositionJsonCodec
import org.rtkcollector.app.profile.ActiveRecordingConfig
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.NtripCasterUploadProfile
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.NtripMountpointOverride
import org.rtkcollector.app.profile.ProfileStores
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingPolicyProfile
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.RtklibProfile
import org.rtkcollector.app.profile.SettingsBackupFile
import org.rtkcollector.app.profile.SettingsImportValidationResult
import org.rtkcollector.app.profile.SettingsSetExportOptions
import org.rtkcollector.app.profile.SolutionPolicyProfile
import org.rtkcollector.app.profile.StorageProfile
import org.rtkcollector.app.profile.UsbBaudProfile
import org.rtkcollector.app.profile.WorkflowApplicationPolicy
import org.rtkcollector.app.profile.displayMountpoint
import org.rtkcollector.app.profile.ntripCasterUploadSecretId
import org.rtkcollector.app.profile.ntripCasterSecretId
import org.rtkcollector.app.profile.readSettingsImportText
import org.rtkcollector.app.profile.renameProfile
import org.rtkcollector.app.profile.validateSettingsImportJson
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
import org.rtkcollector.app.recording.SessionNmeaExporter
import org.rtkcollector.app.recording.SessionNmeaShareSelection
import org.rtkcollector.app.sessions.FilesystemSessionBrowser
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
import org.rtkcollector.app.ui.dashboard.DashboardLayoutPreference
import org.rtkcollector.app.ui.dashboard.BaseCoordinateCandidate
import org.rtkcollector.app.ui.dashboard.CoordinatePair
import org.rtkcollector.app.ui.dashboard.FixCardState
import org.rtkcollector.app.ui.dashboard.ProfilesCardState
import org.rtkcollector.app.ui.dashboard.RtklibCardState
import org.rtkcollector.app.ui.dashboard.HomeDashboard
import org.rtkcollector.app.ui.dashboard.MockGpsDashboardState
import org.rtkcollector.app.ui.dashboard.coordinatePairOrNull
import org.rtkcollector.app.ui.dashboard.dashboardStateFromRecordingIntent
import org.rtkcollector.app.ui.dashboard.formatBytes
import org.rtkcollector.app.ui.dashboard.receiverFrequencyForFamily
import org.rtkcollector.app.ui.dashboard.serviceCoordinateAveragingState
import org.rtkcollector.app.ui.console.DeviceConsoleOption
import org.rtkcollector.app.ui.console.DeviceConsoleScreen
import org.rtkcollector.app.ui.profiles.SettingsSetListScreen
import org.rtkcollector.app.ui.profiles.SettingsSetListState
import org.rtkcollector.app.ui.profiles.NtripMountpointEditorState
import org.rtkcollector.app.ui.profiles.NtripMountpointScreen
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
import org.rtkcollector.app.ui.profiles.persistentBaudWriteAction
import org.rtkcollector.app.ui.profiles.persistentReceiverWriteAction
import org.rtkcollector.app.ui.profiles.profileDeleteActionLabel
import org.rtkcollector.app.ui.imports.SettingsImportScreen
import org.rtkcollector.app.ui.imports.settingsImportUriFromIntent
import org.rtkcollector.app.ui.sessions.SessionsScreen
import org.rtkcollector.app.ui.settings.SettingsHub
import org.rtkcollector.app.ui.usb.UsbDeviceChoice
import org.rtkcollector.app.ui.usb.UsbStartAccessAction
import org.rtkcollector.app.ui.usb.UsbStartAccessDecision
import org.rtkcollector.receiver.unicore.Um980PersistentBaudPlan
import org.rtkcollector.receiver.unicore.Um980PersistentBaudStep
import org.rtkcollector.receiver.unicore.Um980NmeaExportOptions
import org.rtkcollector.receiver.unicore.Um980NmeaReexporter
import org.rtkcollector.receiver.unicore.Um980RuntimeCommandValidator
import org.rtkcollector.core.correction.NtripCredentials
import org.rtkcollector.core.correction.NtripSourcetableClient
import org.rtkcollector.core.correction.NtripSourcetableRequest
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

private const val TEMP_SHARE_ZIP_CLEANUP_DELAY_MILLIS = 60L * 60L * 1000L
private const val PERSISTENT_RECEIVER_COMMAND_DELAY_MILLIS = 100L
private const val PERSISTENT_RECEIVER_SAVE_OK_TIMEOUT_MILLIS = 3_000L
private const val DEVICE_CONSOLE_PERMISSION_REQUIRED = "USB permission is required before opening the device console."
private val persistentReceiverWriteInProgress = AtomicBoolean(false)

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
)

private data class PendingStorageFolderSelection(
    val target: ProfileEditorTarget,
    val values: Map<String, String>,
)

@Composable
fun RtkCollectorApp(
    externalIntent: Intent? = null,
    onExternalIntentConsumed: () -> Unit = {},
) {
    var screen by rememberSaveable(stateSaver = AppScreenSaver) { mutableStateOf(AppScreen.HOME) }
    val context = LocalContext.current
    val profileStore = remember(context) { ProfileStores(context) }
    val baseCoordinateStore = remember(context) { AcceptedBaseCoordinateStore(context) }
    val initialSelectedSettingsSetId = remember(profileStore) { profileStore.selectedSettingsSetId() }
    var settingsSets by remember {
        mutableStateOf(profileStore.settingsSetsWithRememberedMountpoint(initialSelectedSettingsSetId))
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
    var showDashboardLayoutDialog by remember { mutableStateOf(false) }
    var showMockGpsDialog by remember { mutableStateOf(false) }
    var showSettingsExportDialog by remember { mutableStateOf(false) }
    var includePlaintextPasswordsInBackup by remember { mutableStateOf(false) }
    var zipProgressText by remember { mutableStateOf<String?>(null) }
    var sessionProgressFraction by remember { mutableStateOf<Float?>(null) }
    var pendingSettingsImport by remember { mutableStateOf<PendingSettingsImport?>(null) }
    var pendingStorageFolderSelection by remember { mutableStateOf<PendingStorageFolderSelection?>(null) }
    var settingsImportRequestId by remember { mutableStateOf(0) }
    var sessionBrowserState by remember { mutableStateOf(SessionBrowserState()) }
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
    val secretStore = remember(context) { NtripSecretStore(context) }
    @Suppress("UNUSED_VARIABLE")
    val currentProfileRevision = profileRevision
    val usbDeviceChoices = remember(currentProfileRevision) { context.currentUsbDeviceChoices() }
    var selectedWorkflowId by remember {
        val initialWorkflowId = profileStore.selectedWorkflowId()
            ?: settingsSets.firstOrNull { it.id == selectedSettingsSetId }.applyWorkflowPolicy(null)
        profileStore.saveSelectedWorkflowId(initialWorkflowId)
        mutableStateOf(initialWorkflowId)
    }
    var state by remember {
        mutableStateOf(profileStore.plannedDashboardState(settingsSets, selectedSettingsSetId, selectedWorkflowId))
    }
    var startInProgress by rememberSaveable { mutableStateOf(false) }
    var manualBaseCoordinate by remember { mutableStateOf<BaseCoordinateCandidate?>(null) }
    LaunchedEffect(externalIntent) {
        if (externalIntent != null) {
            settingsImportUriFromIntent(externalIntent)?.let { uri ->
                val requestId = settingsImportRequestId + 1
                settingsImportRequestId = requestId
                pendingSettingsImport = PendingSettingsImport(
                    source = uri.toString(),
                    result = SettingsImportValidationResult.Loading,
                )
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        validateSettingsImportJson(readSettingsImportText(context.contentResolver, uri))
                    }.getOrElse { error ->
                        SettingsImportValidationResult.Invalid(error.message ?: "Settings backup could not be read.")
                    }
                }
                if (settingsImportRequestId == requestId) {
                    pendingSettingsImport = PendingSettingsImport(
                        source = uri.toString(),
                        result = result,
                    )
                }
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
    fun runSessionTask(label: String, task: () -> Unit) {
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
                        Toast.makeText(context, "$label failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }.start()
    }
    fun refreshProfileUi(updatedSettingsSets: List<RecordingSettingsSet> = settingsSets) {
        settingsSets = updatedSettingsSets
        val planned = profileStore.plannedDashboardState(updatedSettingsSets, selectedSettingsSetId, selectedWorkflowId)
        state = state.withPlannedConfiguration(planned)
        profileRevision++
    }
    fun updateMockGpsSelection(enabled: Boolean, rateHz: Int) {
        val updated = settingsSets.updateSelected(selectedSettingsSetId) { set ->
            val current = set.overrides.recordingOutput
            set.copy(
                overrides = set.overrides.copy(
                    recordingOutput = (current ?: org.rtkcollector.app.profile.RecordingOutputOverride()).copy(
                        enableMockLocation = enabled,
                        mockLocationRateHz = rateHz,
                    ),
                ),
            )
        }
        settingsSets = updated
        profileStore.saveSettingsSets(updated)
        refreshProfileUi(updated)
        if (state.isRecording) {
            state = state.copy(mockGps = MockGpsDashboardState(enabled = enabled, rateHz = rateHz))
            context.startService(
                RecordingForegroundService.mockLocationUpdateIntent(
                    context = context,
                    enabled = enabled,
                    rateHz = rateHz,
                ),
            )
        }
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                importSettingsBackup(context, uri)
            }.onSuccess {
                val updatedSelectedSettingsSetId = profileStore.selectedSettingsSetId()
                settingsSets = profileStore.settingsSetsWithRememberedMountpoint(updatedSelectedSettingsSetId)
                selectedSettingsSetId = updatedSelectedSettingsSetId
                selectedWorkflowId = profileStore.selectedWorkflowId()
                refreshProfileUi(settingsSets)
                Toast.makeText(context, "Settings backup imported.", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "Cannot import settings backup: ${error.message}", Toast.LENGTH_LONG).show()
            }
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
                baseCoordinateStore.upsert(coordinate)
                baseCoordinateStore.saveSelectedCoordinateId(coordinate.id)
                profileRevision++
                Toast.makeText(context, "Base coordinate imported.", Toast.LENGTH_LONG).show()
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
    fun renameProfile(kind: ProfileKind, id: String, name: String): Boolean =
        runCatching {
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
    fun deleteProfileIfUnused(kind: ProfileKind, id: String, delete: () -> Unit) {
        if (settingsSets.referenceProfile(kind, id)) {
            Toast.makeText(context, "Cannot delete: profile is used by a settings set.", Toast.LENGTH_LONG).show()
            return
        }
        delete()
        refreshProfileUi()
    }
    fun deleteSettingsSet(id: String) {
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
        if (target.kind != ProfileKind.SETTINGS_SET && settingsSets.referenceProfile(target.kind, target.id)) return null
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
        screen = screen.backScreen(profileEditorTarget)
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    RecordingForegroundService.ACTION_STATE -> {
                        val nextState = dashboardStateFromRecordingIntent(intent).withPlannedConfiguration(
                            profileStore.plannedDashboardState(settingsSets, selectedSettingsSetId, selectedWorkflowId),
                        )
                        state = nextState
                        if (nextState.isRecording || nextState.lastError != null || nextState.errorSeverity != "NONE") {
                            startInProgress = false
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        context.startService(
            Intent(context, RecordingForegroundService::class.java).setAction(RecordingForegroundService.ACTION_QUERY),
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            pendingSettingsImport?.let { pendingImport ->
                SettingsImportScreen(
                    source = pendingImport.source,
                    result = pendingImport.result,
                    recordingActive = state.isRecording,
                    onImport = {
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before importing settings.", Toast.LENGTH_LONG).show()
                        } else {
                            val valid = pendingImport.result as? SettingsImportValidationResult.Valid
                            if (valid != null) {
                                runCatching {
                                    importSettingsBackup(context, valid.backup)
                                }.onSuccess {
                                    val updatedSelectedSettingsSetId = profileStore.selectedSettingsSetId()
                                    val updatedSettingsSets =
                                        profileStore.settingsSetsWithRememberedMountpoint(updatedSelectedSettingsSetId)
                                    val updatedSelectedWorkflowId = profileStore.selectedWorkflowId()
                                    settingsSets = updatedSettingsSets
                                    selectedSettingsSetId = updatedSelectedSettingsSetId
                                    selectedWorkflowId = updatedSelectedWorkflowId
                                    state = state.withPlannedConfiguration(
                                        profileStore.plannedDashboardState(
                                            updatedSettingsSets,
                                            updatedSelectedSettingsSetId,
                                            updatedSelectedWorkflowId,
                                        ),
                                    )
                                    profileRevision++
                                    pendingSettingsImport = null
                                    screen = AppScreen.HOME
                                    Toast.makeText(context, "Settings backup imported.", Toast.LENGTH_LONG).show()
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
                        screen = AppScreen.HOME
                    },
                )
                return@Surface
            }
            when (screen) {
                AppScreen.HOME -> HomeDashboard(
                    state = state,
                    layoutPreference = dashboardLayout,
                    startInProgress = startInProgress,
                    onPrimaryAction = {
                        if (state.isRecording) {
                            startInProgress = false
                            context.startService(RecordingForegroundService.stopIntent(context))
                        } else if (persistentReceiverWriteInProgress.get()) {
                            Toast.makeText(
                                context,
                                "Wait for persistent receiver configuration write to finish before recording.",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            buildDashboardStartIntent(
                                context = context,
                                settingsSets = settingsSets,
                                selectedSettingsSetId = selectedSettingsSetId,
                                selectedWorkflowId = selectedWorkflowId,
                                selectedBaseCoordinate = baseCoordinateStore.selectedCoordinate(),
                            )?.let { intent ->
                                startInProgress = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            }
                        }
                    },
                    onMenu = { screen = AppScreen.SETTINGS },
                    onNtrip = { dashboardSelector = DashboardSelector.MOUNTPOINT },
                    onUsbPermission = {
                        requestSelectedUsbPermission(context, selectedSettingsSetId)
                        profileRevision++
                    },
                    onMockGps = { showMockGpsDialog = true },
                    onSettingsSet = {
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing settings set.", Toast.LENGTH_LONG).show()
                        } else {
                            dashboardSelector = DashboardSelector.SETTINGS_SET
                        }
                    },
                    onWorkflow = {
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing workflow.", Toast.LENGTH_LONG).show()
                        } else {
                            dashboardSelector = DashboardSelector.WORKFLOW
                        }
                    },
                    onReceiver = {
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing receiver commands.", Toast.LENGTH_LONG).show()
                        } else {
                            dashboardSelector = DashboardSelector.RECEIVER
                        }
                    },
                    onStorage = {
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing storage.", Toast.LENGTH_LONG).show()
                        } else {
                            dashboardSelector = DashboardSelector.STORAGE
                        }
                    },
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
                                "Fixed base requires latitude, longitude and ellipsoidal height.",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@HomeDashboard
                        }
                        baseCoordinateStore.upsert(acceptedCoordinate)
                        baseCoordinateStore.saveSelectedCoordinateId(acceptedCoordinate.id)
                        manualBaseCoordinate = candidate
                        selectedWorkflowId = WORKFLOW_FIXED_BASE
                        profileStore.saveSelectedWorkflowId(WORKFLOW_FIXED_BASE)
                        settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
                            val baseProfile = profileStore.commandProfiles().preferredBaseCommandProfile()
                            if (baseProfile != null) {
                                set.copy(commandProfileRef = ProfileReference(baseProfile.id, baseProfile.name))
                            } else {
                                set
                            }
                        }
                        profileStore.saveSettingsSets(settingsSets)
                        refreshProfileUi(settingsSets)
                        Toast.makeText(
                            context,
                            "Fixed-base workflow selected with base coordinate ${acceptedCoordinate.displayLabel()}.",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
                AppScreen.SETTINGS ->
                    SettingsHub(
                        activeSettingsSetLabel = settingsSets.firstOrNull { it.id == selectedSettingsSetId }?.displayNameWithOverrides()
                            ?: "n/a",
                        activeWorkflowLabel = (selectedWorkflowId ?: profileStore.selectedWorkflowId()).workflowLabel(),
                        onActiveSettingsSet = {
                            if (state.isRecording) {
                                Toast.makeText(context, "Stop recording before loading a different settings set.", Toast.LENGTH_LONG).show()
                            } else {
                                screen = AppScreen.SETTINGS_SET_SELECTOR
                            }
                        },
                        onSettingsSets = { screen = AppScreen.SETTINGS_SETS },
                        onWorkflowSelection = {
                            if (state.isRecording) {
                                Toast.makeText(context, "Stop recording before changing workflow.", Toast.LENGTH_LONG).show()
                            } else {
                                dashboardSelector = DashboardSelector.WORKFLOW
                                screen = AppScreen.HOME
                            }
                        },
                        onBaseCoordinates = { screen = AppScreen.BASE_COORDINATES },
                        dashboardLayoutLabel = dashboardLayout.displayName,
                        onDashboardLayout = { showDashboardLayoutDialog = true },
                        onNtripCaster = { screen = AppScreen.NTRIP_CASTER },
                        onNtripCasterUpload = { screen = AppScreen.NTRIP_CASTER_UPLOAD },
                        onNtripMountpoint = { screen = AppScreen.NTRIP_MOUNTPOINT_PROFILES },
                        onUsbBaud = { screen = AppScreen.USB_BAUD },
                        onCommands = { screen = AppScreen.COMMANDS },
                        onReceiverProfile = {
                            if (state.isRecording) {
                                Toast.makeText(context, "Stop recording before changing receiver commands.", Toast.LENGTH_LONG).show()
                            } else {
                                dashboardSelector = DashboardSelector.RECEIVER
                                screen = AppScreen.HOME
                            }
                        },
                        onRecordingOutputs = { screen = AppScreen.RECORDING_OUTPUTS },
                        onRtklibProfiles = { screen = AppScreen.RTKLIB_PROFILES },
                        onSolutionPolicy = { screen = AppScreen.SOLUTION_POLICIES },
                        onStorage = { screen = AppScreen.STORAGE },
                        onSessions = {
                            refreshSessions()
                            screen = AppScreen.SESSIONS
                        },
                        onExportSettings = {
                            includePlaintextPasswordsInBackup = false
                            showSettingsExportDialog = true
                        },
                        onImportSettings = {
                            importSettingsLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                        },
                        onDeviceConsole = { screen = AppScreen.DEVICE_CONSOLE },
                        onBack = { screen = AppScreen.HOME },
                    )
                AppScreen.NTRIP_MOUNTPOINT -> NtripMountpointScreen(
                    initialState = NtripMountpointEditorState(
                        mountpointText = profileStore.selectedMountpointLabel(selectedSettingsSetId).takeUnless { it == "n/a" }.orEmpty(),
                        availableMountpoints = profileStore.selectedCasterMountpoints(selectedSettingsSetId),
                    ),
                    onBack = { screen = AppScreen.SETTINGS },
                    onSave = { mountpoint ->
                        val updated = settingsSets.map { set ->
                            if (set.id == selectedSettingsSetId) {
                                set.copy(
                                    overrides = set.overrides.copy(
                                        ntripMountpoint = (set.overrides.ntripMountpoint ?: NtripMountpointOverride()).copy(
                                            mountpoint = mountpoint,
                                        ),
                                    ),
                                )
                            } else {
                                set
                            }
                        }
                        settingsSets = updated
                        profileStore.saveSettingsSets(updated)
                        refreshProfileUi(updated)
                        if (state.isRecording) {
                            buildNtripUpdateIntent(context, settingsSets, selectedSettingsSetId, selectedWorkflowId)?.let {
                                context.startService(it)
                            }
                        }
                        screen = AppScreen.SETTINGS
                    },
                )
                AppScreen.SETTINGS_SETS -> SettingsSetListScreen(
                    title = "Settings sets",
                    state = SettingsSetListState.from(settingsSets, selectedSettingsSetId),
                    onSelect = { id ->
                        selectedSettingsSetId = id
                        manualBaseCoordinate = null
                        profileStore.saveSelectedSettingsSetId(id)
                        settingsSets = profileStore.settingsSetsWithRememberedMountpoint(id)
                        val selected = settingsSets.firstOrNull { it.id == id }
                        selectedWorkflowId = selected.applyWorkflowPolicy(selectedWorkflowId)
                        profileStore.saveSelectedWorkflowId(selectedWorkflowId)
                        refreshProfileUi(settingsSets)
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
                            isSelected = settingsSets.firstOrNull { set -> set.id == selectedSettingsSetId }
                                ?.ntripCasterUploadProfileRef
                                ?.id == it.id,
                        )
                    },
                    onSelect = { id ->
                        val profile = profileStore.ntripCasterUploadProfiles().firstOrNull { it.id == id }
                        if (profile != null) {
                            val updated = settingsSets.map { set ->
                                if (set.id == selectedSettingsSetId) {
                                    set.copy(
                                        ntripCasterUploadProfileRef = ProfileReference(profile.id, profile.name),
                                        baseCasterUploadEnabled = true,
                                    )
                                } else {
                                    set
                                }
                            }
                            settingsSets = updated
                            profileStore.saveSettingsSets(updated)
                            refreshProfileUi(updated)
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
                    supportsSelection = true,
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
                    title = "Init/shutdown scripts",
                    rows = profileStore.commandProfiles().map { it.profileRow() },
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
                        coordinate.profileRow(
                            isSelected = coordinate.id == baseCoordinateStore.selectedCoordinateId(),
                        )
                    },
                    onSelect = { id ->
                        baseCoordinateStore.saveSelectedCoordinateId(id)
                        profileRevision++
                    },
                    onAdd = {
                        val coordinate = AcceptedBaseCoordinate(
                            id = profileStore.duplicateId("base"),
                            name = "New base coordinate",
                            latDeg = 0.0,
                            lonDeg = 0.0,
                            ellipsoidalHeightM = 0.0,
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
                            baseCoordinateStore.upsert(coordinate.copy(name = name.trim()))
                            profileRevision++
                            true
                        } ?: false
                    },
                    onDelete = { id ->
                        baseCoordinateStore.delete(id)
                        profileRevision++
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
                                runCatching {
                                    updated.validate()
                                    baseCoordinateStore.upsert(updated)
                                    profileRevision++
                                    screen = AppScreen.BASE_COORDINATES
                                }.onFailure { error ->
                                    Toast.makeText(context, "Cannot save base coordinate: ${error.message}", Toast.LENGTH_LONG).show()
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
                    rows = SettingsSetListState.from(settingsSets, selectedSettingsSetId).rows,
                    onSelect = { id ->
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing workflow.", Toast.LENGTH_LONG).show()
                            screen = AppScreen.HOME
                        } else {
                            selectedSettingsSetId = id
                            manualBaseCoordinate = null
                            profileStore.saveSelectedSettingsSetId(id)
                            settingsSets = profileStore.settingsSetsWithRememberedMountpoint(id)
                            val selectedSet = settingsSets.firstOrNull { it.id == id }
                            selectedWorkflowId = selectedSet.applyWorkflowPolicy(selectedWorkflowId)
                            profileStore.saveSelectedWorkflowId(selectedWorkflowId)
                            refreshProfileUi(settingsSets)
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
                    rows = profileStore.ntripMountpointProfiles().map {
                        it.profileRow(
                            casters = profileStore.ntripCasterProfiles(),
                            isSelected = it.id == settingsSets.firstOrNull { set -> set.id == selectedSettingsSetId }?.ntripMountpointProfileRef?.id,
                        )
                    },
                    onSelect = { id ->
                        profileStore.ntripMountpointProfiles().firstOrNull { it.id == id }?.let { profile ->
                            profileStore.saveLastActiveNtripMountpointProfileId(profile.id)
                            val casterProfileRef = profileStore.ntripCasterProfiles().firstOrNull { it.id == profile.casterProfileId }?.let {
                                ProfileReference(it.id, it.name)
                            }
                            settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
                                set.copy(
                                    ntripCasterProfileRef = casterProfileRef,
                                    ntripMountpointProfileRef = ProfileReference(profile.id, profile.name),
                                    overrides = set.overrides.copy(ntripMountpoint = null),
                                )
                            }
                            profileStore.saveSettingsSets(settingsSets)
                            refreshProfileUi(settingsSets)
                            if (state.isRecording) {
                                buildNtripUpdateIntent(context, settingsSets, selectedSettingsSetId, selectedWorkflowId)?.let {
                                    context.startService(it)
                                }
                            }
                        }
                        screen = AppScreen.HOME
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
                    title = "Select receiver command profile",
                    rows = profileStore.commandProfiles().map {
                        it.profileRow(isSelected = it.id == settingsSets.firstOrNull { set -> set.id == selectedSettingsSetId }?.commandProfileRef?.id)
                    },
                    onSelect = { id ->
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing receiver commands.", Toast.LENGTH_LONG).show()
                            screen = AppScreen.HOME
                        } else {
                            profileStore.commandProfiles().firstOrNull { it.id == id }?.let { profile ->
                                manualBaseCoordinate = null
                                settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
                                    set.copy(commandProfileRef = ProfileReference(profile.id, profile.name))
                                }
                                profileStore.saveSettingsSets(settingsSets)
                                refreshProfileUi(settingsSets)
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
                        it.profileRow(isSelected = it.id == settingsSets.firstOrNull { set -> set.id == selectedSettingsSetId }?.storageProfileRef?.id)
                    },
                    onSelect = { id ->
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing storage.", Toast.LENGTH_LONG).show()
                            screen = AppScreen.HOME
                        } else {
                            profileStore.storageProfiles().firstOrNull { it.id == id }?.let { profile ->
                                settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
                                    set.copy(storageProfileRef = ProfileReference(profile.id, profile.name))
                                }
                                profileStore.saveSettingsSets(settingsSets)
                                refreshProfileUi(settingsSets)
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
                            zipProgressText = "Preparing NMEA..."
                            sessionProgressFraction = 0f
                            Thread {
                                runCatching {
                                    val cacheRoot = context.cacheDir.resolve("session-share-nmea").toPath()
                                    cleanupTemporaryNmeaShares(cacheRoot)
                                    val filesystemEntries = selected.filterNot { it.isSafLocation }
                                    val safEntries = selected.filter { it.isSafLocation }
                                    val selection = SessionNmeaShareSelection.fromSessionDirectories(
                                        sessionDirectories = filesystemEntries.map { Paths.get(it.location) },
                                        outputDirectory = cacheRoot,
                                    )
                                    val outputs = selection.plans.mapIndexed { index, plan ->
                                        runOnMain(context) {
                                            zipProgressText = "NMEA ${index + 1}/${selected.size}"
                                            sessionProgressFraction = (index + 1).toFloat() / selected.size.toFloat()
                                        }
                                        SessionNmeaExporter.export(plan)
                                    }.toMutableList()
                                    var skipped = selection.skippedCount
                                    safEntries.forEachIndexed { index, entry ->
                                        val output = SafSessionActions.createTemporaryNmeaShare(
                                            resolver = context.contentResolver,
                                            sessionUri = Uri.parse(entry.location),
                                            cacheRoot = cacheRoot,
                                        )
                                        if (output == null) {
                                            skipped++
                                        } else {
                                            outputs.add(output)
                                        }
                                        runOnMain(context) {
                                            val completed = filesystemEntries.size + index + 1
                                            zipProgressText = "NMEA $completed/${selected.size}"
                                            sessionProgressFraction = completed.toFloat() / selected.size.toFloat()
                                        }
                                    }
                                    skipped to outputs
                                }.onSuccess { (skipped, outputs) ->
                                    runOnMain(context) {
                                        zipProgressText = null
                                        sessionProgressFraction = null
                                        if (outputs.isEmpty()) {
                                            Toast.makeText(
                                                context,
                                                "No recorded NMEA file is available for the selected session(s).",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        } else {
                                            shareNmeaFiles(context, outputs.map { it.toFile() })
                                            val message = if (skipped > 0) {
                                                "Shared NMEA for ${outputs.size} session(s); $skipped selected session(s) had no NMEA export."
                                            } else {
                                                "Shared NMEA for ${outputs.size} session(s)."
                                            }
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                        refreshSessions()
                                    }
                                }.onFailure { error ->
                                    runOnMain(context) {
                                        zipProgressText = null
                                        sessionProgressFraction = null
                                        Toast.makeText(context, "Share NMEA failed: ${error.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }.start()
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
                                                Um980NmeaReexporter.reexportReceiverRxRaw(
                                                    receiverRxRaw = receiverRxRaw,
                                                    outputNmea = sessionDirectory.resolve("receiver-solution.nmea"),
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
                    onBack = { screen = AppScreen.SETTINGS },
                )
                AppScreen.DEVICE_CONSOLE -> {
                    val usbProfiles = profileStore.usbBaudProfiles()
                    val commandProfiles = profileStore.commandProfiles()
                    val selectedUsbProfile =
                        usbProfiles.firstOrNull { it.id == selectedConsoleUsbProfileId } ?: usbProfiles.firstOrNull()
                    val selectedCommandProfile =
                        commandProfiles.firstOrNull { it.id == selectedConsoleCommandProfileId } ?: commandProfiles.firstOrNull()

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
                        usbProfiles = usbProfiles.map { DeviceConsoleOption(it.id, it.name) },
                        commandProfiles = commandProfiles.map { DeviceConsoleOption(it.id, it.name) },
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
                        )
                        ProfileEditorScreen(
                            data = data,
                            actions = buildList {
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
                                                    savePassword = secretStore::putPassword,
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
                                                    savePassword = secretStore::putPassword,
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
                                runCatching {
                                    profileStore.saveProfileEditorData(
                                        target = target,
                                        values = values,
                                        settingsSets = settingsSets,
                                        savePassword = secretStore::putPassword,
                                    )
                                }.onSuccess { updated ->
                                    refreshProfileUi(updated)
                                    screen = target.kind.backScreen()
                                }.onFailure { error ->
                                    Toast.makeText(context, "Cannot save profile: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }
                }
            }
            dashboardSelector?.let { selector ->
                ProfileSelectorDialog(
                    title = selector.title,
                    rows = dashboardSelectorRows(selector, profileStore, settingsSets, selectedSettingsSetId),
                    onSelect = { id ->
                        when (selector) {
                            DashboardSelector.WORKFLOW -> {
                                selectedWorkflowId = id
                                manualBaseCoordinate = null
                                profileStore.saveSelectedWorkflowId(id)
                                refreshProfileUi(settingsSets)
                            }
                            DashboardSelector.SETTINGS_SET -> {
                                selectedSettingsSetId = id
                                manualBaseCoordinate = null
                                profileStore.saveSelectedSettingsSetId(id)
                                settingsSets = profileStore.settingsSetsWithRememberedMountpoint(id)
                                val selectedSet = settingsSets.firstOrNull { it.id == id }
                                selectedWorkflowId = selectedSet.applyWorkflowPolicy(selectedWorkflowId)
                                profileStore.saveSelectedWorkflowId(selectedWorkflowId)
                                refreshProfileUi(settingsSets)
                            }
                            DashboardSelector.MOUNTPOINT -> {
                                profileStore.ntripMountpointProfiles().firstOrNull { it.id == id }?.let { profile ->
                                    profileStore.saveLastActiveNtripMountpointProfileId(profile.id)
                                    val casterProfileRef = profileStore.ntripCasterProfiles().firstOrNull { it.id == profile.casterProfileId }?.let {
                                        ProfileReference(it.id, it.name)
                                    }
                                    settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
                                        set.copy(
                                            ntripCasterProfileRef = casterProfileRef,
                                            ntripMountpointProfileRef = ProfileReference(profile.id, profile.name),
                                            overrides = set.overrides.copy(ntripMountpoint = null),
                                        )
                                    }
                                    profileStore.saveSettingsSets(settingsSets)
                                    refreshProfileUi(settingsSets)
                                    if (state.isRecording) {
                                        buildNtripUpdateIntent(context, settingsSets, selectedSettingsSetId, selectedWorkflowId)?.let {
                                            context.startService(it)
                                        }
                                    }
                                }
                            }
                            DashboardSelector.RECEIVER -> {
                                profileStore.commandProfiles().firstOrNull { it.id == id }?.let { profile ->
                                    manualBaseCoordinate = null
                                    settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
                                        set.copy(commandProfileRef = ProfileReference(profile.id, profile.name))
                                    }
                                    profileStore.saveSettingsSets(settingsSets)
                                    refreshProfileUi(settingsSets)
                                }
                            }
                            DashboardSelector.STORAGE -> {
                                profileStore.storageProfiles().firstOrNull { it.id == id }?.let { profile ->
                                    settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
                                        set.copy(storageProfileRef = ProfileReference(profile.id, profile.name))
                                    }
                                    profileStore.saveSettingsSets(settingsSets)
                                    refreshProfileUi(settingsSets)
                                }
                            }
                        }
                        dashboardSelector = null
                    },
                    onDismiss = { dashboardSelector = null },
                )
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
                    onSelect = { layout ->
                        dashboardLayout = layout
                        showDashboardLayoutDialog = false
                    },
                    onDismiss = { showDashboardLayoutDialog = false },
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
    onSelect: (DashboardLayoutPreference) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dashboard layout") },
        text = {
            androidx.compose.foundation.layout.Column {
                DashboardLayoutPreference.entries.forEach { layout ->
                    TextButton(
                        onClick = { onSelect(layout) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val suffix = if (layout == selected) " (selected)" else ""
                        Text("${layout.displayName}$suffix")
                    }
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
    val storageProfile = selectedSet
        ?.storageProfileRef
        ?.id
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
    val profileQuality = selectedSet
        ?.recordingOutputProfileRef
        ?.id
        ?.let { id -> profileStore.recordingPolicyProfiles().firstOrNull { it.id == id } }
        ?.pppNmeaGgaQuality
        ?: RecordingPolicyProfile.DEFAULT_PPP_NMEA_GGA_QUALITY
    return selectedSet
        ?.overrides
        ?.recordingOutput
        ?.pppNmeaGgaQuality
        ?: profileQuality
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
    return selectedSet
        ?.storageProfileRef
        ?.id
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
    savePassword: (String, String) -> Unit,
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
    val secretId = ntripCasterSecretId(targetId)
    savePassword(secretId, password)
    val resolvedPassword = password
    val credentials = username.takeIf(String::isNotBlank)?.let {
        NtripCredentials(username = it, password = resolvedPassword)
    }

    Thread(
        {
            runCatching {
                NtripSourcetableClient(
                    NtripSourcetableRequest(
                        host = host,
                        port = port,
                        credentials = credentials,
                    ),
                ).fetch()
            }.onSuccess { result ->
                runOnMain(context) {
                    val updatedProfiles = profileStore.ntripCasterProfiles().map { profile ->
                        if (profile.id == targetId) {
                            profile.copy(
                                host = host,
                                port = port,
                                username = username,
                                secretId = secretId,
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
    savePassword: (String, String) -> Unit,
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
        ),
        savePassword = savePassword,
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
    val passwords = if (includePlaintextPasswords) {
        secretStore.knownSecretIds().mapNotNull { id ->
            secretStore.getPassword(id)?.let { password -> id to password }
        }.toMap()
    } else {
        emptyMap()
    }
    return SettingsBackupFile.fromProfiles(
        commandProfiles = profileStore.commandProfiles(),
        usbBaudProfiles = profileStore.usbBaudProfiles(),
        ntripCasterProfiles = profileStore.ntripCasterProfiles(),
        ntripCasterUploadProfiles = profileStore.ntripCasterUploadProfiles(),
        ntripMountpointProfiles = profileStore.ntripMountpointProfiles(),
        recordingPolicyProfiles = profileStore.recordingPolicyProfiles(),
        rtklibProfiles = profileStore.rtklibProfiles(),
        solutionPolicyProfiles = profileStore.solutionPolicyProfiles(),
        storageProfiles = profileStore.storageProfiles(),
        settingsSets = profileStore.settingsSets(),
        selectedSettingsSetId = profileStore.selectedSettingsSetId(),
        selectedWorkflowId = profileStore.selectedWorkflowId(),
        lastActiveNtripMountpointProfileId = profileStore.lastActiveNtripMountpointProfileId(),
        passwordsBySecretId = passwords,
        options = SettingsSetExportOptions(includePlaintextPasswords),
    )
}

private fun shareSettingsBackup(context: Context, includePlaintextPasswords: Boolean) {
    val backup = buildSettingsBackup(context, includePlaintextPasswords)
    val directory = context.cacheDir.resolve("settings-backups").also { it.mkdirs() }
    val file = directory.resolve("rtkcollector-settings-${System.currentTimeMillis()}.json")
    file.writeText(backup.toJson().toString(2), Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share RtkCollector settings"))
}

private fun importSettingsBackup(context: Context, uri: Uri) {
    val result = validateSettingsImportJson(readSettingsImportText(context.contentResolver, uri))
    val backup = when (result) {
        SettingsImportValidationResult.Loading -> error("Settings backup is still being read.")
        is SettingsImportValidationResult.Valid -> result.backup
        is SettingsImportValidationResult.Invalid -> error(result.message)
    }
    importSettingsBackup(context, backup)
}

private fun importSettingsBackup(context: Context, backup: SettingsBackupFile) {
    val profileStore = ProfileStores(context)
    val importedSettingsSets = sanitizedImportedSettingsSets(backup.settingsSets)
    profileStore.saveCommandProfiles(backup.commandProfiles)
    profileStore.saveUsbBaudProfiles(backup.usbBaudProfiles)
    profileStore.saveNtripCasterProfiles(backup.ntripCasterProfiles)
    profileStore.saveNtripCasterUploadProfiles(backup.ntripCasterUploadProfiles)
    profileStore.saveNtripMountpointProfiles(backup.ntripMountpointProfiles)
    profileStore.saveRecordingPolicyProfiles(backup.recordingPolicyProfiles)
    profileStore.saveStorageProfiles(backup.storageProfiles)
    profileStore.saveSettingsSets(importedSettingsSets)
    backup.selectedSettingsSetId
        ?.takeIf { id -> importedSettingsSets.any { it.id == id } }
        ?.let(profileStore::saveSelectedSettingsSetId)
    profileStore.saveSelectedWorkflowId(restoredWorkflowIdOrNull(backup.selectedWorkflowId))
    profileStore.saveLastActiveNtripMountpointProfileId(
        backup.lastActiveNtripMountpointProfileId
            ?.takeIf { id -> backup.ntripMountpointProfiles.any { it.id == id } },
    )
    val secretStore = NtripSecretStore(context)
    backup.plaintextPasswordsBySecretId.forEach { (secretId, password) ->
        secretStore.putPassword(secretId, password)
    }
}

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
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
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
            Text(row.displayName, style = MaterialTheme.typography.titleSmall)
            Text(row.summary, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!row.isSelected) {
                    Button(onClick = onSelect) {
                        Text("Use")
                    }
                }
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onCopy) {
                    Text("Copy")
                }
                TextButton(onClick = onRename) {
                    Text("Rename")
                }
                TextButton(onClick = onDelete) {
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
            EditableProfileField("ellipsoidalHeightM", "Ellipsoidal height meters", coordinate.ellipsoidalHeightM.toString()),
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
): ProfileEditorData =
    when (target.kind) {
        ProfileKind.SETTINGS_SET -> settingsSets.first { it.id == target.id }.let { set ->
            ProfileEditorData(
                title = "Edit settings set",
                fields = listOf(
                    EditableProfileField("name", "Name", set.name),
                    EditableProfileField(
                        key = "workflowApplicationPolicy",
                        label = "Workflow when activating this settings set",
                        value = set.workflowApplicationPolicy,
                        optionItems = WORKFLOW_APPLICATION_POLICY_OPTIONS,
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
                        key = "ntripCasterProfileId",
                        label = "NTRIP caster profile",
                        value = set.ntripCasterProfileRef?.id.orEmpty(),
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
                ),
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
                    EditableProfileField("mountpoint", "Mountpoint", profile.mountpoint),
                    EditableProfileField("username", "Username", profile.username),
                    EditableProfileField("password", "Password", storedPassword, secret = true),
                    EditableProfileField(
                        key = "protocolPolicy",
                        label = "Protocol policy",
                        value = profile.protocolPolicy,
                        optionItems = NTRIP_PROTOCOL_POLICY_OPTIONS,
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
                    EditableProfileField("runtimeScript", "Init script", profile.runtimeScript, multiline = true),
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
                        workflowApplicationPolicy = values.required("workflowApplicationPolicy"),
                        workflowId = values.required("workflowId"),
                        commandProfileRef = reference(values.required("commandProfileId"), commandProfiles().map { it.id to it.name }),
                        usbBaudProfileRef = reference(values.required("usbBaudProfileId"), usbBaudProfiles().map { it.id to it.name }),
                        ntripCasterProfileRef = values.optional("ntripCasterProfileId")?.let {
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
                    )
                }
            }
            saveSettingsSets(updated)
            return updated
        }
        ProfileKind.NTRIP_CASTER -> saveNtripCasterProfiles(
            ntripCasterProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected NTRIP caster profiles cannot be edited." }
                    val password = values.optional("password").orEmpty()
                    val secretId = ntripCasterSecretId(target.id)
                    savePassword(secretId, password)
                    profile.copy(
                        name = values.required("name"),
                        host = values.optional("host").orEmpty(),
                        port = values.optional("port")?.toIntOrNull() ?: 2101,
                        username = values.optional("username").orEmpty(),
                        secretId = secretId,
                        protocolPolicy = values.optional("protocolPolicy").orEmpty().ifBlank {
                            "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY"
                        },
                        sourcetableMountpoints = values.optional("sourcetableMountpoints")
                            ?.lines()
                            ?.map(String::trim)
                            ?.filter(String::isNotBlank)
                            ?: profile.sourcetableMountpoints,
                    )
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
                    val password = values.optional("password").orEmpty()
                    val secretId = ntripCasterUploadSecretId(target.id)
                    savePassword(secretId, password)
                    profile.copy(
                        name = values.required("name"),
                        host = values.optional("host").orEmpty(),
                        port = values.optional("port")?.toIntOrNull() ?: 2101,
                        mountpoint = values.optional("mountpoint").orEmpty(),
                        username = values.optional("username").orEmpty(),
                        secretId = secretId,
                        protocolPolicy = values.optional("protocolPolicy").orEmpty().ifBlank {
                            "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY"
                        },
                        enabledByDefault = values.optional("enabledByDefault").toBooleanStrictOrFalse(),
                    )
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
            val casterProfileRef = ProfileReference(
                casterProfileId,
                ntripCasterProfiles().firstOrNull { it.id == casterProfileId }?.name ?: casterProfileId,
            )
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
            val synchronizedSettings = settingsSets.map { set ->
                if (set.ntripMountpointProfileRef?.id == target.id) {
                    set.copy(ntripCasterProfileRef = casterProfileRef)
                } else {
                    set
                }
            }
            return updateSettingsSetReferenceNames(synchronizedSettings, target.kind, target.id, newName)
        }
        ProfileKind.COMMANDS -> saveCommandProfiles(
            commandProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected command profiles cannot be edited." }
                    profile.copy(
                        name = values.required("name"),
                        receiverFamily = values.required("receiverFamily"),
                        initScript = "",
                        runtimeScript = values.optional("runtimeScript").orEmpty(),
                        shutdownScript = values.optional("shutdownScript").orEmpty(),
                    )
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
        when (kind) {
            ProfileKind.COMMANDS -> set.copy(commandProfileRef = set.commandProfileRef.renameIfId(id, name))
            ProfileKind.USB_BAUD -> set.copy(usbBaudProfileRef = set.usbBaudProfileRef.renameIfId(id, name))
            ProfileKind.NTRIP_CASTER -> set.copy(ntripCasterProfileRef = set.ntripCasterProfileRef.renameNullableIfId(id, name))
            ProfileKind.NTRIP_CASTER_UPLOAD -> set.copy(
                ntripCasterUploadProfileRef = set.ntripCasterUploadProfileRef.renameNullableIfId(id, name),
            )
            ProfileKind.NTRIP_MOUNTPOINT -> set.copy(ntripMountpointProfileRef = set.ntripMountpointProfileRef.renameNullableIfId(id, name))
            ProfileKind.RECORDING_OUTPUTS -> set.copy(recordingOutputProfileRef = set.recordingOutputProfileRef.renameIfId(id, name))
            ProfileKind.RTKLIB -> set.copy(rtklibProfileRef = set.rtklibProfileRef.renameNullableIfId(id, name))
            ProfileKind.SOLUTION_POLICY -> set.copy(solutionPolicyProfileRef = set.solutionPolicyProfileRef.renameNullableIfId(id, name))
            ProfileKind.STORAGE -> set.copy(storageProfileRef = set.storageProfileRef.renameIfId(id, name))
            ProfileKind.SETTINGS_SET -> set
        }.also(RecordingSettingsSet::validate)
    }
    saveSettingsSets(updated)
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
            ProfileKind.USB_BAUD -> set.usbBaudProfileRef.id == id
            ProfileKind.NTRIP_CASTER -> set.ntripCasterProfileRef?.id == id
            ProfileKind.NTRIP_CASTER_UPLOAD -> set.ntripCasterUploadProfileRef?.id == id
            ProfileKind.NTRIP_MOUNTPOINT -> set.ntripMountpointProfileRef?.id == id
            ProfileKind.RECORDING_OUTPUTS -> set.recordingOutputProfileRef.id == id
            ProfileKind.RTKLIB -> set.rtklibProfileRef?.id == id
            ProfileKind.SOLUTION_POLICY -> set.solutionPolicyProfileRef?.id == id
            ProfileKind.STORAGE -> set.storageProfileRef.id == id
            ProfileKind.SETTINGS_SET -> false
        }
    }

private enum class AppScreen {
    HOME,
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
private const val WORKFLOW_BASE_CALIBRATION = "base-calibration"
private const val WORKFLOW_FIXED_BASE = "fixed-base"

private val WORKFLOW_MODE_OPTIONS = listOf(
    EditableProfileOption(WORKFLOW_PLAIN_ROVER, "Plain rover"),
    EditableProfileOption(WORKFLOW_ROVER_NTRIP, "Rover with NTRIP"),
    EditableProfileOption(WORKFLOW_BASE_CALIBRATION, "Temporary base"),
    EditableProfileOption(WORKFLOW_FIXED_BASE, "Fixed base"),
)

private val WORKFLOW_APPLICATION_POLICY_OPTIONS = listOf(
    EditableProfileOption(WorkflowApplicationPolicy.SET_SPECIFIC, "Select specific workflow"),
    EditableProfileOption(WorkflowApplicationPolicy.LET_USER_SELECT, "Let user select before start"),
    EditableProfileOption(WorkflowApplicationPolicy.LEAVE_INTACT, "Leave current workflow intact"),
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
    MOUNTPOINT("Select NTRIP mountpoint"),
    RECEIVER("Select receiver command profile"),
    STORAGE("Select storage profile"),
}

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

private fun buildDashboardStartIntent(
    context: Context,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    selectedWorkflowId: String?,
    selectedBaseCoordinate: AcceptedBaseCoordinate?,
): Intent? {
    val profileStore = ProfileStores(context)
    val settingsSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId } ?: profileStore.selectedSettingsSet()
    val commandProfile = profileStore.commandProfiles().findByReference(
        id = settingsSet.commandProfileRef.id,
        label = "command profile",
    )
    val usbProfile = profileStore.usbBaudProfiles().findByReference(
        id = settingsSet.usbBaudProfileRef.id,
        label = "USB/baud profile",
    )
    val ntripResolution = settingsSet.resolveNtripProfiles(
        casterProfiles = profileStore.ntripCasterProfiles(),
        mountpointProfiles = profileStore.ntripMountpointProfiles(),
    )
    val ntripCaster = ntripResolution.caster
    val ntripMountpoint = ntripResolution.mountpoint
    val resolvedSettingsSet = ntripResolution.settingsSet
    val ntripCasterUploadProfile = resolvedSettingsSet.ntripCasterUploadProfileRef?.id?.let { uploadProfileId ->
        profileStore.ntripCasterUploadProfiles().firstOrNull { it.id == uploadProfileId }
    }
    val recordingPolicy = profileStore.recordingPolicyProfiles().findByReference(
        id = settingsSet.recordingOutputProfileRef.id,
        label = "recording policy profile",
    )
    val storageProfile = profileStore.storageProfiles().findByReference(
        id = settingsSet.storageProfileRef.id,
        label = "storage location profile",
    )
    val rtklibProfile = resolvedSettingsSet.rtklibProfileRef?.id?.let {
        profileStore.rtklibProfiles().findByReference(
            id = it,
            label = "RTKLIB profile",
        )
    }
    val solutionPolicyProfile = resolvedSettingsSet.solutionPolicyProfileRef?.id?.let {
        profileStore.solutionPolicyProfiles().findByReference(
            id = it,
            label = "solution policy profile",
        )
    }
    val workflowId = selectedWorkflowId ?: profileStore.selectedWorkflowId()
    if (workflowId.isNullOrBlank()) {
        Toast.makeText(context, "Cannot start: workflow is not selected.", Toast.LENGTH_LONG).show()
        return null
    }
    if (workflowId == WORKFLOW_FIXED_BASE && selectedBaseCoordinate == null) {
        Toast.makeText(
            context,
            "Cannot start: fixed base requires an accepted base coordinate.",
            Toast.LENGTH_LONG,
        ).show()
        return null
    }
    val fixedBaseModeCommand = if (workflowId == WORKFLOW_FIXED_BASE) {
        selectedBaseCoordinate?.toFixedBaseModeCommand()
    } else {
        null
    }
    if (workflowId == WORKFLOW_FIXED_BASE && fixedBaseModeCommand == null) {
        Toast.makeText(
            context,
            "Cannot start: fixed base coordinate needs ellipsoidal height.",
            Toast.LENGTH_LONG,
        ).show()
        return null
    }
    val workflowUsesNtrip = workflowId.workflowUsesNtrip()
    val activeConfig = try {
        ActiveRecordingConfig.resolve(
            settingsSet = resolvedSettingsSet.copy(workflowId = workflowId),
            commandProfile = commandProfile,
            usbBaudProfile = usbProfile,
            ntripCasterProfile = ntripCaster,
            ntripMountpointProfile = ntripMountpoint,
            ntripCasterUploadProfile = ntripCasterUploadProfile,
            recordingPolicyProfile = recordingPolicy,
            storageProfile = storageProfile,
            rtklibProfile = rtklibProfile,
            solutionPolicyProfile = solutionPolicyProfile,
            workflowName = workflowId.workflowName(),
            workflowUsesNtrip = workflowUsesNtrip,
            hasAcceptedBaseCoordinate = selectedBaseCoordinate != null,
            passwordLookup = NtripSecretStore(context)::getPassword,
        )
    } catch (error: IllegalArgumentException) {
        Toast.makeText(context, "Cannot start: ${error.message}", Toast.LENGTH_LONG).show()
        return null
    }
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val usbDevice = usbManager.selectUsbDevice(usbProfile)
    if (usbDevice == null) {
        val message = if (usbProfile.usbVid != null || usbProfile.usbPid != null) {
            "Cannot start: selected USB receiver is not connected."
        } else {
            "Cannot start: no USB receiver is connected or selected."
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        return null
    }
    val usbAccess = UsbStartAccessDecision.evaluate(
        deviceConnected = true,
        permissionReportedGranted = usbManager.hasPermission(usbDevice),
    )
    when (usbAccess.action) {
        UsbStartAccessAction.NO_DEVICE -> {
            Toast.makeText(context, usbAccess.message, Toast.LENGTH_LONG).show()
            return null
        }
        UsbStartAccessAction.REQUEST_PERMISSION -> {
            requestUsbPermissionForDevice(context, usbDevice)
            Toast.makeText(context, usbAccess.message, Toast.LENGTH_LONG).show()
            return null
        }
        UsbStartAccessAction.VERIFY_AND_START -> Unit
    }
    try {
        activeConfig.validateForStart()
    } catch (error: IllegalArgumentException) {
        Toast.makeText(context, "Cannot start: ${error.message}", Toast.LENGTH_LONG).show()
        return null
    }
    return Intent(context, RecordingForegroundService::class.java).apply {
        action = RecordingForegroundService.ACTION_START
        putExtra(RecordingForegroundService.EXTRA_USB_DEVICE, usbDevice)
        putExtra(RecordingForegroundService.EXTRA_PROFILE_BAUD, activeConfig.profileBaud)
        putExtra(RecordingForegroundService.EXTRA_SERIAL_BAUD, activeConfig.serialBaud)
        putStringArrayListExtra(RecordingForegroundService.EXTRA_INIT_COMMANDS, ArrayList(activeConfig.initCommands))
        putStringArrayListExtra(RecordingForegroundService.EXTRA_BAUD_SWITCH_COMMANDS, ArrayList(activeConfig.baudSwitchCommands))
        putStringArrayListExtra(
            RecordingForegroundService.EXTRA_MODE_COMMANDS,
            ArrayList(activeConfig.modeCommands.withFixedBaseModeCommand(fixedBaseModeCommand)),
        )
        putStringArrayListExtra(RecordingForegroundService.EXTRA_SHUTDOWN_COMMANDS, ArrayList(activeConfig.shutdownCommands))
        putExtra(RecordingForegroundService.EXTRA_NTRIP_ENABLED, activeConfig.ntrip.enabled)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_HOST, activeConfig.ntrip.host)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PORT, activeConfig.ntrip.port)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT, activeConfig.ntrip.mountpoint)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_USERNAME, activeConfig.ntrip.username)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PASSWORD, activeConfig.ntrip.password.orEmpty())
        putExtra(RecordingForegroundService.EXTRA_NTRIP_SECRET_REF, activeConfig.ntrip.secretRef.orEmpty())
        putExtra(RecordingForegroundService.EXTRA_NTRIP_GGA, "")
        putExtra(RecordingForegroundService.EXTRA_NTRIP_STATION_ID, activeConfig.ntrip.stationId.orEmpty())
        activeConfig.ntrip.baseLatDeg?.let { putExtra(RecordingForegroundService.EXTRA_NTRIP_BASE_LAT, it) }
        activeConfig.ntrip.baseLonDeg?.let { putExtra(RecordingForegroundService.EXTRA_NTRIP_BASE_LON, it) }
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_ENABLED, activeConfig.casterUpload.enabled)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_HOST, activeConfig.casterUpload.host)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_PORT, activeConfig.casterUpload.port)
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
        putExtra(RecordingForegroundService.EXTRA_WORKFLOW_ID, activeConfig.workflowId)
        putExtra(RecordingForegroundService.EXTRA_WORKFLOW_NAME, activeConfig.workflowName)
        putExtra(RecordingForegroundService.EXTRA_RECEIVER_ROLE, workflowId.receiverRoleForSession())
        putExtra(RecordingForegroundService.EXTRA_RECEIVER_PROFILE_ID, activeConfig.receiverProfileId)
        putExtra(RecordingForegroundService.EXTRA_UM980_PROFILE_ID, activeConfig.commandProfileId)
        putExtra(RecordingForegroundService.EXTRA_COMMAND_PROFILE_ID, activeConfig.commandProfileId)
        putExtra(RecordingForegroundService.EXTRA_COMMAND_RECEIVER_FAMILY, activeConfig.commandReceiverFamily)
        putExtra(RecordingForegroundService.EXTRA_USB_BAUD_PROFILE_ID, activeConfig.usbBaudProfileId)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_CASTER_PROFILE_ID, ntripCaster?.id)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT_PROFILE_ID, ntripMountpoint?.id)
        putExtra(RecordingForegroundService.EXTRA_RECORDING_POLICY_ID, recordingPolicy.id)
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
        val basePositionJson = if (workflowId == WORKFLOW_FIXED_BASE && selectedBaseCoordinate != null) {
            BasePositionJsonCodec.encode(selectedBaseCoordinate)
        } else {
            ""
        }
        putExtra(
            RecordingForegroundService.EXTRA_COORDINATE_SOURCE,
            if (basePositionJson.isNotBlank()) selectedBaseCoordinate?.sourceDescription.orEmpty() else "NONE",
        )
        putExtra(RecordingForegroundService.EXTRA_BASE_POSITION_JSON, basePositionJson)
        putExtra(RecordingForegroundService.EXTRA_BASE_COORDINATE_ID, selectedBaseCoordinate?.id)
        putExtra(RecordingForegroundService.EXTRA_BASE_COORDINATE_NAME, selectedBaseCoordinate?.name)
        putExtra(RecordingForegroundService.EXTRA_BASE_COORDINATE_METHOD, selectedBaseCoordinate?.method)
        putStringArrayListExtra(RecordingForegroundService.EXTRA_EXPECTED_ARTIFACTS, ArrayList(activeConfig.expectedSessionArtifactNames))
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_SET_NAME, resolvedSettingsSet.displayNameWithOverrides())
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_COMMAND_PROFILE_NAME, commandProfile.name)
        putExtra(
            RecordingForegroundService.EXTRA_SETTINGS_USB_BAUD_PROFILE_NAME,
            dashboardBaudLabel(usbProfile.name, activeConfig.serialBaud),
        )
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_NTRIP_CASTER_PROFILE_NAME, ntripCaster?.name ?: "NTRIP disabled")
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_RECORDING_OUTPUT_PROFILE_NAME, recordingPolicy.name)
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_STORAGE_PROFILE_NAME, storageProfile.name)
    }
}

private fun buildNtripUpdateIntent(
    context: Context,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    selectedWorkflowId: String?,
): Intent? {
    val profileStore = ProfileStores(context)
    val settingsSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId } ?: profileStore.selectedSettingsSet()
    val ntripResolution = settingsSet.resolveNtripProfiles(
        casterProfiles = profileStore.ntripCasterProfiles(),
        mountpointProfiles = profileStore.ntripMountpointProfiles(),
    )
    val ntripCaster = ntripResolution.caster
    val ntripMountpoint = ntripResolution.mountpoint
    val resolvedSettingsSet = ntripResolution.settingsSet
    val activeConfig = try {
        val workflowId = selectedWorkflowId ?: profileStore.selectedWorkflowId()
        ActiveRecordingConfig.resolve(
            settingsSet = resolvedSettingsSet.copy(workflowId = workflowId ?: resolvedSettingsSet.workflowId),
            commandProfile = profileStore.commandProfiles().findByReference(
                id = settingsSet.commandProfileRef.id,
                label = "command profile",
            ),
            usbBaudProfile = profileStore.usbBaudProfiles().findByReference(
                id = settingsSet.usbBaudProfileRef.id,
                label = "USB/baud profile",
            ),
            ntripCasterProfile = ntripCaster,
            ntripMountpointProfile = ntripMountpoint,
            recordingPolicyProfile = profileStore.recordingPolicyProfiles().findByReference(
                id = settingsSet.recordingOutputProfileRef.id,
                label = "recording policy profile",
            ),
            storageProfile = profileStore.storageProfiles().findByReference(
                id = settingsSet.storageProfileRef.id,
                label = "storage location profile",
            ),
            solutionPolicyProfile = resolvedSettingsSet.solutionPolicyProfileRef?.id?.let {
                profileStore.solutionPolicyProfiles().findByReference(
                    id = it,
                    label = "solution policy profile",
                )
            },
            workflowName = workflowId.workflowLabel(),
            workflowUsesNtrip = workflowId?.workflowUsesNtrip() == true,
            passwordLookup = NtripSecretStore(context)::getPassword,
        ).also(ActiveRecordingConfig::validateForStart)
    } catch (error: IllegalArgumentException) {
        Toast.makeText(context, "Cannot update NTRIP: ${error.message}", Toast.LENGTH_LONG).show()
        return null
    }
    if (!activeConfig.ntrip.enabled) {
        Toast.makeText(context, "NTRIP is disabled for the selected workflow.", Toast.LENGTH_LONG).show()
        return null
    }
    return Intent(context, RecordingForegroundService::class.java).apply {
        action = RecordingForegroundService.ACTION_UPDATE_NTRIP
        putExtra(RecordingForegroundService.EXTRA_NTRIP_HOST, activeConfig.ntrip.host)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PORT, activeConfig.ntrip.port)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT, activeConfig.ntrip.mountpoint)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_USERNAME, activeConfig.ntrip.username)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PASSWORD, activeConfig.ntrip.password.orEmpty())
        putExtra(RecordingForegroundService.EXTRA_NTRIP_SECRET_REF, activeConfig.ntrip.secretRef.orEmpty())
        putExtra(RecordingForegroundService.EXTRA_NTRIP_GGA, "")
        putExtra(RecordingForegroundService.EXTRA_NTRIP_STATION_ID, activeConfig.ntrip.stationId.orEmpty())
        activeConfig.ntrip.baseLatDeg?.let { putExtra(RecordingForegroundService.EXTRA_NTRIP_BASE_LAT, it) }
        activeConfig.ntrip.baseLonDeg?.let { putExtra(RecordingForegroundService.EXTRA_NTRIP_BASE_LON, it) }
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
    requestUsbPermissionForProfile(context, settingsSet?.usbBaudProfileRef?.id)
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
    val usbProfile = profileStore.usbBaudProfiles().firstOrNull { it.id == settingsSet.usbBaudProfileRef.id }
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

private fun ProfileStores.selectedMountpointLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return "n/a"
    settingsSet.overrides.ntripMountpoint?.mountpoint?.takeIf { it.isNotBlank() }?.let { return it }
    val profile = settingsSet.ntripMountpointProfileRef?.id?.let { id ->
        ntripMountpointProfiles().firstOrNull { it.id == id }
    } ?: return "n/a"
    return profile.displayMountpoint()
}

private fun ProfileStores.selectedStorageLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return "n/a"
    return storageProfiles().firstOrNull { it.id == settingsSet.storageProfileRef.id }?.name ?: settingsSet.storageProfileRef.name
}

private fun ProfileStores.selectedReceiverLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return "n/a"
    return commandProfiles().firstOrNull { it.id == settingsSet.commandProfileRef.id }?.name ?: settingsSet.commandProfileRef.name
}

private fun ProfileStores.selectedBaudProfileLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return "n/a"
    val profile = usbBaudProfiles().firstOrNull { it.id == settingsSet.usbBaudProfileRef.id }
        ?: return settingsSet.usbBaudProfileRef.name
    val targetBaud = settingsSet.overrides.usbBaud?.serialBaud ?: profile.serialBaud
    return dashboardBaudLabel(profile.name, targetBaud)
}

internal fun UsbBaudProfile.dashboardBaudLabel(): String =
    dashboardBaudLabel(name, serialBaud)

internal fun dashboardBaudLabel(profileName: String, targetBaud: Int): String =
    "$profileName · target $targetBaud baud"

private fun ProfileStores.resolveSelectedNtripProfiles(selectedSettingsSetId: String): ResolvedNtripProfiles? {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return null
    return settingsSet.resolveNtripProfiles(
        casterProfiles = ntripCasterProfiles(),
        mountpointProfiles = ntripMountpointProfiles(),
    )
}

private fun ProfileStores.selectedNtripCasterProfileLabel(selectedSettingsSetId: String): String {
    val resolution = resolveSelectedNtripProfiles(selectedSettingsSetId) ?: return "n/a"
    return resolution.caster?.name ?: resolution.settingsSet.ntripCasterProfileRef?.name ?: "n/a"
}

private fun ProfileStores.selectedRecordingOutputProfileLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return "n/a"
    return recordingPolicyProfiles().firstOrNull { it.id == settingsSet.recordingOutputProfileRef.id }?.name
        ?: settingsSet.recordingOutputProfileRef.name
}

private fun ProfileStores.selectedCasterMountpoints(selectedSettingsSetId: String): List<String> {
    val resolution = resolveSelectedNtripProfiles(selectedSettingsSetId) ?: return emptyList()
    return resolution.caster?.sourcetableMountpoints.orEmpty()
}

private fun ProfileStores.plannedDashboardState(
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    selectedWorkflowId: String?,
): DashboardState {
    val selected = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
    val mountpointProfiles = ntripMountpointProfiles()
    val mountpoint = selected.selectedMountpointLabel(mountpointProfiles)
    val selectedCommandProfile = selected?.commandProfileRef?.id?.let { id ->
        commandProfiles().firstOrNull { it.id == id }
    }
    val recordingPolicyProfile = selected?.recordingOutputProfileRef?.id?.let { id ->
        recordingPolicyProfiles().firstOrNull { it.id == id }
    }
    val rtklibProfile = selected?.rtklibProfileRef?.id?.let { id ->
        rtklibProfiles().firstOrNull { it.id == id }
    }
    val mockEnabled = selected?.overrides?.recordingOutput?.enableMockLocation
        ?: recordingPolicyProfile?.enableMockLocation
        ?: false
    val mockRateHz = selected?.overrides?.recordingOutput?.mockLocationRateHz
        ?: recordingPolicyProfile?.mockLocationRateHz
        ?: RecordingPolicyProfile.DEFAULT_MOCK_LOCATION_RATE_HZ
    return DashboardState.planned(
        workflow = selectedWorkflowId.workflowLabel(),
        mountpoint = mountpoint,
        receiver = selectedReceiverLabel(selectedSettingsSetId),
        storage = selectedStorageLabel(selectedSettingsSetId),
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
            settingsSet = selected?.displayNameWithOverrides() ?: "n/a",
            commandProfile = selectedReceiverLabel(selectedSettingsSetId),
            baudProfile = selectedBaudProfileLabel(selectedSettingsSetId),
            ntripCasterProfile = selectedNtripCasterProfileLabel(selectedSettingsSetId),
            recordingOutputProfile = selectedRecordingOutputProfileLabel(selectedSettingsSetId),
            storageLocationProfile = selectedStorageLabel(selectedSettingsSetId),
        ),
        mockGps = MockGpsDashboardState(enabled = mockEnabled, rateHz = mockRateHz),
    )
}

private fun ProfileStores.settingsSetsWithRememberedMountpoint(
    selectedSettingsSetId: String,
): List<RecordingSettingsSet> {
    val settingsSets = settingsSets()
    val rememberedProfile = lastActiveNtripMountpointProfileId()
        ?.let { id -> ntripMountpointProfiles().firstOrNull { it.id == id } }
    val updated = settingsSets.withRememberedMountpointProfile(selectedSettingsSetId, rememberedProfile)
    if (updated != settingsSets) {
        saveSettingsSets(updated)
    }
    return updated
}

internal fun List<RecordingSettingsSet>.withRememberedMountpointProfile(
    selectedSettingsSetId: String,
    rememberedProfile: NtripMountpointProfile?,
): List<RecordingSettingsSet> {
    if (rememberedProfile == null) return this
    return map { settingsSet ->
        if (settingsSet.id != selectedSettingsSetId ||
            settingsSet.ntripMountpointProfileRef != null ||
            settingsSet.overrides.ntripMountpoint != null
        ) {
            settingsSet
        } else {
            settingsSet.copy(
                ntripMountpointProfileRef = ProfileReference(rememberedProfile.id, rememberedProfile.name),
            )
        }
    }
}

internal fun RecordingSettingsSet?.selectedMountpointLabel(
    mountpointProfiles: List<NtripMountpointProfile>,
): String {
    val settingsSet = this ?: return "n/a"
    settingsSet.overrides.ntripMountpoint?.mountpoint?.takeIf { it.isNotBlank() }?.let { return it }
    val profile = settingsSet.ntripMountpointProfileRef?.id?.let { id ->
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

private inline fun <reified T> List<T>.findByReference(id: String, label: String): T =
    firstOrNull { profile ->
        when (profile) {
            is CommandProfile -> profile.id == id
            is UsbBaudProfile -> profile.id == id
            is NtripCasterProfile -> profile.id == id
            is NtripMountpointProfile -> profile.id == id
            is RecordingPolicyProfile -> profile.id == id
            is RtklibProfile -> profile.id == id
            is StorageProfile -> profile.id == id
            else -> false
        }
    } ?: error("Missing $label '$id'.")

private fun List<CommandProfile>.preferredBaseCommandProfile(): CommandProfile? =
    firstOrNull { it.id == ProfileStores.UM980_BASE_CONFIG_PROFILE_ID }
        ?: firstOrNull { profile ->
            profile.receiverFamily.equals("um980-n4", ignoreCase = true) &&
                profile.runtimeScript
                    .lineSequence()
                    .any { it.trimStart().startsWith("MODE BASE", ignoreCase = true) }
        }

private fun List<String>.withFixedBaseModeCommand(command: String?): List<String> {
    if (command.isNullOrBlank()) return this
    var replaced = false
    val updated = map { line ->
        if (line.trimStart().startsWith("MODE BASE", ignoreCase = true)) {
            replaced = true
            command
        } else {
            line
        }
    }
    return if (replaced) updated else listOf(command) + this
}

internal fun String.workflowUsesNtrip(): Boolean =
    this == WORKFLOW_ROVER_NTRIP || this == WORKFLOW_BASE_CALIBRATION

private fun String?.workflowLabel(): String =
    this?.workflowName() ?: "Select workflow"

private fun String.workflowName(): String =
    when (this) {
        WORKFLOW_PLAIN_ROVER -> "Plain rover recording"
        WORKFLOW_ROVER_NTRIP -> "Rover + NTRIP to receiver"
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
    when (this?.workflowApplicationPolicy) {
        WorkflowApplicationPolicy.LET_USER_SELECT -> null
        WorkflowApplicationPolicy.LEAVE_INTACT -> currentWorkflowId
        else -> this?.workflowId ?: currentWorkflowId
    }

private fun CommandProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = listOfNotNull(
            receiverFamily.takeIf(String::isNotBlank),
            "init + shutdown scripts",
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
        summary = "$host:$port · $protocolPolicy",
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
        summary = listOf(caster?.name ?: casterProfileId, mountpoint.ifBlank { "mountpoint not set" }).joinToString(" · "),
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
            ellipsoidalHeightM,
            method,
        ),
    )

private fun AcceptedBaseCoordinate.displayLabel(): String =
    "%.10f, %.10f, h %.3f m".format(
        java.util.Locale.US,
        latDeg,
        lonDeg,
        ellipsoidalHeightM,
    )

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

private fun dashboardSelectorRows(
    selector: DashboardSelector,
    profileStore: ProfileStores,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
): List<ProfileListRow> {
    val selectedSettingsSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
    return when (selector) {
        DashboardSelector.SETTINGS_SET -> SettingsSetListState.from(settingsSets, selectedSettingsSetId).rows
        DashboardSelector.WORKFLOW -> WORKFLOW_MODE_OPTIONS.map { option ->
            ProfileListRow(
                id = option.value,
                name = option.label,
                isProtected = false,
                hasLocalOverrides = false,
                isSelected = option.value == profileStore.selectedWorkflowId(),
            )
        }
        DashboardSelector.MOUNTPOINT -> profileStore.ntripMountpointProfiles().map { profile ->
            profile.profileRow(
                casters = profileStore.ntripCasterProfiles(),
                isSelected = profile.id == selectedSettingsSet?.ntripMountpointProfileRef?.id,
            )
        }
        DashboardSelector.RECEIVER -> profileStore.commandProfiles().map { profile ->
            profile.profileRow(isSelected = profile.id == selectedSettingsSet?.commandProfileRef?.id)
        }
        DashboardSelector.STORAGE -> profileStore.storageProfiles().map { profile ->
            profile.profileRow(isSelected = profile.id == selectedSettingsSet?.storageProfileRef?.id)
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
