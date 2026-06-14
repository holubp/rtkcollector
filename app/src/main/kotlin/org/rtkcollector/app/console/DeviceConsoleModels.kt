package org.rtkcollector.app.console

enum class DeviceConsoleLineEnding(
    val label: String,
    val bytes: ByteArray,
) {
    CRLF("CRLF", byteArrayOf(13, 10)),
    LF("LF", byteArrayOf(10)),
    CR("CR", byteArrayOf(13)),
    NONE("None", byteArrayOf());
}

data class DeviceConsoleAvailability(
    val canConnect: Boolean,
    val message: String? = null,
) {
    companion object {
        fun fromRecordingState(recordingActive: Boolean): DeviceConsoleAvailability =
            if (recordingActive) {
                DeviceConsoleAvailability(
                    canConnect = false,
                    message = "Stop recording before opening the device console.",
                )
            } else {
                DeviceConsoleAvailability(canConnect = true)
            }
    }
}

data class DeviceConsoleRollingBuffer(
    val maxChars: Int,
    val text: String = "",
) {
    init {
        require(maxChars > 0) { "Console buffer length must be positive." }
    }

    fun append(chunk: String): DeviceConsoleRollingBuffer {
        if (chunk.isEmpty()) return this
        val combined = text + chunk
        return copy(text = if (combined.length <= maxChars) combined else combined.takeLast(maxChars))
    }

    fun clear(): DeviceConsoleRollingBuffer = copy(text = "")
}

object DeviceConsoleOutputFormatter {
    fun render(bytes: ByteArray): String =
        buildString(bytes.size) {
            bytes.forEach { raw ->
                val value = raw.toInt() and 0xFF
                when (value) {
                    0x0A -> append('\n')
                    0x0D -> append('\r')
                    in 0x20..0x7E -> append(value.toChar())
                    else -> append("<%02X>".format(value))
                }
            }
        }
}

object DeviceConsoleCommandEncoder {
    fun encode(text: String, lineEnding: DeviceConsoleLineEnding): ByteArray {
        if (lineEnding == DeviceConsoleLineEnding.NONE) return text.encodeToByteArray()
        val ending = lineEnding.bytes.decodeToString()
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n').let { parts ->
            if (parts.lastOrNull()?.isEmpty() == true) parts.dropLast(1) else parts
        }
        return (lines.joinToString(ending) + ending).encodeToByteArray()
    }
}

enum class DeviceConsoleConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
}

data class DeviceConsoleState(
    val status: DeviceConsoleConnectionStatus = DeviceConsoleConnectionStatus.DISCONNECTED,
    val output: String = "",
    val input: String = "",
    val paused: Boolean = false,
    val lineEnding: DeviceConsoleLineEnding = DeviceConsoleLineEnding.CRLF,
    val bufferLimitBytes: Int = DEFAULT_BUFFER_LIMIT_BYTES,
    val selectedUsbProfileId: String? = null,
    val selectedCommandProfileId: String? = null,
    val lastError: String? = null,
) {
    val connected: Boolean
        get() = status == DeviceConsoleConnectionStatus.CONNECTED

    companion object {
        const val DEFAULT_BUFFER_LIMIT_BYTES = 1_048_576
    }
}
