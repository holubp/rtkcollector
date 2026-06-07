package org.rtkcollector.core.correction

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

data class NtripCredentials(
    val username: String,
    val password: String,
)

enum class NtripProtocolVersion {
    NTRIP_V2,
    NTRIP_V1,
}

data class NtripRequest(
    val host: String,
    val port: Int,
    val mountpoint: String,
    val credentials: NtripCredentials? = null,
    val userAgent: String = "RtkCollector/0.1",
    val protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
) {
    init {
        require(host.isNotBlank()) { "NTRIP host must not be blank" }
        require(port in 1..65535) { "NTRIP port must be between 1 and 65535" }
        require(mountpoint.isNotBlank()) { "NTRIP mountpoint must not be blank" }
        require(userAgent.isNotBlank()) { "NTRIP user agent must not be blank" }
        requireNoCrLf("host", host)
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
            add("Host: $host:$port")
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
    val host: String,
    val port: Int,
    val credentials: NtripCredentials? = null,
    val userAgent: String = "RtkCollector/0.1",
    val protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
) {
    init {
        require(host.isNotBlank()) { "NTRIP host must not be blank" }
        require(port in 1..65535) { "NTRIP port must be between 1 and 65535" }
        require(userAgent.isNotBlank()) { "NTRIP user agent must not be blank" }
        requireNoCrLf("host", host)
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
            add("Host: $host:$port")
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
        val socket = connector.connect(request.host, request.port)
        return socket.use {
            socket.output.write(request.render().toByteArray(Charsets.US_ASCII))
            socket.output.flush()
            val rawText = socket.input.readSourcetableBytes(MAX_SOURCETABLE_BYTES).toString(Charsets.ISO_8859_1)
            NtripSourcetableResult(
                mountpoints = NtripSourcetableParser.mountpoints(rawText),
                rawText = rawText,
            )
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
    fun connect(host: String, port: Int): NtripSocket
}

class JavaNtripSocketConnector : NtripSocketConnector {
    override fun connect(host: String, port: Int): NtripSocket {
        val socket = Socket().apply {
            connect(InetSocketAddress(host, port), DEFAULT_CONNECT_TIMEOUT_MILLIS)
            soTimeout = DEFAULT_SOCKET_TIMEOUT_MILLIS
        }
        return object : NtripSocket {
            override val input: InputStream = socket.getInputStream()
            override val output: OutputStream = socket.getOutputStream()

            override fun close() {
                socket.close()
            }
        }
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

    fun cancel() {
        cancelled.set(true)
        runCatching { activeSocket?.close() }
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
            connector.connect(activeRequest.host, activeRequest.port)
        } catch (exception: Exception) {
            return failure(
                kind = NtripFailureKind.CONNECT_FAILED,
                state = NtripConnectionState.CONNECTING,
                message = "Failed to connect to NTRIP caster ${activeRequest.host}:${activeRequest.port}",
                cause = exception,
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
                val bytesRead = streamPayload(header.payload, socket.input, onRtcmBytes)
                NtripConnectionResult.Completed(bytesRead)
            } catch (exception: Exception) {
                failure(
                    kind = NtripFailureKind.STREAM_FAILED,
                    state = NtripConnectionState.STREAMING,
                    message = exception.message ?: "NTRIP stream failed",
                    cause = exception,
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
                            delay(reconnectPolicy.delayMillis)
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
                message = "NTRIP caster rejected credentials: $firstLine",
            )
            firstLine.startsWith("HTTP/", ignoreCase = true) && firstLine.contains(" 403 ") -> NtripFailure(
                kind = NtripFailureKind.AUTHORIZATION_FAILED,
                state = NtripConnectionState.AUTHENTICATING,
                message = "NTRIP caster denied access to mountpoint: $firstLine",
            )
            else -> NtripFailure(
                kind = NtripFailureKind.UNSUPPORTED_RESPONSE,
                state = NtripConnectionState.AUTHENTICATING,
                message = "NTRIP caster response is not ICY 200 or HTTP 200: $firstLine",
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
            (failure.message.contains(" 505 ") || failure.message.contains("Version", ignoreCase = true))

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
    )

    private companion object {
        val CRLF = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
        val HEADER_TERMINATOR = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        const val DEFAULT_BUFFER_SIZE = 4096
    }
}
