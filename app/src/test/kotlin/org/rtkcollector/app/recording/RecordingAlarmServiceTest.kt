package org.rtkcollector.app.recording

import android.content.Intent
import android.content.Context
import android.app.NotificationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.annotation.Config
import org.rtkcollector.app.alarms.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35])
class RecordingAlarmServiceTest {
    @Test fun `deliberate Stop fences an already queued delivery failure`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        try {
            RecordingForegroundService::class.java.getDeclaredField("alarmGeneration")
                .apply { isAccessible = true }.setLong(service, 77)
            val old = AlarmDesiredState(77, 2, emptyList(), null, false)
            RecordingForegroundService::class.java.getDeclaredField("alarmLastDesired")
                .apply { isAccessible = true }.set(service, old)
            service.alarmDeliveryFailed("audio", old)
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP), 0, 1)
            shadowOf(Looper.getMainLooper()).idle()
            val broadcast = RecordingForegroundService::class.java.getDeclaredField("alarmBroadcast").apply { isAccessible = true }
            assertNull((broadcast.get(service) as AlarmBroadcastSnapshot).delivery)
        } finally { owner.destroy() }
    }
    @Test fun `posted delivery failures cannot repaint a recovered revision`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        try {
            RecordingForegroundService::class.java.getDeclaredField("alarmGeneration")
                .apply { isAccessible = true }.setLong(service, 77)
            val recovered = AlarmDesiredState(77, 2, emptyList(), null, false)
            RecordingForegroundService::class.java.getDeclaredField("alarmLastDesired")
                .apply { isAccessible = true }.set(service, recovered)
            val broadcast = RecordingForegroundService::class.java.getDeclaredField("alarmBroadcast").apply { isAccessible = true }
            service.alarmDeliveryFailed("notification", recovered.copy(revision = 1))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull((broadcast.get(service) as AlarmBroadcastSnapshot).delivery)
            service.alarmDeliveryFailed("notification", recovered)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Alarm notification delivery unavailable.", (broadcast.get(service) as AlarmBroadcastSnapshot).delivery)
        } finally { owner.destroy() }
    }
    @Test fun `rejected Start preserves the existing terminal alarm service`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        try {
            RecordingForegroundService::class.java.getDeclaredField("alarmGeneration")
                .apply { isAccessible = true }.setLong(service, 88)
            val controller = RecordingForegroundService::class.java.getDeclaredField("alarmController")
                .apply { isAccessible = true }.get(service) as RecordingAlarmController
            controller.post(AlarmCommand.Begin(88, AlarmSettingsSnapshot.defaults(), 0))
            controller.post(AlarmCommand.TerminalStop(88, RecordingAlarmEvidence.STORAGE, "storage", 1))
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!controller.snapshot().terminalAlertPending && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue(controller.snapshot().terminalAlertPending)
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_START), 0, 1)
            assertFalse(shadowOf(service).isStoppedBySelf)
            assertEquals(88L, controller.snapshot().generation)
            assertTrue(controller.snapshot().terminalAlertPending)
        } finally { owner.destroy() }
    }
    @Test fun `Stop after alert service exited clears the retained visual alarm`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val publisher = AlarmNotificationPublisher(service)
        val episode = AlarmEpisode(AlarmEpisodeId(0, RecordingAlarmEvidence.STORAGE, 1), "storage", true)
        publisher.apply(AlarmDesiredState(0, 1, listOf(episode), null, true)) { true }
        try {
            assertFalse(manager.activeNotifications.isEmpty())
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP).putExtra(AlarmActions.GENERATION, 0L), 0, 1)
            val deadline = System.nanoTime() + 10_000_000_000L
            while (manager.activeNotifications.isNotEmpty() && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue(manager.activeNotifications.isEmpty())
        } finally { owner.destroy() }
    }
    @Test fun `deliberate Stop fences active alarms even without running capture`() {
        assertEquals("alarmTerminalPending", RecordingForegroundService.EXTRA_STATE_ALARM_TERMINAL_PENDING)
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        try {
            val generation = RecordingForegroundService::class.java.getDeclaredField("alarmGeneration").apply { isAccessible = true }
            generation.setLong(service, 77)
            val controller = RecordingForegroundService::class.java.getDeclaredField("alarmController")
                .apply { isAccessible = true }.get(service) as RecordingAlarmController
            val defaults = AlarmSettingsSnapshot.defaults()
            controller.post(AlarmCommand.Begin(77, defaults.copy(categories = defaults.categories.mapValues { it.value.copy(acoustic = false) }), 0))
            controller.post(AlarmCommand.CauseRaised(77, RecordingAlarmEvidence.CONNECTION, "lost", 1))
            var deadline = System.nanoTime() + 10_000_000_000L
            while (controller.snapshot().visibleEpisodes.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5)
            assertFalse(controller.snapshot().visibleEpisodes.isEmpty())
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java).setAction(RecordingForegroundService.ACTION_STOP), 0, 1)
            deadline = System.nanoTime() + 10_000_000_000L
            while (controller.snapshot().visibleEpisodes.isNotEmpty() && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue(controller.snapshot().visibleEpisodes.isEmpty())
            assertFalse(controller.post(AlarmCommand.CauseRaised(77, RecordingAlarmEvidence.CONNECTION, "late", 2)))
        } finally { owner.destroy() }
    }
}
