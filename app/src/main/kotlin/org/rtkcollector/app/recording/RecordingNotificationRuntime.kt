package org.rtkcollector.app.recording

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.rtkcollector.app.alarms.AlarmActions
import org.rtkcollector.app.ui.MainActivity

/** Android work is confined to the delivery lane, except mandatory initial FGS setup. */
internal object RecordingNotificationRuntime {
    const val CHANNEL_ID = "rtkcollector-recording"
    const val NOTIFICATION_ID = 101
    private var instance: Pair<Context, RecordingNotificationDelivery>? = null

    @Synchronized fun get(context: Context): RecordingNotificationDelivery {
        val app = context.applicationContext
        instance?.takeIf { it.first === app }?.let { return it.second }
        instance?.second?.close()
        val manager = checkNotNull(app.getSystemService(NotificationManager::class.java)) { "Android notification service unavailable." }
        return RecordingNotificationDelivery { update ->
            if (update.text == null) manager.cancel(NOTIFICATION_ID)
            else manager.notify(NOTIFICATION_ID, notification(app, update.text, update.terminalAlarmGeneration))
        }.also { instance = app to it }
    }

    fun notification(context: Context, text: String, terminalAlarmGeneration: Long?): Notification {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("RtkCollector").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done).setContentIntent(open)
            .setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
            .apply {
                terminalAlarmGeneration?.let { generation ->
                    val stop = RecordingForegroundService.stopIntent(context)
                        .setData(Uri.parse("rtkcollector://alarm/stop/$generation"))
                        .putExtra(AlarmActions.GENERATION, generation)
                    addAction(Notification.Action.Builder(null, "Stop alarm", PendingIntent.getService(context, 8299,
                        stop, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
                }
            }.build()
    }
}
