package org.rtkcollector.app.ui.alarms

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.rtkcollector.app.alarms.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { AlarmSettingsStore(context) }
    val player = remember(context) { AlarmAudioPlayer.get(context) }
    var settings by remember { mutableStateOf(store.read()) }
    var pickerCategory by remember { mutableStateOf(AlarmCategory.POSITION_LOST) }
    var error by remember { mutableStateOf<String?>(null) }
    var audioStatus by remember { mutableStateOf<String?>(null) }
    var previewBlocked by remember { mutableStateOf(false) }
    var visualRestrictions by remember { mutableStateOf<String?>(null) }
    fun save(next: AlarmSettingsSnapshot) {
        runCatching { store.write(next); settings = next }.onFailure { error = "Alarm settings could not be saved." }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)?.let { uri ->
                    if (isAlarmSoundUri(uri.toString())) save(AlarmSettingsUiModel(settings).sound(pickerCategory,
                        AlarmSoundChoice.SystemNotification(uri.toString())))
                    else error = "Unsupported sound. Previous selection retained."
                }
            }
        }
    }
    DisposableEffect(store) {
        val listener = store.observeChanges { settings = store.read() }
        onDispose { listener.close(); player.stopPreview() }
    }
    LaunchedEffect(context) {
        while (true) {
            audioStatus = player.statusBrief; previewBlocked = player.isAlarmActive
            visualRestrictions = withContext(Dispatchers.IO) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                runCatching {
                    if (!manager.areNotificationsEnabled()) "Visual alarms are blocked by Android."
                    else AlarmCategory.entries.filter { manager.getNotificationChannel(AlarmNotificationPublisher.channelId(it))?.importance == NotificationManager.IMPORTANCE_NONE }
                        .takeIf { it.isNotEmpty() }?.joinToString(prefix = "Blocked in Android: ") { it.title }
                }.getOrElse { "Android notification availability could not be checked." }
            }
            delay(1_000)
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Alarms") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            visualRestrictions?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }.onFailure { error = "Open Android settings to adjust notifications." } }) {
                    Text("Android notification settings")
                }
            }
            audioStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            AlarmCategory.entries.forEach { category ->
                val value = settings.categories.getValue(category)
                Text(category.title, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Visual notification", Modifier.weight(1f))
                    Switch(checked = value.visual, onCheckedChange = { save(AlarmSettingsUiModel(settings).visual(category, it)) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Acoustic alarm", Modifier.weight(1f))
                    Switch(checked = value.acoustic, onCheckedChange = { save(AlarmSettingsUiModel(settings).acoustic(category, it)) })
                }
                var expanded by remember(category) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expanded = true }) {
                            Text(when (val sound = value.sound) {
                                is AlarmSoundChoice.BuiltIn -> sound.pattern.label
                                is AlarmSoundChoice.SystemNotification -> "Android notification sound"
                            })
                        }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            BuiltInAlarmSound.entries.forEach { sound ->
                                DropdownMenuItem(text = { Text(sound.label) }, onClick = {
                                    expanded = false; save(AlarmSettingsUiModel(settings).sound(category, AlarmSoundChoice.BuiltIn(sound)))
                                })
                            }
                            DropdownMenuItem(text = { Text("Android notification sounds...") }, enabled = !previewBlocked, onClick = {
                                expanded = false; pickerCategory = category
                                val uri = (value.sound as? AlarmSoundChoice.SystemNotification)?.uri?.let(Uri::parse)
                                runCatching { picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)) }
                                    .onFailure { error = "Android sound picker unavailable. Built-in sounds remain available." }
                            })
                        }
                    }
                    IconButton(onClick = { runCatching { player.preview(value.sound) }.onFailure { error = "Sound preview unavailable." } }, enabled = !previewBlocked) {
                        Icon(painterResource(android.R.drawable.ic_media_play), "Preview ${category.title} sound")
                    }
                    IconButton(onClick = player::stopPreview) {
                        Icon(painterResource(android.R.drawable.ic_media_pause), "Stop preview")
                    }
                }
                if (category == AlarmCategory.POSITION_LOST) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Loss delay: ${settings.positionLossDelayMillis / 1_000} s", Modifier.weight(1f))
                        TextButton(enabled = settings.positionLossDelayMillis > 1_000,
                            onClick = { save(AlarmSettingsUiModel(settings).delaySeconds((settings.positionLossDelayMillis / 1_000).toInt() - 1)) }) { Text("-") }
                        TextButton(enabled = settings.positionLossDelayMillis < 60_000,
                            onClick = { save(AlarmSettingsUiModel(settings).delaySeconds((settings.positionLossDelayMillis / 1_000).toInt() + 1)) }) { Text("+") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
