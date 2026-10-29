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
@Config(sdk = [33])
class AlarmPlatformTest {
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
