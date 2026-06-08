package org.rtkcollector.core.capture

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.core.transport.SerialTransport
import java.util.ArrayDeque

class CaptureRuntimeTest {
    @Test
    fun `receiver bytes are recorded byte exact before advisory callbacks`() {
        val recorder = MemoryRecorder()
        val events = MemoryEvents()
        val transport = FakeSerialTransport(reads = queueOf(byteArrayOf(0x01, 0x02, 0x03), byteArrayOf()))
        val runtime = CaptureRuntime(
            transport = transport,
            recorder = recorder,
            eventSink = events,
            advisoryReceiverBytes = { error("parser failed") },
        )

        runtime.open()
        val bytesRead = runtime.readOnce(maxBytes = 1024)

        assertEquals(3, bytesRead)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), recorder.receiverBytes())
        assertEquals(listOf("advisory-error"), events.types())
        assertEquals(true, transport.isOpen)
    }

    @Test
    fun `tx bytes are recorded before they are written to transport`() {
        val recorder = MemoryRecorder()
        val transport = FakeSerialTransport()
        val runtime = CaptureRuntime(transport = transport, recorder = recorder, eventSink = MemoryEvents())

        runtime.open()
        runtime.sendToReceiver(byteArrayOf(0x10, 0x11))

        assertArrayEquals(byteArrayOf(0x10, 0x11), recorder.transmittedBytes())
        assertArrayEquals(byteArrayOf(0x10, 0x11), transport.writtenBytes())
    }

    @Test
    fun `correction bytes are recorded separately before receiver injection`() {
        val recorder = MemoryRecorder()
        val transport = FakeSerialTransport()
        val runtime = CaptureRuntime(transport = transport, recorder = recorder, eventSink = MemoryEvents())

        runtime.open()
        runtime.injectCorrectionBytes(byteArrayOf(0x20, 0x21, 0x22))

        assertArrayEquals(byteArrayOf(0x20, 0x21, 0x22), recorder.correctionBytes())
        assertArrayEquals(byteArrayOf(0x20, 0x21, 0x22), recorder.transmittedBytes())
        assertArrayEquals(byteArrayOf(0x20, 0x21, 0x22), transport.writtenBytes())
    }

    @Test
    fun `correction callback failure does not close receiver transport`() {
        val recorder = MemoryRecorder()
        val events = MemoryEvents()
        val transport = FakeSerialTransport(reads = queueOf(byteArrayOf(0x31)))
        val runtime = CaptureRuntime(
            transport = transport,
            recorder = recorder,
            eventSink = events,
            advisoryReceiverBytes = { error("quality parser failed") },
        )

        runtime.open()
        runtime.injectCorrectionBytes(byteArrayOf(0x40))
        runtime.readOnce(maxBytes = 1024)

        assertEquals(true, transport.isOpen)
        assertArrayEquals(byteArrayOf(0x31), recorder.receiverBytes())
    }

    @Test
    fun `advisory failure plus event sink failure does not hide recorded rx bytes`() {
        val recorder = MemoryRecorder()
        val transport = FakeSerialTransport(reads = queueOf(byteArrayOf(0x51, 0x52)))
        val runtime = CaptureRuntime(
            transport = transport,
            recorder = recorder,
            eventSink = ThrowingEvents(),
            advisoryReceiverBytes = { error("parser failed after raw write") },
        )

        runtime.open()
        val bytesRead = runtime.readOnce(maxBytes = 1024)

        assertEquals(2, bytesRead)
        assertArrayEquals(byteArrayOf(0x51, 0x52), recorder.receiverBytes())
        assertEquals(true, transport.isOpen)
    }

    @Test
    fun `advisory fanout consumer failure plus event sink failure does not throw`() {
        val fanout = AdvisoryFanout(
            eventSink = ThrowingEvents(),
            consumers = listOf(AdvisoryConsumer("bad-sidecar") { error("sidecar failed") }),
        )

        fanout.accept(byteArrayOf(0x61))
    }

    private class FakeSerialTransport(
        private val reads: ArrayDeque<ByteArray> = ArrayDeque(),
    ) : SerialTransport {
        private val written = mutableListOf<Byte>()

        override var isOpen: Boolean = false
            private set

        override fun open() {
            isOpen = true
        }

        override fun close() {
            isOpen = false
        }

        override fun readAvailable(maxBytes: Int): ByteArray =
            if (reads.isEmpty()) byteArrayOf() else reads.removeFirst()

        override fun write(bytes: ByteArray) {
            written += bytes.toList()
        }

        fun writtenBytes(): ByteArray = written.toByteArray()
    }

    private class MemoryRecorder : RawRecorder {
        private val receiver = mutableListOf<Byte>()
        private val transmitted = mutableListOf<Byte>()
        private val corrections = mutableListOf<Byte>()

        override fun appendReceiverBytes(bytes: ByteArray) {
            receiver += bytes.toList()
        }

        override fun appendTransmittedBytes(bytes: ByteArray) {
            transmitted += bytes.toList()
        }

        override fun appendCorrectionInputBytes(bytes: ByteArray) {
            corrections += bytes.toList()
        }

        override fun close() = Unit

        fun receiverBytes(): ByteArray = receiver.toByteArray()
        fun transmittedBytes(): ByteArray = transmitted.toByteArray()
        fun correctionBytes(): ByteArray = corrections.toByteArray()
    }

    private class MemoryEvents : CaptureEventSink {
        private val events = mutableListOf<CaptureEvent>()

        override fun recordEvent(event: CaptureEvent) {
            events += event
        }

        fun types(): List<String> = events.map { it.type }
    }

    private class ThrowingEvents : CaptureEventSink {
        override fun recordEvent(event: CaptureEvent) {
            error("event sidecar failed")
        }
    }

    private fun queueOf(vararg chunks: ByteArray): ArrayDeque<ByteArray> =
        ArrayDeque<ByteArray>().apply { chunks.forEach(::add) }
}
