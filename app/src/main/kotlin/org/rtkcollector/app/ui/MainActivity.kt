package org.rtkcollector.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import org.rtkcollector.app.profile.ActiveRecordingConfig
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.NtripMountpointOverride
import org.rtkcollector.app.profile.ProfileStores
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingPolicyProfile
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.StorageProfile
import org.rtkcollector.app.profile.UsbBaudProfile
import org.rtkcollector.app.secrets.NtripSecretStore
import org.rtkcollector.app.recording.RecordingForegroundService
import org.rtkcollector.app.recording.SessionZipExporter
import org.rtkcollector.app.recording.SessionZipPlan
import org.rtkcollector.app.ui.dashboard.DashboardState
import org.rtkcollector.app.ui.dashboard.ProfilesCardState
import org.rtkcollector.app.ui.dashboard.HomeDashboard
import org.rtkcollector.app.ui.dashboard.dashboardStateFromRecordingIntent
import org.rtkcollector.app.ui.profiles.SettingsSetListScreen
import org.rtkcollector.app.ui.profiles.SettingsSetListState
import org.rtkcollector.app.ui.profiles.NtripMountpointEditorState
import org.rtkcollector.app.ui.profiles.NtripMountpointScreen
import org.rtkcollector.app.ui.profiles.EditableProfileField
import org.rtkcollector.app.ui.profiles.ProfileEditorData
import org.rtkcollector.app.ui.profiles.ProfileEditorScreen
import org.rtkcollector.app.ui.profiles.ProfileListScreen
import org.rtkcollector.app.ui.profiles.ProfileListRow
import org.rtkcollector.app.ui.sessions.SessionListItem
import org.rtkcollector.app.ui.sessions.SessionsScreen
import org.rtkcollector.app.ui.settings.SettingsHub
import org.rtkcollector.receiver.unicore.Um980RuntimeProfiles
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

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
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    val context = LocalContext.current
    val profileStore = remember(context) { ProfileStores(context) }
    var settingsSets by remember { mutableStateOf(profileStore.settingsSets()) }
    var selectedSettingsSetId by remember { mutableStateOf(profileStore.selectedSettingsSetId()) }
    var profileEditorTarget by remember { mutableStateOf<ProfileEditorTarget?>(null) }
    var zipProgressText by remember { mutableStateOf<String?>(null) }
    var state by remember {
        val selected = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
        mutableStateOf(
            DashboardState.planned(
                workflow = selected?.workflowId?.workflowName() ?: "Rover + NTRIP to receiver",
                mountpoint = profileStore.selectedMountpointLabel(selectedSettingsSetId),
                receiver = "UM980",
                storage = profileStore.selectedStorageLabel(selectedSettingsSetId),
                profiles = ProfilesCardState(settingsSet = selected?.displayNameWithOverrides() ?: "n/a"),
            ),
        )
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == RecordingForegroundService.ACTION_STATE) {
                    state = dashboardStateFromRecordingIntent(intent)
                }
            }
        }
        val filter = IntentFilter(RecordingForegroundService.ACTION_STATE)
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
                    onPrimaryAction = {
                        if (state.isRecording) {
                            context.startService(RecordingForegroundService.stopIntent(context))
                        } else {
                            buildDashboardStartIntent(context)?.let { intent ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            }
                        }
                    },
                    onMenu = { screen = AppScreen.SETTINGS },
                    onNtrip = { screen = AppScreen.NTRIP_MOUNTPOINT },
                    onMark = {},
                )
                AppScreen.SETTINGS -> SettingsHub(
                    onSettingsSets = { screen = AppScreen.SETTINGS_SETS },
                    onNtripCaster = { screen = AppScreen.NTRIP_CASTER },
                    onNtripMountpoint = { screen = AppScreen.NTRIP_MOUNTPOINT_PROFILES },
                    onCommands = { screen = AppScreen.COMMANDS },
                    onRecordingOutputs = { screen = AppScreen.RECORDING_OUTPUTS },
                    onStorage = { screen = AppScreen.STORAGE },
                    onSessions = { screen = AppScreen.SESSIONS },
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
                        state = state.copy(status = state.status.copy(mountpoint = mountpoint.ifBlank { "n/a" }))
                        if (state.isRecording) {
                            buildNtripUpdateIntent(context)?.let { context.startService(it) }
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
                        state = state.copy(
                            status = state.status.copy(
                                workflow = selected?.workflowId?.workflowName() ?: state.status.workflow,
                                mountpoint = profileStore.selectedMountpointLabel(id),
                                storage = profileStore.selectedStorageLabel(id),
                            ),
                            profiles = state.profiles.copy(
                                settingsSet = selected?.displayNameWithOverrides() ?: state.profiles.settingsSet,
                            ),
                        )
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
                        }
                    },
                    onRename = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.SETTINGS_SET, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onDelete = { id ->
                        val item = settingsSets.firstOrNull { it.id == id }
                        if (item != null) {
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
                        }
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
                            profileStore.saveNtripCasterProfiles(
                                profiles + source.copyProfile(profileStore.duplicateId("caster"), "${source.name} copy"),
                            )
                        }
                    },
                    onRename = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_CASTER, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onDelete = { id ->
                        val profiles = profileStore.ntripCasterProfiles()
                        profileStore.saveNtripCasterProfiles(profiles.filterNot { it.id == id && !it.isProtected })
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
                            profileStore.saveNtripMountpointProfiles(
                                profiles + source.copyProfile(profileStore.duplicateId("mount"), "${source.name} copy"),
                            )
                        }
                    },
                    onRename = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.NTRIP_MOUNTPOINT, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onDelete = { id ->
                        val profiles = profileStore.ntripMountpointProfiles()
                        profileStore.saveNtripMountpointProfiles(profiles.filterNot { it.id == id && !it.isProtected })
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
                            profileStore.saveCommandProfiles(
                                profiles + source.copyProfile(profileStore.duplicateId("commands"), "${source.name} copy"),
                            )
                        }
                    },
                    onRename = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.COMMANDS, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onDelete = { id ->
                        val profiles = profileStore.commandProfiles()
                        profileStore.saveCommandProfiles(profiles.filterNot { it.id == id && !it.isProtected })
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
                            profileStore.saveRecordingPolicyProfiles(
                                profiles + source.copyProfile(profileStore.duplicateId("recording"), "${source.name} copy"),
                            )
                        }
                    },
                    onRename = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.RECORDING_OUTPUTS, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onDelete = { id ->
                        val profiles = profileStore.recordingPolicyProfiles()
                        profileStore.saveRecordingPolicyProfiles(profiles.filterNot { it.id == id && !it.isProtected })
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
                            profileStore.saveStorageProfiles(
                                profiles + source.copyProfile(profileStore.duplicateId("storage"), "${source.name} copy"),
                            )
                        }
                    },
                    onRename = { id ->
                        profileEditorTarget = ProfileEditorTarget(ProfileKind.STORAGE, id)
                        screen = AppScreen.PROFILE_EDITOR
                    },
                    onDelete = { id ->
                        val profiles = profileStore.storageProfiles()
                        profileStore.saveStorageProfiles(profiles.filterNot { it.id == id && !it.isProtected })
                    },
                    onBack = { screen = AppScreen.SETTINGS },
                    supportsSelection = false,
                )
                AppScreen.SESSIONS -> SessionsScreen(
                    sessions = currentSessions(state),
                    progressText = zipProgressText,
                    onCreateZip = { session ->
                        val path = session.sessionId
                        if (path.startsWith("content://")) {
                            Toast.makeText(context, "ZIP for SAF sessions is not available yet.", Toast.LENGTH_LONG).show()
                        } else {
                            val source = Paths.get(path)
                            if (Files.isDirectory(source)) {
                                val zipPath = source.resolveSibling("${source.fileName}.zip")
                                zipProgressText = "Preparing ZIP..."
                                Thread {
                                    runCatching {
                                        val plan = SessionZipPlan.fromSessionDirectory(source, zipPath)
                                        SessionZipExporter.export(plan) { progress ->
                                            runOnMain(context) {
                                                zipProgressText = "ZIP ${progress.filesCompleted}/${progress.totalFiles}"
                                            }
                                        }
                                    }.onSuccess { zip ->
                                        runOnMain(context) {
                                            zipProgressText = null
                                            shareZip(context, zip.toFile())
                                        }
                                    }.onFailure { error ->
                                        runOnMain(context) {
                                            zipProgressText = null
                                            Toast.makeText(context, "ZIP failed: ${error.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }.start()
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
                        val data = profileStore.profileEditorData(target, settingsSets)
                        ProfileEditorScreen(
                            data = data,
                            onBack = { screen = target.kind.backScreen() },
                            onSave = { values ->
                                runCatching {
                                    profileStore.saveProfileEditorData(target, values, settingsSets)
                                }.onSuccess { updated ->
                                    settingsSets = updated
                                    state = state.copy(
                                        status = state.status.copy(
                                            mountpoint = profileStore.selectedMountpointLabel(selectedSettingsSetId),
                                            storage = profileStore.selectedStorageLabel(selectedSettingsSetId),
                                        ),
                                        profiles = state.profiles.copy(
                                            settingsSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
                                                ?.displayNameWithOverrides()
                                                ?: state.profiles.settingsSet,
                                        ),
                                    )
                                    screen = target.kind.backScreen()
                                }.onFailure { error ->
                                    Toast.makeText(context, "Cannot save profile: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun currentSessions(state: DashboardState): List<SessionListItem> =
    if (state.files.sessionLocation != "n/a") {
        listOf(
            SessionListItem(
                sessionId = state.files.sessionLocation,
                title = if (state.isRecording) "Current recording" else "Last known session",
                subtitle = state.files.sessionLocation,
                isActive = state.isRecording,
            ),
        )
    } else {
        emptyList()
    }

private fun runOnMain(context: Context, action: () -> Unit) {
    (context as? ComponentActivity)?.runOnUiThread(action) ?: action()
}

private fun shareZip(context: Context, zipFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share recording ZIP"))
}

private fun ProfileStores.profileEditorData(
    target: ProfileEditorTarget,
    settingsSets: List<RecordingSettingsSet>,
): ProfileEditorData =
    when (target.kind) {
        ProfileKind.SETTINGS_SET -> settingsSets.first { it.id == target.id }.let { set ->
            ProfileEditorData(
                title = "Edit settings set",
                fields = listOf(
                    EditableProfileField("name", "Name", set.name),
                    EditableProfileField("workflowId", "Workflow ID", set.workflowId),
                    EditableProfileField("commandProfileId", "Command profile ID", set.commandProfileRef.id),
                    EditableProfileField("usbBaudProfileId", "USB/baud profile ID", set.usbBaudProfileRef.id),
                    EditableProfileField("ntripCasterProfileId", "NTRIP caster profile ID", set.ntripCasterProfileRef?.id.orEmpty()),
                    EditableProfileField("ntripMountpointProfileId", "NTRIP mountpoint profile ID", set.ntripMountpointProfileRef?.id.orEmpty()),
                    EditableProfileField("recordingOutputProfileId", "Recording output profile ID", set.recordingOutputProfileRef.id),
                    EditableProfileField("storageProfileId", "Storage location profile ID", set.storageProfileRef.id),
                ),
            )
        }
        ProfileKind.NTRIP_CASTER -> ntripCasterProfiles().first { it.id == target.id }.let { profile ->
            ProfileEditorData(
                title = "Edit NTRIP caster",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("host", "Host", profile.host),
                    EditableProfileField("port", "Port", profile.port.toString()),
                    EditableProfileField("username", "Username", profile.username),
                    EditableProfileField("secretId", "Password secret reference", profile.secretId),
                    EditableProfileField("protocolPolicy", "Protocol policy", profile.protocolPolicy),
                    EditableProfileField("sourcetableMountpoints", "Known mountpoints", profile.sourcetableMountpoints.joinToString("\n"), multiline = true),
                ),
            )
        }
        ProfileKind.NTRIP_MOUNTPOINT -> ntripMountpointProfiles().first { it.id == target.id }.let { profile ->
            ProfileEditorData(
                title = "Edit NTRIP mountpoint",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("casterProfileId", "Caster profile ID", profile.casterProfileId),
                    EditableProfileField("mountpoint", "Mountpoint", profile.mountpoint),
                    EditableProfileField("ggaUploadPolicy", "GGA upload policy", profile.ggaUploadPolicy),
                    EditableProfileField("expectedFormat", "Expected format", profile.expectedFormat),
                    EditableProfileField("remoteBaseRawAvailable", "Remote base raw available", profile.remoteBaseRawAvailable.toString()),
                ),
            )
        }
        ProfileKind.COMMANDS -> commandProfiles().first { it.id == target.id }.let { profile ->
            ProfileEditorData(
                title = "Edit command script",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("receiverFamily", "Receiver family", profile.receiverFamily),
                    EditableProfileField("initScript", "Init script", profile.initScript, multiline = true),
                    EditableProfileField("shutdownScript", "Shutdown script", profile.shutdownScript, multiline = true),
                ),
            )
        }
        ProfileKind.RECORDING_OUTPUTS -> recordingPolicyProfiles().first { it.id == target.id }.let { profile ->
            ProfileEditorData(
                title = "Edit recording outputs",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("recordTxToReceiver", "Record app TX to receiver", profile.recordTxToReceiver.toString()),
                    EditableProfileField("recordNtripCorrectionInput", "Record NTRIP correction input", profile.recordNtripCorrectionInput.toString()),
                    EditableProfileField("exportNmea", "Export derived NMEA", profile.exportNmea.toString()),
                    EditableProfileField("exportJsonSolution", "Export JSON solution", profile.exportJsonSolution.toString()),
                    EditableProfileField("exportGpx", "Export GPX", profile.exportGpx.toString()),
                    EditableProfileField("recordRemoteBaseRaw", "Record remote base raw", profile.recordRemoteBaseRaw.toString()),
                ),
            )
        }
        ProfileKind.STORAGE -> storageProfiles().first { it.id == target.id }.let { profile ->
            ProfileEditorData(
                title = "Edit storage location profile",
                fields = listOf(
                    EditableProfileField("name", "Name", profile.name),
                    EditableProfileField("kind", "Kind: APP_PRIVATE or SAF_TREE", profile.kind),
                    EditableProfileField("treeUri", "SAF tree URI", profile.treeUri.orEmpty()),
                ),
            )
        }
    }

private fun ProfileStores.saveProfileEditorData(
    target: ProfileEditorTarget,
    values: Map<String, String>,
    settingsSets: List<RecordingSettingsSet>,
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
                    profile.copy(
                        name = values.required("name"),
                        host = values.optional("host").orEmpty(),
                        port = values.optional("port")?.toIntOrNull() ?: 2101,
                        username = values.optional("username").orEmpty(),
                        secretId = values.optional("secretId").orEmpty(),
                        protocolPolicy = values.optional("protocolPolicy").orEmpty().ifBlank {
                            "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY"
                        },
                        sourcetableMountpoints = values.optional("sourcetableMountpoints")
                            ?.lines()
                            ?.map(String::trim)
                            ?.filter(String::isNotBlank)
                            .orEmpty(),
                    )
                } else {
                    profile
                }
            },
        )
        ProfileKind.NTRIP_MOUNTPOINT -> saveNtripMountpointProfiles(
            ntripMountpointProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected NTRIP mountpoint profiles cannot be edited." }
                    profile.copy(
                        name = values.required("name"),
                        casterProfileId = values.required("casterProfileId"),
                        mountpoint = values.optional("mountpoint").orEmpty(),
                        ggaUploadPolicy = values.optional("ggaUploadPolicy").orEmpty().ifBlank { "NONE" },
                        expectedFormat = values.optional("expectedFormat").orEmpty().ifBlank { "RTCM3" },
                        remoteBaseRawAvailable = values.optional("remoteBaseRawAvailable").toBooleanStrictOrFalse(),
                    )
                } else {
                    profile
                }
            },
        )
        ProfileKind.COMMANDS -> saveCommandProfiles(
            commandProfiles().map { profile ->
                if (profile.id == target.id) {
                    require(!profile.isProtected) { "Protected command profiles cannot be edited." }
                    profile.copy(
                        name = values.required("name"),
                        receiverFamily = values.required("receiverFamily"),
                        initScript = values.optional("initScript").orEmpty(),
                        shutdownScript = values.optional("shutdownScript").orEmpty(),
                    )
                } else {
                    profile
                }
            },
        )
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
        )
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
        )
    }
    return settingsSets
}

private enum class AppScreen {
    HOME,
    SETTINGS,
    NTRIP_CASTER,
    NTRIP_MOUNTPOINT,
    NTRIP_MOUNTPOINT_PROFILES,
    COMMANDS,
    RECORDING_OUTPUTS,
    STORAGE,
    SESSIONS,
    SETTINGS_SETS,
    PROFILE_EDITOR,
}

private enum class ProfileKind {
    SETTINGS_SET,
    NTRIP_CASTER,
    NTRIP_MOUNTPOINT,
    COMMANDS,
    RECORDING_OUTPUTS,
    STORAGE,
}

private data class ProfileEditorTarget(
    val kind: ProfileKind,
    val id: String,
)

private fun ProfileKind.backScreen(): AppScreen =
    when (this) {
        ProfileKind.SETTINGS_SET -> AppScreen.SETTINGS_SETS
        ProfileKind.NTRIP_CASTER -> AppScreen.NTRIP_CASTER
        ProfileKind.NTRIP_MOUNTPOINT -> AppScreen.NTRIP_MOUNTPOINT_PROFILES
        ProfileKind.COMMANDS -> AppScreen.COMMANDS
        ProfileKind.RECORDING_OUTPUTS -> AppScreen.RECORDING_OUTPUTS
        ProfileKind.STORAGE -> AppScreen.STORAGE
    }

@Preview(showBackground = true)
@Composable
private fun RtkCollectorAppPreview() {
    RtkCollectorApp()
}

private fun buildDashboardStartIntent(context: Context): Intent? {
    val profileStore = ProfileStores(context)
    val settingsSet = profileStore.selectedSettingsSet()
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
    val workflowUsesNtrip = settingsSet.workflowId.workflowUsesNtrip()
    val activeConfig = try {
        ActiveRecordingConfig.resolve(
            settingsSet = settingsSet,
            commandProfile = commandProfile,
            usbBaudProfile = usbProfile,
            ntripCasterProfile = ntripCaster,
            ntripMountpointProfile = ntripMountpoint,
            recordingPolicyProfile = recordingPolicy,
            storageProfile = storageProfile,
            workflowName = settingsSet.workflowId.workflowName(),
            workflowUsesNtrip = workflowUsesNtrip,
            passwordLookup = NtripSecretStore(context)::getPassword,
            modeCommands = Um980RuntimeProfiles
                .experimentalRoverBasePreparation(baudRate = usbProfile.serialBaud)
                .commands,
        ).also(ActiveRecordingConfig::validateForStart)
    } catch (error: IllegalArgumentException) {
        Toast.makeText(context, "Cannot start: ${error.message}", Toast.LENGTH_LONG).show()
        return null
    }
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val usbDevice = usbManager.deviceList.values.firstOrNull()
    if (usbDevice == null) {
        Toast.makeText(context, "Cannot start: no USB device found.", Toast.LENGTH_LONG).show()
        return null
    }
    if (!usbManager.hasPermission(usbDevice)) {
        Toast.makeText(context, "Cannot start: USB permission is not granted.", Toast.LENGTH_LONG).show()
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
        putExtra(RecordingForegroundService.EXTRA_UM980_PROFILE_ID, "experimental-rover-base-preparation")
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

private fun buildNtripUpdateIntent(context: Context): Intent? {
    val profileStore = ProfileStores(context)
    val settingsSet = profileStore.selectedSettingsSet()
    val ntripCaster = settingsSet.ntripCasterProfileRef?.let { reference ->
        profileStore.ntripCasterProfiles().findByReference(reference.id, "NTRIP caster profile")
    }
    val ntripMountpoint = settingsSet.ntripMountpointProfileRef?.let { reference ->
        profileStore.ntripMountpointProfiles().findByReference(reference.id, "NTRIP mountpoint profile")
    }
    val activeConfig = try {
        ActiveRecordingConfig.resolve(
            settingsSet = settingsSet,
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
            workflowName = settingsSet.workflowId.workflowName(),
            workflowUsesNtrip = settingsSet.workflowId.workflowUsesNtrip(),
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

private fun ProfileStores.selectedSettingsSet(): RecordingSettingsSet {
    val sets = settingsSets()
    return sets.firstOrNull { it.id == selectedSettingsSetId() } ?: sets.first()
}

private fun ProfileStores.selectedMountpointLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return "n/a"
    val override = settingsSet.overrides.ntripMountpoint?.mountpoint?.takeIf { it.isNotBlank() }
    val profileMountpoint = settingsSet.ntripMountpointProfileRef?.id?.let { id ->
        ntripMountpointProfiles().firstOrNull { it.id == id }?.mountpoint
    }
    return override ?: profileMountpoint?.takeIf { it.isNotBlank() } ?: "n/a"
}

private fun ProfileStores.selectedStorageLabel(selectedSettingsSetId: String): String {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return "n/a"
    return storageProfiles().firstOrNull { it.id == settingsSet.storageProfileRef.id }?.name ?: settingsSet.storageProfileRef.name
}

private fun ProfileStores.selectedCasterMountpoints(selectedSettingsSetId: String): List<String> {
    val settingsSet = settingsSets().firstOrNull { it.id == selectedSettingsSetId } ?: return emptyList()
    val casterId = settingsSet.ntripCasterProfileRef?.id ?: return emptyList()
    return ntripCasterProfiles().firstOrNull { it.id == casterId }?.sourcetableMountpoints.orEmpty()
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

private fun String.workflowName(): String =
    when (this) {
        "plain-rover" -> "Plain rover recording"
        "rover-ntrip" -> "Rover + NTRIP to receiver"
        "base-calibration" -> "Temporary base preparation"
        "fixed-base" -> "Fixed base operation"
        else -> replace('-', ' ').replaceFirstChar { it.titlecase() }
    }

private fun CommandProfile.profileRow(): ProfileListRow =
    ProfileListRow(id = id, name = name, isProtected = isProtected, hasLocalOverrides = false)

private fun NtripCasterProfile.profileRow(): ProfileListRow =
    ProfileListRow(id = id, name = name, isProtected = isProtected, hasLocalOverrides = false)

private fun NtripMountpointProfile.profileRow(): ProfileListRow =
    ProfileListRow(id = id, name = name, isProtected = isProtected, hasLocalOverrides = false)

private fun RecordingPolicyProfile.profileRow(): ProfileListRow =
    ProfileListRow(id = id, name = name, isProtected = isProtected, hasLocalOverrides = false)

private fun StorageProfile.profileRow(): ProfileListRow =
    ProfileListRow(id = id, name = name, isProtected = isProtected, hasLocalOverrides = false)

private fun Map<String, String>.required(key: String): String =
    optional(key) ?: error("$key is required.")

private fun Map<String, String>.optional(key: String): String? =
    get(key)?.trim()?.takeIf(String::isNotBlank)

private fun String?.toBooleanStrictOrFalse(): Boolean =
    equals("true", ignoreCase = true)

private fun reference(id: String, known: List<Pair<String, String>>): ProfileReference =
    ProfileReference(id, known.firstOrNull { it.first == id }?.second ?: id)
