package org.rtkcollector.core.correction

data class RtcmBaseOutputSanityResult(
    val canUpload: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val detectedMessageTypes: Set<Int>,
)

object Um980RtcmBaseOutputSanity {
    private val basePositionMessages = setOf(1005, 1006)
    private val glonassMsmMessages = setOf(1084, 1085, 1087)
    private val msmObservationMessages = setOf(
        1074,
        1075,
        1077,
        1084,
        1085,
        1087,
        1094,
        1095,
        1097,
        1114,
        1115,
        1117,
        1124,
        1125,
        1127,
    )
    private val recognizedMessages = basePositionMessages + msmObservationMessages + setOf(1033, 1230)
    private val monitoringLogs = setOf(
        "BESTNAV",
        "ADRNAV",
        "PPPNAV",
        "STADOP",
        "OBSV",
        "OBSVM",
        "OBSVMCMP",
        "GGA",
        "GNGGA",
        "GPGGA",
        "GSA",
        "GNGSA",
        "GPGSA",
        "GSV",
        "GNGSV",
        "GPGSV",
    )
    private val rtcmCommand = Regex("""\bRTCM(\d{4})\b""", RegexOption.IGNORE_CASE)
    private val ephemerisCommand = Regex("""\b(?:GPS|GLO|GAL|BDS|BD3|QZSS)EPH[ABC]?\b""", RegexOption.IGNORE_CASE)

    fun validateCommands(commands: List<String>): RtcmBaseOutputSanityResult {
        val normalizedCommands = commands
            .flatMap { it.lineSequence().toList() }
            .map { it.substringBefore('#').substringBefore("//").trim() }
            .filter(String::isNotBlank)

        val detected = normalizedCommands
            .flatMap { command -> rtcmCommand.findAll(command).mapNotNull { it.groupValues[1].toIntOrNull() } }
            .filter { it in recognizedMessages }
            .toSortedSet()

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val hasBasePosition = detected.any { it in basePositionMessages }
        val hasMsmObservation = detected.any { it in msmObservationMessages }

        if (!hasBasePosition) {
            errors += "Base RTCM upload requires RTCM1005 or RTCM1006 base-position output."
        }
        if (!hasMsmObservation) {
            errors += "Base RTCM upload requires at least one MSM observation output message."
        }
        if (detected.isEmpty() && normalizedCommands.any(::looksLikeMonitoringOnlyCommand)) {
            errors += "Command script contains only receiver monitoring logs, not base RTCM output."
        }
        if (1033 !in detected) {
            warnings += "RTCM1033 receiver/antenna descriptor output is not enabled."
        }
        if (detected.any { it in glonassMsmMessages } && 1230 !in detected) {
            warnings += "GLONASS MSM output is enabled without RTCM1230 code-phase bias output."
        }
        if (normalizedCommands.none { ephemerisCommand.containsMatchIn(it) }) {
            warnings += "No ephemeris output command was detected in the base script."
        }

        return RtcmBaseOutputSanityResult(
            canUpload = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            detectedMessageTypes = detected,
        )
    }

    private fun looksLikeMonitoringOnlyCommand(command: String): Boolean {
        val token = command.takeWhile { !it.isWhitespace() }
            .uppercase()
        return token in monitoringLogs ||
            token.removeSuffix("A") in monitoringLogs ||
            token.removeSuffix("B") in monitoringLogs
    }
}
