package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AlarmStateBroadcasterTest {
    @Test fun `blocked broadcast cannot delay stop acceptance and latest cleared state is delivered`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val cleared = CountDownLatch(1)
        val broadcaster = AlarmStateBroadcaster { snapshot ->
            if (snapshot.message != null) { entered.countDown(); release.await() }
            else cleared.countDown()
        }
        try {
            broadcaster.offer(AlarmBroadcastSnapshot(1, "lost"))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            broadcaster.offer(AlarmBroadcastSnapshot(generation = 1))
            assertEquals(1L, release.count)
            release.countDown()
            assertTrue(cleared.await(10, TimeUnit.SECONDS))
        } finally { release.countDown(); broadcaster.close() }
    }
}
