package org.rtkcollector.app.ui.alarms

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.rtkcollector.app.alarms.AlarmSettingsStore
import org.rtkcollector.app.alarms.AlarmAudioEnvironment
import org.rtkcollector.app.alarms.alarmAudibilityWarning

private val alarmEnvironmentQueries = Dispatchers.IO.limitedParallelism(1)

/** Each failed platform read stays unknown without discarding the other evidence. */
internal fun readAlarmAudioEnvironment(context: Context): AlarmAudioEnvironment {
    val app = context.applicationContext
    val filter = runCatching { app.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter }.getOrNull()
    val audio = runCatching { app.getSystemService(AudioManager::class.java) }.getOrNull()
    return AlarmAudioEnvironment(
        alarmVolume = runCatching { audio?.getStreamVolume(AudioManager.STREAM_ALARM) }.getOrNull(),
        alarmMuted = runCatching { audio?.isStreamMute(AudioManager.STREAM_ALARM) }.getOrNull(),
        interruptionFilter = filter,
    )
}

/** Home-only advisory, independent of service error/episode state and capture. */
@Composable
fun rememberAlarmAudibilityWarning(recording: Boolean): String? {
    val context = LocalContext.current
    val store = remember(context) { AlarmSettingsStore(context) }
    var settings by remember(store) { mutableStateOf(store.read()) }
    val lifecycle = (context as? LifecycleOwner)?.lifecycle
    var visible by remember(lifecycle) { mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != false) }
    var environment by remember { mutableStateOf(AlarmAudioEnvironment()) }
    DisposableEffect(store, lifecycle) {
        val subscription = store.observeChanges { settings = store.read() }
        val observer = LifecycleEventObserver { _, _ -> visible = lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != false }
        lifecycle?.addObserver(observer)
        onDispose { subscription.close(); lifecycle?.removeObserver(observer) }
    }
    val acoustic = settings.categories.values.any { it.acoustic }
    LaunchedEffect(recording, acoustic, visible, context) {
        environment = AlarmAudioEnvironment()
        if (recording && acoustic && visible) while (isActive) {
            environment = withContext(alarmEnvironmentQueries) { readAlarmAudioEnvironment(context) }
            delay(1_000)
        }
    }
    return alarmAudibilityWarning(recording, acoustic, environment)
}
