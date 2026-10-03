package org.rtkcollector.app.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingPermissionModelTest {
    @Test
    fun opensBatterySettingsWithoutLaunchingTheFallbackOnSuccess() {
        val launched = mutableListOf<BatterySettingsPage>()

        assertTrue(openBatteryOptimisationSettings { page -> launched.add(page); true })

        assertEquals(listOf(BatterySettingsPage.BATTERY_OPTIMISATION), launched)
    }

    @Test
    fun fallsBackToAppDetailsWhenBatterySettingsAreUnavailable() {
        val launched = mutableListOf<BatterySettingsPage>()

        assertTrue(openBatteryOptimisationSettings { page ->
            launched.add(page)
            page == BatterySettingsPage.APP_DETAILS
        })

        assertEquals(listOf(BatterySettingsPage.BATTERY_OPTIMISATION, BatterySettingsPage.APP_DETAILS), launched)
    }

    @Test
    fun reportsFailureWhenNeitherSettingsScreenCanBeOpened() {
        val launched = mutableListOf<BatterySettingsPage>()

        assertFalse(openBatteryOptimisationSettings { page -> launched.add(page); false })

        assertEquals(listOf(BatterySettingsPage.BATTERY_OPTIMISATION, BatterySettingsPage.APP_DETAILS), launched)
    }

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

    @Test
    fun batteryWarningIsHiddenWhileRecording() {
        val warning = batteryOptimisationWarning(
            isIgnoringBatteryOptimisations = false,
            isRecording = true,
        )

        assertFalse(warning.show)
        assertEquals("", warning.message)
    }
}
