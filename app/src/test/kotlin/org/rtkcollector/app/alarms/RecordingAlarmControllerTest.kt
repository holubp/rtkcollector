package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class RecordingAlarmControllerTest {
    @Test fun `conflation preserves first valid fix and recovery without mock output`() {
        val clock = AtomicLong(0)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val lost = CountDownLatch(1); val recovered = CountDownLatch(1)
        val controller = RecordingAlarmController(clock::get, { state ->
            if (state.revision == 1L) { entered.countDown(); release.await(10, TimeUnit.SECONDS) }
            if (state.audio?.episodeId?.cause?.category == AlarmCategory.POSITION_LOST) lost.countDown()
            if (lost.count == 0L && state.visibleEpisodes.isEmpty()) recovered.countDown()
        })
        try {
            controller.post(AlarmCommand.Begin(1, AlarmSettingsSnapshot.defaults(), 0))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            controller.post(AlarmCommand.Position(1, AlarmPositionEvidence("main", 1, 0, true)))
            controller.post(AlarmCommand.Position(1, AlarmPositionEvidence("main", 2, 100, false)))
            clock.set(6_000); release.countDown()
            assertTrue(lost.await(10, TimeUnit.SECONDS))
            clock.set(6_001)
            controller.post(AlarmCommand.Position(1, AlarmPositionEvidence("main", 3, 6_001, true)))
            assertTrue(recovered.await(10, TimeUnit.SECONDS))
        } finally { release.countDown(); controller.close() }
    }
    @Test fun `stop invalidation does not wait for stalled advisory callback`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val invalidated = CountDownLatch(1)
        val controller = RecordingAlarmController({ 0 }, { entered.countDown(); release.await(10, TimeUnit.SECONDS) },
            onInvalidate = { invalidated.countDown() })
        try {
            controller.post(AlarmCommand.Begin(1, AlarmSettingsSnapshot.defaults(), 0))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            controller.userStop(1)
            assertTrue(invalidated.await(1, TimeUnit.SECONDS))
            assertFalse(controller.post(AlarmCommand.CauseRaised(1, RecordingAlarmEvidence.CONNECTION, "late", 1)))
        } finally { release.countDown(); controller.close() }
    }
}
