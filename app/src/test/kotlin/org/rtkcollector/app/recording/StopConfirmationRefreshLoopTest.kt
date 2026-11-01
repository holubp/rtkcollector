package org.rtkcollector.app.recording

import org.junit.Assert.*
import org.junit.Test

class StopConfirmationRefreshLoopTest {
    @Test fun `rendering cost cannot delay activation beyond absolute boundary`() {
        val fixture = Fixture(renderCost = 15)
        fixture.start()
        fixture.advanceTo(2000)
        assertEquals(StopConfirmationPhase.ACTIVE, fixture.frame!!.phase)
        assertEquals(10, fixture.frame!!.remainingSeconds)
        assertEquals(1, fixture.pendingCount)
    }

    @Test fun `literal countdown expectations survive rendering cost`() {
        val fixture = Fixture(renderCost = 15)
        fixture.start()
        for ((at, seconds) in listOf(0L to 2, 999L to 2, 1000L to 1, 1999L to 1)) {
            fixture.advanceTo(at)
            assertEquals("at $at", StopConfirmationPhase.WAITING, fixture.frame!!.phase)
            assertEquals("at $at", seconds, fixture.frame!!.remainingSeconds)
        }
        for ((at, seconds) in listOf(2000L to 10, 3000L to 9, 4000L to 8, 5000L to 7,
            6000L to 6, 7000L to 5, 8000L to 4, 9000L to 3, 10000L to 2,
            11000L to 1, 11999L to 1)) {
            fixture.advanceTo(at)
            assertEquals("at $at", StopConfirmationPhase.ACTIVE, fixture.frame!!.phase)
            assertEquals("at $at", seconds, fixture.frame!!.remainingSeconds)
        }
        fixture.advanceTo(12000)
        assertEquals(1, fixture.dismissals)
        assertEquals(0, fixture.pendingCount)
    }

    @Test fun `elapsed target translates to different uptime without render drift`() {
        val fixture = Fixture(startElapsed = 50000, uptimeOffset = 49000, renderCost = 15)
        fixture.start()
        assertEquals(1100L, fixture.pendingAt)
        fixture.advanceTo(52000)
        assertEquals(StopConfirmationPhase.ACTIVE, fixture.frame!!.phase)
        assertEquals(10, fixture.frame!!.remainingSeconds)
    }

    @Test fun `late callbacks skip missed labels and do not extend expiry`() {
        val fixture = Fixture()
        fixture.start()
        fixture.deliverLateAt(9000)
        assertEquals(StopConfirmationPhase.ACTIVE, fixture.frame!!.phase)
        assertEquals(3, fixture.frame!!.remainingSeconds)
        fixture.deliverLateAt(12500)
        assertEquals(1, fixture.dismissals)
        assertEquals(0, fixture.pendingCount)
    }

    @Test fun `cancellation during rendering fences rescheduling`() {
        val fixture = Fixture()
        fixture.onRender = { fixture.loop.cancel() }
        fixture.start()
        assertEquals(0, fixture.pendingCount)
        fixture.loop.run()
        assertEquals(1, fixture.rendered)
        assertEquals(0, fixture.dismissals)
    }

    @Test fun `owner retirement cancels outstanding refresh`() {
        val fixture = Fixture()
        fixture.start()
        fixture.current = false
        fixture.advanceTo(100)
        assertEquals(1, fixture.rendered)
        assertEquals(1, fixture.dismissals)
        assertEquals(0, fixture.pendingCount)
    }

    @Test fun `cancel removes pending work and fences an already delivered callback`() {
        val fixture = Fixture()
        fixture.start()
        val callback = requireNotNull(fixture.pendingCallback)
        fixture.loop.cancel()
        callback.run()
        assertEquals(0, fixture.pendingCount)
        assertEquals(1, fixture.rendered)
        assertEquals(0, fixture.dismissals)
    }

    @Test fun `overdue target posts immediately rather than adding another delay`() {
        val fixture = Fixture(renderCost = 250)
        fixture.start()
        assertEquals(250L, fixture.pendingAt)
        fixture.advanceTo(250)
        assertEquals(350L, fixture.pendingAt)
        assertEquals(1, fixture.pendingCount)
    }

    @Test fun `owner retirement during rendering cannot schedule a refresh`() {
        val fixture = Fixture()
        fixture.onRender = { fixture.current = false }
        fixture.start()
        assertEquals(1, fixture.dismissals)
        assertEquals(0, fixture.pendingCount)
    }

    @Test fun `rendering across expiry dismisses immediately without another refresh`() {
        val fixture = Fixture()
        fixture.onRender = { fixture.elapsed = 12001 }
        fixture.start()
        assertEquals(1, fixture.dismissals)
        assertEquals(0, fixture.pendingCount)
    }

    private class Fixture(val startElapsed: Long = 0, val uptimeOffset: Long = 0, val renderCost: Long = 0) {
        var elapsed = startElapsed
        var current = true
        var frame: StopConfirmationFrame? = null
        var rendered = 0
        var dismissals = 0
        var onRender: () -> Unit = {}
        private var scheduled: Pair<Runnable, Long>? = null
        val pendingCount get() = if (scheduled == null) 0 else 1
        val pendingAt get() = scheduled?.second
        val pendingCallback get() = scheduled?.first
        val loop = StopConfirmationRefreshLoop(
            window = StopConfirmationWindow(startElapsed, StopConfirmationTiming()),
            elapsedMillis = { elapsed }, uptimeMillis = { elapsed - uptimeOffset },
            isCurrent = { current },
            render = { next ->
                rendered++
                if (frame != next) elapsed += renderCost
                frame = next
                onRender()
            },
            dismiss = { dismissals++ },
            postAtUptime = { runnable, at ->
                assertNull("only one outstanding refresh", scheduled)
                scheduled = runnable to at
            },
            remove = { runnable -> if (scheduled?.first === runnable) scheduled = null },
        )
        fun start() = loop.run()
        fun advanceTo(target: Long) {
            var budget = 1000
            while (scheduled?.second?.let { it + uptimeOffset <= target } == true) {
                check(budget-- > 0) { "refresh loop did not yield" }
                val next = requireNotNull(scheduled); scheduled = null
                elapsed = maxOf(elapsed, next.second + uptimeOffset)
                next.first.run()
            }
            elapsed = maxOf(elapsed, target)
        }
        fun deliverLateAt(target: Long) {
            val next = requireNotNull(scheduled); scheduled = null
            elapsed = target
            next.first.run()
        }
    }
}
