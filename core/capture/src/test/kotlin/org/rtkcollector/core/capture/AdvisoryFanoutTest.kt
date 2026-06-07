package org.rtkcollector.core.capture

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.transport.SerialTransport
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AdvisoryFanoutTest {
    @Test
    fun `fanout records warning when advisory consumer fails and continues`() {
        val events = mutableListOf<CaptureEvent>()
        var goodConsumerCalled = false
        val fanout = AdvisoryFanout(
            eventSink = object : CaptureEventSink {
                override fun recordEvent(event: CaptureEvent) {
                    events += event
                }
            },
            consumers = listOf(
                AdvisoryConsumer("bad") { error("parser failed") },
                AdvisoryConsumer("good") { goodConsumerCalled = true },
            ),
        )

        fanout.accept(byteArrayOf(1, 2, 3))

        assertEquals(1, events.size)
        assertEquals("advisory-consumer-error", events.single().type)
        assertEquals(true, goodConsumerCalled)
    }

    @Test
    fun `capture runtime records receiver bytes before advisory fanout`() {
        val recorder = OrderedRecorder()
        val fanout = AdvisoryFanout(
            eventSink = MemoryEvents(),
            consumers = listOf(
                AdvisoryConsumer("order-check") {
                    recorder.assertReceiverBytes(byteArrayOf(0x41, 0x42))
                },
            ),
        )
        val runtime = CaptureRuntime(
            transport = FakeSerialTransport(reads = queueOf(byteArrayOf(0x41, 0x42))),
            recorder = recorder,
            eventSink = MemoryEvents(),
            advisoryFanout = fanout,
        )

        runtime.open()
        runtime.readOnce(maxBytes = 1024)

        assertArrayEquals(byteArrayOf(0x41, 0x42), recorder.receiverBytes())
    }

    @Test
    fun `async fanout drops advisory bytes instead of blocking capture path`() {
        val events = mutableListOf<CaptureEvent>()
        val release = CountDownLatch(1)
        val delegateStarted = CountDownLatch(1)
        val fanout = AsyncAdvisoryFanout(
            delegate = ReceiverBytesConsumer {
                delegateStarted.countDown()
                release.await(5, TimeUnit.SECONDS)
            },
            eventSink = object : CaptureEventSink {
                override fun recordEvent(event: CaptureEvent) {
                    events += event
                }
            },
            queueCapacity = 1,
        )

        try {
            fanout.accept(byteArrayOf(1))
            assertTrue(delegateStarted.await(2, TimeUnit.SECONDS))
            fanout.accept(byteArrayOf(2))
            fanout.accept(byteArrayOf(3))
            fanout.accept(byteArrayOf(4))

            assertEquals(1, events.count { it.type == "advisory-queue-dropped" })
        } finally {
            release.countDown()
            fanout.close()
        }
        assertEquals(1, events.count { it.type == "advisory-queue-drop-summary" })
        assertTrue(events.single { it.type == "advisory-queue-drop-summary" }.message.contains("2"))
    }

    private class FakeSerialTransport(
        private val reads: ArrayDeque<ByteArray> = ArrayDeque(),
    ) : SerialTransport {
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

        override fun write(bytes: ByteArray) = Unit
    }

    private class OrderedRecorder : RawRecorder {
        private val receiver = mutableListOf<Byte>()

        override fun appendReceiverBytes(bytes: ByteArray) {
            receiver += bytes.toList()
        }

        override fun appendTransmittedBytes(bytes: ByteArray) = Unit

        override fun appendCorrectionInputBytes(bytes: ByteArray) = Unit

        override fun close() = Unit

        fun receiverBytes(): ByteArray = receiver.toByteArray()

        fun assertReceiverBytes(expected: ByteArray) {
            assertArrayEquals(expected, receiverBytes())
        }
    }

    private class MemoryEvents : CaptureEventSink {
        override fun recordEvent(event: CaptureEvent) = Unit
    }

    private fun queueOf(vararg chunks: ByteArray): ArrayDeque<ByteArray> =
        ArrayDeque<ByteArray>().apply { chunks.forEach(::add) }
}
