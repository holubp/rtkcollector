package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AlarmClipOwnerTest {
    private class Clip : AlarmClipPlayback {
        var played = false
        var stopped = false
        var failStop = false
        override fun play() { played = true }
        override fun poll(now: Long) = AlarmClipStatus(AlarmClipState.PLAYING)
        override fun stop() { if (failStop) error("release failed"); stopped = true }
    }

    @Test fun `cancel during blocked creation cannot play a stale alarm`() {
        val owner = AlarmClipOwner()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val clip = Clip()
        val current = java.util.concurrent.atomic.AtomicBoolean(true)
        val worker = Thread { owner.install({ entered.countDown(); release.await(); clip }, current::get) }
        worker.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        current.set(false)
        release.countDown()
        worker.join(1_000)
        assertFalse(worker.isAlive)
        assertFalse(clip.played)
        assertTrue(clip.stopped)
        assertFalse(owner.hasClip)
    }

    @Test fun `failed release retains ownership and prevents allocating another player`() {
        val owner = AlarmClipOwner()
        val clip = Clip()
        owner.install({ clip }, { true })
        clip.failStop = true
        assertThrows(IllegalStateException::class.java) { owner.stop() }
        var created = false
        assertThrows(IllegalStateException::class.java) { owner.install({ created = true; Clip() }, { true }) }
        assertFalse(created)
        assertTrue(owner.hasClip)
        clip.failStop = false
        owner.stop()
        assertFalse(owner.hasClip)
    }

    @Test fun `cancel immediately after play stops created player`() {
        val owner = AlarmClipOwner()
        val clip = Clip()
        var checks = 0
        owner.install({ clip }, { ++checks < 3 })
        assertTrue(clip.played)
        assertTrue(clip.stopped)
        assertFalse(owner.hasClip)
    }
}
