package org.rtkcollector.app.alarms

import org.rtkcollector.core.solution.SolutionSourcePolicy
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.BestSolutionSelector

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
    val PROTOCOL = AlarmCauseKey(AlarmCategory.RECORDING_INTERRUPTED, "receiver protocol", "unavailable")
    fun matchesMainEngine(sourceIsRtklib: Boolean, screenPolicy: SolutionSourcePolicy, autoIsRtklib: Boolean): Boolean = when (screenPolicy) {
        SolutionSourcePolicy.OFF -> false
        SolutionSourcePolicy.RTKLIB_ONLY -> sourceIsRtklib
        SolutionSourcePolicy.DEVICE_INTERNAL_ONLY -> !sourceIsRtklib
        SolutionSourcePolicy.AUTO_BEST -> sourceIsRtklib == autoIsRtklib
    }
}

class AlarmSourceSelector(private val family: String, private val expectsBestnav: Boolean) {
    private var selected: String? = null
    @Synchronized fun accept(sourceId: String): Boolean {
        val primary = when {
            family.startsWith("um980", true) || family.startsWith("unicore", true) -> "UM980-BESTNAV"
            family.startsWith("ublox", true) -> "UBX-NAV-PVT"
            else -> "NMEA-GGA"
        }
        if (expectsBestnav && primary == "UM980-BESTNAV") return sourceId == primary
        if (sourceId == primary || selected == null) selected = sourceId
        return sourceId == selected
    }
}

/** GNSS solution epoch, not status-poll time, identifies genuinely new RTKLIB output. */
class AlarmRtklibEpochTracker {
    private var last: Long? = null
    private var revision = 0L
    @Synchronized fun accept(epochMillis: Long): Long? {
        if (epochMillis <= 0 || epochMillis == last) return null
        last = epochMillis
        return ++revision
    }
}

/** Ranks genuinely new decoded evidence using receipt time, without a mock/display ticker. */
class AlarmSolutionEvidence {
    private data class Entry(val candidate: SolutionCandidate, val sequence: Long)
    private val sources = linkedMapOf<String, Entry>()
    @Synchronized fun accept(candidate: SolutionCandidate, sequence: Long, receivedElapsedMillis: Long,
        policy: SolutionSourcePolicy, maxAgeMillis: Long = 5_000): AlarmPositionEvidence? {
        val old = sources[candidate.sourceId]
        if (old != null && sequence <= old.sequence || old == null && sources.size >= 16) return null
        sources[candidate.sourceId] = Entry(candidate.copy(updatedAtMillis = receivedElapsedMillis), sequence)
        val valid = sources.values.map { it.candidate }.filter {
            RecordingAlarmEvidence.validPosition(it.latDeg, it.lonDeg, it.fixClass != org.rtkcollector.core.solution.FixClass.NONE)
        }
        val selected = BestSolutionSelector.select(valid, receivedElapsedMillis, maxAgeMillis, policy)
        if (selected?.sourceId != candidate.sourceId) return null
        return AlarmPositionEvidence(candidate.sourceId, sequence, receivedElapsedMillis, true)
    }
}
