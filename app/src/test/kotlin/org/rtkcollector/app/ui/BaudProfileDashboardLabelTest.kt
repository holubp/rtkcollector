package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.UsbBaudProfile

class BaudProfileDashboardLabelTest {
    @Test
    fun `dashboard baud label shows actual target baud`() {
        val profile = UsbBaudProfile(
            id = "um980-usb",
            name = "UM980 USB default",
            profileBaud = 115200,
            serialBaud = 230400,
        )

        assertEquals("UM980 USB default · target 230400 baud", profile.dashboardBaudLabel())
    }
}
