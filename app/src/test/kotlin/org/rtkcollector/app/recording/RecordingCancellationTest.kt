package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Timeout(15)
class RecordingCancellationTest {
    @Test fun `cancellation wakes a command delay without interrupting file IO`() {
        val token = RecordingCancellation()
        val entered = CountDownLatch(1)
        val done = CountDownLatch(1)
        val completed = AtomicBoolean(true)
        val interrupted = AtomicBoolean(true)
        val worker = Thread {
            entered.countDown()
            completed.set(token.awaitDelay(60_000))
            interrupted.set(Thread.currentThread().isInterrupted)
            done.countDown()
        }
        try {
            worker.start(); assertTrue(entered.await(5, TimeUnit.SECONDS))
            token.cancel()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertFalse(completed.get())
            assertFalse(interrupted.get())
            assertTrue(token.isCancelled)
            assertFalse(token.awaitDelay(0))
        } finally { token.cancel(); worker.join(5_000) }
    }

    @Test fun `active zero delay completes while negative delays are rejected`() {
        val token = RecordingCancellation()
        assertTrue(token.awaitDelay(0))
        assertThrows(IllegalArgumentException::class.java) { token.awaitDelay(-1) }
    }
}
