package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RecordingNotificationDeliveryTest {
    @TempDir lateinit var directory: Path

    @Test fun `destroyed service cannot resurrect its notification with a late higher revision`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val settled = CountDownLatch(1)
        val delivered = CopyOnWriteArrayList<String?>()
        val delivery = RecordingNotificationDelivery { update ->
            if (update.text == "in flight") { entered.countDown(); release.await() }
            delivered.add(update.text)
            if (update.text != "in flight") settled.countDown()
        }
        try {
            delivery.offer(RecordingNotificationUpdate(1, 1, "in flight"))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            delivery.retire(1)
            delivery.offer(RecordingNotificationUpdate(1, Long.MAX_VALUE, "late stale recording"))
            release.countDown()
            assertTrue(settled.await(10, TimeUnit.SECONDS))
            assertEquals(listOf("in flight", null), delivered.toList())
        } finally { release.countDown(); delivery.close() }
    }

    @Test fun `blocked notify does not block receiver append or finalization and Stop reconciles late delivery`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val cleared = CountDownLatch(1)
        val delivered = CopyOnWriteArrayList<String?>()
        val delivery = RecordingNotificationDelivery { update ->
            if (update.text == "recording") { entered.countDown(); release.await() }
            delivered.add(update.text)
            if (update.text == null) cleared.countDown()
        }
        val writers = PathRecordingSessionWriters.open(directory)
        try {
            delivery.offer(RecordingNotificationUpdate(1, 1, "recording"))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val bytes = ByteArray(16_384) { it.toByte() }
            writers.appendReceiverRx(bytes)
            delivery.offer(RecordingNotificationUpdate(1, 2, null))
            assertTrue(closeRecordingSessionWriters(writers, null).closeReport.issues.isEmpty())
            assertEquals(1L, release.count)
            assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("receiver-rx.raw")))
            release.countDown()
            assertTrue(cleared.await(10, TimeUnit.SECONDS))
            assertNull(delivered.last())
        } finally { release.countDown(); delivery.close(); writers.closeAll() }
    }

    @Test fun `old service cannot clear a newer recording after blocked delivery completes`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val newest = CountDownLatch(1)
        val delivered = CopyOnWriteArrayList<String?>()
        val delivery = RecordingNotificationDelivery { update ->
            if (update.text == "old") { entered.countDown(); release.await() }
            delivered.add(update.text)
            if (update.text == "new") newest.countDown()
        }
        try {
            delivery.offer(RecordingNotificationUpdate(1, 1, "old"))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            delivery.offer(RecordingNotificationUpdate(2, 1, "new"))
            delivery.retire(1)
            release.countDown()
            assertTrue(newest.await(10, TimeUnit.SECONDS))
            assertEquals("new", delivered.last())
        } finally { release.countDown(); delivery.close() }
    }
}
