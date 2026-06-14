package org.rtkcollector.app.recording

enum class RecordingLifecycleState {
    IDLE,
    STARTING,
    RECORDING,
    STOPPING,
    STOPPED,
    FAILED,
}

enum class RecordingErrorCategory {
    NONE,
    USB,
    STORAGE,
    NTRIP,
    RECEIVER_COMMAND,
    PARSER_EXPORT,
    SERVICE_LIFECYCLE,
}

enum class RecordingErrorSeverity {
    NONE,
    INFO,
    DEGRADED,
    FATAL,
}

data class RecordingServiceState(
    val running: Boolean = false,
    val lifecycle: RecordingLifecycleState = RecordingLifecycleState.IDLE,
    val workflowLabel: String = "n/a",
    val receiverLabel: String = "n/a",
    val storageLabel: String = "n/a",
    val settingsSetLabel: String = "n/a",
    val settingsCommandProfileLabel: String = "n/a",
    val settingsBaudProfileLabel: String = "n/a",
    val settingsNtripCasterProfileLabel: String = "n/a",
    val settingsRecordingOutputProfileLabel: String = "n/a",
    val settingsStorageProfileLabel: String = "n/a",
    val sessionPath: String? = null,
    val receiverRxBytes: Long = 0,
    val txToReceiverBytes: Long = 0,
    val correctionInputBytes: Long = 0,
    val nmeaBytes: Long = 0,
    val ntripState: String = "Not configured",
    val ntripUrl: String = "n/a",
    val ntripTransferred: String = "0 B",
    val ntripRates: String = "n/a",
    val ntripStationId: String = "",
    val ntripBaseLatLon: String = "n/a",
    val ggaFixQuality: Int? = null,
    val bestnavPositionType: String? = null,
    val pppStatus: String? = null,
    val receiverRtkStatus: String = "n/a",
    val rtkPositionType: String? = null,
    val rtkCalculateStatus: Int? = null,
    val rtkCalculateStatusDescription: String? = null,
    val receiverRtkEvidenceAtMillis: Long? = null,
    val rtcmDecodedAtMillis: Long? = null,
    val rtcmLastMessageId: Int? = null,
    val rtcmLastBaseId: Int? = null,
    val latLon: String = "n/a",
    val ellipsoidalHeight: String = "n/a",
    val altitude: String = "n/a",
    val utcTime: String = "n/a",
    val latError: String = "n/a",
    val lonError: String = "n/a",
    val satellites: String = "n/a",
    val satellitesUsed: Int? = null,
    val satellitesInView: Int? = null,
    val pdop: String = "n/a",
    val vdop: Double? = null,
    val hdopVdop: String = "n/a",
    val horizontalAccuracy: String = "n/a",
    val verticalAccuracy: String = "n/a",
    val differentialAge: String = "n/a",
    val baseline: String = "n/a",
    val rtcmFrames: Long = 0,
    val lastError: String? = null,
    val errorCategory: RecordingErrorCategory = RecordingErrorCategory.NONE,
    val errorSeverity: RecordingErrorSeverity = RecordingErrorSeverity.NONE,
    val rawRecordingActive: Boolean = false,
    val correctionsActive: Boolean = false,
    val um980Frequency: String = "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM -/-/-/-/-/- Hz",
    val um980Mode: String = "n/a",
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
    val ntripBaseLatDeg: Double? = null,
    val ntripBaseLonDeg: Double? = null,
)

internal fun recordingNotificationText(state: RecordingServiceState): String =
    recordingNotificationText(
        running = state.running,
        receiverRxBytes = state.receiverRxBytes,
        correctionInputBytes = state.correctionInputBytes,
    )

internal fun recordingNotificationText(
    running: Boolean,
    receiverRxBytes: Long,
    correctionInputBytes: Long,
): String =
    if (!running) {
        "Recording inactive"
    } else if (receiverRxBytes == 0L && correctionInputBytes == 0L) {
        "Starting recording"
    } else {
        "Recording in progress · RAW ${recordingNotificationBytes(receiverRxBytes)} · " +
            "NTRIP ${recordingNotificationBytes(correctionInputBytes)}"
    }

private fun recordingNotificationBytes(bytes: Long): String =
    when {
        bytes < 1000 -> "$bytes B"
        bytes < 1_000_000 -> "%.1f kB".format(java.util.Locale.US, bytes / 1000.0)
        bytes < 1_000_000_000 -> "%.1f MB".format(java.util.Locale.US, bytes / 1_000_000.0)
        else -> "%.1f GB".format(java.util.Locale.US, bytes / 1_000_000_000.0)
    }

internal fun RecordingServiceState.clearRecoverableUsbError(): RecordingServiceState =
    if (errorCategory == RecordingErrorCategory.USB && errorSeverity == RecordingErrorSeverity.DEGRADED) {
        copy(
            lastError = null,
            errorCategory = RecordingErrorCategory.NONE,
            errorSeverity = RecordingErrorSeverity.NONE,
        )
    } else {
        this
    }
