package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileCompatibilityTest {
    @Test
    fun `command profile remains editable but cannot activate for mismatched receiver family`() {
        val result = ProfileCompatibility.commandProfile(
            receiverProfileId = "um980-n4",
            commandProfile = CommandProfile(
                id = "ublox-m8t-raw",
                name = "u-blox M8T raw",
                receiverFamily = "ublox-m8t",
            ),
        )

        assertTrue(result.editable)
        assertFalse(result.activatable)
        assertEquals(ProfileCompatibilityStatus.INCOMPATIBLE, result.status)
    }

    @Test
    fun `um980 rtklib profile requires um980 raw observation route`() {
        val result = ProfileCompatibility.rtklibProfile(
            receiverProfileId = "um980-n4",
            commandProfile = CommandProfile(
                id = "um980-binary-multihz-rtklib-obsvmb",
                name = "UM980 multi-Hz binary RTKLIB OBSVMB",
                receiverFamily = "um980-n4",
                runtimeScript = "OBSVMB COM1 1",
            ),
            rtklibProfile = RtklibProfile(id = "rtklib", name = "RTKLIB", enabled = true),
        )

        assertTrue(result.activatable)
        assertEquals(ProfileCompatibilityStatus.COMPATIBLE, result.status)
    }

    @Test
    fun `known receiver baud is recommended and supported-list baud is untested`() {
        val recommended = ProfileCompatibility.baudProfile(
            receiverProfileId = "um980-n4",
            usbBaudProfile = UsbBaudProfile(id = "um980-230400", name = "UM980 230400", serialBaud = 230400),
        )
        val untested = ProfileCompatibility.baudProfile(
            receiverProfileId = "um980-n4",
            usbBaudProfile = UsbBaudProfile(id = "um980-128000", name = "UM980 128000", serialBaud = 128000),
        )

        assertEquals(BaudCompatibilityStatus.RECOMMENDED, recommended.baudStatus)
        assertEquals(BaudCompatibilityStatus.UNTESTED, untested.baudStatus)
    }
}
