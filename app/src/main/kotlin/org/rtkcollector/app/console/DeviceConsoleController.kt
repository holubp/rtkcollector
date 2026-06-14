package org.rtkcollector.app.console

import org.rtkcollector.core.transport.SerialTransport
import java.util.concurrent.atomic.AtomicBoolean

class DeviceConsoleController(
    private val recordingActive: () -> Boolean,
    private val transportFactory: () -> SerialTransport,
    private val stateListener: (DeviceConsoleState) -> Unit,
) {
    @Volatile private var transport: SerialTransport? = null
    @Volatile private var readerThread: Thread? = null
    private val keepReading = AtomicBoolean(false)
    private var buffer = DeviceConsoleRollingBuffer(DeviceConsoleState.DEFAULT_BUFFER_LIMIT_BYTES)

    @Volatile
    var state: DeviceConsoleState = DeviceConsoleState()
        private set

    fun setBufferLimit(bytes: Int) {
        buffer = DeviceConsoleRollingBuffer(bytes, buffer.text.takeLast(bytes))
        update { it.copy(bufferLimitBytes = bytes, output = buffer.text) }
    }

    fun setPaused(paused: Boolean) {
        update { it.copy(paused = paused, output = if (paused) it.output else buffer.text) }
    }

    fun connect(): Result<Unit> {
        if (recordingActive()) {
            val message = "Stop recording before opening the device console."
            update { it.copy(lastError = message, status = DeviceConsoleConnectionStatus.DISCONNECTED) }
            return Result.failure(IllegalStateException(message))
        }
        if (transport?.isOpen == true) return Result.success(Unit)
        update { it.copy(status = DeviceConsoleConnectionStatus.CONNECTING, lastError = null) }
        return runCatching {
            val opened = transportFactory()
            opened.open()
            transport = opened
            keepReading.set(true)
            startReader(opened)
            update { it.copy(status = DeviceConsoleConnectionStatus.CONNECTED, lastError = null) }
        }.onFailure { error ->
            update {
                it.copy(
                    status = DeviceConsoleConnectionStatus.DISCONNECTED,
                    lastError = error.message ?: "Device console could not connect.",
                )
            }
            runCatching { transport?.close() }
            transport = null
        }
    }

    fun send(text: String, lineEnding: DeviceConsoleLineEnding): Result<Unit> =
        runCatching {
            val opened = transport ?: error("Device console is not connected.")
            opened.write(DeviceConsoleCommandEncoder.encode(text, lineEnding))
        }.onFailure { error ->
            update { it.copy(lastError = error.message ?: "Device console send failed.") }
        }

    fun disconnect() {
        keepReading.set(false)
        update { it.copy(status = DeviceConsoleConnectionStatus.DISCONNECTING) }
        runCatching { transport?.close() }
        transport = null
        readerThread = null
        update { it.copy(status = DeviceConsoleConnectionStatus.DISCONNECTED) }
    }

    fun clearOutput() {
        buffer = buffer.clear()
        update { it.copy(output = "") }
    }

    private fun startReader(opened: SerialTransport) {
        readerThread = Thread {
            while (keepReading.get() && opened.isOpen) {
                runCatching {
                    val bytes = opened.readAvailable(4096)
                    if (bytes.isNotEmpty()) {
                        appendOutput(DeviceConsoleOutputFormatter.render(bytes))
                    }
                }.onFailure { error ->
                    update { it.copy(lastError = error.message ?: "Device console read failed.") }
                    keepReading.set(false)
                }
            }
        }.apply {
            name = "rtkcollector-device-console-reader"
            isDaemon = true
            start()
        }
    }

    private fun appendOutput(chunk: String) {
        buffer = buffer.append(chunk)
        if (!state.paused) {
            update { it.copy(output = buffer.text) }
        }
    }

    private fun update(change: (DeviceConsoleState) -> DeviceConsoleState) {
        state = change(state)
        stateListener(state)
    }
}
