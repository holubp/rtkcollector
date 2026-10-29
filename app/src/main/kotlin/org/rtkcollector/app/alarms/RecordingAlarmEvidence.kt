package org.rtkcollector.app.alarms

/** Evidence is accepted from decoded frames, never copied dashboard text. */
object RecordingAlarmEvidence {
    fun validPosition(lat: Double?, lon: Double?, usableFix: Boolean): Boolean =
        usableFix && lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0

    val CONNECTION = AlarmCauseKey(AlarmCategory.CONNECTION_LOST, "receiver", "unavailable")
    val CORRECTIONS = AlarmCauseKey(AlarmCategory.RECORDING_INTERRUPTED, "corrections", "unavailable")
    val UPLOAD = AlarmCauseKey(AlarmCategory.RECORDING_INTERRUPTED, "upload", "unavailable")
    val RTKLIB = AlarmCauseKey(AlarmCategory.RECORDING_INTERRUPTED, "processing", "unavailable")
    val STORAGE = AlarmCauseKey(AlarmCategory.RECORDING_INTERRUPTED, "storage", "write-failed")
    val SERVICE = AlarmCauseKey(AlarmCategory.RECORDING_INTERRUPTED, "recording", "unexpected-stop")
}
