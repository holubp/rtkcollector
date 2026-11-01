package org.rtkcollector.app.recording

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal data class RecordingStartupLease(
    val sessionId: String,
    val generation: Long,
    val cancellation: RecordingCancellation = RecordingCancellation(),
    val startedAtNanos: Long = System.nanoTime(),
)

/** The completion barrier covers queued work and failure reporting, not just launch. */
internal class RecordingStartupOwner(
    private val launch: (Runnable) -> Unit = { Thread(it, "rtkcollector-startup").start() },
) {
    private val lock = Any()
    private var generation = 0L
    private var current: RecordingStartupLease? = null
    private var stopped = CountDownLatch(0)
    val isBusy: Boolean get() = synchronized(lock) { stopped.count != 0L }
    val currentLease: RecordingStartupLease? get() = synchronized(lock) { current }

    fun start(sessionId: String, initialize: (RecordingStartupLease) -> Unit,
        failed: (RecordingStartupLease, Throwable) -> Unit): Boolean {
        val lease: RecordingStartupLease
        val completion: CountDownLatch
        synchronized(lock) {
            if (stopped.count != 0L || current?.cancellation?.isCancelled == false) return false
            lease = RecordingStartupLease(sessionId, ++generation)
            completion = CountDownLatch(1)
            current = lease
            stopped = completion
        }
        fun report(error: Throwable) {
            runCatching { failed(lease, error) }
                .onFailure { lease.cancellation.cancel() }
        }
        return try {
            launch(Runnable {
                try {
                    if (isCurrent(lease)) initialize(lease)
                } catch (error: Throwable) {
                    report(error)
                } finally { completion.countDown() }
            })
            true
        } catch (error: Throwable) {
            try { report(error) }
            finally { lease.cancellation.cancel(); completion.countDown() }
            false
        }
    }

    fun isCurrent(lease: RecordingStartupLease): Boolean = synchronized(lock) {
        current === lease && !lease.cancellation.isCancelled
    }

    fun publish(lease: RecordingStartupLease, publication: () -> Unit): Boolean = synchronized(lock) {
        if (!isCurrent(lease)) false else { publication(); true }
    }

    /** Canceled acquisition still belongs to this worker until its completion barrier exits. */
    fun retainForCleanup(lease: RecordingStartupLease, retention: () -> Unit): Boolean = synchronized(lock) {
        if (current !== lease || stopped.count == 0L) false else { retention(); true }
    }

    fun cancel() = synchronized(lock) { current?.cancellation?.cancel(); Unit }

    fun awaitStopped(timeoutMillis: Long): Boolean {
        require(timeoutMillis >= 0)
        val completion = synchronized(lock) { stopped }
        return try { completion.await(timeoutMillis, TimeUnit.MILLISECONDS) }
        catch (_: InterruptedException) { Thread.interrupted(); false }
    }
}
