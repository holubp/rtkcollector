package org.rtkcollector.core.capture

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.transport.SerialTransport
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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

    @Test
    fun `async fanout shutdown interrupts in-flight work and discards queued chunks`() {
        val started = CountDownLatch(1)
        val processed = mutableListOf<Byte>()
        val fanout = AsyncAdvisoryFanout(
            delegate = ReceiverBytesConsumer { bytes ->
                processed += bytes.single()
                started.countDown()
                try {
                    CountDownLatch(1).await()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            },
            eventSink = MemoryEvents(),
            queueCapacity = 2,
        )

        fanout.accept(byteArrayOf(1))
        assertTrue(started.await(2, TimeUnit.SECONDS))
        fanout.accept(byteArrayOf(2))

        assertTrue(fanout.shutdown(1_000))
        fanout.accept(byteArrayOf(3))

        assertEquals(listOf(1.toByte()), processed)
    }

    @Test
    fun `async fanout idle shutdown does not escape interrupted exception`() {
        val processed = CountDownLatch(1)
        val uncaught = AtomicReference<Throwable?>()
        val fanout = AsyncAdvisoryFanout(
            delegate = ReceiverBytesConsumer { processed.countDown() },
            eventSink = MemoryEvents(),
        )

        fanout.accept(byteArrayOf(1))
        assertTrue(processed.await(2, TimeUnit.SECONDS))
        val worker = advisoryWorkerOf(fanout)
        worker.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error ->
            uncaught.set(error)
        }
        assertTrue(waitUntil(2_000) { worker.state == Thread.State.TIMED_WAITING })

        assertTrue(fanout.shutdown(1_000))
        assertNull(uncaught.get())
    }

    @Test
    fun `async fanout close reports timeout while delegate can still write`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val fanout = AsyncAdvisoryFanout(
            delegate = ReceiverBytesConsumer {
                started.countDown()
                while (true) {
                    try {
                        if (release.await(10, TimeUnit.MILLISECONDS)) {
                            break
                        }
                    } catch (_: InterruptedException) {
                        // Simulate a delegate that cannot stop until its writer operation returns.
                    }
                }
                finished.countDown()
            },
            eventSink = MemoryEvents(),
        )

        try {
            fanout.accept(byteArrayOf(1))
            assertTrue(started.await(2, TimeUnit.SECONDS))

            assertFalse(fanout.shutdown(20))
            assertThrows(IllegalStateException::class.java) { fanout.close() }

            release.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertTrue(fanout.shutdown(1_000))
        } finally {
            release.countDown()
            fanout.shutdown(1_000)
        }
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

    private fun advisoryWorkerOf(fanout: AsyncAdvisoryFanout): Thread =
        AsyncAdvisoryFanout::class.java
            .getDeclaredField("worker")
            .apply { isAccessible = true }
            .get(fanout) as Thread

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadlineNanos) {
            if (condition()) return true
            Thread.sleep(1)
        }
        return condition()
    }

    private fun queueOf(vararg chunks: ByteArray): ArrayDeque<ByteArray> =
        ArrayDeque<ByteArray>().apply { chunks.forEach(::add) }
}
