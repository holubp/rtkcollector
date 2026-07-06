package org.rtkcollector.app.profile

enum class ProfileDeviceFilter(
    val storageValue: String,
    val displayName: String,
) {
    ANY("any", "Any"),
    UM980("um980", "UM980"),
    UBLOX_M8T("ublox-m8t", "u-blox M8T"),
    ;

    fun matchesSettingsSet(settingsSet: RecordingSettingsSet): Boolean =
        matchesReceiverId(settingsSet.receiverProfileId)

    fun matchesCommandProfile(commandProfile: CommandProfile): Boolean =
        matchesReceiverId(commandProfile.receiverFamily)

    fun matchesReceiverId(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return when (this) {
            ANY -> true
            UM980 -> normalized.startsWith("um980") ||
                normalized.startsWith("unicore") ||
                normalized.contains("n4")
            UBLOX_M8T -> normalized.startsWith("ublox-m8t")
        }
    }

    companion object {
        fun fromStorageValue(value: String?): ProfileDeviceFilter =
            entries.firstOrNull { it.storageValue.equals(value.orEmpty(), ignoreCase = true) } ?: ANY
    }
}
