package org.rtkcollector.core.correction

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom

data class NtripCredentials(
    val username: String,
    val password: String,
)

enum class NtripProtocolVersion {
    NTRIP_V2,
    NTRIP_V1,
}

const val DEFAULT_NTRIP_USER_AGENT: String = "NTRIP RtkCollector/1.0-RC1"

data class NtripRequest(
    val policy: NtripEndpointSecurityPolicy,
    val mountpoint: String,
    val credentials: NtripCredentials? = null,
    val userAgent: String = DEFAULT_NTRIP_USER_AGENT,
    val protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
) {
    val host: String get() = policy.endpoint.host
    val port: Int get() = policy.endpoint.port
    init {
        require(mountpoint.isNotBlank()) { "NTRIP mountpoint must not be blank" }
        require(userAgent.isNotBlank()) { "NTRIP user agent must not be blank" }
        requireNoCrLf("mountpoint", mountpoint)
        requireNoCrLf("userAgent", userAgent)
    }

    fun render(): String {
        val path = mountpoint.trimStart('/')
        val lines = buildList {
            add(
                when (protocolVersion) {
                    NtripProtocolVersion.NTRIP_V2 -> "GET /$path HTTP/1.1"
                    NtripProtocolVersion.NTRIP_V1 -> "GET /$path HTTP/1.0"
                },
            )
            add("Host: ${policy.endpoint.hostHeader}")
            add("User-Agent: $userAgent")
            add(
                when (protocolVersion) {
                    NtripProtocolVersion.NTRIP_V2 -> "Ntrip-Version: Ntrip/2.0"
                    NtripProtocolVersion.NTRIP_V1 -> "Ntrip-Version: Ntrip/1.0"
                },
            )
            add("Connection: close")
            credentials?.let { add("Authorization: Basic ${basicAuthToken(it)}") }
        }

        return lines.joinToString(separator = "\r\n", postfix = "\r\n\r\n")
    }

    fun toRedactedMetadata(): NtripRedactedMetadata = NtripRedactedMetadata(
        host = host,
        port = port,
        mountpoint = mountpoint.trimStart('/'),
        username = credentials?.username,
        authentication = if (credentials == null) "none" else "basic:redacted",
    )

    fun withProtocolVersion(version: NtripProtocolVersion): NtripRequest =
        copy(protocolVersion = version)

}

data class NtripRedactedMetadata(
    val host: String,
    val port: Int,
    val mountpoint: String,
    val username: String? = null,
    val authentication: String,
)

data class NtripSourcetableRequest(
    val policy: NtripEndpointSecurityPolicy,
    val credentials: NtripCredentials? = null,
    val userAgent: String = DEFAULT_NTRIP_USER_AGENT,
    val protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
) {
    val host: String get() = policy.endpoint.host
    val port: Int get() = policy.endpoint.port
    init {
        require(userAgent.isNotBlank()) { "NTRIP user agent must not be blank" }
        requireNoCrLf("userAgent", userAgent)
    }

    fun render(): String {
        val lines = buildList {
            add(
                when (protocolVersion) {
                    NtripProtocolVersion.NTRIP_V2 -> "GET / HTTP/1.1"
                    NtripProtocolVersion.NTRIP_V1 -> "GET / HTTP/1.0"
                },
            )
            add("Host: ${policy.endpoint.hostHeader}")
            add("User-Agent: $userAgent")
            add(
                when (protocolVersion) {
                    NtripProtocolVersion.NTRIP_V2 -> "Ntrip-Version: Ntrip/2.0"
                    NtripProtocolVersion.NTRIP_V1 -> "Ntrip-Version: Ntrip/1.0"
                },
            )
            add("Connection: close")
            credentials?.let { add("Authorization: Basic ${basicAuthToken(it)}") }
        }

        return lines.joinToString(separator = "\r\n", postfix = "\r\n\r\n")
    }
}

data class NtripSourcetableResult(
    val mountpoints: List<String>,
    val rawText: String,
)

object NtripSourcetableParser {
    fun mountpoints(sourcetableText: String): List<String> {
        val seen = linkedSetOf<String>()
        sourcetableText.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("STR;", ignoreCase = true) }
            .mapNotNull { line -> line.split(';').getOrNull(1)?.trim()?.takeIf(String::isNotBlank) }
            .forEach(seen::add)
        return seen.toList()
    }
}

class NtripSourcetableClient(
    private val request: NtripSourcetableRequest,
    private val connector: NtripSocketConnector = JavaNtripSocketConnector(),
) {
    fun fetch(): NtripSourcetableResult {
        try {
            val socket = connector.connect(request.policy)
            return socket.use {
                socket.output.write(request.render().toByteArray(Charsets.US_ASCII))
                socket.output.flush()
                val rawText = socket.input.readSourcetableBytes(MAX_SOURCETABLE_BYTES).toString(Charsets.ISO_8859_1)
                NtripSourcetableResult(
                    mountpoints = NtripSourcetableParser.mountpoints(rawText),
                    rawText = rawText,
                )
            }
        } catch (_: Exception) {
            throw IOException("NTRIP sourcetable fetch failed")
        }
    }

    private fun InputStream.readSourcetableBytes(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (output.size() < maxBytes) {
            val count = read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
            if (count == -1) break
            output.write(buffer, 0, count)
            if (output.toString(Charsets.ISO_8859_1.name()).contains("ENDSOURCETABLE", ignoreCase = true)) break
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_SOURCETABLE_BYTES = 1024 * 1024
        const val DEFAULT_BUFFER_SIZE = 4096
    }
}

interface NtripSocket : Closeable {
    val input: InputStream
    val output: OutputStream
}

private fun basicAuthToken(credentials: NtripCredentials): String {
    val rawCredentials = "${credentials.username}:${credentials.password}".toByteArray(Charsets.UTF_8)
    return Base64.getEncoder().encodeToString(rawCredentials)
}

private fun requireNoCrLf(label: String, value: String) {
    require('\r' !in value && '\n' !in value) { "NTRIP $label must not contain CR/LF characters" }
}

interface NtripSocketConnector {
    fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket
}

class JavaNtripSocketConnector internal constructor(
    private val systemSocketFactory: SSLSocketFactory,
    private val rawSocketFactory: () -> Socket = ::Socket,
) : NtripSocketConnector {
    constructor() : this(SSLSocketFactory.getDefault() as SSLSocketFactory)
    private fun connectPlaintext(host: String, port: Int): NtripSocket {
        val socket = connectTcp(host, port)
        try {
            return object : NtripSocket {
                override val input: InputStream = socket.getInputStream()
                override val output: OutputStream = socket.getOutputStream()

                override fun close() {
                    socket.close()
                }
            }
        } catch (exception: Exception) {
            socket.close()
            throw exception
        }
    }

    private fun connectTcp(host: String, port: Int): Socket {
        val socket = rawSocketFactory()
        try {
            socket.connect(InetSocketAddress(host, port), DEFAULT_CONNECT_TIMEOUT_MILLIS)
            socket.soTimeout = DEFAULT_SOCKET_TIMEOUT_MILLIS
            return socket
        } catch (exception: Exception) {
            socket.close()
            throw exception
        }
    }

    override fun connect(policy: NtripEndpointSecurityPolicy): NtripSocket {
        val host = policy.endpoint.host
        val port = policy.endpoint.port
        if (policy.transport == NtripTransportMode.PLAINTEXT) {
            return connectPlaintext(host, port)
        }
        val rawSocket = connectTcp(host, port)
        try {
            val socket = (socketFactory(policy).createSocket(rawSocket, host, port, true) as SSLSocket).apply {
                soTimeout = DEFAULT_SOCKET_TIMEOUT_MILLIS
                enabledProtocols = supportedProtocols.filter { protocol ->
                    protocol == "TLSv1.2" || protocol == "TLSv1.3"
                }.also { require(it.isNotEmpty()) { "TLS 1.2 or newer is unavailable" } }.toTypedArray()
                sslParameters = sslParameters.apply {
                    if (policy.verification != NtripTlsVerification.Unsafe) {
                        endpointIdentificationAlgorithm = "HTTPS"
                    }
                    serverNames = policy.endpoint.sniName?.let { listOf(SNIHostName(it)) } ?: emptyList()
                }
                startHandshake()
                require(session.protocol == "TLSv1.2" || session.protocol == "TLSv1.3") {
                    "NTRIP TLS negotiated an unsupported protocol"
                }
            }
            return object : NtripSocket {
                override val input: InputStream = socket.inputStream
                override val output: OutputStream = socket.outputStream

                override fun close() {
                    socket.close()
                }
            }
        } catch (exception: Exception) {
            rawSocket.close()
            throw exception
        }
    }

    private fun socketFactory(policy: NtripEndpointSecurityPolicy): SSLSocketFactory = when (policy.verification) {
        NtripTlsVerification.SystemTrust -> systemSocketFactory
        NtripTlsVerification.Unsafe -> SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(UnsafeTrustManager), SecureRandom())
        }.socketFactory
    }

    private data object UnsafeTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000
        const val DEFAULT_SOCKET_TIMEOUT_MILLIS = 15_000
    }
}

data class NtripReconnectPolicy(
    val maxAttempts: Int = 1,
    val delayMillis: Long = 1_000,
) {
    init {
        require(maxAttempts >= 1) { "NTRIP reconnect maxAttempts must be at least 1" }
        require(delayMillis >= 0) { "NTRIP reconnect delayMillis must not be negative" }
    }
}

enum class NtripFailureKind {
    CONNECT_FAILED,
    CANCELLED,
    EMPTY_RESPONSE,
    SOURCETABLE_RESPONSE,
    AUTHENTICATION_FAILED,
    AUTHORIZATION_FAILED,
    UNSUPPORTED_RESPONSE,
    STREAM_FAILED,
}

data class NtripFailure(
    val kind: NtripFailureKind,
    val message: String,
    val state: NtripConnectionState,
    val cause: Throwable? = null,
)

sealed class NtripConnectionResult {
    data class Completed(val bytesRead: Long) : NtripConnectionResult()
    data class Failure(val failure: NtripFailure) : NtripConnectionResult()
}

class NtripClient(
    private val request: NtripRequest,
    private val connector: NtripSocketConnector = JavaNtripSocketConnector(),
    private val reconnectPolicy: NtripReconnectPolicy = NtripReconnectPolicy(),
    private val delay: (Long) -> Unit = { Thread.sleep(it) },
) {
    private val cancelled = AtomicBoolean(false)
    @Volatile
    private var activeSocket: NtripSocket? = null
    @Volatile
    private var reconnectDelayThread: Thread? = null

    fun cancel() {
        cancelled.set(true)
        runCatching { activeSocket?.close() }
        reconnectDelayThread?.interrupt()
    }

    fun connectOnce(
        ggaLines: Iterable<String> = emptyList(),
        onState: (CorrectionStatus) -> Unit = {},
        onRtcmBytes: (ByteArray) -> Unit = {},
    ): NtripConnectionResult =
        connectOnceWithRequest(
            activeRequest = request,
            ggaLines = ggaLines,
            onState = onState,
            onRtcmBytes = onRtcmBytes,
            allowCompatibilityFallback = true,
        )

    private fun connectOnceWithRequest(
        activeRequest: NtripRequest,
        ggaLines: Iterable<String> = emptyList(),
        onState: (CorrectionStatus) -> Unit = {},
        onRtcmBytes: (ByteArray) -> Unit = {},
        allowCompatibilityFallback: Boolean,
    ): NtripConnectionResult {
        if (cancelled.get()) {
            onState(CorrectionStatus(NtripConnectionState.STOPPED))
            return stoppedBeforeConnection()
        }
        onState(CorrectionStatus(NtripConnectionState.CONNECTING))
        val socket = try {
            connector.connect(activeRequest.policy)
        } catch (exception: Exception) {
            return failure(
                kind = NtripFailureKind.CONNECT_FAILED,
                state = NtripConnectionState.CONNECTING,
                message = "Failed to connect to NTRIP caster ${activeRequest.host}:${activeRequest.port}",
                onState = onState,
            )
        }

        activeSocket = socket
        return socket.use {
            try {
                writeRequestAndGga(socket.output, activeRequest, ggaLines)
                onState(CorrectionStatus(NtripConnectionState.AUTHENTICATING))
                val header = readHeader(socket.input)
                val accepted = evaluateHeader(header.text)
                if (accepted != null) {
                    if (allowCompatibilityFallback && activeRequest.shouldTryCompatibilityFallback(accepted)) {
                        return@use connectOnceWithRequest(
                            activeRequest = activeRequest.withProtocolVersion(NtripProtocolVersion.NTRIP_V1),
                            ggaLines = ggaLines,
                            onState = onState,
                            onRtcmBytes = onRtcmBytes,
                            allowCompatibilityFallback = false,
                        )
                    }
                    onState(CorrectionStatus(accepted.state, lastError = accepted.message))
                    return@use NtripConnectionResult.Failure(accepted)
                }

                onState(CorrectionStatus(NtripConnectionState.STREAMING))
                val bytesRead = if (header.isChunkedTransferEncoding()) {
                    streamChunkedPayload(header.payload, socket.input, onRtcmBytes)
                } else {
                    streamPayload(header.payload, socket.input, onRtcmBytes)
                }
                NtripConnectionResult.Completed(bytesRead)
            } catch (exception: Exception) {
                failure(
                    kind = NtripFailureKind.STREAM_FAILED,
                    state = NtripConnectionState.STREAMING,
                    message = "NTRIP stream failed",
                    onState = onState,
                )
            } finally {
                activeSocket = null
            }
        }
    }

    fun runWithReconnect(
        ggaLines: Iterable<String> = emptyList(),
        onState: (CorrectionStatus) -> Unit = {},
        onRtcmBytes: (ByteArray) -> Unit = {},
    ): NtripConnectionResult {
        var lastFailure: NtripConnectionResult.Failure? = null
        repeat(reconnectPolicy.maxAttempts) { attemptIndex ->
            if (cancelled.get()) {
                onState(CorrectionStatus(NtripConnectionState.STOPPED, lastError = lastFailure?.failure?.message))
                return lastFailure ?: stoppedBeforeConnection()
            }
            val result = connectOnce(ggaLines = ggaLines, onState = onState, onRtcmBytes = onRtcmBytes)
            when (result) {
                is NtripConnectionResult.Completed -> {
                    if (attemptIndex < reconnectPolicy.maxAttempts - 1 && !cancelled.get()) {
                        lastFailure = NtripConnectionResult.Failure(
                            NtripFailure(
                                kind = NtripFailureKind.STREAM_FAILED,
                                state = NtripConnectionState.STREAMING,
                                message = "NTRIP stream ended unexpectedly after ${result.bytesRead} bytes",
                            ),
                        )
                        onState(
                            CorrectionStatus(
                                NtripConnectionState.RECONNECT_WAIT,
                                lastError = "NTRIP stream ended unexpectedly after ${result.bytesRead} bytes",
                            ),
                        )
                        try {
                            waitBeforeReconnect(reconnectPolicy.delayMillis)
                        } catch (exception: InterruptedException) {
                            Thread.currentThread().interrupt()
                            onState(CorrectionStatus(NtripConnectionState.STOPPED))
                            return cancelledResult(exception)
                        }
                        return@repeat
                    }
                    onState(CorrectionStatus(NtripConnectionState.STOPPED))
                    return result
                }
                is NtripConnectionResult.Failure -> {
                    lastFailure = result
                    if (!result.failure.isRetryable()) {
                        onState(CorrectionStatus(NtripConnectionState.STOPPED, lastError = result.failure.message))
                        return result
                    }
                    if (attemptIndex < reconnectPolicy.maxAttempts - 1) {
                        onState(CorrectionStatus(NtripConnectionState.RECONNECT_WAIT, lastError = result.failure.message))
                        try {
                            waitBeforeReconnect(reconnectPolicy.delayMillis)
                        } catch (exception: InterruptedException) {
                            Thread.currentThread().interrupt()
                            onState(CorrectionStatus(NtripConnectionState.STOPPED))
                            return cancelledResult(exception)
                        }
                    }
                }
            }
        }

        onState(CorrectionStatus(NtripConnectionState.STOPPED, lastError = lastFailure?.failure?.message))
        return lastFailure ?: NtripConnectionResult.Failure(
            NtripFailure(
                kind = NtripFailureKind.CONNECT_FAILED,
                state = NtripConnectionState.STOPPED,
                message = "NTRIP client stopped without attempting a connection",
            ),
        )
    }

    private fun waitBeforeReconnect(delayMillis: Long) {
        val currentThread = Thread.currentThread()
        reconnectDelayThread = currentThread
        try {
            if (!cancelled.get()) {
                delay(delayMillis)
            }
        } finally {
            if (reconnectDelayThread === currentThread) {
                reconnectDelayThread = null
            }
        }
    }

    private fun writeRequestAndGga(output: OutputStream, activeRequest: NtripRequest, ggaLines: Iterable<String>) {
        output.write(activeRequest.render().toByteArray(Charsets.US_ASCII))
        ggaLines.forEach { line ->
            output.write(line.trimEnd('\r', '\n').toByteArray(Charsets.US_ASCII))
            output.write(CRLF)
        }
        output.flush()
    }

    private fun readHeader(input: InputStream): NtripHeader {
        val bytes = ByteArrayOutputStream()
        var matched = 0
        while (true) {
            val next = input.read()
            if (next == -1) {
                if (bytes.size() == 0) {
                    return NtripHeader(text = "", payload = ByteArray(0))
                }
                break
            }

            bytes.write(next)
            matched = if (next.toByte() == HEADER_TERMINATOR[matched]) matched + 1 else if (next == '\r'.code) 1 else 0
            if (matched == HEADER_TERMINATOR.size) break
        }

        val headerBytes = bytes.toByteArray()
        return NtripHeader(text = headerBytes.toString(Charsets.ISO_8859_1), payload = ByteArray(0))
    }

    private fun evaluateHeader(headerText: String): NtripFailure? {
        val firstLine = headerText.lineSequence().firstOrNull().orEmpty()
        return when {
            firstLine.isBlank() -> NtripFailure(
                kind = NtripFailureKind.EMPTY_RESPONSE,
                state = NtripConnectionState.AUTHENTICATING,
                message = "NTRIP caster returned an empty response",
            )
            firstLine.startsWith("SOURCETABLE", ignoreCase = true) -> NtripFailure(
                kind = NtripFailureKind.SOURCETABLE_RESPONSE,
                state = NtripConnectionState.AUTHENTICATING,
                message = "NTRIP caster returned a sourcetable instead of an RTCM stream",
            )
            firstLine.startsWith("ICY 200", ignoreCase = true) -> null
            firstLine.startsWith("HTTP/", ignoreCase = true) && firstLine.contains(" 200 ") -> null
            firstLine.startsWith("HTTP/", ignoreCase = true) && firstLine.contains(" 401 ") -> NtripFailure(
                kind = NtripFailureKind.AUTHENTICATION_FAILED,
                state = NtripConnectionState.AUTHENTICATING,
                message = "NTRIP caster rejected credentials",
            )
            firstLine.startsWith("HTTP/", ignoreCase = true) && firstLine.contains(" 403 ") -> NtripFailure(
                kind = NtripFailureKind.AUTHORIZATION_FAILED,
                state = NtripConnectionState.AUTHENTICATING,
                message = "NTRIP caster denied access to mountpoint",
            )
            else -> NtripFailure(
                kind = NtripFailureKind.UNSUPPORTED_RESPONSE,
                state = NtripConnectionState.AUTHENTICATING,
                message = if (firstLine.startsWith("HTTP/", ignoreCase = true) && firstLine.contains(" 505 ")) {
                    "NTRIP caster response is not ICY 200 or HTTP 200 (HTTP 505)"
                } else if (firstLine.contains("Version", ignoreCase = true)) {
                    "NTRIP caster response indicates protocol Version incompatibility"
                } else {
                    "NTRIP caster response is not ICY 200 or HTTP 200"
                },
            )
        }
    }

    private fun streamPayload(
        initialPayload: ByteArray,
        input: InputStream,
        onRtcmBytes: (ByteArray) -> Unit,
    ): Long {
        var bytesRead = 0L
        if (initialPayload.isNotEmpty()) {
            onRtcmBytes(initialPayload)
            bytesRead += initialPayload.size
        }

        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (!cancelled.get()) {
            val count = input.read(buffer)
            if (count == -1) break
            val chunk = buffer.copyOf(count)
            onRtcmBytes(chunk)
            bytesRead += count
        }
        return bytesRead
    }

    private fun streamChunkedPayload(
        initialPayload: ByteArray,
        input: InputStream,
        onRtcmBytes: (ByteArray) -> Unit,
    ): Long {
        require(initialPayload.isEmpty()) { "Chunked NTRIP response unexpectedly included payload bytes with headers." }
        var bytesRead = 0L
        while (!cancelled.get()) {
            val sizeLine = input.readAsciiLine() ?: break
            val chunkSize = sizeLine
                .substringBefore(';')
                .trim()
                .toIntOrNull(radix = 16)
                ?: error("Invalid chunk size in NTRIP stream: $sizeLine")
            if (chunkSize == 0) {
                input.discardChunkTrailers()
                break
            }
            val chunk = input.readExact(chunkSize)
            input.expectCrLf()
            onRtcmBytes(chunk)
            bytesRead += chunk.size
        }
        return bytesRead
    }

    private fun InputStream.readAsciiLine(): String? {
        val line = ByteArrayOutputStream()
        var previous = -1
        while (true) {
            val next = read()
            if (next == -1) {
                return if (line.size() == 0 && previous == -1) null else line.toString(Charsets.US_ASCII.name())
            }
            if (previous == '\r'.code && next == '\n'.code) {
                val bytes = line.toByteArray()
                return bytes.copyOf(bytes.size - 1).toString(Charsets.US_ASCII)
            }
            line.write(next)
            previous = next
        }
    }

    private fun InputStream.discardChunkTrailers() {
        while (true) {
            val line = readAsciiLine() ?: return
            if (line.isEmpty()) return
        }
    }

    private fun InputStream.readExact(byteCount: Int): ByteArray {
        val output = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val count = read(output, offset, byteCount - offset)
            if (count == -1) {
                error("NTRIP chunk ended before $byteCount bytes were read.")
            }
            offset += count
        }
        return output
    }

    private fun InputStream.expectCrLf() {
        val cr = read()
        val lf = read()
        require(cr == '\r'.code && lf == '\n'.code) { "NTRIP chunk was not terminated with CR/LF." }
    }

    private fun stoppedBeforeConnection(): NtripConnectionResult.Failure =
        NtripConnectionResult.Failure(
            NtripFailure(
                kind = NtripFailureKind.CANCELLED,
                state = NtripConnectionState.STOPPED,
                message = "NTRIP client was cancelled before connection completed",
            ),
        )

    private fun NtripRequest.shouldTryCompatibilityFallback(failure: NtripFailure): Boolean =
        protocolVersion == NtripProtocolVersion.NTRIP_V2 &&
            failure.kind == NtripFailureKind.UNSUPPORTED_RESPONSE &&
            (failure.message.contains("(HTTP 505)") || failure.message.contains("protocol Version incompatibility"))

    private fun NtripFailure.isRetryable(): Boolean =
        kind !in setOf(
            NtripFailureKind.CANCELLED,
            NtripFailureKind.AUTHENTICATION_FAILED,
            NtripFailureKind.AUTHORIZATION_FAILED,
        )

    private fun cancelledResult(cause: Throwable? = null): NtripConnectionResult.Failure =
        NtripConnectionResult.Failure(
            NtripFailure(
                kind = NtripFailureKind.CANCELLED,
                state = NtripConnectionState.STOPPED,
                message = "NTRIP client was cancelled",
                cause = cause,
            ),
        )

    private fun failure(
        kind: NtripFailureKind,
        state: NtripConnectionState,
        message: String,
        cause: Throwable? = null,
        onState: (CorrectionStatus) -> Unit,
    ): NtripConnectionResult.Failure {
        onState(CorrectionStatus(state, lastError = message))
        return NtripConnectionResult.Failure(NtripFailure(kind = kind, message = message, state = state, cause = cause))
    }

    private data class NtripHeader(
        val text: String,
        val payload: ByteArray,
    ) {
        fun isChunkedTransferEncoding(): Boolean =
            text.lineSequence().any { line ->
                line.substringBefore(':').equals("Transfer-Encoding", ignoreCase = true) &&
                    line.substringAfter(':', "").split(',').any { value ->
                        value.trim().equals("chunked", ignoreCase = true)
                    }
            }
    }

    private companion object {
        val CRLF = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
        val HEADER_TERMINATOR = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        const val DEFAULT_BUFFER_SIZE = 4096
    }
}
