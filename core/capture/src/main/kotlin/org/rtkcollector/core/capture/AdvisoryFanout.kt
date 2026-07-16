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
                    eventSink.recordBestEffort(
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
    init {
        require(queueCapacity > 0) { "queueCapacity must be positive" }
    }

    private val queue = ArrayBlockingQueue<ByteArray>(queueCapacity)
    private val droppedChunks = AtomicLong(0)
    private val dropEventRecorded = AtomicBoolean(false)
    private val shutdownSummaryRecorded = AtomicBoolean(false)
    private val shutdownLock = Any()
    @Volatile
    private var accepting = true

    private val worker = Thread(
        {
            while (accepting || queue.isNotEmpty()) {
                val bytes = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                runCatching { delegate.accept(bytes) }
                    .onFailure { error ->
                        eventSink.recordBestEffort(
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
        val accepted = synchronized(shutdownLock) {
            if (!accepting) {
                return
            }
            queue.offer(bytes.copyOf())
        }
        if (!accepted) {
            droppedChunks.incrementAndGet()
            if (dropEventRecorded.compareAndSet(false, true)) {
                eventSink.recordBestEffort(
                    CaptureEvent(
                        timestamp = Instant.now().toString(),
                        type = "advisory-queue-dropped",
                        message = "Advisory parser queue is full; raw recording continues.",
                    ),
                )
            }
        }
    }

    /**
     * Stops accepting work, discards queued advisory chunks, and waits at most [timeoutMillis]
     * for an in-flight delegate call to finish.
     *
     * A `false` result means the delegate can still access its dependencies. Callers must not
     * close those dependencies until a later call returns `true`.
     */
    fun shutdown(timeoutMillis: Long): Boolean {
        require(timeoutMillis >= 0) { "timeoutMillis must not be negative" }

        synchronized(shutdownLock) {
            accepting = false
            queue.clear()
            worker.interrupt()
            if (timeoutMillis > 0 && worker !== Thread.currentThread()) {
                worker.join(timeoutMillis)
            }

            if (worker.isAlive) {
                return false
            }

            recordDropSummary()
            return true
        }
    }

    override fun close() {
        check(shutdown(DEFAULT_SHUTDOWN_TIMEOUT_MILLIS)) {
            "Async advisory fanout did not stop within $DEFAULT_SHUTDOWN_TIMEOUT_MILLIS ms. " +
                "Dependent writers must remain open until shutdown succeeds."
        }
    }

    private fun recordDropSummary() {
        if (!shutdownSummaryRecorded.compareAndSet(false, true)) {
            return
        }
        val dropped = droppedChunks.get()
        if (dropped > 1) {
            eventSink.recordBestEffort(
                CaptureEvent(
                    timestamp = Instant.now().toString(),
                    type = "advisory-queue-drop-summary",
                    message = "$dropped advisory byte chunks were dropped; raw recording continued.",
                ),
            )
        }
    }

    private companion object {
        const val DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 1_000L
    }
}

private fun CaptureEventSink.recordBestEffort(event: CaptureEvent) {
    runCatching { recordEvent(event) }
}
