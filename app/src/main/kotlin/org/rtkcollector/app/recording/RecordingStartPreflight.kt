package org.rtkcollector.app.recording

object RecordingStartPreflight {
    data class Input(
        val workflowUsesNtrip: Boolean,
        val usbProfileSelected: Boolean,
        val usbDeviceConnected: Boolean,
        val usbPermissionGranted: Boolean,
        val serialDriverAvailable: Boolean,
        val serialOpenSucceeded: Boolean,
        val storageWritable: Boolean,
        val ntripMountpointConfigured: Boolean,
    )

    data class Result(
        val canStart: Boolean,
        val category: RecordingErrorCategory,
        val severity: RecordingErrorSeverity,
        val message: String,
    )

    fun validate(input: Input): Result =
        when {
            !input.usbProfileSelected -> fatal(RecordingErrorCategory.USB, "No USB/baud profile is selected.")
            !input.usbDeviceConnected -> fatal(RecordingErrorCategory.USB, "Selected USB receiver is not connected.")
            !input.usbPermissionGranted -> fatal(RecordingErrorCategory.USB, "USB permission has not been granted.")
            !input.serialDriverAvailable ->
                fatal(RecordingErrorCategory.USB, "No supported USB serial driver is available for the selected receiver.")
            !input.serialOpenSucceeded -> fatal(RecordingErrorCategory.USB, "USB serial device could not be opened.")
            !input.storageWritable -> fatal(RecordingErrorCategory.STORAGE, "Recording storage is not writable.")
            input.workflowUsesNtrip && !input.ntripMountpointConfigured ->
                fatal(RecordingErrorCategory.NTRIP, "NTRIP mountpoint is required for this workflow.")
            else -> Result(
                canStart = true,
                category = RecordingErrorCategory.NONE,
                severity = RecordingErrorSeverity.NONE,
                message = "Ready to start recording.",
            )
        }

    private fun fatal(category: RecordingErrorCategory, message: String): Result =
        Result(
            canStart = false,
            category = category,
            severity = RecordingErrorSeverity.FATAL,
            message = message,
        )
}
