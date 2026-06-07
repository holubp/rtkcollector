package org.rtkcollector.core.capture

interface RawRecorder {
    fun appendReceiverBytes(bytes: ByteArray)
    fun appendTransmittedBytes(bytes: ByteArray)
    fun appendCorrectionInputBytes(bytes: ByteArray)
    fun close()
}

interface CaptureEventSink {
    fun recordEvent(event: CaptureEvent)
}

data class CaptureEvent(
    val timestamp: String,
    val type: String,
    val message: String,
)
