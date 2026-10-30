package org.rtkcollector.app.recording

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/** Stop cancellation must never reach Android's process-killing uncaught handler. */
internal fun runCaptureLoopWhileActive(active: () -> Boolean, capture: () -> Unit) {
    while (active()) {
        try {
            capture()
            return
        } catch (_: InterruptedException) {
            Thread.interrupted()
        }
    }
}

internal fun runUsbReconnectAttempt(active: () -> Boolean, failed: (Throwable) -> Unit, attempt: () -> Boolean): Boolean {
    if (!active()) return false
    return try { attempt() && active() } catch (failure: Throwable) {
        // Failure reporting is advisory; fatal-storage fencing is handled first by the caller.
        runCatching { failed(failure) }
        false
    }
}

data class UsbConnectionLease(val generation: Long, val deviceId: Int)

/** Only short in-memory publication belongs here, never USB IO or writer teardown. */
internal class ReceiverReadRecoveryFence {
    private var revision = 0L
    @Synchronized fun beforeRead(): Long = revision
    @Synchronized fun detached(publish: () -> Unit) { revision++; publish() }
    @Synchronized fun publish(readRevision: Long, allowed: () -> Boolean = { true }, publish: () -> Unit): Boolean {
        if (readRevision != revision || !allowed()) return false
        publish()
        return true
    }
}

/** This lock protects only assignments, never USB IO, TX admission, or writer teardown. */
class UsbReconnectIdentity {
    private var generation = 0L
    private var active: UsbConnectionLease? = null
    private var attemptOwned = false
    private var retryAfter = 0L
    @Synchronized fun begin(deviceId: Int): UsbConnectionLease = UsbConnectionLease(++generation, deviceId).also {
        active = it; attemptOwned = false; retryAfter = 0
    }
    @Synchronized fun current(): UsbConnectionLease? = active
    @Synchronized fun isCurrent(lease: UsbConnectionLease): Boolean = active?.generation == lease.generation
    @Synchronized fun matches(deviceId: Int): Boolean = active?.deviceId == deviceId
    @Synchronized fun stop() { active = null; attemptOwned = false }
    @Synchronized fun claimAttempt(lease: UsbConnectionLease, now: Long): Boolean {
        if (!isCurrent(lease) || attemptOwned || now < retryAfter) return false
        attemptOwned = true
        return true
    }
    @Synchronized fun finishAttempt(lease: UsbConnectionLease, now: Long) {
        if (isCurrent(lease)) { attemptOwned = false; retryAfter = now + 1_000 }
    }
    @Synchronized fun promote(lease: UsbConnectionLease, deviceId: Int, install: () -> Unit): Boolean {
        if (!isCurrent(lease)) return false
        install()
        active = lease.copy(deviceId = deviceId)
        return true
    }
}

/** Reentrant for a whole transition containing individual command/probe writes. */
class ReceiverTxGate {
    private val lock = ReentrantLock()
    fun <T> run(current: () -> Boolean, operation: () -> T): T {
        lock.lockInterruptibly()
        try {
            check(current()) { "Receiver operation canceled or connection replaced." }
            return operation()
        } finally { lock.unlock() }
    }
    fun tryShutdown(timeoutMillis: Long, operation: () -> Unit): Boolean {
        if (!lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)) return false
        try { operation(); return true } finally { lock.unlock() }
    }
}

/** Accept/fence synchronously, but never execute blocking teardown on the service callback. */
class RecordingStopDispatcher(private val retryDelayMillis: Long = 1_000) {
    init { require(retryDelayMillis in 1..10_000) }
    private val busy = AtomicBoolean(false)
    val isBusy: Boolean get() = busy.get()
    fun request(fence: () -> Unit, cleanup: () -> Unit, failed: (Throwable) -> Unit): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        fence()
        return try {
            Thread({
                try {
                    var delay = retryDelayMillis
                    while (true) {
                        try { cleanup(); break } catch (failure: Throwable) {
                            runCatching { failed(failure) }
                            runCatching { Thread.sleep(delay) }
                            delay = (delay * 2).coerceAtMost(10_000)
                        }
                    }
                } finally { busy.set(false) }
            }, "rtkcollector-stop-coordinator").apply { isDaemon = true; start() }
            true
        } catch (failure: Throwable) {
            busy.set(false)
            runCatching { failed(failure) }
            false
        }
    }
}
