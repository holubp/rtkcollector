package org.rtkcollector.receiver.unicore

object Um980OutputFrequencyValidator {
    private val supportedPeriods = setOf("1", "0.5", "0.2", "0.1", "0.05", "0.02")
    private val supportedFrequencyDisplay = "1, 2, 5, 10, 20, or 50 Hz"

    fun validateCommands(commands: List<String>): String? =
        commands.asSequence()
            .mapNotNull(::unsupportedCommand)
            .firstOrNull()

    private fun unsupportedCommand(command: String): String? {
        val parts = command.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (parts.size < 2) return null
        val message = parts[0].uppercase()
        if (!message.isContinuousOutputMessage()) return null

        val period = parts.last().uppercase()
        if (period == "ONCHANGED") return null
        if (period.toDoubleOrNull() == null) return null
        if (period.normalizedPeriod() in supportedPeriods) return null

        return "Unsupported UM980 output frequency in `$command`: use $supportedFrequencyDisplay."
    }

    private fun String.isContinuousOutputMessage(): Boolean =
        when {
            startsWith("RTCM") && this != "RTCMSTATUSB" && this != "RTCMSTATUSA" -> false
            endsWith("EPHB") || endsWith("EPHA") -> false
            endsWith("IONB") || endsWith("IONA") -> false
            endsWith("UTCB") || endsWith("UTCA") -> false
            this in continuousMessages -> true
            startsWith("GN") || startsWith("GP") || startsWith("GL") || startsWith("GA") || startsWith("GB") -> true
            else -> false
        }

    private fun String.normalizedPeriod(): String =
        trimEnd('0').trimEnd('.').ifEmpty { "0" }

    private val continuousMessages = setOf(
        "ADRNAVA",
        "ADRNAVB",
        "BESTNAVA",
        "BESTNAVB",
        "BESTSATA",
        "BESTSATB",
        "OBSVMA",
        "OBSVMB",
        "OBSVMCMPB",
        "PPPNAVA",
        "PPPNAVB",
        "RTCMSTATUSA",
        "RTCMSTATUSB",
        "RTKSTATUSA",
        "RTKSTATUSB",
        "STADOPA",
        "STADOPB",
        "TROPINFOA",
        "TROPINFOB",
        "VERSIONA",
        "VERSIONB",
    )
}
