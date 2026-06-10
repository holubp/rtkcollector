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
