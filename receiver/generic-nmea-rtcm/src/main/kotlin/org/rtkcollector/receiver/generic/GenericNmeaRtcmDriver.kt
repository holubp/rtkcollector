package org.rtkcollector.receiver.generic

import org.rtkcollector.receiver.api.BaseConfig
import org.rtkcollector.receiver.api.BasePosition
import org.rtkcollector.receiver.api.GnssReceiverDriver
import org.rtkcollector.receiver.api.QualityEvent
import org.rtkcollector.receiver.api.ReceiverCapabilities
import org.rtkcollector.receiver.api.ReceiverCommand
import org.rtkcollector.receiver.api.ReceiverIdentification
import org.rtkcollector.receiver.api.ReceiverProfile
import org.rtkcollector.receiver.api.RoverConfig
import org.rtkcollector.receiver.api.RtcmFrame
import org.rtkcollector.receiver.api.SolutionEvent

class GenericNmeaRtcmDriver : GnssReceiverDriver {
    override val id: String = "generic-nmea-rtcm"
    override val displayName: String = "Generic NMEA + RTCM"
    override val capabilities: ReceiverCapabilities = ReceiverCapabilities(
        supportsRoverMode = true,
        supportsRtcmInput = true,
    )

    override fun identify(sample: ByteArray): ReceiverIdentification? {
        val hasNmea = NmeaLineSplitter.completeLines(sample).any { it.startsWith("$") }
        return if (hasNmea) {
            ReceiverIdentification(manufacturer = "Generic", model = "NMEA + RTCM")
        } else {
            null
        }
    }

    override fun buildInitCommands(profile: ReceiverProfile): List<ReceiverCommand> = emptyList()

    override fun buildRoverCommands(config: RoverConfig): List<ReceiverCommand> = emptyList()

    override fun buildBaseCommands(config: BaseConfig): List<ReceiverCommand> = emptyList()

    override fun buildFixedBaseCommands(position: BasePosition): List<ReceiverCommand> = emptyList()

    override fun parseSolution(input: ByteArray): List<SolutionEvent> =
        NmeaLineSplitter.completeLines(input).mapNotNull { line ->
            BasicGgaParser.parseFixQuality(line)?.let { fix ->
                SolutionEvent(
                    source = "nmea-gga",
                    fixType = fix.fixDescription,
                    fixQuality = fix.fixQuality,
                )
            }
        }

    override fun parseQuality(input: ByteArray): List<QualityEvent> = emptyList()

    override fun extractRtcmFrames(input: ByteArray): List<RtcmFrame> = emptyList()
}

object NmeaLineSplitter {
    fun completeLines(input: ByteArray): List<String> {
        val text = input.decodeToString()
        val lines = mutableListOf<String>()
        var start = 0
        for (index in text.indices) {
            if (text[index] == '\n') {
                val line = text.substring(start, index).trimEnd('\r')
                if (line.isNotEmpty()) {
                    lines += line
                }
                start = index + 1
            }
        }
        return lines
    }
}

data class GgaFixQuality(
    val fixQuality: Int,
    val fixDescription: String,
)

object BasicGgaParser {
    fun parseFixQuality(line: String): GgaFixQuality? {
        val fields = line.substringBefore('*').split(',')
        if (fields.size <= 6 || !fields[0].endsWith("GGA")) {
            return null
        }
        val fixQuality = fields[6].toIntOrNull() ?: return null
        return GgaFixQuality(
            fixQuality = fixQuality,
            fixDescription = describeFixQuality(fixQuality),
        )
    }

    private fun describeFixQuality(fixQuality: Int): String =
        when (fixQuality) {
            0 -> "invalid"
            1 -> "gps"
            2 -> "dgps"
            4 -> "rtk-fixed"
            5 -> "rtk-float"
            6 -> "estimated"
            else -> "quality-$fixQuality"
        }
}
