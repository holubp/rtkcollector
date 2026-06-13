package org.rtkcollector.app.receiver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersistentReceiverWritePolicyTest {
    @Test
    fun `active recording uses service path`() {
        assertEquals(
            PersistentReceiverWriteRoute.ACTIVE_RECORDING_SERVICE,
            persistentReceiverWriteRoute(
                recordingActive = true,
                usbProfileAvailable = true,
                receiverConnected = true,
                usbPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `idle with selected connected permitted usb uses maintenance path`() {
        assertEquals(
            PersistentReceiverWriteRoute.IDLE_MAINTENANCE_CONNECTION,
            persistentReceiverWriteRoute(
                recordingActive = false,
                usbProfileAvailable = true,
                receiverConnected = true,
                usbPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `idle without usb profile is rejected`() {
        assertEquals(
            PersistentReceiverWriteRoute.Rejected("USB/baud profile is not available."),
            persistentReceiverWriteRoute(
                recordingActive = false,
                usbProfileAvailable = false,
                receiverConnected = true,
                usbPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `idle without connected receiver is rejected`() {
        assertEquals(
            PersistentReceiverWriteRoute.Rejected("Selected USB receiver is not connected."),
            persistentReceiverWriteRoute(
                recordingActive = false,
                usbProfileAvailable = true,
                receiverConnected = false,
                usbPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `idle without usb permission is rejected`() {
        assertEquals(
            PersistentReceiverWriteRoute.Rejected("USB permission is required before writing receiver configuration."),
            persistentReceiverWriteRoute(
                recordingActive = false,
                usbProfileAvailable = true,
                receiverConnected = true,
                usbPermissionGranted = false,
            ),
        )
    }
}
