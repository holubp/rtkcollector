package org.rtkcollector.app.alarms

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 27, 33])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AlarmPlatformTest {
    @Test fun `recording notification snapshot adds Stop only for terminal alarm shell`() {
        val context = RuntimeEnvironment.getApplication()
        val regular = org.rtkcollector.app.recording.RecordingNotificationRuntime.notification(context, "Recording", null)
        assertTrue(regular.actions.isNullOrEmpty())
        val terminal = org.rtkcollector.app.recording.RecordingNotificationRuntime.notification(context, "Recording stopped. Alarm active.", 20)
        assertEquals("Recording stopped. Alarm active.", terminal.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Stop alarm", terminal.actions.single().title.toString())
    }
    @Test fun `device callback failures are isolated and callbacks after close are ignored`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        var queries = 0
        val devices = AlarmAudioDevices(manager, android.os.Handler(android.os.Looper.getMainLooper())) {
            queries++
            if (queries > 1) error("device service unavailable")
            emptyList()
        }
        val callback = AlarmAudioDevices::class.java.getDeclaredField("callback").let {
            it.isAccessible = true; it.get(devices) as android.media.AudioDeviceCallback
        }
        val initialQueries = queries
        callback.onAudioDevicesRemoved(emptyArray())
        assertTrue(devices.devices.isEmpty())
        val version = devices.version
        assertEquals(initialQueries + 1, queries)
        devices.close()
        callback.onAudioDevicesAdded(emptyArray())
        assertEquals(initialQueries + 1, queries)
        assertEquals(version, devices.version)
    }
    @Test fun `routed local playback can be constructed and released without changing communication routing`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val originalMode = manager.mode
        val originalSpeaker = manager.isSpeakerphoneOn
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val devices = AlarmAudioDevices(manager, handler)
        try {
            assertTrue(LocalAlarmBandwidthMeter.getBitrateEstimate() > 0)
            assertNull(LocalAlarmBandwidthMeter.getTransferListener())
            val clip = Media3AlarmClip(context, handler,
                AlarmAudioPlayer.builtinUri(context, BuiltInAlarmSound.SINGLE_LOW), true, devices) { true }
            clip.stop()
            assertEquals(originalMode, manager.mode)
            assertEquals(originalSpeaker, manager.isSpeakerphoneOn)
        } finally { devices.close() }
    }
    @Test fun `late clear from an old generation cannot cancel a new recording alarm`() {
        val context = RuntimeEnvironment.getApplication()
        val publisher = AlarmNotificationPublisher(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val episode = AlarmEpisode(AlarmEpisodeId(200, RecordingAlarmEvidence.CONNECTION, 1), "lost", false)
        publisher.apply(AlarmDesiredState(200, 1, listOf(episode), null, false)) { true }
        publisher.apply(AlarmDesiredState(100, 2, emptyList(), null, false)) { true }
        assertTrue(manager.activeNotifications.any { it.notification.extras.getLong(AlarmActions.GENERATION) == 200L })
    }
    @Test fun `built in sound URIs refer to distinct packaged assets`() {
        val context = RuntimeEnvironment.getApplication()
        val low = AlarmAudioPlayer.builtinUri(context, BuiltInAlarmSound.SINGLE_LOW)
        val high = AlarmAudioPlayer.builtinUri(context, BuiltInAlarmSound.TRIPLE_HIGH)
        assertEquals("android.resource", low.scheme)
        assertNotEquals(low, high)
        context.contentResolver.openInputStream(low).use { assertTrue(it!!.read() >= 0) }
    }
    @Test fun `alarm channels are distinct silent and recovery removes warning`() {
        val context = RuntimeEnvironment.getApplication()
        val publisher = AlarmNotificationPublisher(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val episode = AlarmEpisode(AlarmEpisodeId(1, RecordingAlarmEvidence.CONNECTION, 1), "private path must not escape", false)
        publisher.apply(AlarmDesiredState(1, 1, listOf(episode), null, false)) { true }
        val notification = Shadows.shadowOf(manager).allNotifications.single()
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("private path"))
        assertEquals(0, notification.flags and Notification.FLAG_LOCAL_ONLY)
        assertNull(manager.getNotificationChannel(AlarmNotificationPublisher.channelId(AlarmCategory.CONNECTION_LOST)).sound)
        assertNotEquals(AlarmNotificationPublisher.channelId(AlarmCategory.CONNECTION_LOST),
            AlarmNotificationPublisher.channelId(AlarmCategory.POSITION_LOST))
        publisher.apply(AlarmDesiredState(1, 2, emptyList(), null, false)) { true }
        assertTrue(Shadows.shadowOf(manager).allNotifications.isEmpty())
    }
}
