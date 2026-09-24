package org.rtkcollector.app.ui

import androidx.compose.runtime.saveable.SaverScope
import org.rtkcollector.app.testing.TestFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionBrowserNavigationTest {
    @Test
    fun `session browser returns to its entry screen`() {
        assertEquals(AppScreen.HOME, SessionBrowserEntryPoint.HOME.returnScreen())
        assertEquals(AppScreen.SETTINGS, SessionBrowserEntryPoint.SETTINGS.returnScreen())
    }

    @Test
    fun `session browser entry point saver round trips both origins`() {
        val saverScope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }

        listOf(SessionBrowserEntryPoint.HOME, SessionBrowserEntryPoint.SETTINGS).forEach { entryPoint ->
            val saved = requireNotNull(SessionBrowserEntryPointSaver.run { saverScope.save(entryPoint) })

            assertEquals(entryPoint, SessionBrowserEntryPointSaver.restore(saved))
        }
    }

    @Test
    fun `session browser entry point saver defaults unknown values to settings`() {
        assertEquals(
            SessionBrowserEntryPoint.SETTINGS,
            SessionBrowserEntryPointSaver.restore("unknown-entry-point"),
        )
    }

    @Test
    fun `session browser callers and back actions preserve their navigation origins`() {
        val source = TestFiles.readString(
            TestFiles.locateProjectPath("app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt"),
        )
        val homeBlock = source.substringBetween("AppScreen.HOME ->", "AppScreen.SETTINGS ->")
        val settingsBlock = source.substringBetween("AppScreen.SETTINGS ->", "AppScreen.NTRIP_MOUNTPOINT ->")
        val backHandlerBlock = source.substringBetween(
            "BackHandler(enabled = screen != AppScreen.HOME)",
            "DisposableEffect(context)",
        )
        val sessionsBlock = source.substringBetween("AppScreen.SESSIONS -> SessionsScreen(", "AppScreen.APP_DIAGNOSTICS ->")
        val mountpointBlock = source.substringBetween(
            "AppScreen.NTRIP_MOUNTPOINT -> NtripMountpointScreen(",
            "onSave = { mountpoint ->",
        )

        assertTrue(
            homeBlock.contains("onSessions = { openSessions(SessionBrowserEntryPoint.HOME) },"),
            "Home must open Sessions with the Home entry point",
        )
        assertTrue(
            settingsBlock.contains("onSessions = { openSessions(SessionBrowserEntryPoint.SETTINGS) },"),
            "Settings must open Sessions with the Settings entry point",
        )
        assertTrue(
            backHandlerBlock.contains("screen = if (screen == AppScreen.SESSIONS) {") &&
                backHandlerBlock.contains("sessionBrowserEntryPoint.returnScreen()"),
            "System Back must use the saved Sessions entry point",
        )
        assertTrue(
            sessionsBlock.contains("onBack = { screen = sessionBrowserEntryPoint.returnScreen() },"),
            "Sessions screen Back must use the saved entry point",
        )
        assertTrue(
            mountpointBlock.contains("onBack = { screen = AppScreen.SETTINGS },"),
            "NTRIP mountpoint Back must continue to return to Settings",
        )
    }

    private fun String.substringBetween(startMarker: String, endMarker: String): String {
        val start = indexOf(startMarker)
        assertTrue(start >= 0, "Missing source marker: $startMarker")
        val end = indexOf(endMarker, start + startMarker.length)
        assertTrue(end > start, "Missing source marker after $startMarker: $endMarker")
        return substring(start, end)
    }
}
