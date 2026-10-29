package org.rtkcollector.app.alarms

import android.content.Context
import android.content.Intent
import org.rtkcollector.app.recording.RecordingForegroundService
import java.io.Closeable
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class AlarmBroadcastSnapshot(val generation: Long = -1, val message: String? = null,
    val terminalPending: Boolean = false, val tokens: List<String> = emptyList(), val delivery: String? = null)

/** One process-owned, conflated broadcast lane. Producers never call Binder or join it. */
class AlarmStateBroadcaster(private val deliver: (AlarmBroadcastSnapshot) -> Unit) : Closeable {
    private val latest = AtomicReference(AlarmBroadcastSnapshot())
    private val pending = AtomicReference<AlarmBroadcastSnapshot?>(null)
    private val ready = Semaphore(0)
    private val closed = AtomicBoolean(false)
    private val worker = Thread({
        while (!closed.get()) {
            try { ready.acquire() } catch (_: InterruptedException) { break }
            val snapshot = pending.getAndSet(null) ?: continue
            if (snapshot == latest.get()) runCatching { deliver(snapshot) }
        }
    }, "rtkcollector-alarm-broadcast").apply { isDaemon = true }
    init { runCatching { worker.start() }.onFailure { closed.set(true) } }
    fun offer(snapshot: AlarmBroadcastSnapshot) {
        if (closed.get()) return
        while (true) {
            val old = latest.get()
            if (snapshot.generation < old.generation) return
            if (latest.compareAndSet(old, snapshot)) break
        }
        if (pending.getAndUpdate { if (snapshot == latest.get()) snapshot else it } == null) ready.release()
    }
    override fun close() { closed.set(true); worker.interrupt() }
    companion object {
        private var instance: Pair<Context, AlarmStateBroadcaster>? = null
        @Synchronized fun get(context: Context): AlarmStateBroadcaster {
            val app = context.applicationContext
            instance?.takeIf { it.first === app }?.let { return it.second }
            instance?.second?.close()
            return AlarmStateBroadcaster { snapshot ->
                app.sendBroadcast(Intent(RecordingForegroundService.ACTION_ALARM_STATE).setPackage(app.packageName)
                    .putExtra(AlarmActions.GENERATION, snapshot.generation)
                    .putExtra(RecordingForegroundService.EXTRA_STATE_ALARM_MESSAGE, snapshot.message)
                    .putExtra(RecordingForegroundService.EXTRA_STATE_ALARM_TERMINAL_PENDING, snapshot.terminalPending)
                    .putStringArrayListExtra(AlarmActions.EPISODES, ArrayList(snapshot.tokens))
                    .putExtra(RecordingForegroundService.EXTRA_STATE_ALARM_DELIVERY, snapshot.delivery))
            }.also { instance = app to it }
        }
    }
}
