package org.rtkcollector.app.recording

import org.junit.Assert.*
import org.junit.Test

class StopConfirmationPolicyTest {
    @Test fun `delay and full active window have independent deadlines`() {
        val window = StopConfirmationWindow(100, StopConfirmationTiming())
        assertEquals(StopConfirmationPhase.WAITING, window.phase(100))
        assertEquals(2, window.remainingSeconds(100))
        assertEquals(1, window.remainingSeconds(2099))
        assertEquals(StopConfirmationPhase.ACTIVE, window.phase(2100))
        assertEquals(10, window.remainingSeconds(2100))
        assertEquals(1, window.remainingSeconds(12099))
        assertEquals(StopConfirmationPhase.EXPIRED, window.phase(12100))
        assertEquals(0, window.remainingSeconds(99999))
    }

    @Test fun `settings validate both extrema and reject invalid ranges`() {
        StopConfirmationTiming(1, 1); StopConfirmationTiming(60, 120)
        for ((delay, timeout) in listOf(0 to 10, 61 to 10, 2 to 0, 2 to 121,
            Int.MAX_VALUE to 10, 2 to Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { StopConfirmationTiming(delay, timeout) }
        }
    }

    @Test fun `authorization rejects early expired replayed and stale requests`() {
        val authority = StopConfirmationAuthority()
        authority.activate("A")
        val first = requireNotNull(authority.open("home", 0, StopConfirmationTiming()))
        assertFalse(authority.consume(first.requestId, 1999, "A"))
        assertTrue(authority.consume(first.requestId, 2000, "A"))
        assertFalse(authority.consume(first.requestId, 2001, "A"))
        val expired = requireNotNull(authority.open("home", 3000, StopConfirmationTiming()))
        assertFalse(authority.consume(expired.requestId, 15000, "A"))
        val stale = requireNotNull(authority.open("home", 16000, StopConfirmationTiming()))
        authority.activate("B")
        assertFalse(authority.consume(stale.requestId, 18000, "B"))
        assertNull(authority.pending())
    }

    @Test fun `cancel retire and absent ownership cannot authorize stop`() {
        val authority = StopConfirmationAuthority()
        assertNull(authority.open("home", 0, StopConfirmationTiming()))
        authority.activate("startup")
        val request = requireNotNull(authority.open("recovery", 0, StopConfirmationTiming()))
        assertEquals(request, authority.open("home", 1, StopConfirmationTiming(10, 20)))
        authority.cancel(request.requestId)
        assertFalse(authority.consume(request.requestId, 2000, "startup"))
        val next = requireNotNull(authority.open("home", 3000, StopConfirmationTiming()))
        authority.retire("startup")
        assertFalse(authority.consume(next.requestId, 5000, "startup"))
    }

    @Test fun `failed diagnostic delivery does not prevent confirmation`() {
        val authority = StopConfirmationAuthority()
        authority.activate("A") { _, _ -> throw IllegalStateException("diagnostics unavailable") }
        val request = requireNotNull(authority.open("home", 0, StopConfirmationTiming()))
        assertTrue(authority.consume(request.requestId, 2000, "A"))
    }
}
