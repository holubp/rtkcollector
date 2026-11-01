package org.rtkcollector.app.recording

internal data class StopConfirmationFrame(val phase: StopConfirmationPhase, val remainingSeconds: Int)

/** Refresh policy is independent of Android rendering and never requests recording Stop. */
internal class StopConfirmationRefreshLoop(
    private val window: StopConfirmationWindow,
    private val elapsedMillis: () -> Long,
    private val uptimeMillis: () -> Long,
    private val isCurrent: () -> Boolean,
    private val render: (StopConfirmationFrame) -> Unit,
    private val dismiss: () -> Unit,
    private val postAtUptime: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit,
) : Runnable {
    private var cancelled = false

    fun cancel() {
        cancelled = true
        remove(this)
    }

    override fun run() {
        if (cancelled) return
        remove(this)
        if (!isCurrent()) { cancel(); dismiss(); return }
        val now = elapsedMillis()
        val phase = window.phase(now)
        if (phase == StopConfirmationPhase.EXPIRED) { cancel(); dismiss(); return }
        val nextSecond = window.openedAtMillis + ((now - window.openedAtMillis) / 1000 + 1) * 1000
        val targetElapsed = minOf(now + 100, nextSecond)
        render(StopConfirmationFrame(phase, window.remainingSeconds(now)))
        if (cancelled) return
        val afterRender = elapsedMillis()
        if (!isCurrent() || window.phase(afterRender) == StopConfirmationPhase.EXPIRED) {
            cancel(); dismiss(); return
        }
        // Rendering can consume time; keep the pre-render target instead of drifting.
        val remaining = maxOf(0, targetElapsed - afterRender)
        postAtUptime(this, uptimeMillis() + remaining)
    }
}
