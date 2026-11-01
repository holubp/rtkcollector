package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Timeout(15)
class RecordingStartupOwnerTest {
    @Test fun `canceled startup retains its late resource for cleanup but cannot activate it`() {
        val owner = RecordingStartupOwner()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val retained = java.util.concurrent.atomic.AtomicBoolean()
        try {
            owner.start("old", { lease ->
                entered.countDown(); release.await()
                assertTrue(owner.retainForCleanup(lease) { retained.set(true) })
                assertFalse(owner.publish(lease) { fail("canceled startup cannot activate") })
            }, { _, error -> throw error })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            owner.cancel(); release.countDown()
            assertTrue(owner.awaitStopped(5_000))
            assertTrue(retained.get())
        } finally { release.countDown(); owner.cancel(); owner.awaitStopped(5_000) }
    }

    @Test fun `genuine failure after cancellation is reported before ownership exits`() {
        val owner = RecordingStartupOwner()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = java.nio.channels.ClosedChannelException()
        val observed = AtomicReference<Throwable>()
        try {
            owner.start("old", { entered.countDown(); release.await(); throw failure }, { _, error ->
                assertTrue(owner.isBusy)
                observed.set(error)
            })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            owner.cancel(); release.countDown()
            assertTrue(owner.awaitStopped(5_000))
            assertSame(failure, observed.get())
        } finally { release.countDown(); owner.cancel(); owner.awaitStopped(5_000) }
    }

    @Test fun `blocked initialization runs off caller and stays owned until it exits`() {
        val owner = RecordingStartupOwner()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val caller = Thread.currentThread()
        val worker = AtomicReference<Thread>()
        try {
            assertTrue(owner.start("session", {
                worker.set(Thread.currentThread()); entered.countDown(); release.await()
            }, { _, _ -> fail("unexpected initialization failure") }))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertNotSame(caller, worker.get())
            assertTrue(owner.isBusy)
            assertFalse(owner.start("duplicate", {}, { _, _ -> }))
            owner.cancel()
            assertFalse(owner.awaitStopped(1), "cancellation is not completion")
            assertFalse(owner.start("new", {}, { _, _ -> }))
            release.countDown()
            assertTrue(owner.awaitStopped(5_000))
            assertFalse(owner.isBusy)
        } finally { owner.cancel(); release.countDown(); owner.awaitStopped(5_000) }
    }

    @Test fun `Stop before queued launch prevents initialization and leaves no phantom owner`() {
        lateinit var task: Runnable
        val owner = RecordingStartupOwner(launch = { task = it })
        var initialized = false
        assertTrue(owner.start("old", { initialized = true }, { _, _ -> fail("cancellation is not failure") }))
        owner.cancel()
        assertFalse(owner.awaitStopped(0))
        task.run()
        assertFalse(initialized)
        assertTrue(owner.awaitStopped(0))
    }

    @Test fun `canceled acquisition cannot publish its late resource`() {
        val owner = RecordingStartupOwner()
        val acquired = CountDownLatch(1)
        val release = CountDownLatch(1)
        val rejected = CountDownLatch(1)
        var published = false
        try {
            owner.start("old", { lease ->
                acquired.countDown(); release.await()
                if (!owner.publish(lease) { published = true }) rejected.countDown()
            }, { _, _ -> fail("unexpected failure") })
            assertTrue(acquired.await(5, TimeUnit.SECONDS))
            owner.cancel(); release.countDown()
            assertTrue(rejected.await(5, TimeUnit.SECONDS))
            assertTrue(owner.awaitStopped(5_000))
            assertFalse(published)
        } finally { owner.cancel(); release.countDown(); owner.awaitStopped(5_000) }
    }

    @Test fun `completed initialization keeps its publication lease until Stop`() {
        val owner = RecordingStartupOwner()
        val lease = AtomicReference<RecordingStartupLease>()
        assertTrue(owner.start("session", { lease.set(it) }, { _, _ -> fail("unexpected failure") }))
        assertTrue(owner.awaitStopped(5_000))
        var published = false
        assertTrue(owner.publish(lease.get()) { published = true })
        assertTrue(published)
        assertFalse(owner.start("duplicate", {}, { _, _ -> }))
        owner.cancel()
        assertFalse(owner.publish(lease.get()) { fail("stale publication") })
    }

    @Test fun `worker launch failure releases admission and reports the actual failure`() {
        val expected = IllegalStateException("launcher failed")
        val observed = AtomicReference<Throwable>()
        val owner = RecordingStartupOwner(launch = { throw expected })
        assertFalse(owner.start("session", { fail("must not initialize") }, { _, error -> observed.set(error) }))
        assertSame(expected, observed.get())
        assertTrue(owner.awaitStopped(0))
        assertFalse(owner.isBusy)
    }

    @Test fun `a throwing failure observer cannot escape the startup worker or strand ownership`() {
        val thread = AtomicReference<Thread>()
        val uncaught = AtomicReference<Throwable>()
        val owner = RecordingStartupOwner(launch = { task ->
            Thread(task).apply {
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e -> uncaught.set(e) }
                thread.set(this); start()
            }
        })
        owner.start("session", { error("init failed") }, { _, _ -> error("observer failed") })
        assertTrue(owner.awaitStopped(5_000))
        thread.get().join(5_000)
        assertFalse(thread.get().isAlive)
        assertNull(uncaught.get())
        assertFalse(owner.isBusy)
    }
}
