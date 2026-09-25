package org.rtkcollector.core.correction

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

enum class NtripTransportMode { TLS, PLAINTEXT }

/** Unsafe is retained only to decode old profiles; it is never a usable connection policy. */
enum class NtripTlsVerification { SystemTrust, Unsafe }

/** A parsed authority. Host is unbracketed for DNS/TCP and bracketed only in HTTP Host. */
class NtripEndpoint private constructor(
    val host: String,
    val port: Int,
    val hostHeader: String,
    val sniName: String?,
) {
    companion object {
        fun parse(host: String, port: Int): NtripEndpoint {
            require(port in 1..65535) { "NTRIP port must be between 1 and 65535" }
            require(host.isNotEmpty() && host == host.trim() && host.none { it.isWhitespace() || it.isISOControl() }) {
                "NTRIP host is invalid"
            }
            require(host.none { it in "/?#@\\" }) { "NTRIP host is invalid" }
            if (host.startsWith('[')) {
                require(host.endsWith(']') && host.count { it == '[' } == 1 && host.count { it == ']' } == 1) {
                    "NTRIP IPv6 host must be bracketed"
                }
                val literal = host.substring(1, host.lastIndex)
                require(':' in literal && '%' !in literal && literal.all { it.isDigit() || it in "abcdefABCDEF:." }) {
                    "NTRIP IPv6 literal is invalid"
                }
                val address = runCatching { InetAddress.getByName(literal) }.getOrNull()
                require(address is Inet6Address) { "NTRIP IPv6 literal is invalid" }
                val canonical = address.hostAddress.lowercase(Locale.ROOT)
                return NtripEndpoint(canonical, port, "[$canonical]:$port", null)
            }
            require(':' !in host && '[' !in host && ']' !in host) { "NTRIP host must not contain a port" }
            if (host.all { it.isDigit() || it == '.' }) {
                val parts = host.split('.')
                require(parts.size == 4 && parts.all { part ->
                    part.isNotEmpty() && (part == "0" || !part.startsWith('0')) &&
                        part.toIntOrNull() in 0..255
                }) { "NTRIP IPv4 literal is invalid" }
                return NtripEndpoint(host, port, "$host:$port", null)
            }
            val ascii = runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }.getOrNull()
                ?.lowercase(Locale.ROOT)
            require(!ascii.isNullOrEmpty() && ascii.length <= 253 && ascii == ascii.trimEnd('.') &&
                ascii.split('.').all { label ->
                    label.isNotEmpty() && label.length <= 63 &&
                        (!label.startsWith("xn--") || run {
                            val decoded = IDN.toUnicode(label, IDN.USE_STD3_ASCII_RULES)
                            decoded != label && runCatching {
                                IDN.toASCII(decoded, IDN.USE_STD3_ASCII_RULES)
                                    .lowercase(Locale.ROOT) == label
                            }.getOrDefault(false)
                        })
                } &&
                ascii.any(Char::isLetter)
            ) { "NTRIP DNS host is invalid" }
            return NtripEndpoint(ascii, port, "$ascii:$port", ascii)
        }
    }
}

/** Constructed before any NTRIP request or socket. */
data class NtripEndpointSecurityPolicy(
    val endpoint: NtripEndpoint,
    val transport: NtripTransportMode,
    val verification: NtripTlsVerification,
    val allowInsecure: Boolean,
    val unsafeAcknowledged: Boolean,
) {
    init {
        when (transport) {
            NtripTransportMode.PLAINTEXT -> {
                require(verification == NtripTlsVerification.SystemTrust) { "Plaintext cannot select TLS verification" }
            }
            NtripTransportMode.TLS -> require(verification == NtripTlsVerification.SystemTrust) {
                "Unsafe TLS is no longer supported. Choose system-trusted TLS or explicit plaintext in profile settings."
            }
        }
    }

    companion object {
        fun systemTrust(host: String, port: Int): NtripEndpointSecurityPolicy =
            NtripEndpointSecurityPolicy(NtripEndpoint.parse(host, port), NtripTransportMode.TLS,
                NtripTlsVerification.SystemTrust, allowInsecure = false, unsafeAcknowledged = false)
    }
}
