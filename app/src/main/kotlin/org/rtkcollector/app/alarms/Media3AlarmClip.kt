package org.rtkcollector.app.alarms

import android.content.Context
import android.media.AudioTrack
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import androidx.media3.exoplayer.upstream.BandwidthMeter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Local sound decoding and per-player routing. Called only on the alarm Looper. */
internal class Media3AlarmClip(context: Context, handler: Handler, private val uri: Uri,
    private val builtIn: Boolean, private val devices: AlarmAudioDevices,
    private val current: () -> Boolean) : AlarmClipPlayback {
    private val alive = AtomicBoolean(true)
    private val track = AtomicReference<AudioTrack?>(null)
    private val routes = AlarmRoutingPolicy()
    private var appliedTrack: AudioTrack? = null
    private var appliedDevice: Int? = null
    private var error: AlarmPlaybackAttempt? = null
    private val attempts = AlarmPlaybackAttempts()
    private val progress = AlarmPlaybackProgress()
    private val releaseGuard = AlarmReleaseGuard()
    private val player: ExoPlayer
    private val outputProvider: AudioOutputProvider

    init {
        require(isAlarmSoundUri(uri.toString()))
        val provider = object : ForwardingAudioOutputProvider(AudioTrackAudioOutputProvider.Builder(context).build()) {
            override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
                val output = try {
                    createCurrentAlarmOutput({ alive.get() && current() }, { super.getAudioOutput(config) },
                        AudioOutput::release, releaseGuard::unconfirmed)
                } catch (failure: Exception) { throw AudioOutputProvider.InitializationException(failure) }
                if (output is AudioTrackAudioOutput) track.set(output.audioTrack)
                return object : AudioOutput by output {
                    override fun play() { if (alive.get() && current()) output.play() }
                }
            }
        }
        outputProvider = provider
        player = ExoPlayer.Builder(context)
            .setLooper(handler.looper)
            .setAudioOutputProvider(provider)
            .setBandwidthMeter(BandwidthMeter.NO_OP)
            .setUsePlatformDiagnostics(false)
            .setReleaseTimeoutMs(500)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(500, 2_000, 250, 500)
                .setTargetBufferBytes(512 * 1024).setPrioritizeTimeOverSizeThresholds(false).build())
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_ALARM)
                .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION).build(), false)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(failure: PlaybackException) {
                if (failure.errorCode == PlaybackException.ERROR_CODE_TIMEOUT) releaseGuard.timeout()
                if (alive.get()) error = attempts.failure()
            }
        })
        player.repeatMode = if (builtIn) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override fun play() {
        check(alive.get())
        if (!current()) return
        val now = SystemClock.elapsedRealtime()
        progress.start(now)
        startCurrentAlarmPlayback(current, { player.setMediaItem(MediaItem.fromUri(uri)) }, {
            val route = applyRoute(now)
            attempts.prepare(route)
            !route.failed
        }, player::prepare, player::play)
    }

    override fun poll(now: Long): AlarmClipStatus {
        if (!current() || !alive.get()) return AlarmClipStatus(AlarmClipState.ENDED)
        val failure = error
        var decision = applyRoute(now)
        if (decision.failed) return AlarmClipStatus(AlarmClipState.FAILED, decision.label, decision.warning)
        if (failure == null) attempts.confirm(decision, player.isPlaying)
        when (progress.observe(now, player.isPlaying, player.playbackState == Player.STATE_ENDED,
            failure != null, (failure ?: attempts.failure()).speaker)) {
            AlarmPlaybackAction.RETRY_SPEAKER -> {
                val failedAttempt = failure ?: attempts.failure()
                failedAttempt.deviceId?.let { routes.reject(it,
                    if (failedAttempt.speaker) AlarmRouteKind.SPEAKER else AlarmRouteKind.BLUETOOTH) }
                error = null
                progress.start(now)
                startCurrentAlarmPlayback(current, {}, {
                    decision = applyRoute(now)
                    attempts.prepare(decision)
                    !decision.failed
                }, player::prepare, player::play)
            }
            AlarmPlaybackAction.FAILED -> return AlarmClipStatus(AlarmClipState.FAILED,
                decision.label, "Alarm sound could not be played.")
            AlarmPlaybackAction.REPLAY -> startCurrentAlarmPlayback(current, { player.seekTo(0) },
                { !decision.failed }, {}, player::play)
            AlarmPlaybackAction.CONTINUE -> Unit
        }
        if (decision.failed) return AlarmClipStatus(AlarmClipState.FAILED, decision.label, decision.warning)
        return AlarmClipStatus(if (decision.confirmed && player.isPlaying) AlarmClipState.PLAYING
            else AlarmClipState.PREPARING, decision.label, decision.warning)
    }

    private fun applyRoute(now: Long): AlarmRouteDecision {
        // The source/output must exist before direct platform routing (OSMTracker NO_INIT lesson).
        val output = track.get()?.takeIf { runCatching { it.state == AudioTrack.STATE_INITIALIZED }.getOrDefault(false) }
        val playing = runCatching { output?.playState == AudioTrack.PLAYSTATE_PLAYING }.getOrDefault(false)
        val actual = if (playing) runCatching { output?.routedDevice?.id }.getOrNull() else null
        var decision = routes.decide(true, devices.devices, actual, now, devices.version, playing)
        val requested = decision.deviceId
        if (requested != null && !decision.failed && (requested != appliedDevice || output !== appliedTrack)) {
            val device = devices.find(requested)
            if (device != null) attempts.request(decision)
            val accepted = device != null && runCatching {
                player.setPreferredAudioDevice(device)
                output?.setPreferredDevice(device) ?: true
            }.getOrDefault(false)
            appliedDevice = requested; appliedTrack = output
            if (!accepted) {
                routes.reject(requested)
                decision = routes.decide(true, devices.devices, actual, now, devices.version, playing)
                val speaker = decision.deviceId?.let(devices::find)
                if (!decision.failed && speaker != null) {
                    player.setPreferredAudioDevice(speaker)
                    if (output?.setPreferredDevice(speaker) == false) {
                        routes.reject(speaker.id)
                        decision = routes.decide(true, devices.devices, actual, now, devices.version, playing)
                    }
                    appliedDevice = speaker.id
                }
            }
        }
        return decision
    }

    override fun stop() {
        // Keep ownership on release failure. No second native engine may then be allocated.
        alive.set(false)
        releaseGuard.release({ outputProvider.hasPendingReleases() }) { player.release() }
        track.set(null)
    }
}
