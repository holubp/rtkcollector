package org.rtkcollector.core.capture

import java.io.Closeable
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

fun interface ReceiverBytesConsumer {
    fun accept(bytes: ByteArray)
}

data class AdvisoryConsumer(
    val id: String,
    val accept: (ByteArray) -> Unit,
)

class AdvisoryFanout(
    private val eventSink: CaptureEventSink,
    private val consumers: List<AdvisoryConsumer>,
) : ReceiverBytesConsumer {
    override fun accept(bytes: ByteArray) {
        consumers.forEach { consumer ->
            runCatching { consumer.accept(bytes) }
                .onFailure { error ->
                    eventSink.recordEvent(
                        CaptureEvent(
                            timestamp = Instant.now().toString(),
                            type = "advisory-consumer-error",
                            message = "${consumer.id}: ${error.message ?: error.javaClass.simpleName}",
                        ),
                    )
                }
        }
    }
}

class AsyncAdvisoryFanout(
    private val delegate: ReceiverBytesConsumer,
    private val eventSink: CaptureEventSink,
    queueCapacity: Int = 64,
) : ReceiverBytesConsumer, Closeable {
    private val queue = ArrayBlockingQueue<ByteArray>(queueCapacity)
    private val droppedChunks = AtomicLong(0)
    private val dropEventRecorded = AtomicBoolean(false)
    @Volatile
    private var accepting = true

    private val worker = Thread(
        {
            while (accepting || queue.isNotEmpty()) {
                val bytes = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                runCatching { delegate.accept(bytes) }
                    .onFailure { error ->
                        eventSink.recordEvent(
                            CaptureEvent(
                                timestamp = Instant.now().toString(),
                                type = "advisory-async-error",
                                message = error.message ?: error.javaClass.simpleName,
                            ),
                        )
                    }
            }
        },
        "rtkcollector-advisory",
    ).apply {
        isDaemon = true
        start()
    }

    override fun accept(bytes: ByteArray) {
        if (!accepting) {
            return
        }
        if (!queue.offer(bytes.copyOf())) {
            droppedChunks.incrementAndGet()
            if (dropEventRecorded.compareAndSet(false, true)) {
                eventSink.recordEvent(
                    CaptureEvent(
                        timestamp = Instant.now().toString(),
                        type = "advisory-queue-dropped",
                        message = "Advisory parser queue is full; raw recording continues.",
                    ),
                )
            }
        }
    }

    override fun close() {
        accepting = false
        worker.join(1_000)
        val dropped = droppedChunks.get()
        if (dropped > 1) {
            eventSink.recordEvent(
                CaptureEvent(
                    timestamp = Instant.now().toString(),
                    type = "advisory-queue-drop-summary",
                    message = "$dropped advisory byte chunks were dropped; raw recording continued.",
                ),
            )
        }
    }
}
