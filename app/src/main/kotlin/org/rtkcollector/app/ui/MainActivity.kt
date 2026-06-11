package org.rtkcollector.app.ui

import android.content.BroadcastReceiver
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import org.rtkcollector.app.profile.ActiveRecordingConfig
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.NtripMountpointOverride
import org.rtkcollector.app.profile.ProfileStores
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingPolicyProfile
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.SettingsBackupFile
import org.rtkcollector.app.profile.SettingsSetExportOptions
import org.rtkcollector.app.profile.StorageProfile
import org.rtkcollector.app.profile.UsbBaudProfile
import org.rtkcollector.app.profile.WorkflowApplicationPolicy
import org.rtkcollector.app.profile.displayMountpoint
import org.rtkcollector.app.profile.renameProfile
import org.rtkcollector.app.secrets.NtripSecretStore
import org.rtkcollector.app.recording.RecordingForegroundService
import org.rtkcollector.app.sessions.FilesystemSessionBrowser
import org.rtkcollector.app.sessions.SessionArchiveManager
import org.rtkcollector.app.sessions.SessionBrowserEntry
import org.rtkcollector.app.sessions.SessionBrowserState
import org.rtkcollector.app.sessions.SessionEntryKind
import org.rtkcollector.app.sessions.sessionBrowserStateOf
import org.rtkcollector.app.usb.AndroidUsbSerialTransport
import org.rtkcollector.app.usb.UsbSerialOpenOptions
import org.rtkcollector.app.ui.dashboard.DashboardState
import org.rtkcollector.app.ui.dashboard.DashboardLayoutPreference
import org.rtkcollector.app.ui.dashboard.ProfilesCardState
import org.rtkcollector.app.ui.dashboard.HomeDashboard
import org.rtkcollector.app.ui.dashboard.dashboardStateFromRecordingIntent
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
import org.rtkcollector.app.ui.profiles.persistentReceiverWriteAction
import org.rtkcollector.app.ui.profiles.profileDeleteActionLabel
import org.rtkcollector.app.ui.sessions.SessionsScreen
import org.rtkcollector.app.ui.settings.SettingsHub
import org.rtkcollector.app.ui.usb.UsbDeviceChoice
import org.rtkcollector.receiver.unicore.Um980RuntimeCommandValidator
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

private const val TEMP_SHARE_ZIP_CLEANUP_DELAY_MILLIS = 60L * 60L * 1000L
private const val PERSISTENT_RECEIVER_COMMAND_DELAY_MILLIS = 100L
private val persistentReceiverWriteInProgress = AtomicBoolean(false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RtkCollectorApp()
        }
    }
}

@Composable
fun RtkCollectorApp() {
    var screen by rememberSaveable(stateSaver = AppScreenSaver) { mutableStateOf(AppScreen.HOME) }
    val context = LocalContext.current
    val profileStore = remember(context) { ProfileStores(context) }
    var settingsSets by remember { mutableStateOf(profileStore.settingsSets()) }
    var selectedSettingsSetId by remember { mutableStateOf(profileStore.selectedSettingsSetId()) }
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
    var showSettingsExportDialog by remember { mutableStateOf(false) }
    var includePlaintextPasswordsInBackup by remember { mutableStateOf(false) }
    var zipProgressText by remember { mutableStateOf<String?>(null) }
    var sessionBrowserState by remember { mutableStateOf(SessionBrowserState()) }
    var profileRevision by remember { mutableStateOf(0) }
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
        Thread {
            runCatching(task)
                .onSuccess {
                    runOnMain(context) {
                        zipProgressText = null
                        refreshSessions()
                    }
                }
                .onFailure { error ->
                    runOnMain(context) {
                        zipProgressText = null
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
    val importSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                importSettingsBackup(context, uri)
            }.onSuccess {
                settingsSets = profileStore.settingsSets()
                selectedSettingsSetId = profileStore.selectedSettingsSetId()
                selectedWorkflowId = profileStore.selectedWorkflowId()
                refreshProfileUi(settingsSets)
                Toast.makeText(context, "Settings backup imported.", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "Cannot import settings backup: ${error.message}", Toast.LENGTH_LONG).show()
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
            ProfileKind.NTRIP_MOUNTPOINT -> profileStore.ntripMountpointProfiles().firstOrNull { it.id == target.id }?.profileRow()
            ProfileKind.USB_BAUD -> profileStore.usbBaudProfiles().firstOrNull { it.id == target.id }?.profileRow()
            ProfileKind.COMMANDS -> profileStore.commandProfiles().firstOrNull { it.id == target.id }?.profileRow()
            ProfileKind.RECORDING_OUTPUTS -> profileStore.recordingPolicyProfiles().firstOrNull { it.id == target.id }?.profileRow()
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
                        state = dashboardStateFromRecordingIntent(intent).withPlannedConfiguration(
                            profileStore.plannedDashboardState(settingsSets, selectedSettingsSetId, selectedWorkflowId),
                        )
                        if (screen == AppScreen.SESSIONS) {
                            refreshSessions()
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED,
                    ACTION_USB_PERMISSION -> {
                        profileRevision++
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
            when (screen) {
                AppScreen.HOME -> HomeDashboard(
                    state = state,
                    layoutPreference = dashboardLayout,
                    onPrimaryAction = {
                        if (state.isRecording) {
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
                            )?.let { intent ->
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
                    onMark = {},
                )
                AppScreen.SETTINGS ->
                    SettingsHub(
                        onSettingsSets = { screen = AppScreen.SETTINGS_SETS },
                        onWorkflowSelection = {
                            if (state.isRecording) {
                                Toast.makeText(context, "Stop recording before changing workflow.", Toast.LENGTH_LONG).show()
                            } else {
                                dashboardSelector = DashboardSelector.WORKFLOW
                                screen = AppScreen.HOME
                            }
                        },
                        dashboardLayoutLabel = dashboardLayout.displayName,
                        onDashboardLayout = { showDashboardLayoutDialog = true },
                        onNtripCaster = { screen = AppScreen.NTRIP_CASTER },
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
                        profileStore.saveSelectedSettingsSetId(id)
                        settingsSets = profileStore.settingsSets()
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
                AppScreen.NTRIP_MOUNTPOINT_PROFILES -> ProfileListScreen(
                    title = "NTRIP mountpoints",
                    rows = profileStore.ntripMountpointProfiles().map { it.profileRow() },
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
                    title = "Command scripts",
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
                AppScreen.SETTINGS_SET_SELECTOR -> ProfileListScreen(
                    title = "Select workflow/settings",
                    rows = SettingsSetListState.from(settingsSets, selectedSettingsSetId).rows,
                    onSelect = { id ->
                        if (state.isRecording) {
                            Toast.makeText(context, "Stop recording before changing workflow.", Toast.LENGTH_LONG).show()
                            screen = AppScreen.HOME
                        } else {
                            selectedSettingsSetId = id
                            profileStore.saveSelectedSettingsSetId(id)
                            settingsSets = profileStore.settingsSets()
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
                        it.profileRow(isSelected = it.id == settingsSets.firstOrNull { set -> set.id == selectedSettingsSetId }?.ntripMountpointProfileRef?.id)
                    },
                    onSelect = { id ->
                        profileStore.ntripMountpointProfiles().firstOrNull { it.id == id }?.let { profile ->
                            profileStore.saveLastActiveNtripMountpointProfileId(profile.id)
                            settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
                                set.copy(
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
                    onToggle = { id -> sessionBrowserState = sessionBrowserState.toggle(id) },
                    onSelectCurrent = { sessionBrowserState = sessionBrowserState.selectCurrent() },
                    onSelectRecordings = { sessionBrowserState = sessionBrowserState.selectRecordings() },
                    onSelectArchives = { sessionBrowserState = sessionBrowserState.selectArchives() },
                    onSelectAll = { sessionBrowserState = sessionBrowserState.selectAll() },
                    onClearSelection = { sessionBrowserState = sessionBrowserState.clearSelection() },
                    onShareSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canShareZip)
                        if (selected.isEmpty()) {
                            Toast.makeText(context, "Select at least one completed recording.", Toast.LENGTH_LONG).show()
                        } else {
                            zipProgressText = "Preparing ZIP..."
                            Thread {
                                runCatching {
                                    val cacheRoot = context.cacheDir.resolve("session-share-zips").toPath()
                                    SessionArchiveManager.cleanupTemporaryShareZips(cacheRoot)
                                    selected.mapIndexed { index, entry ->
                                        val source = Paths.get(entry.location)
                                        SessionArchiveManager.createTemporaryShareZip(source, cacheRoot) { progress ->
                                            runOnMain(context) {
                                                zipProgressText = "ZIP ${index + 1}/${selected.size}: ${progress.filesCompleted}/${progress.totalFiles}"
                                            }
                                        }
                                    }
                                }.onSuccess { zips ->
                                    runOnMain(context) {
                                        zipProgressText = null
                                        shareZipFiles(context, zips.map { it.toFile() })
                                        refreshSessions()
                                    }
                                }.onFailure { error ->
                                    runOnMain(context) {
                                        zipProgressText = null
                                        Toast.makeText(context, "Share ZIP failed: ${error.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }.start()
                        }
                    },
                    onArchiveSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canArchive)
                        runSessionTask("Archive") {
                            selected.forEachIndexed { index, entry ->
                                val source = Paths.get(entry.location)
                                SessionArchiveManager.archiveSession(source) { progress ->
                                    runOnMain(context) {
                                        zipProgressText = "Archive ${index + 1}/${selected.size}: ${progress.filesCompleted}/${progress.totalFiles}"
                                    }
                                }
                            }
                        }
                    },
                    onRestoreSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canRestore)
                        runSessionTask("Restore") {
                            selected.forEachIndexed { index, entry ->
                                runOnMain(context) {
                                    zipProgressText = "Restore ${index + 1}/${selected.size}"
                                }
                                SessionArchiveManager.restoreArchive(Paths.get(entry.location))
                            }
                        }
                    },
                    onDeleteSelected = {
                        val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canDelete)
                        runSessionTask("Delete") {
                            selected.forEachIndexed { index, entry ->
                                runOnMain(context) {
                                    zipProgressText = "Delete ${index + 1}/${selected.size}"
                                }
                                when (entry.kind) {
                                    SessionEntryKind.ARCHIVE -> FilesystemSessionBrowser.deleteArchive(Paths.get(entry.location))
                                    SessionEntryKind.CURRENT_STOPPED,
                                    SessionEntryKind.RECORDING -> FilesystemSessionBrowser.deleteRecording(Paths.get(entry.location))
                                    SessionEntryKind.CURRENT_ACTIVE -> Unit
                                }
                            }
                        }
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                )
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
                                profileStore.saveSelectedWorkflowId(id)
                                refreshProfileUi(settingsSets)
                            }
                            DashboardSelector.SETTINGS_SET -> {
                                selectedSettingsSetId = id
                                profileStore.saveSelectedSettingsSetId(id)
                                settingsSets = profileStore.settingsSets()
                                val selectedSet = settingsSets.firstOrNull { it.id == id }
                                selectedWorkflowId = selectedSet.applyWorkflowPolicy(selectedWorkflowId)
                                profileStore.saveSelectedWorkflowId(selectedWorkflowId)
                                refreshProfileUi(settingsSets)
                            }
                            DashboardSelector.MOUNTPOINT -> {
                                profileStore.ntripMountpointProfiles().firstOrNull { it.id == id }?.let { profile ->
                                    profileStore.saveLastActiveNtripMountpointProfileId(profile.id)
                                    settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
                                        set.copy(
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
        sessionLocation
            ?.let { location ->
                sessionBrowserStateOf(
                    listOf(
                        SessionBrowserEntry(
                            id = location,
                            title = if (dashboardState.isRecording) "Current recording" else "Last session",
                            subtitle = "SAF browsing/export is not available yet: $location",
                            location = location,
                            kind = if (dashboardState.isRecording) SessionEntryKind.CURRENT_ACTIVE else SessionEntryKind.CURRENT_STOPPED,
                            modifiedEpochMillis = System.currentTimeMillis(),
                            filesystemBacked = false,
                        ),
                    ),
                )
            }
            ?: SessionBrowserState()
    }
}

private fun runOnMain(context: Context, action: () -> Unit) {
    (context as? ComponentActivity)?.runOnUiThread(action) ?: action()
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
        ntripMountpointProfiles = profileStore.ntripMountpointProfiles(),
        recordingPolicyProfiles = profileStore.recordingPolicyProfiles(),
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
    val text = context.contentResolver.openInputStream(uri)
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { it.readText() }
        ?: error("Settings backup could not be read.")
    val backup = SettingsBackupFile.fromJson(JSONObject(text))
    val profileStore = ProfileStores(context)
    profileStore.saveCommandProfiles(backup.commandProfiles)
    profileStore.saveUsbBaudProfiles(backup.usbBaudProfiles)
    profileStore.saveNtripCasterProfiles(backup.ntripCasterProfiles)
    profileStore.saveNtripMountpointProfiles(backup.ntripMountpointProfiles)
    profileStore.saveRecordingPolicyProfiles(backup.recordingPolicyProfiles)
    profileStore.saveStorageProfiles(backup.storageProfiles)
    profileStore.saveSettingsSets(backup.settingsSets)
    backup.selectedSettingsSetId
        ?.takeIf { id -> backup.settingsSets.any { it.id == id } }
        ?.let(profileStore::saveSelectedSettingsSetId)
    profileStore.saveSelectedWorkflowId(backup.selectedWorkflowId)
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
            )
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
                    EditableProfileField("protocolPolicy", "Protocol policy", profile.protocolPolicy),
                    EditableProfileField(
                        key = "sourcetableMountpoints",
                        label = "Known mountpoints",
                        value = "",
                        readOnlyList = profile.sourcetableMountpoints.ifEmpty { listOf("No cached mountpoints") },
                    ),
                ),
            )
        }
        ProfileKind.NTRIP_MOUNTPOINT -> ntripMountpointProfiles().first { it.id == target.id }.let { profile ->
            val selectedCasterMountpoints = ntripCasterProfiles()
                .firstOrNull { it.id == profile.casterProfileId }
                ?.sourcetableMountpoints
                .orEmpty()
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
                        optionItems = selectedCasterMountpoints.map { EditableProfileOption(it, it) },
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
            )
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
            )
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
            )
        }
        ProfileKind.RECORDING_OUTPUTS -> recordingPolicyProfiles().first { it.id == target.id }.let { profile ->
            ProfileEditorData(
                title = "Edit recording outputs",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("recordTxToReceiver", "Record app TX to receiver", profile.recordTxToReceiver.toString(), boolean = true),
                    EditableProfileField("recordNtripCorrectionInput", "Record NTRIP correction input", profile.recordNtripCorrectionInput.toString(), boolean = true),
                    EditableProfileField("exportNmea", "Export derived NMEA", profile.exportNmea.toString(), boolean = true),
                    EditableProfileField("exportJsonSolution", "Export JSON solution", profile.exportJsonSolution.toString(), boolean = true),
                    EditableProfileField("exportGpx", "Export GPX", profile.exportGpx.toString(), boolean = true),
                    EditableProfileField("recordRemoteBaseRaw", "Record remote base raw", profile.recordRemoteBaseRaw.toString(), boolean = true),
                ),
            )
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
                    EditableProfileField("treeUri", "SAF tree URI", profile.treeUri.orEmpty()),
                ),
            )
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
                    val password = values.optional("password")
                    val secretId = when {
                        !password.isNullOrBlank() && profile.secretId.isNotBlank() -> profile.secretId
                        !password.isNullOrBlank() -> "ntrip:${values.optional("host").orEmpty()}:${target.id}:${values.optional("username").orEmpty()}"
                        else -> profile.secretId
                    }
                    if (!password.isNullOrBlank()) {
                        savePassword(secretId, password)
                    }
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
        ProfileKind.NTRIP_MOUNTPOINT -> saveNtripMountpointProfiles(
            ntripMountpointProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected NTRIP mountpoint profiles cannot be edited." }
                    profile.copy(
                        name = values.required("name"),
                        casterProfileId = values.required("casterProfileId"),
                        mountpoint = values.optional("mountpoint").orEmpty(),
                        ggaUploadPolicy = values.optional("ggaUploadPolicy").orEmpty(),
                        expectedFormat = values.optional("expectedFormat").orEmpty().ifBlank { "RTCM3" },
                        remoteBaseRawAvailable = values.optional("remoteBaseRawAvailable").toBooleanStrictOrFalse(),
                    )
                } else {
                    profile
                }
            },
        ).also {
            return updateSettingsSetReferenceNames(settingsSets, target.kind, target.id, values.required("name"))
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
                        exportJsonSolution = values.optional("exportJsonSolution").toBooleanStrictOrFalse(),
                        exportGpx = values.optional("exportGpx").toBooleanStrictOrFalse(),
                        recordRemoteBaseRaw = values.optional("recordRemoteBaseRaw").toBooleanStrictOrFalse(),
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
            ProfileKind.NTRIP_MOUNTPOINT -> set.copy(ntripMountpointProfileRef = set.ntripMountpointProfileRef.renameNullableIfId(id, name))
            ProfileKind.RECORDING_OUTPUTS -> set.copy(recordingOutputProfileRef = set.recordingOutputProfileRef.renameIfId(id, name))
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
            ProfileKind.NTRIP_MOUNTPOINT -> set.ntripMountpointProfileRef?.id == id
            ProfileKind.RECORDING_OUTPUTS -> set.recordingOutputProfileRef.id == id
            ProfileKind.STORAGE -> set.storageProfileRef.id == id
            ProfileKind.SETTINGS_SET -> false
        }
    }

private enum class AppScreen {
    HOME,
    SETTINGS,
    NTRIP_CASTER,
    NTRIP_MOUNTPOINT,
    NTRIP_MOUNTPOINT_PROFILES,
    COMMANDS,
    USB_BAUD,
    RECORDING_OUTPUTS,
    STORAGE,
    SESSIONS,
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

private val WORKFLOW_MODE_OPTIONS = listOf(
    EditableProfileOption("plain-rover", "Plain rover"),
    EditableProfileOption("rover-ntrip", "Rover with NTRIP"),
    EditableProfileOption("base-calibration", "Temporary base recording"),
    EditableProfileOption("fixed-base", "Fixed base"),
)

private val WORKFLOW_APPLICATION_POLICY_OPTIONS = listOf(
    EditableProfileOption(WorkflowApplicationPolicy.SET_SPECIFIC, "Select specific workflow"),
    EditableProfileOption(WorkflowApplicationPolicy.LET_USER_SELECT, "Let user select before start"),
    EditableProfileOption(WorkflowApplicationPolicy.LEAVE_INTACT, "Leave current workflow intact"),
)

private const val ACTION_USB_PERMISSION = "org.rtkcollector.app.USB_PERMISSION"

private enum class ProfileKind {
    SETTINGS_SET,
    NTRIP_CASTER,
    NTRIP_MOUNTPOINT,
    USB_BAUD,
    COMMANDS,
    RECORDING_OUTPUTS,
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
        ProfileKind.NTRIP_MOUNTPOINT -> AppScreen.NTRIP_MOUNTPOINT_PROFILES
        ProfileKind.USB_BAUD -> AppScreen.USB_BAUD
        ProfileKind.COMMANDS -> AppScreen.COMMANDS
        ProfileKind.RECORDING_OUTPUTS -> AppScreen.RECORDING_OUTPUTS
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
    val ntripCaster = settingsSet.ntripCasterProfileRef?.let { reference ->
        profileStore.ntripCasterProfiles().findByReference(reference.id, "NTRIP caster profile")
    }
    val ntripMountpoint = settingsSet.ntripMountpointProfileRef?.let { reference ->
        profileStore.ntripMountpointProfiles().findByReference(reference.id, "NTRIP mountpoint profile")
    }
    val recordingPolicy = profileStore.recordingPolicyProfiles().findByReference(
        id = settingsSet.recordingOutputProfileRef.id,
        label = "recording policy profile",
    )
    val storageProfile = profileStore.storageProfiles().findByReference(
        id = settingsSet.storageProfileRef.id,
        label = "storage location profile",
    )
    val workflowId = selectedWorkflowId ?: profileStore.selectedWorkflowId()
    if (workflowId.isNullOrBlank()) {
        Toast.makeText(context, "Cannot start: workflow is not selected.", Toast.LENGTH_LONG).show()
        return null
    }
    val workflowUsesNtrip = workflowId.workflowUsesNtrip()
    val activeConfig = try {
        ActiveRecordingConfig.resolve(
            settingsSet = settingsSet.copy(workflowId = workflowId),
            commandProfile = commandProfile,
            usbBaudProfile = usbProfile,
            ntripCasterProfile = ntripCaster,
            ntripMountpointProfile = ntripMountpoint,
            recordingPolicyProfile = recordingPolicy,
            storageProfile = storageProfile,
            workflowName = workflowId.workflowName(),
            workflowUsesNtrip = workflowUsesNtrip,
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
    if (!usbManager.hasPermission(usbDevice)) {
        Toast.makeText(context, "Cannot start: USB permission is not granted.", Toast.LENGTH_LONG).show()
        return null
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
        putStringArrayListExtra(RecordingForegroundService.EXTRA_MODE_COMMANDS, ArrayList(activeConfig.modeCommands))
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
        putExtra(RecordingForegroundService.EXTRA_WORKFLOW_ID, activeConfig.workflowId)
        putExtra(RecordingForegroundService.EXTRA_WORKFLOW_NAME, activeConfig.workflowName)
        putExtra(RecordingForegroundService.EXTRA_RECEIVER_ROLE, "ROVER")
        putExtra(RecordingForegroundService.EXTRA_RECEIVER_PROFILE_ID, activeConfig.receiverProfileId)
        putExtra(RecordingForegroundService.EXTRA_UM980_PROFILE_ID, activeConfig.commandProfileId)
        putExtra(RecordingForegroundService.EXTRA_COMMAND_PROFILE_ID, activeConfig.commandProfileId)
        putExtra(RecordingForegroundService.EXTRA_USB_BAUD_PROFILE_ID, activeConfig.usbBaudProfileId)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_CASTER_PROFILE_ID, ntripCaster?.id)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT_PROFILE_ID, ntripMountpoint?.id)
        putExtra(RecordingForegroundService.EXTRA_RECORDING_POLICY_ID, recordingPolicy.id)
        putExtra(RecordingForegroundService.EXTRA_STORAGE_PROFILE_ID, activeConfig.storage.id)
        putExtra(RecordingForegroundService.EXTRA_STORAGE_KIND, activeConfig.storage.kind)
        putExtra(RecordingForegroundService.EXTRA_STORAGE_TREE_URI, activeConfig.storage.treeUri)
        putExtra(RecordingForegroundService.EXTRA_RECORD_NTRIP_CORRECTION_INPUT, activeConfig.recording.recordNtripCorrectionInput)
        putExtra(RecordingForegroundService.EXTRA_EXPORT_NMEA, activeConfig.recording.exportNmea)
        putExtra(RecordingForegroundService.EXTRA_EXPORT_JSON_SOLUTION, activeConfig.recording.exportJsonSolution)
        putExtra(RecordingForegroundService.EXTRA_RECORD_REMOTE_BASE_RAW, activeConfig.recording.recordRemoteBaseRaw)
        putExtra(RecordingForegroundService.EXTRA_COORDINATE_SOURCE, "NONE")
        putStringArrayListExtra(RecordingForegroundService.EXTRA_EXPECTED_ARTIFACTS, ArrayList(activeConfig.expectedSessionArtifactNames))
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_SET_NAME, settingsSet.displayNameWithOverrides())
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_COMMAND_PROFILE_NAME, commandProfile.name)
        putExtra(RecordingForegroundService.EXTRA_SETTINGS_USB_BAUD_PROFILE_NAME, usbProfile.name)
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
    val ntripCaster = settingsSet.ntripCasterProfileRef?.let { reference ->
        profileStore.ntripCasterProfiles().findByReference(reference.id, "NTRIP caster profile")
    }
    val ntripMountpoint = settingsSet.ntripMountpointProfileRef?.let { reference ->
        profileStore.ntripMountpointProfiles().findByReference(reference.id, "NTRIP mountpoint profile")
    }
    val activeConfig = try {
        val workflowId = selectedWorkflowId ?: profileStore.selectedWorkflowId()
        ActiveRecordingConfig.resolve(
            settingsSet = settingsSet.copy(workflowId = workflowId ?: settingsSet.workflowId),
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
    usbManager.requestPermission(device, permissionIntent)
}

private fun writeCommandProfilePersistentlyToDevice(
    context: Context,
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    commandProfileId: String,
    runtimeScript: String,
    isRecording: Boolean,
) {
    if (isRecording) {
        Toast.makeText(context, "Stop recording before writing receiver configuration persistently.", Toast.LENGTH_LONG).show()
        return
    }
    val profileStore = ProfileStores(context)
    val commandProfile = profileStore.commandProfiles().firstOrNull { it.id == commandProfileId }
    if (commandProfile == null) {
        Toast.makeText(context, "Command profile is not available.", Toast.LENGTH_LONG).show()
        return
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
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val device = usbManager.selectUsbDevice(usbProfile)
    if (device == null) {
        Toast.makeText(context, "Selected USB receiver is not connected.", Toast.LENGTH_LONG).show()
        return
    }
    if (!usbManager.hasPermission(device)) {
        Toast.makeText(context, "USB permission is required before writing receiver configuration.", Toast.LENGTH_LONG).show()
        return
    }
    val commands = persistentReceiverCommands(runtimeScript)
    if (!persistentReceiverWriteInProgress.compareAndSet(false, true)) {
        Toast.makeText(context, "Persistent receiver configuration write is already in progress.", Toast.LENGTH_LONG).show()
        return
    }
    Toast.makeText(context, "Writing persistent receiver configuration...", Toast.LENGTH_SHORT).show()
    Thread {
        val transport = AndroidUsbSerialTransport(
            usbManager = usbManager,
            device = device,
            options = UsbSerialOpenOptions(baudRate = usbProfile.profileBaud),
        )
        try {
            runCatching {
                transport.open()
                commands.forEach { command ->
                    transport.write("$command\r\n".toByteArray(Charsets.US_ASCII))
                    Thread.sleep(PERSISTENT_RECEIVER_COMMAND_DELAY_MILLIS)
                }
            }.onSuccess {
                runOnMain(context) {
                    Toast.makeText(
                        context,
                        "Persistent receiver configuration written for ${commandProfile.name}.",
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

internal fun persistentReceiverCommands(runtimeScript: String): List<String> =
    runtimeScript
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .filterNot { it.equals("SAVECONFIG", ignoreCase = true) }
        .onEach(Um980RuntimeCommandValidator::validateRuntimeCommand)
        .toList() + "SAVECONFIG"

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
    return usbBaudProfiles().firstOrNull { it.id == settingsSet.usbBaudProfileRef.id }?.name ?: settingsSet.usbBaudProfileRef.name
}

private fun ProfileStores.selectedNtripCasterProfileLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return "n/a"
    val reference = settingsSet.ntripCasterProfileRef ?: return "n/a"
    return ntripCasterProfiles().firstOrNull { it.id == reference.id }?.name ?: reference.name
}

private fun ProfileStores.selectedRecordingOutputProfileLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return "n/a"
    return recordingPolicyProfiles().firstOrNull { it.id == settingsSet.recordingOutputProfileRef.id }?.name
        ?: settingsSet.recordingOutputProfileRef.name
}

private fun ProfileStores.selectedCasterMountpoints(selectedSettingsSetId: String): List<String> {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return emptyList()
    val casterId = settingsSet.ntripCasterProfileRef?.id ?: return emptyList()
    return ntripCasterProfiles().firstOrNull { it.id == casterId }?.sourcetableMountpoints.orEmpty()
}

private fun ProfileStores.plannedDashboardState(
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    selectedWorkflowId: String?,
): DashboardState {
    val selected = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
    val mountpointProfiles = ntripMountpointProfiles()
    val mountpoint = selected.selectedMountpointLabel(mountpointProfiles)
        .takeUnless { it.isMissingMountpointValue() }
        ?: selectedMountpointLabelFromProfileId(lastActiveNtripMountpointProfileId(), mountpointProfiles)
    return DashboardState.planned(
        workflow = selectedWorkflowId.workflowLabel(),
        mountpoint = mountpoint,
        receiver = selectedReceiverLabel(selectedSettingsSetId),
        storage = selectedStorageLabel(selectedSettingsSetId),
        profiles = ProfilesCardState(
            settingsSet = selected?.displayNameWithOverrides() ?: "n/a",
            commandProfile = selectedReceiverLabel(selectedSettingsSetId),
            baudProfile = selectedBaudProfileLabel(selectedSettingsSetId),
            ntripCasterProfile = selectedNtripCasterProfileLabel(selectedSettingsSetId),
            recordingOutputProfile = selectedRecordingOutputProfileLabel(selectedSettingsSetId),
            storageLocationProfile = selectedStorageLabel(selectedSettingsSetId),
        ),
    )
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
            is StorageProfile -> profile.id == id
            else -> false
        }
    } ?: error("Missing $label '$id'.")

private fun String.workflowUsesNtrip(): Boolean =
    contains("ntrip", ignoreCase = true)

private fun String?.workflowLabel(): String =
    this?.workflowName() ?: "Select workflow"

private fun String.workflowName(): String =
    when (this) {
        "plain-rover" -> "Plain rover recording"
        "rover-ntrip" -> "Rover + NTRIP to receiver"
        "base-calibration" -> "Temporary base preparation"
        "fixed-base" -> "Fixed base operation"
        else -> replace('-', ' ').replaceFirstChar { it.titlecase() }
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

private fun NtripMountpointProfile.profileRow(isSelected: Boolean = false): ProfileListRow =
    ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = listOf(casterProfileId, mountpoint.ifBlank { "mountpoint not set" }).joinToString(" · "),
    )

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
            if (exportJsonSolution) add("JSON")
            if (exportGpx) add("GPX")
            if (recordRemoteBaseRaw) add("remote base raw")
        }.ifEmpty { listOf("receiver RX only") }.joinToString(" · "),
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
            profile.profileRow(isSelected = profile.id == selectedSettingsSet?.ntripMountpointProfileRef?.id)
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
