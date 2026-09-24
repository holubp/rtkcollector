package org.rtkcollector.app.ui.dashboard

import org.rtkcollector.core.correction.NtripEndpointSecurityPolicy
import org.rtkcollector.core.correction.NtripTlsVerification
import org.rtkcollector.core.correction.NtripTransportMode

fun ntripSecurityDisclosure(
    correction: NtripEndpointSecurityPolicy?,
    upload: NtripEndpointSecurityPolicy?,
    allowInsecure: Boolean,
): String? {
    if (correction == null && upload == null) return null
    fun route(label: String, policy: NtripEndpointSecurityPolicy?): String? = policy?.let {
        val mode = when {
            it.transport == NtripTransportMode.PLAINTEXT -> "plaintext (unencrypted)"
            it.verification == NtripTlsVerification.Unsafe -> "unsafe TLS (certificate and hostname not verified)"
            else -> "TLS with system trust"
        }
        "$label: $mode"
    }
    val distribution = if (allowInsecure) "Sideload build" else "Google Play build"
    return (listOf(distribution) + listOfNotNull(route("Correction download", correction), route("Source upload", upload)))
        .joinToString("; ")
}
