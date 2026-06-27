package org.rtkcollector.core.correction

import java.io.IOException

enum class NtripCasterUploadRetryMode {
    ADAPTIVE,
    FIXED,
}

data class NtripCasterUploadRetryPolicy(
    val mode: NtripCasterUploadRetryMode = NtripCasterUploadRetryMode.ADAPTIVE,
    val fixedReconnectDelayMillis: Long = 10_000,
    val adaptiveInitialDelayMillis: Long = 10_000,
    val adaptiveMaxDelayMillis: Long = 300_000,
    val stopAfterFailuresEnabled: Boolean = true,
    val stopAfterConsecutiveFailures: Int = 5,
) {
    init {
        require(fixedReconnectDelayMillis >= 10_000) {
            "Fixed reconnect delay must be at least 10 seconds."
        }
        require(adaptiveInitialDelayMillis >= 10_000) {
            "Adaptive initial reconnect delay must be at least 10 seconds."
        }
        require(adaptiveMaxDelayMillis >= adaptiveInitialDelayMillis) {
            "Adaptive max reconnect delay must not be below initial delay."
        }
        require(stopAfterConsecutiveFailures >= 1) {
            "Stop-after-failures count must be at least 1."
        }
    }
}

data class NtripCasterUploadSafetyPolicy(
    val enabled: Boolean = false,
    val forced: Boolean = false,
    val maxBitrateKbps: Double = 35.0,
    val bitrateWindowMillis: Long = 60_000,
    val maxSessionUploadBytes: Long = 500L * 1024L * 1024L,
    val noDataTimeoutMillis: Long = 12_000,
) {
    init {
        require(maxBitrateKbps > 0.0) { "Safety bitrate threshold must be positive." }
        require(bitrateWindowMillis > 0L) { "Safety bitrate window must be positive." }
        require(maxSessionUploadBytes > 0L) { "Safety session upload limit must be positive." }
        require(noDataTimeoutMillis > 0L) { "No-data timeout must be positive." }
    }
}

data class NtripCasterUploadPolicy(
    val retry: NtripCasterUploadRetryPolicy = NtripCasterUploadRetryPolicy(),
    val safety: NtripCasterUploadSafetyPolicy = NtripCasterUploadSafetyPolicy(),
)

enum class NtripCasterUploadStopReason {
    RETRY_LIMIT,
    NO_RTCM_DATA,
    BITRATE_LIMIT,
    SESSION_VOLUME_LIMIT,
}

data class NtripCasterUploadMessageRate(
    val messageType: Int,
    val hz: Double,
)

data class NtripCasterUploadEvent(
    val kind: String,
    val message: String,
    val timestampMillis: Long,
)

class NtripCasterUploadNoDataException(
    message: String,
) : IOException(message)

class NtripCasterUploadSafetyException(
    val stopReason: NtripCasterUploadStopReason,
    message: String,
) : IOException(message)
