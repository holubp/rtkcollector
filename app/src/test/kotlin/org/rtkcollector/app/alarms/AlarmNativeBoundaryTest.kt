package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlarmNativeBoundaryTest {
    @Test fun `source is initialized before routing preparation and first beep`() {
        val calls = mutableListOf<String>()
        startCurrentAlarmPlayback({ true }, { calls.add("source") }, { calls.add("route"); true },
            { calls.add("prepare") }, { calls.add("play") })
        assertEquals(listOf("source", "route", "prepare", "play"), calls)
    }
    @Test fun `unavailable route never prepares or plays default audio`() {
        var calls = 0
        startCurrentAlarmPlayback({ true }, {}, { false }, { calls++ }, { calls++ })
        assertEquals(0, calls)
    }
    @Test fun `cancellation while preparing cannot start late audio`() {
        var current = true
        var played = false
        startCurrentAlarmPlayback({ current }, {}, { true }, { current = false }, { played = true })
        assertFalse(played)
    }
    @Test fun `canceled output creation is disposed and initialization retry allocates nothing`() {
        var current = true
        var created = 0
        var disposed = 0
        val create = { created++; current = false; Any() }
        assertThrows(IllegalStateException::class.java) {
            createCurrentAlarmOutput({ current }, create, { disposed++ }, { fail("unexpected disposal failure") })
        }
        assertEquals(1, created)
        assertEquals(1, disposed)
        assertThrows(IllegalStateException::class.java) {
            createCurrentAlarmOutput({ current }, create, { disposed++ }, { fail("unexpected disposal failure") })
        }
        assertEquals(1, created)
    }
    @Test fun `disposal failure on creation thread prevents replacement ownership`() {
        val guard = AlarmReleaseGuard()
        var current = true
        assertThrows(IllegalStateException::class.java) {
            createCurrentAlarmOutput({ current }, { current = false; Any() }, { error("native release failed") }, guard::unconfirmed)
        }
        var cleanupCalls = 0
        assertThrows(IllegalStateException::class.java) { guard.release { cleanupCalls++ } }
        assertEquals(1, cleanupCalls) // Still request renderer shutdown once; never claim disposal confirmed.
        assertThrows(IllegalStateException::class.java) { guard.release { cleanupCalls++ } }
        assertEquals(1, cleanupCalls)
    }
    @Test fun `successful player release cannot discard still pending native output`() {
        var now = 0L
        val guard = AlarmReleaseGuard({ now }, { now += it })
        var released = false
        assertThrows(IllegalStateException::class.java) { guard.release({ true }) { released = true } }
        assertTrue(released)
        assertEquals(500L, now)
        assertThrows(IllegalStateException::class.java) { guard.release { fail("must retain failed release") } }
    }
    @Test fun `completed native disposal allows subsequent replacement`() {
        var now = 0L
        val guard = AlarmReleaseGuard({ now }, { now += it })
        var releases = 0
        guard.release({ now < 100 }) { releases++ }
        guard.release { releases++ }
        assertEquals(100L, now)
        assertEquals(2, releases)
    }
}
