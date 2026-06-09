package org.rtkcollector.app.ui.usb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UsbSelectionModelsTest {
    @Test
    fun `baud selector exposes validated UM980 values`() {
        val state = BaudSelectorState()

        assertEquals(230400, state.selectedBaudRate)
        assertTrue(4800 in state.allowedBaudRates)
        assertTrue(256000 in state.allowedBaudRates)
        assertTrue(921600 in state.allowedBaudRates)
        assertEquals(921600, state.select(921600).selectedBaudRate)
    }

    @Test
    fun `baud selector rejects unsupported values`() {
        assertThrows(IllegalArgumentException::class.java) {
            BaudSelectorState(selectedBaudRate = 123456)
        }
    }

    @Test
    fun `device choice label includes product device and vid pid`() {
        val choice = UsbDeviceChoice(
            vendorId = 0x0403,
            productId = 0x6015,
            deviceName = "/dev/bus/usb/001/002",
            productName = "UM980 USB",
        )

        assertEquals("UM980 USB - /dev/bus/usb/001/002 - VID:0403 PID:6015", choice.label)
    }

    @Test
    fun `device choice encodes and decodes profile value`() {
        val choice = UsbDeviceChoice(
            vendorId = 0x0403,
            productId = 0x6015,
            deviceName = "/dev/bus/usb/001/002",
            productName = "UM980 USB",
        )

        val decoded = UsbDeviceChoice.fromProfileValue(choice.toProfileValue())

        assertEquals(choice, decoded)
    }
}
