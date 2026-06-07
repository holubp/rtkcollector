package org.rtkcollector.app.profile

import android.content.Context

data class SavedRecordingDefaults(
    val ntripHost: String = "",
    val ntripPort: Int = 2101,
    val ntripMountpoint: String = "",
    val ntripUsername: String = "",
    val ntripSecretId: String = "",
    val profileBaud: Int = 230400,
    val serialBaud: Int = 230400,
)

class RecordingProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("recording-profiles", Context.MODE_PRIVATE)

    fun loadDefaults(): SavedRecordingDefaults =
        SavedRecordingDefaults(
            ntripHost = preferences.getString("ntripHost", "") ?: "",
            ntripPort = preferences.getInt("ntripPort", 2101),
            ntripMountpoint = preferences.getString("ntripMountpoint", "") ?: "",
            ntripUsername = preferences.getString("ntripUsername", "") ?: "",
            ntripSecretId = preferences.getString("ntripSecretId", "") ?: "",
            profileBaud = preferences.getInt("profileBaud", 230400),
            serialBaud = preferences.getInt("serialBaud", 230400),
        )

    fun saveDefaults(defaults: SavedRecordingDefaults) {
        require(defaults.ntripPort in 1..65535) { "NTRIP port must be 1..65535." }
        require(defaults.profileBaud in 9600..921600) { "Profile baud must be 9600..921600." }
        require(defaults.serialBaud in 9600..921600) { "Serial baud must be 9600..921600." }
        preferences.edit()
            .putString("ntripHost", defaults.ntripHost)
            .putInt("ntripPort", defaults.ntripPort)
            .putString("ntripMountpoint", defaults.ntripMountpoint)
            .putString("ntripUsername", defaults.ntripUsername)
            .putString("ntripSecretId", defaults.ntripSecretId)
            .putInt("profileBaud", defaults.profileBaud)
            .putInt("serialBaud", defaults.serialBaud)
            .apply()
    }
}
