package org.rtkcollector.app.recording

import java.util.concurrent.TimeUnit

/** A session wake signal that never interrupt-closes storage IO. */
internal class RecordingCancellation {
    private val lock = Object()
    @Volatile var isCancelled: Boolean = false
        private set

    fun cancel() = synchronized(lock) {
        isCancelled = true
        lock.notifyAll()
    }

    fun awaitDelay(delayMillis: Long): Boolean {
        require(delayMillis >= 0)
        val started = System.nanoTime()
        val duration = TimeUnit.MILLISECONDS.toNanos(delayMillis)
        return synchronized(lock) {
            while (!isCancelled) {
                val remaining = duration - (System.nanoTime() - started)
                if (remaining <= 0) return@synchronized true
                try { TimeUnit.NANOSECONDS.timedWait(lock, remaining) }
                catch (_: InterruptedException) {
                    // External interruption is not permission to shorten receiver settling.
                }
            }
            false
        }
    }
}
