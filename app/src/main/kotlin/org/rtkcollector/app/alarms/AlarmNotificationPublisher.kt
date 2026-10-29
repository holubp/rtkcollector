package org.rtkcollector.app.alarms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.rtkcollector.app.ui.MainActivity
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

object AlarmActions {
    const val ACK = "org.rtkcollector.app.recording.ACK_ALARM"
    const val GENERATION = "alarmGeneration"
    const val EPISODES = "alarmEpisodes"
    const val CATEGORY = "alarmCategory"
    fun token(id: AlarmEpisodeId): String = "${id.cause.category.name}|${id.cause.component}|${id.cause.code}|${id.revision}"
}

class AlarmNotificationPublisher(context: Context) : AlarmEffect {
    private val context = context.applicationContext
    private val newestGeneration = synchronized(generations) { generations.getOrPut(this.context) { AtomicLong(-1) } }
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    override fun apply(state: AlarmDesiredState, isCurrent: () -> Boolean) {
        if (!isCurrent() || state.generation < newestGeneration.updateAndGet { maxOf(it, state.generation) }) return
        manager.activeNotifications.filter { it.id in AlarmCategory.entries.map(::notificationId) &&
            it.notification.extras.getLong(AlarmActions.GENERATION, Long.MAX_VALUE) < state.generation }
            .forEach { manager.cancel(it.tag, it.id) }
        AlarmCategory.entries.forEach { category ->
            if (!isCurrent() || state.generation < newestGeneration.get()) return
            manager.createNotificationChannel(NotificationChannel(channelId(category), category.title, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(true); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
            val episodes = state.visibleEpisodes.filter { it.id.cause.category == category }
            if (episodes.isEmpty()) manager.cancel("alarm-${state.generation}", notificationId(category))
            else {
                val revision = episodes.maxOf { it.id.revision }
                val intent = Intent().setClassName(context, "org.rtkcollector.app.recording.RecordingForegroundService")
                    .setAction(AlarmActions.ACK)
                    .setData(Uri.parse("rtkcollector://alarm/${state.generation}/${category.name}/$revision"))
                    .putExtra(AlarmActions.GENERATION, state.generation)
                    .putExtra(AlarmActions.CATEGORY, category.name)
                    .putStringArrayListExtra(AlarmActions.EPISODES, ArrayList(episodes.map { AlarmActions.token(it.id) }))
                val ack = PendingIntent.getService(context, notificationId(category), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                val components = episodes.map { episode -> when (episode.id.cause.component) {
                    "corrections" -> "Correction intake"
                    "upload" -> "Caster upload"
                    "processing" -> "RTKLIB processing"
                    "storage" -> "Raw storage"
                    "receiver protocol" -> "Receiver protocol"
                    else -> "Recording"
                } }.distinct().joinToString(", ")
                val text = when (category) {
                    AlarmCategory.CONNECTION_LOST -> "Receiver data unavailable. Reconnecting."
                    AlarmCategory.POSITION_LOST -> "No fresh valid position. Receiver data may still be recording."
                    AlarmCategory.RECORDING_INTERRUPTED -> if (episodes.any { it.terminal }) "$components: recording stopped unexpectedly. Open app for details."
                        else "$components interrupted. Receiver recording continues."
                }
                val icon = when (category) {
                    AlarmCategory.CONNECTION_LOST -> android.R.drawable.ic_menu_close_clear_cancel
                    AlarmCategory.POSITION_LOST -> android.R.drawable.ic_menu_mylocation
                    AlarmCategory.RECORDING_INTERRUPTED -> android.R.drawable.stat_notify_error
                }
                val notification = Notification.Builder(context, channelId(category)).setSmallIcon(icon)
                    .setContentTitle(category.title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                    .setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open).setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_ERROR).addAction(Notification.Action.Builder(null, "Acknowledge", ack).build())
                    .setExtras(android.os.Bundle().apply { putLong(AlarmActions.GENERATION, state.generation); putLong("alarmRevision", revision) })
                    .build()
                if (isCurrent() && state.generation >= newestGeneration.get()) {
                    manager.notify("alarm-${state.generation}", notificationId(category), notification)
                    if (!isCurrent() || state.generation < newestGeneration.get())
                        manager.cancel("alarm-${state.generation}", notificationId(category))
                }
            }
        }
    }
    fun cancelAcknowledged(intent: Intent) {
        val category = runCatching { AlarmCategory.valueOf(intent.getStringExtra(AlarmActions.CATEGORY).orEmpty()) }.getOrNull() ?: return
        val tokens = intent.getStringArrayListExtra(AlarmActions.EPISODES).orEmpty()
        val revision = tokens.mapNotNull { it.substringAfterLast('|').toLongOrNull() }.maxOrNull() ?: return
        manager.activeNotifications.firstOrNull { it.id == notificationId(category) &&
            it.notification.extras.getLong(AlarmActions.GENERATION, -1) == intent.getLongExtra(AlarmActions.GENERATION, -2) &&
            it.notification.extras.getLong("alarmRevision", -1) == revision }?.let { manager.cancel(it.tag, it.id) }
    }
    companion object {
        private val generations = WeakHashMap<Context, AtomicLong>()
        fun channelId(category: AlarmCategory) = "recording-alarm-${category.name.lowercase()}"
        fun notificationId(category: AlarmCategory) = 8200 + category.ordinal
    }
}
