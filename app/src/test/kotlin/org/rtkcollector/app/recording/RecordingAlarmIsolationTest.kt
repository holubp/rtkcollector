package org.rtkcollector.app.recording

import org.rtkcollector.app.alarms.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

class RecordingAlarmIsolationTest {
    @TempDir lateinit var directory: Path
    @Test fun `blocked player creation after Stop cannot delay raw close or start late audio`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val current = java.util.concurrent.atomic.AtomicBoolean(true)
        val played = java.util.concurrent.atomic.AtomicBoolean(false)
        val stopped = java.util.concurrent.atomic.AtomicBoolean(false)
        val owner = AlarmClipOwner()
        val worker = Executors.newSingleThreadExecutor()
        val writers = PathRecordingSessionWriters.open(directory)
        try {
            val creation = worker.submit {
                owner.install({
                    entered.countDown(); release.await()
                    object : AlarmClipPlayback {
                        override fun play() { played.set(true) }
                        override fun poll(now: Long) = AlarmClipStatus(AlarmClipState.PLAYING)
                        override fun stop() { stopped.set(true) }
                    }
                }, current::get)
            }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val bytes = ByteArray(32_768) { it.toByte() }
            writers.appendReceiverRx(bytes)
            current.set(false)
            assertTrue(closeRecordingSessionWriters(writers, null).closeReport.issues.isEmpty())
            assertEquals(1L, release.count)
            assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("receiver-rx.raw")))
            release.countDown()
            creation.get(10, TimeUnit.SECONDS)
            assertFalse(played.get())
            assertTrue(stopped.get())
        } finally { release.countDown(); worker.shutdownNow(); writers.closeAll() }
    }
    @Test fun `blocked alarm state broadcast does not own raw finalization`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val broadcaster = AlarmStateBroadcaster { entered.countDown(); release.await() }
        val writers = PathRecordingSessionWriters.open(directory)
        val close = Executors.newSingleThreadExecutor()
        try {
            broadcaster.offer(AlarmBroadcastSnapshot(1, "lost"))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val bytes = ByteArray(4_096) { it.toByte() }
            writers.appendReceiverRx(bytes)
            broadcaster.offer(AlarmBroadcastSnapshot(generation = 1))
            val result = close.submit<RecordingSessionFinalization> { closeRecordingSessionWriters(writers, null) }.get(10, TimeUnit.SECONDS)
            assertTrue(result.closeReport.issues.isEmpty())
            assertEquals(1L, release.count)
            assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("receiver-rx.raw")))
        } finally { release.countDown(); broadcaster.close(); writers.closeAll(); close.shutdownNow() }
    }
    @Test fun `blocked alarm effect cannot retain raw stream or stop finalization`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val dispatcher = AlarmEffectDispatcher(AlarmEffect { _, _ -> entered.countDown(); release.await() }, AlarmEffect { _, _ -> })
        val controller = RecordingAlarmController({ 0 }, { dispatcher.offer(it) }, dispatcher::invalidate)
        val writers = PathRecordingSessionWriters.open(directory)
        val close = Executors.newSingleThreadExecutor()
        try {
            controller.post(AlarmCommand.Begin(1, AlarmSettingsSnapshot.defaults(), 0))
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val bytes = ByteArray(32_768) { it.toByte() }
            writers.appendReceiverRx(bytes); writers.flushRaw()
            controller.userStop(1)
            val result = close.submit<RecordingSessionFinalization> { closeRecordingSessionWriters(writers, null) }.get(10, TimeUnit.SECONDS)
            assertTrue(result.closeReport.issues.isEmpty())
            assertEquals(1L, release.count)
            assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("receiver-rx.raw")))
        } finally { release.countDown(); controller.close(); dispatcher.close(); writers.closeAll(); close.shutdownNow() }
    }
}
