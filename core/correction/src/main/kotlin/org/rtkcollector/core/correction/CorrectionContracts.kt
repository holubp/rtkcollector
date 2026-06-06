package org.rtkcollector.core.correction

enum class NtripConnectionState {
    IDLE,
    RESOLVING,
    CONNECTING,
    AUTHENTICATING,
    STREAMING,
    RECONNECT_WAIT,
    STOPPED,
}

data class CorrectionStatus(
    val state: NtripConnectionState,
    val correctionAgeSeconds: Double? = null,
    val lastError: String? = null,
)
