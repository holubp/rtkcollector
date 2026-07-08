package org.rtkcollector.app.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingPermissionModelTest {
    @Test
    fun androidTiramisuAndNewerRequiresNotificationPermission() {
        assertEquals(
            listOf("android.permission.POST_NOTIFICATIONS"),
            runtimePermissionsRequiredBeforeRecording(sdkInt = 33),
        )
        assertEquals(
            listOf("android.permission.POST_NOTIFICATIONS"),
            runtimePermissionsRequiredBeforeRecording(sdkInt = 36),
        )
    }

    @Test
    fun androidBeforeTiramisuRequiresNoRuntimeNotificationPermission() {
        assertEquals(emptyList<String>(), runtimePermissionsRequiredBeforeRecording(sdkInt = 32))
    }

    @Test
    fun batteryWarningIsShownWhenOptimisationMayApply() {
        val warning = batteryOptimisationWarning(isIgnoringBatteryOptimisations = false)

        assertTrue(warning.show)
        assertEquals(
            "Battery optimisation may interrupt long GNSS recordings on this device.",
            warning.message,
        )
    }

    @Test
    fun batteryWarningIsHiddenWhenOptimisationIsAlreadyIgnored() {
        assertFalse(batteryOptimisationWarning(isIgnoringBatteryOptimisations = true).show)
    }
}
