package org.rtkcollector.app.permissions

private const val NotificationPermission = "android.permission.POST_NOTIFICATIONS"
private const val BatteryOptimisationWarningMessage =
    "Battery optimisation may interrupt long GNSS recordings on this device."

data class BatteryOptimisationWarning(
    val show: Boolean,
    val message: String,
)

fun runtimePermissionsRequiredBeforeRecording(sdkInt: Int): List<String> =
    if (sdkInt >= 33) {
        listOf(NotificationPermission)
    } else {
        emptyList()
    }

fun batteryOptimisationWarning(isIgnoringBatteryOptimisations: Boolean): BatteryOptimisationWarning =
    if (isIgnoringBatteryOptimisations) {
        BatteryOptimisationWarning(show = false, message = "")
    } else {
        BatteryOptimisationWarning(
            show = true,
            message = BatteryOptimisationWarningMessage,
        )
    }
