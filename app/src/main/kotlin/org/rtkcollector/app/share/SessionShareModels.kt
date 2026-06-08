package org.rtkcollector.app.share

data class SessionShareState(
    val isRecording: Boolean,
    val allowPartialSnapshot: Boolean,
) {
    val zipEnabled: Boolean
        get() = !isRecording || allowPartialSnapshot
}
