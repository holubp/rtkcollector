package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AlarmEffectsRuntimeTest {
    @Test fun `late failed effect cannot report failure against recovered revision`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val recovered = CountDownLatch(1)
        val failures = java.util.concurrent.atomic.AtomicInteger()
        val effects = AlarmEffectsRuntime(AlarmEffect { state, _ ->
            if (state.revision == 1L) { entered.countDown(); release.await(); error("old failure") }
            recovered.countDown()
        }, AlarmEffect { _, _ -> })
        val host = object : AlarmForegroundHost {
            override fun ownsAlarmGeneration(generation: Long) = true
            override fun applyAlarmForeground(state: AlarmDesiredState, current: () -> Boolean) = false
            override fun alarmDeliveryFailed(lane: String) { failures.incrementAndGet() }
        }
        try {
            effects.attach(host); effects.offer(desired(1))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            effects.offer(desired(1).copy(revision = 2, visibleEpisodes = emptyList(), activeEpisodes = emptyList(), audio = null))
            release.countDown(); assertTrue(recovered.await(10, TimeUnit.SECONDS))
            assertEquals(0, failures.get())
        } finally { release.countDown(); effects.close() }
    }
    private fun desired(generation: Long): AlarmDesiredState {
        val id = AlarmEpisodeId(generation, RecordingAlarmEvidence.CONNECTION, 1)
        return AlarmDesiredState(generation, 1, listOf(AlarmEpisode(id, "lost", false)),
            AlarmAudioSelection(id, AlarmSoundChoice.BuiltIn(BuiltInAlarmSound.TRIPLE_HIGH)), false)
    }
    @Test fun `blocked foreground promotion leaves notification lane and new generation free`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val visible = CountDownLatch(1); val newAudio = CountDownLatch(1)
        val oldAudio = java.util.concurrent.atomic.AtomicBoolean(false)
        val effects = AlarmEffectsRuntime(AlarmEffect { state, _ -> if (state.generation == 2L) visible.countDown() },
            AlarmEffect { state, _ ->
                if (state.generation == 1L && state.audio != null) oldAudio.set(true)
                if (state.generation == 2L && state.audio != null) newAudio.countDown()
            })
        val old = object : AlarmForegroundHost {
            override fun ownsAlarmGeneration(generation: Long) = generation == 1L
            override fun applyAlarmForeground(state: AlarmDesiredState, current: () -> Boolean): Boolean {
                entered.countDown(); release.await(); return true
            }
            override fun alarmDeliveryFailed(lane: String) {}
        }
        val next = object : AlarmForegroundHost {
            override fun ownsAlarmGeneration(generation: Long) = generation == 2L
            override fun applyAlarmForeground(state: AlarmDesiredState, current: () -> Boolean) = true
            override fun alarmDeliveryFailed(lane: String) {}
        }
        try {
            effects.attach(old); effects.offer(desired(1))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            effects.invalidate(1); effects.detach(old); effects.attach(next); effects.offer(desired(2))
            assertTrue(visible.await(10, TimeUnit.SECONDS))
            release.countDown(); assertTrue(newAudio.await(10, TimeUnit.SECONDS)); assertFalse(oldAudio.get())
        } finally { release.countDown(); effects.close() }
    }
    @Test fun `foreground rejection clears acoustic request instead of leaving old playback`() {
        val cleared = CountDownLatch(1)
        val effects = AlarmEffectsRuntime(AlarmEffect { _, _ -> }, AlarmEffect { state, _ -> if (state.audio == null) cleared.countDown() })
        val host = object : AlarmForegroundHost {
            override fun ownsAlarmGeneration(generation: Long) = true
            override fun applyAlarmForeground(state: AlarmDesiredState, current: () -> Boolean) = false
            override fun alarmDeliveryFailed(lane: String) {}
        }
        try { effects.attach(host); effects.offer(desired(1)); assertTrue(cleared.await(10, TimeUnit.SECONDS)) }
        finally { effects.close() }
    }
    @Test fun `exit request does not wait behind a stalled foreground call`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val exited = CountDownLatch(1)
        val effects = AlarmEffectsRuntime(AlarmEffect { _, _ -> }, AlarmEffect { _, _ -> })
        val host = object : AlarmForegroundHost {
            override fun ownsAlarmGeneration(generation: Long) = true
            override fun applyAlarmForeground(state: AlarmDesiredState, current: () -> Boolean): Boolean {
                entered.countDown(); release.await(); return true
            }
            override fun alarmDeliveryFailed(lane: String) {}
            override fun alarmStartId() = 7
            override fun finishAlarmService(generation: Long, startId: Int) {
                assertEquals(7, startId); exited.countDown()
            }
        }
        try {
            effects.attach(host); effects.offer(desired(1))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            effects.requestExit(host, 1)
            assertTrue(exited.await(10, TimeUnit.SECONDS)); assertEquals(1L, release.count)
        } finally { release.countDown(); effects.close() }
    }
}
