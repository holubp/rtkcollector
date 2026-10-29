package org.rtkcollector.app.alarms

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.rtkcollector.app.R
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

/** Application-scoped playback owner shared by recording and short UI previews. */
class AlarmAudioPlayer private constructor(private val context: Context) : AlarmEffect {
    private data class Preview(val selection: AlarmAudioSelection, val expires: Long)
    private val real = AtomicReference(AlarmDesiredState.EMPTY)
    private val preview = AtomicReference<Preview?>(null)
    private val canceledGeneration = AtomicLong(-1)
    private val suppressed = AtomicReference<AlarmAudioSelection?>(null)
    private val scheduled = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "rtkcollector-alarm-player").apply { isDaemon = true } }
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val policy = AlarmAudioPolicy()
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    @Volatile var onStatus: ((Long, Long, AlarmEffectStatus, String?) -> Unit)? = null
    @Volatile var statusBrief: String? = null
        private set
    @Volatile var lastProgressElapsedMillis: Long = SystemClock.elapsedRealtime()
        private set
    val isAlarmActive: Boolean get() = real.get().let { it.generation > canceledGeneration.get() && it.audio != null && it.audio != suppressed.get() }
    @Volatile private var focus = AlarmFocusState.WAITING
    @Volatile private var selected: AlarmAudioSelection? = null
    private var request: AudioFocusRequest? = null
    private var ringtone: Ringtone? = null
    private var resolvedUri: Uri? = null
    private var nextCycle = 0L
    private var fallback = false
    private var lastReport: List<Any?>? = null

    override fun apply(state: AlarmDesiredState, isCurrent: () -> Boolean) {
        if (!isCurrent() || state.generation <= canceledGeneration.get()) return
        real.set(state)
        if (state.audio != null) preview.set(null)
        startTicks()
    }
    fun cancel(generation: Long) {
        canceledGeneration.updateAndGet { maxOf(it, generation) }
        real.updateAndGet { if (it.generation == generation) it.copy(audio = null) else it }
        preview.set(null); startTicks()
    }
    fun suppress(selection: AlarmAudioSelection) { suppressed.set(selection); startTicks() }
    fun preview(sound: AlarmSoundChoice) {
        if (isAlarmActive) return
        val now = SystemClock.elapsedRealtime()
        preview.set(Preview(AlarmAudioSelection(AlarmEpisodeId(-1,
            AlarmCauseKey(AlarmCategory.POSITION_LOST, "preview", "preview"), System.nanoTime()), sound), now + 3_000))
        startTicks()
    }
    fun stopPreview() { preview.set(null); startTicks() }

    private fun wanted(now: Long): AlarmAudioSelection? = real.get().takeIf { it.generation > canceledGeneration.get() }?.audio?.takeIf { it != suppressed.get() }
        ?: preview.get()?.takeIf { now < it.expires }?.selection
    private fun startTicks() {
        if (scheduled.compareAndSet(false, true)) runCatching { executor.execute(::tick) }
            .onFailure { scheduled.set(false); report(AlarmEffectStatus.FAILED, "Audio worker unavailable.") }
    }
    private fun tick() {
        val beforeReal = real.get()
        val beforePreview = preview.get()
        try { updatePlayback() } catch (_: Exception) {
            focus = AlarmFocusState.PERMANENTLY_LOST
            runCatching { stopRingtone() }
            report(AlarmEffectStatus.FAILED, "Alarm audio unavailable. Check sound and Android audio settings.")
        }
        val now = SystemClock.elapsedRealtime()
        if (wanted(now) != null && focus != AlarmFocusState.PERMANENTLY_LOST) {
            runCatching { executor.schedule(::tick, 100, TimeUnit.MILLISECONDS) }
                .onFailure { scheduled.set(false); report(AlarmEffectStatus.FAILED, "Audio scheduling unavailable.") }
        } else {
            if (wanted(now) == null) {
                runCatching { stopRingtone(); abandonFocus() }
                selected = null
                policy.decide(null, now, AlarmFocusState.WAITING)
                report(AlarmEffectStatus.IDLE, null)
            }
            scheduled.set(false)
            if (real.get() !== beforeReal || preview.get() !== beforePreview) startTicks()
        }
    }
    private fun updatePlayback() {
        val now = SystemClock.elapsedRealtime()
        val next = wanted(now)
        if (next != selected) {
            stopRingtone(); abandonFocus()
            selected = next; resolvedUri = null; fallback = false; focus = AlarmFocusState.WAITING; nextCycle = 0
            if (next != null) {
                val identity = next
                request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener({ change ->
                        if (selected == identity) {
                            focus = when (change) {
                                AudioManager.AUDIOFOCUS_GAIN -> AlarmFocusState.AVAILABLE
                                AudioManager.AUDIOFOCUS_LOSS -> AlarmFocusState.PERMANENTLY_LOST
                                else -> AlarmFocusState.WAITING
                            }
                            startTicks()
                        }
                    }, Handler(Looper.getMainLooper())).build()
            }
        }
        if (next == null) { report(AlarmEffectStatus.IDLE, null); return }
        val decision = policy.decide(next, now, focus)
        if (decision.unavailable) {
            stopRingtone(); abandonFocus(); focus = AlarmFocusState.PERMANENTLY_LOST
            report(AlarmEffectStatus.FAILED, "Alarm sound could not obtain audio focus. Visual alert remains where enabled.")
            return
        }
        if (decision.requestFocus) {
            val result = request?.let(manager::requestAudioFocus)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focus = AlarmFocusState.AVAILABLE
        }
        if (!decision.play) { stopRingtone(); report(AlarmEffectStatus.PENDING, "Waiting for Android audio focus."); return }
        if (ringtone != null && ringtone?.isPlaying == false) {
            ringtone = null
            nextCycle = now + if (next.sound is AlarmSoundChoice.SystemNotification) 1_600 else 0
            if (preview.get()?.selection == next) preview.set(null)
        }
        if (ringtone == null && now >= nextCycle && wanted(now) == next) {
            val uri = resolvedUri ?: when (val sound = next.sound) {
                is AlarmSoundChoice.BuiltIn -> builtinUri(context, sound.pattern)
                is AlarmSoundChoice.SystemNotification -> Uri.parse(sound.uri.takeIf(::isAlarmSoundUri)
                    ?: error("Unsupported sound URI"))
            }
            val loaded = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r").use { checkNotNull(it) }
                RingtoneManager.getRingtone(context, uri)
            }.getOrNull()
            val player = loaded ?: RingtoneManager.getRingtone(context,
                builtinUri(context, if (next.episodeId.cause.category == AlarmCategory.POSITION_LOST)
                    BuiltInAlarmSound.SINGLE_LOW else BuiltInAlarmSound.TRIPLE_HIGH)).also { fallback = true }
            checkNotNull(player) { "Built-in alarm unavailable" }
            resolvedUri = if (fallback) builtinUri(context, if (next.episodeId.cause.category == AlarmCategory.POSITION_LOST)
                BuiltInAlarmSound.SINGLE_LOW else BuiltInAlarmSound.TRIPLE_HIGH) else uri
            player.audioAttributes = attributes
            if (wanted(SystemClock.elapsedRealtime()) == next) {
                ringtone = player; player.play()
                if (wanted(SystemClock.elapsedRealtime()) != next) stopRingtone()
            }
        }
        report(AlarmEffectStatus.ACTIVE, if (fallback) "Selected sound unavailable; using built-in alarm." else null)
    }
    private fun stopRingtone() { ringtone?.stop(); ringtone = null }
    private fun abandonFocus() { request?.let { runCatching { manager.abandonAudioFocusRequest(it) } }; request = null }
    private fun report(status: AlarmEffectStatus, message: String?) {
        lastProgressElapsedMillis = SystemClock.elapsedRealtime()
        statusBrief = message
        val state = real.get()
        val report = listOf(state.generation, state.revision, status, message)
        if (report != lastReport) {
            lastReport = report
            runCatching { onStatus?.invoke(state.generation, state.revision, status, message) }
        }
    }
    companion object {
        @Volatile private var instance: AlarmAudioPlayer? = null
        fun get(context: Context): AlarmAudioPlayer = instance ?: synchronized(this) {
            instance ?: AlarmAudioPlayer(context.applicationContext).also { instance = it }
        }
        fun builtinUri(context: Context, sound: BuiltInAlarmSound): Uri = Uri.parse("android.resource://${context.packageName}/" +
            if (sound == BuiltInAlarmSound.SINGLE_LOW) R.raw.alarm_single_low else R.raw.alarm_triple_high)
    }
}
