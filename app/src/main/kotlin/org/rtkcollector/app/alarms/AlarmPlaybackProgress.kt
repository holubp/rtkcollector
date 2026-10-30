package org.rtkcollector.app.alarms

import java.util.concurrent.atomic.AtomicBoolean

enum class AlarmPlaybackAction { CONTINUE, RETRY_SPEAKER, REPLAY, FAILED }

data class AlarmPlaybackAttempt(val deviceId: Int?, val speaker: Boolean)
class AlarmPlaybackAttempts {
    private var active = AlarmPlaybackAttempt(null, false)
    fun prepare(route: AlarmRouteDecision) { active = AlarmPlaybackAttempt(route.deviceId, route.fallback) }
    fun request(route: AlarmRouteDecision) { if (!route.failed && !route.fallback) prepare(route) }
    fun confirm(route: AlarmRouteDecision, playing: Boolean) { if (playing && route.confirmed) prepare(route) }
    fun failure(): AlarmPlaybackAttempt = active
}

/** Source first, route next, then preparation/play; recheck cancellation across native calls. */
internal fun startCurrentAlarmPlayback(current: () -> Boolean, source: () -> Unit,
    routeAvailable: () -> Boolean, prepare: () -> Unit, play: () -> Unit) {
    if (!current()) return
    source()
    if (!current() || !routeAvailable() || !current()) return
    prepare()
    if (current()) play()
}

internal fun <T> createCurrentAlarmOutput(current: () -> Boolean, create: () -> T,
    dispose: (T) -> Unit, disposalFailed: () -> Unit): T {
    check(current()) { "Alarm output canceled" }
    val output = create()
    if (!current()) {
        try { dispose(output) } catch (failure: Exception) { disposalFailed(); throw failure }
        error("Alarm output canceled")
    }
    return output
}

/** Decode/start deadlines belong to each attempt, not the age of an alarm episode. */
class AlarmPlaybackProgress {
    private var preparingSince: Long? = null
    private var repeatAt: Long? = null
    fun start(now: Long) { preparingSince = now; repeatAt = null }
    fun observe(now: Long, playing: Boolean, ended: Boolean, error: Boolean, speaker: Boolean): AlarmPlaybackAction {
        fun failure() = if (speaker) AlarmPlaybackAction.FAILED else AlarmPlaybackAction.RETRY_SPEAKER
        if (error) return failure()
        if (ended) {
            val due = repeatAt ?: (now + 1_600).also { repeatAt = it }
            if (now >= due) { start(now); return AlarmPlaybackAction.REPLAY }
            return AlarmPlaybackAction.CONTINUE
        }
        if (playing) preparingSince = null
        else {
            val since = preparingSince ?: now.also { preparingSince = it }
            if (now - since >= 10_000) return failure()
        }
        return AlarmPlaybackAction.CONTINUE
    }
}

/** Media3 reports release timeout only through its listener, even with null playerError. */
class AlarmReleaseGuard(private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wait: (Long) -> Unit = Thread::sleep) {
    private val releasing = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    fun unconfirmed() { failed.set(true) }
    fun timeout() { if (releasing.get()) unconfirmed() }
    fun release(pending: () -> Boolean = { false }, action: () -> Unit) {
        check(!releasing.get() || !failed.get()) { "Alarm player release is unconfirmed" }
        releasing.set(true)
        try {
            action()
            val until = clock() + 500
            while (!failed.get() && pending() && clock() < until) wait(5)
            check(!failed.get() && !pending()) { "Alarm player release timed out" }
        } catch (failure: Exception) {
            unconfirmed()
            throw failure
        }
    }
}
