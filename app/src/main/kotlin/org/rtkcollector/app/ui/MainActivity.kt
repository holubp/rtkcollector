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
import org.rtkcollector.app.profile.ProfileStores
import org.rtkcollector.app.profile.RecordingProfileStore
import org.rtkcollector.app.secrets.NtripSecretStore
import org.rtkcollector.app.recording.RecordingForegroundService
import org.rtkcollector.app.ui.dashboard.DashboardState
import org.rtkcollector.app.ui.dashboard.HomeDashboard
import org.rtkcollector.app.ui.dashboard.dashboardStateFromRecordingIntent
import org.rtkcollector.app.ui.profiles.NtripMountpointEditorState
import org.rtkcollector.app.ui.profiles.NtripMountpointScreen
import org.rtkcollector.app.ui.profiles.SimpleSettingsScreen
import org.rtkcollector.app.ui.sessions.SessionListItem
import org.rtkcollector.app.ui.sessions.SessionsScreen
import org.rtkcollector.app.ui.settings.SettingsHub

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
    var state by remember {
        mutableStateOf(
            DashboardState.planned(
                workflow = "Plain rover recording",
                mountpoint = "n/a",
                receiver = "UM980",
                storage = "App-private",
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
                    onNtripCaster = { screen = AppScreen.NTRIP_CASTER },
                    onNtripMountpoint = { screen = AppScreen.NTRIP_MOUNTPOINT },
                    onCommands = { screen = AppScreen.COMMANDS },
                    onRecordingOutputs = { screen = AppScreen.RECORDING_OUTPUTS },
                    onStorage = { screen = AppScreen.STORAGE },
                    onSessions = { screen = AppScreen.SESSIONS },
                    onBack = { screen = AppScreen.HOME },
                )
                AppScreen.NTRIP_MOUNTPOINT -> NtripMountpointScreen(
                    initialState = NtripMountpointEditorState(mountpointText = "TUBO00CZE0"),
                    onBack = { screen = AppScreen.SETTINGS },
                )
                AppScreen.NTRIP_CASTER -> SimpleSettingsScreen("NTRIP caster") { screen = AppScreen.SETTINGS }
                AppScreen.COMMANDS -> SimpleSettingsScreen("Command scripts") { screen = AppScreen.SETTINGS }
                AppScreen.RECORDING_OUTPUTS -> SimpleSettingsScreen("Recording outputs") { screen = AppScreen.SETTINGS }
                AppScreen.STORAGE -> SimpleSettingsScreen("Storage") { screen = AppScreen.SETTINGS }
                AppScreen.SESSIONS -> SessionsScreen(
                    sessions = currentSessions(state),
                    onBack = { screen = AppScreen.SETTINGS },
                )
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

private enum class AppScreen {
    HOME,
    SETTINGS,
    NTRIP_CASTER,
    NTRIP_MOUNTPOINT,
    COMMANDS,
    RECORDING_OUTPUTS,
    STORAGE,
    SESSIONS,
}

@Preview(showBackground = true)
@Composable
private fun RtkCollectorAppPreview() {
    RtkCollectorApp()
}

private fun buildDashboardStartIntent(context: Context): Intent? {
    val recordingDefaults = RecordingProfileStore(context).loadDefaults()
    val profileStore = ProfileStores(context)
    val usbProfiles = profileStore.usbBaudProfiles()
    val commandProfiles = profileStore.commandProfiles()
    val ntripCasters = profileStore.ntripCasterProfiles()
    val ntripMountpoints = profileStore.ntripMountpointProfiles()
    val recordingPolicies = profileStore.recordingPolicyProfiles()
    val storageProfiles = profileStore.storageProfiles()
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
    val usbProfile = usbProfiles.firstOrNull()
    val commandProfile = commandProfiles.firstOrNull()
    val recordingPolicy = recordingPolicies.firstOrNull()
    val storageProfile = storageProfiles.firstOrNull()
    val selectedCaster = ntripCasters.firstOrNull()
    val selectedMountpoint = selectedCaster?.let { caster ->
        ntripMountpoints.firstOrNull { it.casterProfileId == caster.id } ?: ntripMountpoints.firstOrNull()
    } ?: ntripMountpoints.firstOrNull()
    val secretRef = (selectedCaster?.secretId ?: recordingDefaults.ntripSecretId).takeIf { it.isNotBlank() }
    val secretStore = NtripSecretStore(context)
    val password = secretRef?.let(secretStore::getPassword).orEmpty()
    val initCommands = splitProfileCommands(commandProfile?.initScript)
    val shutdownCommands = splitProfileCommands(commandProfile?.shutdownScript)
    return Intent(context, RecordingForegroundService::class.java).apply {
        action = RecordingForegroundService.ACTION_START
        putExtra(RecordingForegroundService.EXTRA_USB_DEVICE, usbDevice)
        putExtra(
            RecordingForegroundService.EXTRA_PROFILE_BAUD,
            usbProfile?.profileBaud ?: recordingDefaults.profileBaud,
        )
        putExtra(RecordingForegroundService.EXTRA_SERIAL_BAUD, usbProfile?.serialBaud ?: recordingDefaults.serialBaud)
        putStringArrayListExtra(RecordingForegroundService.EXTRA_INIT_COMMANDS, ArrayList(initCommands))
        putStringArrayListExtra(RecordingForegroundService.EXTRA_BAUD_SWITCH_COMMANDS, ArrayList())
        putStringArrayListExtra(RecordingForegroundService.EXTRA_MODE_COMMANDS, ArrayList())
        putStringArrayListExtra(RecordingForegroundService.EXTRA_SHUTDOWN_COMMANDS, ArrayList(shutdownCommands))
        putExtra(RecordingForegroundService.EXTRA_NTRIP_ENABLED, false)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_HOST, recordingDefaults.ntripHost.ifBlank { selectedCaster?.host.orEmpty() })
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PORT, recordingDefaults.ntripPort.ifZeroOrDefault(selectedCaster?.port ?: 2101))
        putExtra(
            RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT,
            selectedMountpoint?.mountpoint?.ifBlank { recordingDefaults.ntripMountpoint }.orEmpty(),
        )
        putExtra(RecordingForegroundService.EXTRA_NTRIP_USERNAME, recordingDefaults.ntripUsername.ifBlank { selectedCaster?.username.orEmpty() })
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PASSWORD, password)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_SECRET_REF, secretRef.orEmpty())
        putExtra(RecordingForegroundService.EXTRA_NTRIP_GGA, "")
        putExtra(RecordingForegroundService.EXTRA_WORKFLOW_ID, "plain-rover")
        putExtra(RecordingForegroundService.EXTRA_WORKFLOW_NAME, "Plain rover recording")
        putExtra(RecordingForegroundService.EXTRA_RECEIVER_ROLE, "ROVER")
        putExtra(RecordingForegroundService.EXTRA_RECEIVER_PROFILE_ID, "um980-n4")
        putExtra(RecordingForegroundService.EXTRA_UM980_PROFILE_ID, "default")
        putExtra(RecordingForegroundService.EXTRA_COMMAND_PROFILE_ID, commandProfile?.id)
        putExtra(RecordingForegroundService.EXTRA_USB_BAUD_PROFILE_ID, usbProfile?.id)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_CASTER_PROFILE_ID, selectedCaster?.id)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT_PROFILE_ID, selectedMountpoint?.id)
        putExtra(RecordingForegroundService.EXTRA_RECORDING_POLICY_ID, recordingPolicy?.id)
        putExtra(RecordingForegroundService.EXTRA_STORAGE_PROFILE_ID, storageProfile?.id)
        putExtra(RecordingForegroundService.EXTRA_STORAGE_KIND, storageProfile?.kind ?: "APP_PRIVATE")
        putExtra(RecordingForegroundService.EXTRA_STORAGE_TREE_URI, storageProfile?.treeUri)
        putExtra(
            RecordingForegroundService.EXTRA_RECORD_NTRIP_CORRECTION_INPUT,
            recordingPolicy?.recordNtripCorrectionInput ?: true,
        )
        putExtra(RecordingForegroundService.EXTRA_EXPORT_NMEA, recordingPolicy?.exportNmea ?: true)
        putExtra(RecordingForegroundService.EXTRA_EXPORT_JSON_SOLUTION, recordingPolicy?.exportJsonSolution ?: true)
        putExtra(RecordingForegroundService.EXTRA_RECORD_REMOTE_BASE_RAW, recordingPolicy?.recordRemoteBaseRaw ?: false)
        putExtra(RecordingForegroundService.EXTRA_COORDINATE_SOURCE, "NONE")
        putExtra(
            RecordingForegroundService.EXTRA_EXPECTED_ARTIFACTS,
            ArrayList(
                listOf(
                    "session.json",
                    "receiver-rx.raw",
                    "tx-to-receiver.raw",
                    "events.jsonl",
                    "quality-live.jsonl",
                ),
            ),
        )
    }
}

private fun splitProfileCommands(script: String?): List<String> =
    script
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter { it.isNotEmpty() && !it.startsWith("#") }
        ?.toList()
        ?: emptyList()

private fun Int.ifZeroOrDefault(defaultValue: Int): Int =
    if (this == 0) defaultValue else this
