package org.rtkcollector.app.alarms

/** Results remain bound to the operation that produced them, including preview work. */
data class AlarmAudioReportOrigin(val desired: AlarmDesiredState, val selection: AlarmAudioSelection?) {
    fun accepts(now: AlarmDesiredState, wanted: AlarmAudioSelection?): Boolean =
        desired.generation == now.generation && desired.revision == now.revision &&
            desired.audio == now.audio && selection == wanted

    fun serviceIdentity(): Pair<Long, Long>? = (desired.generation to desired.revision)
        .takeIf { desired.generation >= 0 && (selection == null || desired.audio == selection) }
}

internal fun cleanupFailedAlarmPlayback(abandonFocus: () -> Unit, stop: () -> Unit) {
    try { abandonFocus() } finally { stop() }
}
