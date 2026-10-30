package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlarmPlaybackProgressTest {
    @Test fun `reconnected Bluetooth error before actual confirmation still retries speaker`() {
        val routes = AlarmRoutingPolicy()
        val attempts = AlarmPlaybackAttempts()
        val speaker = AlarmRouteDevice(1, AlarmRouteKind.SPEAKER)
        val bluetooth = AlarmRouteDevice(2, AlarmRouteKind.BLUETOOTH)
        attempts.prepare(routes.decide(true, listOf(speaker), 1, 0, 1))
        val requested = routes.decide(true, listOf(speaker, bluetooth), 1, 1, 2)
        assertFalse(requested.confirmed)
        attempts.request(requested)
        val failed = attempts.failure()
        assertEquals(2, failed.deviceId)
        assertFalse(failed.speaker)
        assertEquals(AlarmPlaybackAction.RETRY_SPEAKER,
            AlarmPlaybackProgress().observe(1, false, false, true, failed.speaker))
        routes.reject(failed.deviceId!!, AlarmRouteKind.BLUETOOTH)
        assertEquals(1, routes.decide(true, listOf(speaker, bluetooth), 1, 2, 2).deviceId)
    }
    @Test fun `disconnect plus native error is attributed to Bluetooth not newly selected speaker`() {
        val routes = AlarmRoutingPolicy()
        val attempts = AlarmPlaybackAttempts()
        val bluetooth = AlarmRouteDevice(2, AlarmRouteKind.BLUETOOTH)
        val speaker = AlarmRouteDevice(1, AlarmRouteKind.SPEAKER)
        attempts.prepare(routes.decide(true, listOf(bluetooth, speaker), 2, 0, 1))
        val newlyDesired = routes.decide(true, listOf(speaker), 2, 1, 2)
        attempts.confirm(newlyDesired, true)
        val failed = attempts.failure()
        assertEquals(2, failed.deviceId)
        assertFalse(failed.speaker)
        assertEquals(AlarmPlaybackAction.RETRY_SPEAKER,
            AlarmPlaybackProgress().observe(1, false, false, true, failed.speaker))
        routes.reject(failed.deviceId!!)
        val retry = routes.decide(true, listOf(speaker), 2, 2, 2)
        assertEquals(1, retry.deviceId)
        assertFalse(retry.failed)
        attempts.prepare(retry)
        assertTrue(attempts.failure().speaker)
    }
    @Test fun `failed retired Bluetooth attempt forces speaker rather than trying another headset`() {
        val routes = AlarmRoutingPolicy()
        val first = AlarmRouteDevice(2, AlarmRouteKind.BLUETOOTH)
        val other = AlarmRouteDevice(3, AlarmRouteKind.BLUETOOTH)
        val speaker = AlarmRouteDevice(1, AlarmRouteKind.SPEAKER)
        routes.decide(true, listOf(first, speaker), 2, 0, 1)
        routes.decide(true, listOf(other, speaker), 2, 1, 2)
        routes.reject(2, AlarmRouteKind.BLUETOOTH)
        assertEquals(1, routes.decide(true, listOf(other, speaker), 2, 2, 2).deviceId)
    }
    @Test fun `Bluetooth accepted but never playing retries on speaker before failure`() {
        val progress = AlarmPlaybackProgress()
        progress.start(0)
        assertEquals(AlarmPlaybackAction.CONTINUE, progress.observe(9_999, false, false, false, false))
        assertEquals(AlarmPlaybackAction.RETRY_SPEAKER, progress.observe(10_000, false, false, false, false))
        progress.start(10_000)
        assertEquals(AlarmPlaybackAction.CONTINUE, progress.observe(10_100, true, false, false, true))
        progress.start(20_000)
        assertEquals(AlarmPlaybackAction.FAILED, progress.observe(30_000, false, false, false, true))
    }
    @Test fun `each Bluetooth failure including after reconnection can retry speaker`() {
        val progress = AlarmPlaybackProgress()
        progress.start(0)
        assertEquals(AlarmPlaybackAction.RETRY_SPEAKER, progress.observe(1, false, false, true, false))
        progress.start(1)
        assertEquals(AlarmPlaybackAction.CONTINUE, progress.observe(2, true, false, false, true))
        assertEquals(AlarmPlaybackAction.CONTINUE, progress.observe(100, true, false, false, false))
        assertEquals(AlarmPlaybackAction.RETRY_SPEAKER, progress.observe(101, false, false, true, false))
        progress.start(101)
        assertEquals(AlarmPlaybackAction.FAILED, progress.observe(102, false, false, true, true))
    }
    @Test fun `late system sound repeat gets a fresh preparation deadline`() {
        val progress = AlarmPlaybackProgress()
        progress.start(0)
        progress.observe(1, true, false, false, true)
        assertEquals(AlarmPlaybackAction.CONTINUE, progress.observe(20_000, false, true, false, true))
        assertEquals(AlarmPlaybackAction.CONTINUE, progress.observe(21_599, false, true, false, true))
        assertEquals(AlarmPlaybackAction.REPLAY, progress.observe(21_600, false, true, false, true))
        assertEquals(AlarmPlaybackAction.CONTINUE, progress.observe(21_700, false, false, false, true))
        assertEquals(AlarmPlaybackAction.CONTINUE, progress.observe(22_000, true, false, false, true))
    }
    @Test fun `callback only release timeout remains sticky and retains player ownership`() {
        val guard = AlarmReleaseGuard()
        val owner = AlarmClipOwner()
        var released = 0
        val clip = object : AlarmClipPlayback {
            override fun play() = Unit
            override fun poll(now: Long) = AlarmClipStatus(AlarmClipState.PLAYING)
            override fun stop() = guard.release { released++; guard.timeout() /* playerError can still be null */ }
        }
        owner.install({ clip }, { true })
        assertThrows(IllegalStateException::class.java) { owner.stop() }
        assertTrue(owner.hasClip)
        var created = false
        assertThrows(IllegalStateException::class.java) { owner.install({ created = true; clip }, { true }) }
        assertFalse(created)
        assertEquals(1, released)
    }
}
