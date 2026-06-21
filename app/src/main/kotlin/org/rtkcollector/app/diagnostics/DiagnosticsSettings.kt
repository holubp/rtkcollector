package org.rtkcollector.app.diagnostics

import android.content.Context

class DiagnosticsSettings(context: Context) {
    private val preferences = context.getSharedPreferences("rtkcollector-diagnostics", Context.MODE_PRIVATE)

    var runtimeLoggingEnabled: Boolean
        get() = preferences.getBoolean(KEY_RUNTIME_LOGGING, false)
        set(value) {
            preferences.edit().putBoolean(KEY_RUNTIME_LOGGING, value).apply()
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
