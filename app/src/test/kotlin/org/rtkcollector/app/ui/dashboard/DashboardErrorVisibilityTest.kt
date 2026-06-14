package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardErrorVisibilityTest {
    @Test
    fun `blank error is hidden immediately`() {
        val snapshot = DashboardErrorSnapshot(category = "NTRIP", severity = "DEGRADED", message = "")

        assertFalse(snapshot.shouldDisplay(ageMillis = 0))
    }

    @Test
    fun `none none stale message is hidden immediately`() {
        val snapshot = DashboardErrorSnapshot(
            category = "NONE",
            severity = "NONE",
            message = "Software caused connection abort",
        )

        assertFalse(snapshot.shouldDisplay(ageMillis = 0))
    }

    @Test
    fun `non fatal error is visible before expiry and hidden after fifteen seconds`() {
        val snapshot = DashboardErrorSnapshot(
            category = "NTRIP",
            severity = "DEGRADED",
            message = "NTRIP connection failed",
        )

        assertTrue(snapshot.shouldDisplay(ageMillis = 14_999))
        assertFalse(snapshot.shouldDisplay(ageMillis = 15_000))
    }

    @Test
    fun `fatal error remains visible beyond expiry`() {
        val snapshot = DashboardErrorSnapshot(
            category = "USB",
            severity = "FATAL",
            message = "USB serial device could not be opened",
        )

        assertTrue(snapshot.shouldDisplay(ageMillis = 120_000))
    }

    @Test
    fun `fingerprint combines category severity and message`() {
        val snapshot = DashboardErrorSnapshot(
            category = "NTRIP",
            severity = "DEGRADED",
            message = "NTRIP connection failed",
        )

        assertEquals("NTRIP|DEGRADED|NTRIP connection failed", snapshot.fingerprint)
    }
}
