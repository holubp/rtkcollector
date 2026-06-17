package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StateBroadcastRateLimiterTest {
    @Test
    fun `routine broadcasts are limited to configured interval`() {
        val limiter = StateBroadcastRateLimiter(minIntervalMillis = 1_000)

        assertTrue(limiter.shouldBroadcast(10_000))
        assertFalse(limiter.shouldBroadcast(10_250))
        assertFalse(limiter.shouldBroadcast(10_999))
        assertTrue(limiter.shouldBroadcast(11_000))
    }

    @Test
    fun `reset allows the next routine broadcast immediately`() {
        val limiter = StateBroadcastRateLimiter(minIntervalMillis = 1_000)

        assertTrue(limiter.shouldBroadcast(10_000))
        assertFalse(limiter.shouldBroadcast(10_100))

        limiter.reset()

        assertTrue(limiter.shouldBroadcast(10_100))
    }
}
