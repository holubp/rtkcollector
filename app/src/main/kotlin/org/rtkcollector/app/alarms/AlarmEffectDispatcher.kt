package org.rtkcollector.app.alarms

import java.io.Closeable
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

fun interface AlarmEffect { fun apply(state: AlarmDesiredState, isCurrent: () -> Boolean) }

/** One in-flight and one conflated desired state per independent effect lane. */
class AlarmEffectDispatcher(notification: AlarmEffect, audio: AlarmEffect,
    private val onFailure: (String) -> Unit = {},
    private val onScopedFailure: (String, AlarmDesiredState) -> Unit = { _, _ -> }) : Closeable {
    private val closed = AtomicBoolean(false)
    private val latest = AtomicReference(AlarmDesiredState.EMPTY)
    private inner class Lane(private val label: String, private val effect: AlarmEffect) {
        private val pending = AtomicReference<AlarmDesiredState?>(null)
        private val ready = Semaphore(0)
        init {
            runCatching { Thread({
                while (!closed.get() || pending.get() != null) {
                    try { ready.acquire() } catch (_: InterruptedException) { break }
                    val next = pending.getAndSet(null) ?: continue
                    val finalVisual = closed.get() && label == "notification"
                    if (current(next, finalVisual)) runCatching { effect.apply(next) { current(next, finalVisual) } }
                        .onFailure { if (current(next, finalVisual)) runCatching { onFailure(label); onScopedFailure(label, next) } }
                }
            }, "rtkcollector-alarm-$label").apply { isDaemon = true; start() } }
                .onFailure { runCatching { onFailure(label) } }
        }
        fun offer(value: AlarmDesiredState) { if (pending.getAndSet(value) == null) ready.release() }
        fun stop(final: AlarmDesiredState? = null) { pending.set(final); ready.release() }
    }
    private val visual = Lane("notification", notification)
    private val acoustic = Lane("audio", audio)
    private fun current(state: AlarmDesiredState, finalVisual: Boolean = false) =
        (!closed.get() || finalVisual) && latest.get() == state
    fun offer(desired: AlarmDesiredState): Boolean {
        if (closed.get()) return false
        while (true) {
            val old = latest.get()
            if (desired.generation < old.generation || desired.generation == old.generation && desired.revision < old.revision) return false
            if (latest.compareAndSet(old, desired)) break
        }
        visual.offer(desired); acoustic.offer(desired)
        return true
    }
    fun invalidate(generation: Long) {
        val clear = latest.updateAndGet { if (it.generation == generation)
            it.copy(revision = it.revision + 1, visibleEpisodes = emptyList(), activeEpisodes = emptyList(),
                audio = null, terminalAlertPending = false) else it }
        if (clear.generation == generation) { visual.offer(clear); acoustic.offer(clear) }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        visual.stop(latest.get().takeIf { it.generation >= 0 }); acoustic.stop()
    }
}
