package org.rtkcollector.app.recording

import org.rtkcollector.core.solution.SolutionCandidate

internal fun SolutionCandidate.isPrimaryScreenCandidateFor(activeReceiverFamily: String): Boolean {
    val family = activeReceiverFamily.lowercase()
    return when {
        family.startsWith("um980") || family.startsWith("unicore") ->
            sourceId == "UM980-BESTNAV"
        family.startsWith("ublox") ->
            sourceId == "UBX-NAV-PVT"
        else ->
            sourceId == "NMEA-GGA"
    }
}
