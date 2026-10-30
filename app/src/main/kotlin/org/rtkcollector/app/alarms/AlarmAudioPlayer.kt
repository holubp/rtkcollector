package org.rtkcollector.app.alarms

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import org.rtkcollector.app.R
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
    private val bluetoothPreference = AtomicBoolean(false)
    private val worker = HandlerThread("rtkcollector-alarm-player").apply { start() }
    private val handler = Handler(worker.looper)
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val policy = AlarmAudioPolicy()
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    @Volatile var onStatus: ((Long, Long, AlarmEffectStatus, String?) -> Unit)? = null
    @Volatile var statusBrief: String? = null
        private set
    @Volatile var routeBrief: String? = null
        private set
    @Volatile var lastProgressElapsedMillis: Long = SystemClock.elapsedRealtime()
        private set
    val isAlarmActive: Boolean get() = real.get().let { it.generation > canceledGeneration.get() && it.audio != null && it.audio != suppressed.get() }
    @Volatile private var focus = AlarmFocusState.WAITING
    @Volatile private var selected: AlarmAudioSelection? = null
    private var request: AudioFocusRequest? = null
    @Volatile private var focusToken: Any? = null
    private var ringtone: Ringtone? = null
    private val routedClip = AlarmClipOwner()
    private var devices: AlarmAudioDevices? = null
    private var routed = false
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
    fun setBluetoothPreference(enabled: Boolean) {
        if (bluetoothPreference.getAndSet(enabled) != enabled) startTicks()
    }

    private fun wanted(now: Long): AlarmAudioSelection? = real.get().takeIf { it.generation > canceledGeneration.get() }?.audio?.takeIf { it != suppressed.get() }
        ?: preview.get()?.takeIf { now < it.expires }?.selection
    private fun startTicks() {
        val origin = reportOrigin()
        if (scheduled.compareAndSet(false, true)) runCatching { check(handler.post(::tick)) }
            .onFailure { scheduled.set(false); report(origin, AlarmEffectStatus.FAILED, "Audio worker unavailable.") }
    }
    private fun tick() {
        val beforeReal = real.get()
        val beforePreview = preview.get()
        val beforeRouting = bluetoothPreference.get()
        val origin = AlarmAudioReportOrigin(beforeReal, wanted(SystemClock.elapsedRealtime()))
        try { updatePlayback(origin) } catch (_: Exception) {
            focus = AlarmFocusState.PERMANENTLY_LOST
            report(origin, AlarmEffectStatus.FAILED, "Alarm audio unavailable. Check sound and Android audio settings.")
        }
        val now = SystemClock.elapsedRealtime()
        if (focus == AlarmFocusState.PERMANENTLY_LOST) {
            runCatching { cleanupFailedAlarmPlayback(::abandonFocus, ::stopRingtone) }
        }
        if (wanted(now) != null && focus != AlarmFocusState.PERMANENTLY_LOST) {
            runCatching { check(handler.postDelayed(::tick, 100)) }
                .onFailure {
                    scheduled.set(false)
                    report(origin, AlarmEffectStatus.FAILED, "Audio scheduling unavailable.")
                    runCatching { cleanupFailedAlarmPlayback(::abandonFocus, ::stopRingtone) }
                }
        } else {
            if (wanted(now) == null) {
                runCatching { cleanupFailedAlarmPlayback(::abandonFocus, ::stopRingtone) }
                selected = null
                policy.decide(null, now, AlarmFocusState.WAITING)
                report(origin, AlarmEffectStatus.IDLE, null)
            }
            scheduled.set(false)
            if (real.get() !== beforeReal || preview.get() !== beforePreview || bluetoothPreference.get() != beforeRouting) startTicks()
        }
    }
    private fun updatePlayback(origin: AlarmAudioReportOrigin) {
        val now = SystemClock.elapsedRealtime()
        if (!origin.accepts(real.get(), wanted(now))) return
        val next = origin.selection
        if (next != selected) {
            cleanupFailedAlarmPlayback(::abandonFocus, ::stopRingtone)
            selected = next; resolvedUri = null; fallback = false; focus = AlarmFocusState.WAITING; nextCycle = 0
            if (next != null) {
                val identity = next
                val token = Any().also { focusToken = it }
                request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener({ change ->
                        if (selected == identity && focusToken === token) {
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
        if (routed != bluetoothPreference.get()) {
            stopRingtone()
            routed = bluetoothPreference.get()
            resolvedUri = null; fallback = false; nextCycle = 0
        }
        if (next == null) { report(origin, AlarmEffectStatus.IDLE, null); return }
        val decision = policy.decide(next, now, focus)
        if (decision.unavailable) {
            focus = AlarmFocusState.PERMANENTLY_LOST
            report(origin, AlarmEffectStatus.FAILED, "Alarm sound could not obtain audio focus. Visual alert remains where enabled.")
            return
        }
        if (decision.requestFocus) {
            val result = request?.let(manager::requestAudioFocus)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focus = AlarmFocusState.AVAILABLE
        }
        if (!decision.play) { stopRingtone(); report(origin, AlarmEffectStatus.PENDING, "Waiting for Android audio focus."); return }
        if (routed) { updateRoutedPlayback(origin, next, now); return }
        routeBrief = null
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
        report(origin, AlarmEffectStatus.ACTIVE, if (fallback) "Selected sound unavailable; using built-in alarm." else null)
    }
    private fun updateRoutedPlayback(origin: AlarmAudioReportOrigin, next: AlarmAudioSelection, now: Long) {
        fun current() = wanted(SystemClock.elapsedRealtime()) == next && bluetoothPreference.get()
        if (!routedClip.hasClip) {
            val audioDevices = devices ?: AlarmAudioDevices(manager, handler).also { devices = it }
            val sound = next.sound
            val uri = resolvedUri ?: when (sound) {
                is AlarmSoundChoice.BuiltIn -> builtinUri(context, sound.pattern)
                is AlarmSoundChoice.SystemNotification -> Uri.parse(sound.uri)
            }
            routedClip.install({ Media3AlarmClip(context, handler, uri,
                fallback || sound is AlarmSoundChoice.BuiltIn, audioDevices, ::current) }, ::current)
        }
        val state = routedClip.poll(now) ?: return
        if (origin.accepts(real.get(), wanted(SystemClock.elapsedRealtime()))) routeBrief = state.route
        if (state.state == AlarmClipState.FAILED) {
            if (!fallback && next.sound is AlarmSoundChoice.SystemNotification) {
                routedClip.stop()
                fallback = true
                resolvedUri = builtinUri(context, if (next.episodeId.cause.category == AlarmCategory.POSITION_LOST)
                    BuiltInAlarmSound.SINGLE_LOW else BuiltInAlarmSound.TRIPLE_HIGH)
                report(origin, AlarmEffectStatus.PENDING, "Selected sound unavailable; using built-in alarm.")
            } else {
                focus = AlarmFocusState.PERMANENTLY_LOST
                report(origin, AlarmEffectStatus.FAILED, state.warning ?: "Alarm audio unavailable.")
            }
            return
        }
        report(origin, if (state.state == AlarmClipState.PLAYING) AlarmEffectStatus.ACTIVE else AlarmEffectStatus.PENDING,
            state.warning ?: if (fallback) "Selected sound unavailable; using built-in alarm." else null)
    }
    private fun stopRingtone() {
        ringtone?.stop(); ringtone = null
        routedClip.stop()
        devices?.close(); devices = null
        routeBrief = null
    }
    private fun abandonFocus() {
        focusToken = null
        val abandoned = request
        request = null
        abandoned?.let { runCatching { manager.abandonAudioFocusRequest(it) } }
    }
    private fun reportOrigin() = AlarmAudioReportOrigin(real.get(), wanted(SystemClock.elapsedRealtime()))
    private fun report(origin: AlarmAudioReportOrigin, status: AlarmEffectStatus, message: String?) {
        lastProgressElapsedMillis = SystemClock.elapsedRealtime()
        if (!origin.accepts(real.get(), wanted(lastProgressElapsedMillis))) return
        statusBrief = message
        val report = listOf(origin.desired.generation, origin.desired.revision, origin.selection, status, message)
        if (report != lastReport) {
            lastReport = report
            origin.serviceIdentity()?.let { (generation, revision) ->
                runCatching { onStatus?.invoke(generation, revision, status, message) }
            }
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
