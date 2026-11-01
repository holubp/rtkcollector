package org.rtkcollector.app.receiver

sealed class PersistentReceiverWriteRoute {
    data object ActiveRecordingService : PersistentReceiverWriteRoute()
    data object IdleMaintenanceConnection : PersistentReceiverWriteRoute()
    data class Rejected(
        val reason: PersistentReceiverWriteRejectionReason,
        val message: String,
    ) : PersistentReceiverWriteRoute()
}

enum class PersistentReceiverWriteRejectionReason {
    RECEIVER_BUSY,
    USB_PROFILE_MISSING,
    RECEIVER_DISCONNECTED,
    USB_PERMISSION_MISSING,
}

fun persistentReceiverWriteRoute(
    recordingActive: Boolean,
    usbProfileAvailable: Boolean,
    receiverConnected: Boolean,
    usbPermissionGranted: Boolean,
    receiverBusy: Boolean = false,
): PersistentReceiverWriteRoute {
    if (recordingActive) return PersistentReceiverWriteRoute.ActiveRecordingService
    if (receiverBusy) return PersistentReceiverWriteRoute.Rejected(
        PersistentReceiverWriteRejectionReason.RECEIVER_BUSY,
        "Receiver is starting or stopping. Wait before writing persistent configuration.",
    )
    if (!usbProfileAvailable) {
        return PersistentReceiverWriteRoute.Rejected(
            reason = PersistentReceiverWriteRejectionReason.USB_PROFILE_MISSING,
            message = "USB/baud profile is not available.",
        )
    }
    if (!receiverConnected) {
        return PersistentReceiverWriteRoute.Rejected(
            reason = PersistentReceiverWriteRejectionReason.RECEIVER_DISCONNECTED,
            message = "Selected USB receiver is not connected.",
        )
    }
    if (!usbPermissionGranted) {
        return PersistentReceiverWriteRoute.Rejected(
            reason = PersistentReceiverWriteRejectionReason.USB_PERMISSION_MISSING,
            message = "USB permission is required before writing receiver configuration.",
        )
    }
    return PersistentReceiverWriteRoute.IdleMaintenanceConnection
}
