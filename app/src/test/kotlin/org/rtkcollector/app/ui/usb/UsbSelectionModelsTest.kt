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

    @Test
    fun `start with no connected receiver reports no device`() {
        val result = UsbStartAccessDecision.evaluate(
            deviceConnected = false,
            permissionReportedGranted = false,
        )

        assertEquals(UsbStartAccessAction.NO_DEVICE, result.action)
        assertEquals("Selected USB receiver is not connected.", result.message)
    }

    @Test
    fun `start with missing permission requests permission and tells user to press start again`() {
        val result = UsbStartAccessDecision.evaluate(
            deviceConnected = true,
            permissionReportedGranted = false,
        )

        assertEquals(UsbStartAccessAction.REQUEST_PERMISSION, result.action)
        assertEquals(
            "USB permission requested. Approve the Android permission dialog, then press Start again.",
            result.message,
        )
    }

    @Test
    fun `start with reported permission verifies access`() {
        val result = UsbStartAccessDecision.evaluate(
            deviceConnected = true,
            permissionReportedGranted = true,
        )

        assertEquals(UsbStartAccessAction.VERIFY_AND_START, result.action)
        assertEquals("USB permission reported granted; access will be verified on Start.", result.message)
    }

    @Test
    fun `open failure message names stale permission and busy receiver cases`() {
        assertEquals(
            "Android reports USB permission, but the receiver could not be opened. Reconnect the receiver, close other serial apps, then retry USB access.",
            UsbStartAccessDecision.openFailureMessage(),
        )
    }
}
