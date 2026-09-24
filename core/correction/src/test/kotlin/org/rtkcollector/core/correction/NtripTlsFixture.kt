package org.rtkcollector.core.correction

import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

internal object NtripTlsFixture {
    fun trustedFactory(): SSLSocketFactory {
        val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        val certificate = javaClass.getResourceAsStream("/tls/localhost.crt")!!.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        trust.setCertificateEntry("local", certificate)
        val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
        return SSLContext.getInstance("TLS").apply { init(null, managers.trustManagers, null) }.socketFactory
    }

    class Server(private val count: Int = 1, private val handle: (SSLSocket) -> Unit) : AutoCloseable {
        private val socket: SSLServerSocket
        private val done = CompletableFuture<Unit>()
        val port: Int get() = socket.localPort

        init {
            val keys = KeyStore.getInstance("PKCS12").apply {
                NtripTlsFixture::class.java.getResourceAsStream("/tls/localhost.p12")!!.use {
                    load(it, "test-only".toCharArray())
                }
            }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keys, "test-only".toCharArray())
            }
            val context = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
            socket = context.serverSocketFactory.createServerSocket(0, 10, InetAddress.getByName("0.0.0.0")) as SSLServerSocket
            socket.soTimeout = 5_000
            Thread {
                try {
                    repeat(count) {
                        (socket.accept() as SSLSocket).use { peer ->
                            peer.soTimeout = 5_000
                            handle(peer)
                        }
                    }
                    done.complete(Unit)
                } catch (error: Throwable) {
                    done.completeExceptionally(error)
                }
            }.apply { isDaemon = true; start() }
        }

        fun await() { done.get(6, TimeUnit.SECONDS) }
        override fun close() { socket.close() }
    }
}
