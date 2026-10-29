package org.rtkcollector.app.alarms

import java.util.concurrent.atomic.AtomicReference

/** Independent of Binder/provider/player progress, including focus loss after long playback. */
class AlarmPlaybackDeadline {
    private data class State(val selection: AlarmAudioSelection? = null, val status: AlarmEffectStatus = AlarmEffectStatus.IDLE,
        val until: Long? = null, val expired: Boolean = false)
    private val state = AtomicReference(State())
    val status: AlarmEffectStatus get() = state.get().status
    fun select(selection: AlarmAudioSelection?, now: Long) {
        state.updateAndGet { old -> if (old.selection == selection) old else
            State(selection, if (selection == null) AlarmEffectStatus.IDLE else AlarmEffectStatus.PENDING,
                if (selection == null) null else now + 30_000) }
    }
    fun report(selection: AlarmAudioSelection, status: AlarmEffectStatus, now: Long) {
        state.updateAndGet { old ->
            if (old.selection != selection || old.expired || status == AlarmEffectStatus.IDLE) old else when (status) {
                AlarmEffectStatus.ACTIVE -> old.copy(status = status, until = null)
                AlarmEffectStatus.PENDING -> old.copy(status = status,
                    until = if (old.status == AlarmEffectStatus.PENDING) old.until else now + 30_000)
                AlarmEffectStatus.FAILED, AlarmEffectStatus.BLOCKED -> old.copy(status = AlarmEffectStatus.FAILED, until = null, expired = true)
            }
        }
    }
    fun allowed(selection: AlarmAudioSelection): Boolean = state.get().let { it.selection == selection && !it.expired }
    fun expire(now: Long, lastProgress: Long? = null): AlarmAudioSelection? {
        while (true) {
            val old = state.get()
            val pendingExpired = old.until?.let { now >= it } == true
            val playerStalled = old.status == AlarmEffectStatus.ACTIVE &&
                lastProgress != null && now - lastProgress >= 30_000
            if (old.expired || (!pendingExpired && !playerStalled)) return null
            if (state.compareAndSet(old, old.copy(status = AlarmEffectStatus.FAILED, until = null, expired = true))) return old.selection
        }
    }
}
