package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AlarmEffectDispatcherTest {
    private fun desired(revision: Long) = AlarmDesiredState(1, revision, emptyList(), null, false)
    @Test fun `closing drains final visual clear without waiting for a blocked effect`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val cleared = CountDownLatch(1)
        val dispatcher = AlarmEffectDispatcher(AlarmEffect { state, current ->
            if (state.revision == 1L) { entered.countDown(); release.await() }
            if (state.revision == 2L && current()) cleared.countDown()
        }, AlarmEffect { _, _ -> })
        try {
            dispatcher.offer(desired(1)); assertTrue(entered.await(10, TimeUnit.SECONDS))
            dispatcher.invalidate(1); dispatcher.close()
            release.countDown()
            assertTrue(cleared.await(10, TimeUnit.SECONDS))
        } finally { release.countDown(); dispatcher.close() }
    }
    @Test fun `invalidation permits an immediate clear effect for the stopped generation`() {
        val cleared = CountDownLatch(1)
        val dispatcher = AlarmEffectDispatcher(AlarmEffect { state, _ -> if (state.revision == 2L) cleared.countDown() }, AlarmEffect { _, _ -> })
        try {
            dispatcher.offer(desired(1)); dispatcher.invalidate(1)
            assertTrue(dispatcher.offer(desired(2)))
            assertTrue(cleared.await(10, TimeUnit.SECONDS))
        } finally { dispatcher.close() }
    }
    @Test fun `blocked notification does not block audio or bounded latest state submission`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val audio = CountDownLatch(1); val last = CountDownLatch(1)
        val oldCanceled = AtomicBoolean(false)
        val dispatcher = AlarmEffectDispatcher(
            AlarmEffect { state, current ->
                if (state.revision == 1L) {
                    entered.countDown(); release.await(10, TimeUnit.SECONDS)
                    oldCanceled.set(!current())
                }
                if (state.revision == 10_000L) last.countDown()
            }, AlarmEffect { _, _ -> audio.countDown() })
        try {
            assertTrue(dispatcher.offer(desired(1)))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            assertTrue(audio.await(10, TimeUnit.SECONDS))
            for (revision in 2L..10_000L) assertTrue(dispatcher.offer(desired(revision)))
            release.countDown()
            assertTrue(last.await(10, TimeUnit.SECONDS)); assertTrue(oldCanceled.get())
        } finally { release.countDown(); dispatcher.close() }
        assertFalse(dispatcher.offer(desired(10_001)))
    }
    @Test fun `throwing effects do not terminate worker or propagate to caller`() {
        val failures = CountDownLatch(2); val recovered = CountDownLatch(1)
        val dispatcher = AlarmEffectDispatcher(
            AlarmEffect { state, _ -> if (state.revision == 1L) error("notification unavailable") else recovered.countDown() },
            AlarmEffect { _, _ -> error("audio unavailable") }, onFailure = { failures.countDown() })
        try {
            dispatcher.offer(desired(1)); assertTrue(failures.await(10, TimeUnit.SECONDS))
            dispatcher.offer(desired(2)); assertTrue(recovered.await(10, TimeUnit.SECONDS))
        } finally { dispatcher.close() }
    }
}
