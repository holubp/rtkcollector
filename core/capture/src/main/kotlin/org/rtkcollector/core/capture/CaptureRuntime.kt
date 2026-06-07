package org.rtkcollector.core.capture

import org.rtkcollector.core.transport.SerialTransport
import java.time.Instant

class CaptureRuntime(
    private val transport: SerialTransport,
    private val recorder: RawRecorder,
    private val eventSink: CaptureEventSink,
    private val advisoryFanout: ReceiverBytesConsumer? = null,
    private val advisoryReceiverBytes: (ByteArray) -> Unit = {},
) {
    fun open() {
        if (!transport.isOpen) {
            transport.open()
        }
    }

    fun readOnce(maxBytes: Int): Int {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val bytes = transport.readAvailable(maxBytes)
        if (bytes.isEmpty()) {
            return 0
        }

        recorder.appendReceiverBytes(bytes)
        runAdvisory("advisory-fanout-error") {
            advisoryFanout?.accept(bytes)
        }
        runAdvisory("advisory-error") {
            advisoryReceiverBytes(bytes)
        }
        return bytes.size
    }

    fun sendToReceiver(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            return
        }
        recorder.appendTransmittedBytes(bytes)
        transport.write(bytes)
    }

    fun injectCorrectionBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            return
        }
        recorder.appendCorrectionInputBytes(bytes)
        sendToReceiver(bytes)
    }

    fun close() {
        runCatching { recorder.close() }
            .onFailure { recordEvent("recorder-close-error", it.message ?: "Recorder close failed.") }
        runCatching { transport.close() }
            .onFailure { recordEvent("transport-close-error", it.message ?: "Transport close failed.") }
    }

    private fun runAdvisory(type: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            recordEvent(type, error.message ?: "Advisory capture consumer failed.")
        }
    }

    private fun recordEvent(type: String, message: String) {
        eventSink.recordEvent(
            CaptureEvent(
                timestamp = Instant.now().toString(),
                type = type,
                message = message,
            ),
        )
    }
}
