package org.rtkcollector.app.ui.dashboard

const val DASHBOARD_ERROR_EXPIRY_MILLIS: Long = 15_000

data class DashboardErrorSnapshot(
    val category: String,
    val severity: String,
    val message: String?,
) {
    val fingerprint: String
        get() = "${category.trim()}|${severity.trim()}|${message.orEmpty().trim()}"

    fun shouldDisplay(
        ageMillis: Long,
        expiryMillis: Long = DASHBOARD_ERROR_EXPIRY_MILLIS,
    ): Boolean {
        val text = message?.trim().orEmpty()
        if (text.isBlank()) return false
        if (category.equals("NONE", ignoreCase = true) && severity.equals("NONE", ignoreCase = true)) {
            return false
        }
        if (severity.equals("FATAL", ignoreCase = true)) return true
        return ageMillis < expiryMillis
    }
}
