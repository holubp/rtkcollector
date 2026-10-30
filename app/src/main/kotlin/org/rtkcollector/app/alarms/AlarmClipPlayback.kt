package org.rtkcollector.app.alarms

enum class AlarmClipState { PREPARING, PLAYING, ENDED, FAILED }
data class AlarmClipStatus(val state: AlarmClipState, val route: String? = null,
    val warning: String? = null)

interface AlarmClipPlayback {
    fun play()
    fun poll(now: Long): AlarmClipStatus
    fun stop()
}

/** Worker-confined ownership; a failed native release must not accumulate players. */
class AlarmClipOwner {
    private var clip: AlarmClipPlayback? = null
    val hasClip: Boolean get() = clip != null
    fun install(factory: () -> AlarmClipPlayback, isCurrent: () -> Boolean) {
        stop()
        if (!isCurrent()) return
        val created = factory()
        clip = created
        if (!isCurrent()) { stop(); return }
        created.play()
        if (!isCurrent()) stop()
    }
    fun poll(now: Long): AlarmClipStatus? = clip?.poll(now)
    fun stop() { clip?.stop(); clip = null }
}
