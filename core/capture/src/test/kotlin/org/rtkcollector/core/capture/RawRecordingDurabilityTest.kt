package org.rtkcollector.core.capture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RawRecordingDurabilityTest {
    private val policy = RawRecordingFlushPolicy(
        maxUnflushedBytes = 100,
        maxUnflushedMillis = 1_000,
    )

    @Test
    fun `flush becomes due at byte or time boundary`() {
        assertFalse(policy.isFlushDue(unflushedBytes = 99, elapsedSinceFlushMillis = 999))
        assertTrue(policy.isFlushDue(unflushedBytes = 100, elapsedSinceFlushMillis = 1))
        assertTrue(policy.isFlushDue(unflushedBytes = 1, elapsedSinceFlushMillis = 1_000))
    }

    @Test
    fun `empty raw buffer does not flush only because time elapsed`() {
        assertFalse(policy.isFlushDue(unflushedBytes = 0, elapsedSinceFlushMillis = 5_000))
    }

    @Test
    fun `invalid policy and counters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RawRecordingFlushPolicy(maxUnflushedBytes = 0, maxUnflushedMillis = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.isFlushDue(unflushedBytes = -1, elapsedSinceFlushMillis = 0)
        }
    }

    @Test
    fun `raw storage failures never request USB retry`() {
        assertEquals(
            CaptureFailureDisposition.FATAL_STORAGE_STOP,
            captureFailureDisposition(
                RawRecordingStorageException("raw write failed", IllegalStateException("disk full")),
            ),
        )
        assertEquals(
            CaptureFailureDisposition.RETRY_USB,
            captureFailureDisposition(IllegalStateException("USB serial read failed")),
        )
    }
}
