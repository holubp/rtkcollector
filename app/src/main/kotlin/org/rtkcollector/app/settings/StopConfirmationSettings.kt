package org.rtkcollector.app.settings

import android.content.Context
import org.rtkcollector.app.recording.StopConfirmationTiming

class StopConfirmationSettings(context: Context) {
    private val preferences = context.getSharedPreferences("rtkcollector-stop-confirmation", Context.MODE_PRIVATE)
    var timing: StopConfirmationTiming
        get() = runCatching {
            StopConfirmationTiming(preferences.getInt("delaySeconds", 2), preferences.getInt("timeoutSeconds", 10))
        }.getOrDefault(StopConfirmationTiming())
        set(value) {
            preferences.edit().putInt("delaySeconds", value.delaySeconds)
                .putInt("timeoutSeconds", value.timeoutSeconds).apply()
        }
}
