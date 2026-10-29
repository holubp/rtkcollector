package org.rtkcollector.app.alarms

enum class AlarmFocusState { AVAILABLE, WAITING, PERMANENTLY_LOST }
data class AlarmAudioDecision(val play: Boolean = false, val requestFocus: Boolean = false, val unavailable: Boolean = false)

/** A delayed focus callback is not authority to restart an expired episode. */
class AlarmAudioPolicy {
    private var selection: AlarmAudioSelection? = null
    private var waitingSince: Long? = null
    private var nextRetry = 0L
    private var retryMillis = 1_000L
    private var expired = false
    fun decide(next: AlarmAudioSelection?, now: Long, focus: AlarmFocusState): AlarmAudioDecision {
        if (next != selection) {
            selection = next; waitingSince = null; nextRetry = now; retryMillis = 1_000; expired = false
        }
        if (next == null) return AlarmAudioDecision()
        if (focus == AlarmFocusState.PERMANENTLY_LOST) expired = true
        if (expired) return AlarmAudioDecision(unavailable = true)
        val since = waitingSince
        if (since != null && now - since >= 30_000) {
            expired = true; return AlarmAudioDecision(unavailable = true)
        }
        if (focus == AlarmFocusState.AVAILABLE) { waitingSince = null; return AlarmAudioDecision(play = true) }
        if (waitingSince == null) waitingSince = now
        val retry = now >= nextRetry
        if (retry) { nextRetry = now + retryMillis; retryMillis = minOf(8_000, retryMillis * 2) }
        return AlarmAudioDecision(requestFocus = retry)
    }
}
