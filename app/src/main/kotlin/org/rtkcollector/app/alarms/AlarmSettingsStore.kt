package org.rtkcollector.app.alarms

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.Closeable

class AlarmSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("rtkcollector-alarms", Context.MODE_PRIVATE)
    fun read(): AlarmSettingsSnapshot = runCatching {
        val root = JSONObject(preferences.getString("snapshot", "{}") ?: "{}")
        val defaults = AlarmSettingsSnapshot.defaults()
        defaults.copy(positionLossDelayMillis = root.optLong("delay", 5_000).takeIf { it in 1_000..60_000 } ?: 5_000,
            preferBluetoothAudio = root.optBoolean("preferBluetoothAudio", false),
            categories = AlarmCategory.entries.associateWith { category ->
                val item = root.optJSONObject(category.name) ?: JSONObject()
                val choice = item.optString("uri").takeIf(::isAlarmSoundUri)?.let(AlarmSoundChoice::SystemNotification)
                    ?: runCatching { AlarmSoundChoice.BuiltIn(BuiltInAlarmSound.valueOf(item.getString("builtin"))) }.getOrNull()
                    ?: AlarmSettingsSnapshot.defaultSound(category)
                AlarmCategorySettings(item.optBoolean("visual", true), item.optBoolean("acoustic", true), choice)
            })
    }.getOrElse { AlarmSettingsSnapshot.defaults() }

    fun write(snapshot: AlarmSettingsSnapshot) {
        val root = JSONObject().put("delay", snapshot.positionLossDelayMillis)
            .put("preferBluetoothAudio", snapshot.preferBluetoothAudio)
        snapshot.categories.forEach { (category, value) ->
            val item = JSONObject().put("visual", value.visual).put("acoustic", value.acoustic)
            when (val sound = value.sound) {
                is AlarmSoundChoice.BuiltIn -> item.put("builtin", sound.pattern.name)
                is AlarmSoundChoice.SystemNotification -> { require(isAlarmSoundUri(sound.uri)); item.put("uri", sound.uri) }
            }
            root.put(category.name, item)
        }
        preferences.edit().putString("snapshot", root.toString()).apply()
    }
    fun observeChanges(listener: () -> Unit): Closeable {
        val observer = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> listener() }
        preferences.registerOnSharedPreferenceChangeListener(observer)
        return Closeable { preferences.unregisterOnSharedPreferenceChangeListener(observer) }
    }
}

fun isAlarmSoundUri(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    uri.scheme in setOf("content", "android.resource") && !uri.authority.isNullOrBlank() && value.length <= 2048
}.getOrDefault(false)
