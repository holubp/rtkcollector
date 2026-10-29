package org.rtkcollector.app.alarms

import android.content.Context
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

interface AlarmForegroundHost {
    fun ownsAlarmGeneration(generation: Long): Boolean
    fun applyAlarmForeground(state: AlarmDesiredState, current: () -> Boolean): Boolean
    fun alarmDeliveryFailed(lane: String)
    fun alarmDeliveryFailed(lane: String, state: AlarmDesiredState) { alarmDeliveryFailed(lane) }
    fun alarmStartId(): Int = 0
    fun finishAlarmService(generation: Long, startId: Int) {}
}

/** Process-owned lanes: blocked platform calls cannot accumulate threads on service recreation. */
class AlarmEffectsRuntime(notification: AlarmEffect, audio: AlarmEffect) : Closeable {
    private val host = AtomicReference<WeakReference<AlarmForegroundHost>?>(null)
    private val dispatcher = AlarmEffectDispatcher(notification, AlarmEffect { state, current ->
        val owner = host.get()?.get()?.takeIf { it.ownsAlarmGeneration(state.generation) }
        val play = if (owner != null && current()) runCatching { owner.applyAlarmForeground(state, current) }
            .onFailure { if (current()) owner.alarmDeliveryFailed("foreground", state) }.getOrDefault(false) else false
        if (current()) audio.apply(if (play) state else state.copy(audio = null), current)
    }, onScopedFailure = { lane, state ->
        host.get()?.get()?.takeIf { it.ownsAlarmGeneration(state.generation) }?.alarmDeliveryFailed(lane, state)
    })
    fun attach(owner: AlarmForegroundHost) { host.set(WeakReference(owner)) }
    fun detach(owner: AlarmForegroundHost) { host.updateAndGet { if (it?.get() === owner) null else it } }
    fun offer(desired: AlarmDesiredState) = dispatcher.offer(desired)
    fun invalidate(generation: Long) = dispatcher.invalidate(generation)
    fun requestExit(owner: AlarmForegroundHost, generation: Long) {
        val reference = WeakReference(owner)
        val startId = owner.alarmStartId()
        runCatching { exits.execute {
            reference.get()?.takeIf { it.ownsAlarmGeneration(generation) }?.let {
                runCatching { it.finishAlarmService(generation, startId) }
            }
        } }.onFailure { owner.alarmDeliveryFailed("service exit") }
    }
    override fun close() { host.set(null); dispatcher.close() }
    companion object {
        private val exits = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1),
            java.util.concurrent.ThreadFactory { Thread(it, "rtkcollector-alarm-exit").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardOldestPolicy())
        private var instance: Pair<Context, AlarmEffectsRuntime>? = null
        @Synchronized fun get(context: Context): AlarmEffectsRuntime {
            val app = context.applicationContext
            instance?.takeIf { it.first === app }?.let { return it.second }
            instance?.second?.close()
            return AlarmEffectsRuntime(AlarmNotificationPublisher(app), AlarmAudioPlayer.get(app))
                .also { instance = app to it }
        }
    }
}
