package org.rtkcollector.core.correction

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

data class NtripCasterUploadRequest(
    val host: String,
    val port: Int,
    val mountpoint: String,
    val credentials: NtripCredentials?,
    val userAgent: String = DEFAULT_NTRIP_USER_AGENT,
    val protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
) {
    init {
        require(host.isNotBlank()) { "NTRIP caster upload host must not be blank" }
        require(port in 1..65535) { "NTRIP caster upload port must be between 1 and 65535" }
        require(mountpoint.isNotBlank()) { "NTRIP caster upload mountpoint must not be blank" }
        require(userAgent.isNotBlank()) { "NTRIP caster upload user agent must not be blank" }
        requireNoUploadCrLf("host", host)
        requireNoUploadCrLf("mountpoint", mountpoint)
        requireNoUploadCrLf("userAgent", userAgent)
        credentials?.let {
            requireNoUploadCrLf("username", it.username)
            requireNoUploadCrLf("password", it.password)
        }
    }

    fun render(): String {
        val path = mountpoint.trimStart('/')
        val sourcePassword = credentials?.password.orEmpty()
        val lines = buildList {
            add(
                when (protocolVersion) {
                    NtripProtocolVersion.NTRIP_V2 -> "SOURCE $sourcePassword /$path HTTP/1.1"
                    NtripProtocolVersion.NTRIP_V1 -> "SOURCE $sourcePassword /$path HTTP/1.0"
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
            credentials?.let { add("Authorization: Basic ${uploadBasicAuthToken(it)}") }
        }
        return lines.joinToString(separator = "\r\n", postfix = "\r\n\r\n")
    }
}

enum class NtripCasterUploadFailureKind {
    CONNECT_FAILED,
    CANCELLED,
    EMPTY_RESPONSE,
    AUTHENTICATION_FAILED,
    AUTHORIZATION_FAILED,
    UNSUPPORTED_RESPONSE,
    STREAM_FAILED,
}

data class NtripCasterUploadFailure(
    val kind: NtripCasterUploadFailureKind,
    val message: String,
    val state: NtripConnectionState,
    val cause: Throwable? = null,
)

sealed class NtripCasterUploadResult {
    data class Completed(val bytesUploaded: Long) : NtripCasterUploadResult()
    data class Failure(val failure: NtripCasterUploadFailure) : NtripCasterUploadResult()
}

class NtripCasterUploadClient(
    private val request: NtripCasterUploadRequest,
    private val connector: NtripSocketConnector = JavaNtripSocketConnector(),
) {
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var activeSocket: NtripSocket? = null

    fun connectOnce(
        onState: (NtripConnectionState) -> Unit = {},
        writeRtcmBytes: (OutputStream) -> Unit,
    ): NtripCasterUploadResult {
        if (cancelled.get()) {
            onState(NtripConnectionState.STOPPED)
            return stoppedFailure()
        }
        onState(NtripConnectionState.CONNECTING)
        val socket = runCatching { connector.connect(request.host, request.port) }
            .getOrElse {
                return NtripCasterUploadResult.Failure(
                    NtripCasterUploadFailure(
                        kind = NtripCasterUploadFailureKind.CONNECT_FAILED,
                        message = it.message ?: "NTRIP caster upload connection failed.",
                        state = NtripConnectionState.CONNECTING,
                        cause = it,
                    ),
                )
            }

        activeSocket = socket
        return socket.use {
            onState(NtripConnectionState.AUTHENTICATING)
            socket.output.write(request.render().toByteArray(Charsets.US_ASCII))
            socket.output.flush()
            val response = socket.input.readHeaderText()
            classifyResponse(response)?.let { failure ->
                return NtripCasterUploadResult.Failure(failure)
            }
            if (cancelled.get()) {
                onState(NtripConnectionState.STOPPED)
                return stoppedFailure()
            }
            onState(NtripConnectionState.STREAMING)
            val counting = CountingOutputStream(socket.output)
            runCatching {
                writeRtcmBytes(counting)
                counting.flush()
            }.fold(
                onSuccess = { NtripCasterUploadResult.Completed(counting.bytesWritten) },
                onFailure = {
                    NtripCasterUploadResult.Failure(
                        NtripCasterUploadFailure(
                            kind = if (cancelled.get()) {
                                NtripCasterUploadFailureKind.CANCELLED
                            } else {
                                NtripCasterUploadFailureKind.STREAM_FAILED
                            },
                            message = it.message ?: "NTRIP caster upload stream failed.",
                            state = if (cancelled.get()) NtripConnectionState.STOPPED else NtripConnectionState.STREAMING,
                            cause = it,
                        ),
                    )
                },
            )
        }.also {
            activeSocket = null
        }
    }

    fun cancel() {
        cancelled.set(true)
        activeSocket?.close()
    }

    private fun classifyResponse(response: String): NtripCasterUploadFailure? {
        if (response.isBlank()) {
            return NtripCasterUploadFailure(
                kind = NtripCasterUploadFailureKind.EMPTY_RESPONSE,
                message = "NTRIP caster upload returned an empty response.",
                state = NtripConnectionState.AUTHENTICATING,
            )
        }
        val firstLine = response.lineSequence().firstOrNull().orEmpty()
        if (
            firstLine.startsWith("ICY 200", ignoreCase = true) ||
            firstLine.startsWith("HTTP/1.1 200", ignoreCase = true) ||
            firstLine.startsWith("HTTP/1.0 200", ignoreCase = true)
        ) {
            return null
        }
        val kind = when {
            firstLine.contains("401") -> NtripCasterUploadFailureKind.AUTHENTICATION_FAILED
            firstLine.contains("403") -> NtripCasterUploadFailureKind.AUTHORIZATION_FAILED
            else -> NtripCasterUploadFailureKind.UNSUPPORTED_RESPONSE
        }
        return NtripCasterUploadFailure(
            kind = kind,
            message = "NTRIP caster upload rejected SOURCE request: $firstLine",
            state = NtripConnectionState.AUTHENTICATING,
        )
    }

    private fun stoppedFailure(): NtripCasterUploadResult.Failure =
        NtripCasterUploadResult.Failure(
            NtripCasterUploadFailure(
                kind = NtripCasterUploadFailureKind.CANCELLED,
                message = "NTRIP caster upload was cancelled.",
                state = NtripConnectionState.STOPPED,
            ),
        )
}

private class CountingOutputStream(
    output: OutputStream,
) : FilterOutputStream(output) {
    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        bytesWritten += 1
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        out.write(buffer, offset, length)
        bytesWritten += length.toLong()
    }
}

private fun java.io.InputStream.readHeaderText(maxBytes: Int = 16 * 1024): String {
    val output = java.io.ByteArrayOutputStream()
    while (output.size() < maxBytes) {
        val next = read()
        if (next < 0) break
        output.write(next)
        val bytes = output.toByteArray()
        if (
            bytes.size >= 4 &&
            bytes[bytes.size - 4] == '\r'.code.toByte() &&
            bytes[bytes.size - 3] == '\n'.code.toByte() &&
            bytes[bytes.size - 2] == '\r'.code.toByte() &&
            bytes[bytes.size - 1] == '\n'.code.toByte()
        ) {
            break
        }
    }
    return output.toString(Charsets.ISO_8859_1.name())
}

private fun uploadBasicAuthToken(credentials: NtripCredentials): String {
    val rawCredentials = "${credentials.username}:${credentials.password}".toByteArray(Charsets.UTF_8)
    return Base64.getEncoder().encodeToString(rawCredentials)
}

private fun requireNoUploadCrLf(label: String, value: String) {
    require('\r' !in value && '\n' !in value) {
        "NTRIP caster upload $label must not contain CR/LF characters"
    }
}
