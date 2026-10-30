package org.rtkcollector.app.recording

import java.io.Closeable
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class RecordingNotificationUpdate(val epoch: Long, val revision: Long, val text: String?,
    val terminalAlarmGeneration: Long? = null)

/** One bounded process lane. A newer clear/update reconciles a late in-flight Binder post. */
class RecordingNotificationDelivery(private val deliver: (RecordingNotificationUpdate) -> Unit) : Closeable {
    private val latest = AtomicReference<RecordingNotificationUpdate?>(null)
    private val pending = AtomicReference<RecordingNotificationUpdate?>(null)
    private val ready = Semaphore(0)
    private val closed = AtomicBoolean(false)
    private var retiredThrough = 0L
    private val worker = Thread({
        while (!closed.get()) {
            try { ready.acquire() } catch (_: InterruptedException) { break }
            val update = pending.getAndSet(null) ?: continue
            if (update == latest.get()) runCatching { deliver(update) }
        }
    }, "rtkcollector-recording-notification").apply { isDaemon = true }
    init { runCatching { worker.start() }.onFailure { closed.set(true) } }

    @Synchronized fun offer(update: RecordingNotificationUpdate) {
        if (closed.get() || update.epoch <= retiredThrough) return
        enqueue(update)
    }

    /** Destruction is irreversible even if a publisher already passed a service-side check. */
    @Synchronized fun retire(epoch: Long) {
        if (closed.get()) return
        retiredThrough = maxOf(retiredThrough, epoch)
        enqueue(RecordingNotificationUpdate(epoch, Long.MAX_VALUE, null))
    }

    private fun enqueue(update: RecordingNotificationUpdate) {
        val old = latest.get()
        if (old != null && (update.epoch < old.epoch || update.epoch == old.epoch && update.revision <= old.revision)) return
        latest.set(update)
        if (pending.getAndSet(update) == null) ready.release()
    }
    override fun close() { closed.set(true); worker.interrupt() }

    companion object {
        private val epochs = AtomicLong(0)
        fun nextEpoch(): Long = epochs.incrementAndGet()
    }
}
