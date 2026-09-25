package org.rtkcollector.core.correction

import java.security.cert.CertificateException
import javax.net.ssl.SSLException

internal fun ntripTlsFailureMessage(error: Throwable): String? {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    return when {
        causes.any { it is CertificateException } ->
            "NTRIP TLS certificate or hostname verification failed."
        causes.any { it is SSLException } ->
            "NTRIP TLS handshake failed. Check the caster certificate, hostname and TLS settings."
        else -> null
    }
}
