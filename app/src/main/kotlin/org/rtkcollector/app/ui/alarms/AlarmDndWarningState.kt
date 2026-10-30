package org.rtkcollector.app.ui.alarms

import android.app.NotificationManager
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
import org.rtkcollector.app.alarms.alarmDndWarning
import org.rtkcollector.app.alarms.isAlarmDndEnabled

private val dndQueries = Dispatchers.IO.limitedParallelism(1)

/** Home-only advisory, independent of service error/episode state and capture. */
@Composable
fun rememberAlarmDndWarning(recording: Boolean): String? {
    val context = LocalContext.current
    val store = remember(context) { AlarmSettingsStore(context) }
    var settings by remember(store) { mutableStateOf(store.read()) }
    val lifecycle = (context as? LifecycleOwner)?.lifecycle
    var visible by remember(lifecycle) { mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != false) }
    var dnd by remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(store, lifecycle) {
        val subscription = store.observeChanges { settings = store.read() }
        val observer = LifecycleEventObserver { _, _ -> visible = lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != false }
        lifecycle?.addObserver(observer)
        onDispose { subscription.close(); lifecycle?.removeObserver(observer) }
    }
    val acoustic = settings.categories.values.any { it.acoustic }
    LaunchedEffect(recording, acoustic, visible, context) {
        dnd = null
        if (recording && acoustic && visible) while (isActive) {
            dnd = withContext(dndQueries) {
                runCatching { context.applicationContext.getSystemService(NotificationManager::class.java)
                    ?.currentInterruptionFilter?.let(::isAlarmDndEnabled) }.getOrNull()
            }
            delay(1_000)
        }
    }
    return alarmDndWarning(recording, acoustic, dnd)
}
