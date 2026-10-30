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
    @Test fun `queued RX recovered health event cannot undo a later detach`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        fun field(name: String) = RecordingForegroundService::class.java.getDeclaredField(name).apply { isAccessible = true }
        try {
            val monitor = field("recordingHealthMonitor").get(service) as org.rtkcollector.core.capture.RecordingHealthMonitor
            monitor.reset(0)
            monitor.recordReceiverRead(0, 60_000)
            val recovered = monitor.recordReceiverRead(10, 60_001)
            assertTrue(recovered.contains(org.rtkcollector.core.capture.RecordingHealthEvent.ReceiverRxRecovered))
            val lost = field("alarmReceiverLost").get(service) as java.util.concurrent.atomic.AtomicBoolean
            lost.set(true)
            (field("usbRecoveryNeeded").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)
            val before = field("state").get(service)
            RecordingForegroundService::class.java.declaredMethods.single { it.name == "handleRecordingHealthEvents" && it.parameterCount == 2 }
                .apply { isAccessible = true }.invoke(service, recovered, null)
            assertTrue(lost.get())
            assertSame(before, field("state").get(service))
        } finally { owner.destroy() }
    }
    @Test fun `accepted permission reply changes consent only and never repaints recovered service state`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        fun field(name: String) = RecordingForegroundService::class.java.getDeclaredField(name).apply { isAccessible = true }
        val active = field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean
        try {
            val device = org.robolectric.shadow.api.Shadow.newInstanceOf(android.hardware.usb.UsbDevice::class.java)
            val consent = field("usbConsent").get(service) as UsbRecoveryConsent
            active.set(true)
            consent.begin(7); consent.observe(device.deviceId)
            val ticket = requireNotNull(consent.request())
            val before = field("state").get(service)
            val receiver = field("usbPermissionReceiver").get(service) as android.content.BroadcastReceiver
            receiver.onReceive(service, usbConsentIntent(service, ticket)
                .putExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE, device)
                .putExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false))
            assertTrue(consent.denied)
            assertSame(before, field("state").get(service))
            consent.accessAvailable()
            receiver.onReceive(service, usbConsentIntent(service, ticket)
                .putExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE, device))
            assertSame(before, field("state").get(service))
            assertFalse(consent.denied)
        } finally { active.set(false); owner.destroy() }
    }

    @Test fun `queued consent request is fenced by Stop or next session before main dispatch`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        fun field(name: String) = RecordingForegroundService::class.java.getDeclaredField(name).apply { isAccessible = true }
        val active = field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean
        try {
            val device = org.robolectric.shadow.api.Shadow.newInstanceOf(android.hardware.usb.UsbDevice::class.java)
            val consent = field("usbConsent").get(service) as UsbRecoveryConsent
            active.set(true); consent.begin(7); consent.observe(device.deviceId)
            val request = RecordingForegroundService::class.java.getDeclaredMethod("requestRecoveryPermission", android.hardware.usb.UsbDevice::class.java)
                .apply { isAccessible = true }
            request.invoke(service, device)
            active.set(false); consent.stop()
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(field("usbPermissionIntent").get(service))
            active.set(true); consent.begin(8); consent.observe(device.deviceId)
            request.invoke(service, device)
            consent.begin(9); consent.observe(device.deviceId)
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(field("usbPermissionIntent").get(service))
        } finally { active.set(false); owner.destroy() }
    }
    @Test fun `USB recovery permission intent keeps ticket identity and uses Android device extra`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val ticket = UsbConsentTicket(7, 20, 1)
        val intent = usbConsentIntent(context, ticket)
        assertEquals(context.packageName, intent.`package`)
        assertEquals(ticket, usbConsentTicket(intent))
        assertFalse(intent.filterEquals(usbConsentIntent(context, ticket.copy(nonce = 2))))
        assertTrue(usbConsentFilter().matchData(intent.type, intent.scheme, intent.data) >= 0)
        assertTrue(usbConsentPendingIntent(context, ticket) != usbConsentPendingIntent(context, ticket.copy(nonce = 2)))
        val device = org.robolectric.shadow.api.Shadow.newInstanceOf(android.hardware.usb.UsbDevice::class.java)
        assertSame(device, Intent().putExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE, device).platformUsbDevice())
        assertNull(Intent().putExtra("usbDevice", device).platformUsbDevice())
    }
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
