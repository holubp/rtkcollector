package org.rtkcollector.app.ui.dashboard

enum class DashboardLayoutPreference(
    val storageId: String,
    val displayName: String,
) {
    COMPACT_FIELD("compact_field", "Compact field dashboard"),
    RAIL("rail", "Rail layout");

    companion object {
        val default: DashboardLayoutPreference = COMPACT_FIELD

        fun fromStorageId(value: String?): DashboardLayoutPreference =
            entries.firstOrNull { it.storageId == value } ?: default
    }
}

enum class DashboardDistanceUnitPreference(
    val storageId: String,
    val displayName: String,
) {
    DYNAMIC("dynamic", "Dynamic m/cm/mm"),
    STATIC_METERS("meters", "Static metres"),
    ;

    companion object {
        val default: DashboardDistanceUnitPreference = DYNAMIC

        fun fromStorageId(value: String?): DashboardDistanceUnitPreference =
            entries.firstOrNull { it.storageId == value } ?: default
    }
}
