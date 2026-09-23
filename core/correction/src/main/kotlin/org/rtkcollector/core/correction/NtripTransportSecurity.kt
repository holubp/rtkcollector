package org.rtkcollector.core.correction

/** Transport policy for an NTRIP connection. App distribution policy is enforced above this core API. */
data class NtripTransportSecurity(
    val mode: NtripTransportMode = NtripTransportMode.TLS,
    val verification: NtripTlsVerification = NtripTlsVerification.SystemTrust,
) {
    init {
        if (mode == NtripTransportMode.PLAINTEXT) {
            require(verification == NtripTlsVerification.SystemTrust) {
                "Plaintext NTRIP cannot use a TLS verification mode."
            }
        }
        if (verification is NtripTlsVerification.CustomCa) {
            require(verification.certificateDer.isNotEmpty()) {
                "Custom NTRIP CA certificate must not be empty."
            }
        }
    }
}

enum class NtripTransportMode {
    TLS,
    PLAINTEXT,
}

sealed interface NtripTlsVerification {
    data object SystemTrust : NtripTlsVerification

    /** DER encoded trust anchor. The app must not log this byte content. */
    data class CustomCa(val certificateDer: ByteArray) : NtripTlsVerification

    /** Sideload-only compatibility mode. The app layer requires explicit user confirmation. */
    data object UnsafeAccepted : NtripTlsVerification
}
