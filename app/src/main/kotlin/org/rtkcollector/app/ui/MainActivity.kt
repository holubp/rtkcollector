package org.rtkcollector.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
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
import org.rtkcollector.app.recording.RecordingForegroundService
import org.rtkcollector.app.ui.dashboard.DashboardState
import org.rtkcollector.app.ui.dashboard.HomeDashboard
import org.rtkcollector.app.ui.dashboard.dashboardStateFromRecordingIntent
import org.rtkcollector.app.ui.profiles.NtripMountpointEditorState
import org.rtkcollector.app.ui.profiles.NtripMountpointScreen
import org.rtkcollector.app.ui.profiles.SimpleSettingsScreen
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
                    onPrimaryAction = {},
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
                AppScreen.SESSIONS -> SimpleSettingsScreen("Sessions") { screen = AppScreen.SETTINGS }
            }
        }
    }
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
