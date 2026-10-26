package org.rtkcollector.app.diagnostics

import android.content.Context
import android.content.SharedPreferences
import java.io.Closeable

class DiagnosticsSettings(context: Context) {
    private val preferences = context.getSharedPreferences("rtkcollector-diagnostics", Context.MODE_PRIVATE)

    fun observeChanges(onChange: () -> Unit): Closeable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return Closeable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var runtimeLoggingEnabled: Boolean
        get() = level != DiagnosticLevel.MINIMAL
        set(value) {
            level = if (value) DiagnosticLevel.VERBOSE else DiagnosticLevel.MINIMAL
        }

    var level: DiagnosticLevel
        get() = DiagnosticLevel.fromStored(preferences.getString("level", null), preferences.getBoolean(KEY_RUNTIME_LOGGING, false))
        set(value) { preferences.edit().putString("level", value.name).apply() }

    var retention: DiagnosticsRetention
        get() {
            val mb = preferences.getInt("optionalCapMB", 10).takeIf { it in DiagnosticsRetention.SIZE_CHOICES_MB } ?: 10
            val days = preferences.getInt("optionalAgeDays", 7).takeIf { it in DiagnosticsRetention.AGE_CHOICES_DAYS } ?: 7
            return DiagnosticsRetention(mb * 1_000_000L, days)
        }
        set(value) {
            require(value.maxOptionalBytes in DiagnosticsRetention.SIZE_CHOICES_MB.map { it * 1_000_000L })
            preferences.edit().putInt("optionalCapMB", (value.maxOptionalBytes / 1_000_000L).toInt())
                .putInt("optionalAgeDays", value.maxOptionalAgeDays).apply()
        }

    var performanceMonitoringEnabled: Boolean
        get() = preferences.getBoolean(KEY_PERFORMANCE_MONITORING, false)
        set(value) {
            preferences.edit().putBoolean(KEY_PERFORMANCE_MONITORING, value).apply()
        }

    private companion object {
        const val KEY_RUNTIME_LOGGING = "runtimeLoggingEnabled"
        const val KEY_PERFORMANCE_MONITORING = "performanceMonitoringEnabled"
    }
}
