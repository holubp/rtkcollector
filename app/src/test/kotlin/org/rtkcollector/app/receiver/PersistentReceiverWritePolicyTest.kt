package org.rtkcollector.app.receiver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersistentReceiverWritePolicyTest {
    @Test
    fun `active recording uses service path`() {
        assertEquals(
            PersistentReceiverWriteRoute.ActiveRecordingService,
            persistentReceiverWriteRoute(
                recordingActive = true,
                usbProfileAvailable = true,
                receiverConnected = true,
                usbPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `active recording always uses service path before idle checks`() {
        assertEquals(
            PersistentReceiverWriteRoute.ActiveRecordingService,
            persistentReceiverWriteRoute(
                recordingActive = true,
                usbProfileAvailable = false,
                receiverConnected = false,
                usbPermissionGranted = false,
            ),
        )
    }

    @Test
    fun `idle with selected connected permitted usb uses maintenance path`() {
        assertEquals(
            PersistentReceiverWriteRoute.IdleMaintenanceConnection,
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
            PersistentReceiverWriteRoute.Rejected(
                reason = PersistentReceiverWriteRejectionReason.USB_PROFILE_MISSING,
                message = "USB/baud profile is not available.",
            ),
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
            PersistentReceiverWriteRoute.Rejected(
                reason = PersistentReceiverWriteRejectionReason.RECEIVER_DISCONNECTED,
                message = "Selected USB receiver is not connected.",
            ),
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
            PersistentReceiverWriteRoute.Rejected(
                reason = PersistentReceiverWriteRejectionReason.USB_PERMISSION_MISSING,
                message = "USB permission is required before writing receiver configuration.",
            ),
            persistentReceiverWriteRoute(
                recordingActive = false,
                usbProfileAvailable = true,
                receiverConnected = true,
                usbPermissionGranted = false,
            ),
        )
    }
}
