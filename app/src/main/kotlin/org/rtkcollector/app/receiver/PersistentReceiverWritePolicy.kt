package org.rtkcollector.app.receiver

sealed class PersistentReceiverWriteRoute {
    data object ACTIVE_RECORDING_SERVICE : PersistentReceiverWriteRoute()
    data object IDLE_MAINTENANCE_CONNECTION : PersistentReceiverWriteRoute()
    data class Rejected(val message: String) : PersistentReceiverWriteRoute()
}

fun persistentReceiverWriteRoute(
    recordingActive: Boolean,
    usbProfileAvailable: Boolean,
    receiverConnected: Boolean,
    usbPermissionGranted: Boolean,
): PersistentReceiverWriteRoute {
    if (recordingActive) return PersistentReceiverWriteRoute.ACTIVE_RECORDING_SERVICE
    if (!usbProfileAvailable) return PersistentReceiverWriteRoute.Rejected("USB/baud profile is not available.")
    if (!receiverConnected) return PersistentReceiverWriteRoute.Rejected("Selected USB receiver is not connected.")
    if (!usbPermissionGranted) {
        return PersistentReceiverWriteRoute.Rejected("USB permission is required before writing receiver configuration.")
    }
    return PersistentReceiverWriteRoute.IDLE_MAINTENANCE_CONNECTION
}
