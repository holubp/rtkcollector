package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@org.junit.jupiter.api.Timeout(20)
class ReceiverRecoveryOwnershipTest {
    @Test fun `read begun before detach cannot clear the new outage`() {
        val fence = ReceiverReadRecoveryFence()
        val oldRead = fence.beforeRead()
        var alarm = false
        fence.detached { alarm = true }
        assertFalse(fence.publish(oldRead) { alarm = false })
        assertTrue(alarm)
        val newRead = fence.beforeRead()
        assertTrue(fence.publish(newRead) { alarm = false })
        assertFalse(alarm)
    }

    @Test fun `detach publication follows an admitted recovery without being lost`() {
        val fence = ReceiverReadRecoveryFence()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        val events = java.util.concurrent.CopyOnWriteArrayList<String>()
        val detachThread = java.util.concurrent.atomic.AtomicReference<Thread>()
        val attemptingDetach = CountDownLatch(1)
        val read = fence.beforeRead()
        try {
            val recovery = workers.submit { fence.publish(read) { entered.countDown(); release.await(); events.add("recovered") } }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val detach = workers.submit {
                detachThread.set(Thread.currentThread()); attemptingDetach.countDown()
                fence.detached { events.add("lost") }
            }
            assertTrue(attemptingDetach.await(10, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (detachThread.get().state != Thread.State.BLOCKED && !detach.isDone && System.nanoTime() < deadline) Thread.yield()
            assertEquals(Thread.State.BLOCKED, detachThread.get().state)
            assertTrue(events.isEmpty())
            release.countDown()
            recovery.get(10, TimeUnit.SECONDS); detach.get(10, TimeUnit.SECONDS)
            assertEquals(listOf("recovered", "lost"), events.toList())
        } finally { release.countDown(); workers.shutdownNow() }
    }
    @Test fun `Stop during a USB retry sleep is normal cancellation not an uncaught process crash`() {
        val active = AtomicBoolean(true)
        val sleeping = CountDownLatch(1)
        val uncaught = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val worker = Thread {
            runCaptureLoopWhileActive(active::get) {
                sleeping.countDown()
                Thread.sleep(60_000)
            }
        }.apply { uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> uncaught.set(error) } }
        try {
            worker.start()
            assertTrue(sleeping.await(10, TimeUnit.SECONDS))
            active.set(false)
            worker.interrupt()
            worker.join(10_000)
            assertFalse(worker.isAlive)
            assertNull(uncaught.get(), "InterruptedException must not reach Android's process-killing handler")
        } finally { active.set(false); worker.interrupt(); worker.join(10_000) }
    }
    @Test fun `unexpected active interruption resumes capture instead of leaving a dead running worker`() {
        val active = AtomicBoolean(true)
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val uncaught = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val worker = Thread {
            runCaptureLoopWhileActive(active::get) {
                if (attempts.incrementAndGet() == 1) {
                    Thread.currentThread().interrupt()
                    throw InterruptedException("transient")
                }
                assertFalse(Thread.currentThread().isInterrupted)
                active.set(false)
            }
        }.apply { uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> uncaught.set(error) } }
        worker.start(); worker.join(10_000)
        assertFalse(worker.isAlive)
        assertNull(uncaught.get())
        assertEquals(2, attempts.get())
    }
    @Test fun `stopped worker never enters capture`() {
        var entered = false
        runCaptureLoopWhileActive({ false }) { entered = true }
        assertFalse(entered)
    }
    @Test fun `cleanup failures retry without reopening admission or repeating the Stop fence`() {
        val dispatcher = RecordingStopDispatcher(retryDelayMillis = 1)
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val failures = java.util.concurrent.atomic.AtomicInteger()
        val fences = java.util.concurrent.atomic.AtomicInteger()
        val done = CountDownLatch(1)
        dispatcher.request({ fences.incrementAndGet() }, {
            if (attempts.incrementAndGet() == 1) error("transient cleanup failure")
            done.countDown()
        }, { failures.incrementAndGet() })
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(2, attempts.get())
        assertEquals(1, failures.get())
        assertEquals(1, fences.get())
    }
    @Test fun `reconnect retries are paced after completion but remain unlimited while active`() {
        val identity = UsbReconnectIdentity()
        val lease = identity.begin(10)
        for (attempt in 0 until 10) {
            val now = attempt * 2_000L
            assertTrue(identity.claimAttempt(lease, now))
            assertFalse(identity.claimAttempt(lease, now + 1))
            identity.finishAttempt(lease, now + 100)
            assertFalse(identity.claimAttempt(lease, now + 1_099))
        }
        identity.stop()
        assertFalse(identity.claimAttempt(lease, 99_000))
    }
    @Test fun `enumeration permission and reporting exceptions remain recoverable across retries`() {
        var failures = 0
        var attempts = 0
        for (phase in listOf("enumeration", "permission", "reporting")) {
            assertFalse(runUsbReconnectAttempt({ true }, { failures++ }) { attempts++; error(phase) })
        }
        assertTrue(runUsbReconnectAttempt({ true }, { failures++ }) { attempts++; true })
        assertEquals(4, attempts)
        assertEquals(3, failures)
    }
    @Test fun `stopped session does not attempt reconnect and rejects a late result`() {
        var active = false
        assertFalse(runUsbReconnectAttempt({ active }, { fail("must not report") }) { fail("must not open") })
        active = true
        assertFalse(runUsbReconnectAttempt({ active }, { fail("must not report") }) { active = false; true })
    }
    @Test fun `promoted USB identity detects new detach and Stop fences stale promotion`() {
        val identity = UsbReconnectIdentity()
        val old = identity.begin(10)
        var installed = 0
        assertTrue(identity.promote(old, 20) { installed++ })
        assertTrue(identity.matches(20))
        assertFalse(identity.matches(10))
        identity.stop()
        assertFalse(identity.promote(old, 30) { installed++ })
        identity.begin(40)
        assertFalse(identity.promote(old, 30) { installed++ })
        assertTrue(identity.matches(40))
        assertEquals(1, installed)
    }
    @Test fun `queued correction cannot transmit after Stop even if admission was requested earlier`() {
        val gate = ReceiverTxGate()
        val active = AtomicBoolean(true)
        val held = CountDownLatch(1); val release = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        val sent = AtomicBoolean(false)
        try {
            val first = workers.submit { gate.run({ true }) { held.countDown(); release.await() } }
            assertTrue(held.await(10, TimeUnit.SECONDS))
            val queued = workers.submit { runCatching { gate.run(active::get) { sent.set(true) } } }
            active.set(false)
            release.countDown()
            first.get(10, TimeUnit.SECONDS); queued.get(10, TimeUnit.SECONDS)
            assertFalse(sent.get())
        } finally { release.countDown(); workers.shutdownNow() }
    }
    @Test fun `whole baud transition excludes correction and normal retirement waits for TX`() {
        val gate = ReceiverTxGate()
        val between = CountDownLatch(1); val resume = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        val order = java.util.concurrent.CopyOnWriteArrayList<String>()
        try {
            val transition = workers.submit {
                gate.run({ true }) {
                    order.add("receiver baud")
                    between.countDown(); resume.await()
                    gate.run({ true }) { order.add("probe") }
                    order.add("host ready")
                }
            }
            assertTrue(between.await(10, TimeUnit.SECONDS))
            val correction = workers.submit { gate.run({ true }) { order.add("correction or retirement") } }
            assertEquals(listOf("receiver baud"), order.toList())
            resume.countDown()
            transition.get(10, TimeUnit.SECONDS); correction.get(10, TimeUnit.SECONDS)
            assertEquals(listOf("receiver baud", "probe", "host ready", "correction or retirement"), order.toList())
        } finally { resume.countDown(); workers.shutdownNow() }
    }
    @Test fun `Stop callback fences immediately while cleanup waits for blocked TX off caller`() {
        val active = AtomicBoolean(true)
        val cleanupEntered = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(1)
        val dispatcher = RecordingStopDispatcher()
        try {
            assertTrue(dispatcher.request({ active.set(false) }, {
                cleanupEntered.countDown(); release.await(); done.countDown()
            }, { fail("cleanup failed") }))
            assertFalse(active.get())
            assertTrue(cleanupEntered.await(10, TimeUnit.SECONDS))
            assertFalse(dispatcher.request({ fail("duplicate fence") }, { fail("duplicate shutdown") }, {}))
            release.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
        } finally { release.countDown() }
    }
}
