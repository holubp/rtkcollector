package org.rtkcollector.receiver.unicore

import java.nio.charset.StandardCharsets

data class NmeaGgaFix(
    val talker: String,
    val utcTime: String,
    val latDeg: Double?,
    val lonDeg: Double?,
    val fixQuality: Int?,
    val satelliteCount: Int?,
    val hdop: Double?,
    val altitudeM: Double?,
)

data class Um980AsciiSolution(
    val logName: String,
    val solutionStatus: String?,
    val positionType: String?,
    val latDeg: Double?,
    val lonDeg: Double?,
    val heightM: Double?,
)

class NmeaGgaParser {
    private val lineBuffer = StringBuilder()

    fun accept(bytes: ByteArray): List<NmeaGgaFix> =
        acceptText(bytes.toString(StandardCharsets.US_ASCII))

    fun acceptText(text: String): List<NmeaGgaFix> {
        val fixes = mutableListOf<NmeaGgaFix>()
        text.forEach { character ->
            if (character == '\n') {
                parseLine(lineBuffer.toString().trim())?.let(fixes::add)
                lineBuffer.clear()
            } else if (character != '\r') {
                lineBuffer.append(character)
            }
        }
        return fixes
    }

    fun parseLine(line: String): NmeaGgaFix? {
        if (!line.startsWith("$") || line.length < 6 || !line.substring(3, 6).equals("GGA", ignoreCase = true)) {
            return null
        }
        val withoutChecksum = line.substringBefore('*')
        val fields = withoutChecksum.split(',')
        if (fields.size < 10) {
            return null
        }
        return NmeaGgaFix(
            talker = fields[0].removePrefix("$"),
            utcTime = fields[1],
            latDeg = parseNmeaCoordinate(fields[2], fields[3]),
            lonDeg = parseNmeaCoordinate(fields[4], fields[5]),
            fixQuality = fields[6].toIntOrNull(),
            satelliteCount = fields[7].toIntOrNull(),
            hdop = fields[8].toDoubleOrNull(),
            altitudeM = fields[9].toDoubleOrNull(),
        )
    }

    private fun parseNmeaCoordinate(value: String, hemisphere: String): Double? {
        if (value.isBlank() || hemisphere.isBlank()) {
            return null
        }
        val degreeDigits = when {
            hemisphere.equals("N", ignoreCase = true) || hemisphere.equals("S", ignoreCase = true) -> 2
            hemisphere.equals("E", ignoreCase = true) || hemisphere.equals("W", ignoreCase = true) -> 3
            else -> return null
        }
        if (degreeDigits <= 0 || value.length <= degreeDigits) {
            return null
        }
        val degrees = value.take(degreeDigits).toDoubleOrNull() ?: return null
        val minutes = value.drop(degreeDigits).toDoubleOrNull() ?: return null
        val signed = degrees + minutes / 60.0
        return when (hemisphere.uppercase()) {
            "S", "W" -> -signed
            "N", "E" -> signed
            else -> null
        }
    }
}

class Um980AsciiSolutionParser {
    private val lineBuffer = StringBuilder()

    fun accept(bytes: ByteArray): List<Um980AsciiSolution> =
        acceptText(bytes.toString(StandardCharsets.US_ASCII))

    fun acceptText(text: String): List<Um980AsciiSolution> {
        val solutions = mutableListOf<Um980AsciiSolution>()
        text.forEach { character ->
            if (character == '\n') {
                parseLine(lineBuffer.toString().trim())?.let(solutions::add)
                lineBuffer.clear()
            } else if (character != '\r') {
                lineBuffer.append(character)
            }
        }
        return solutions
    }

    fun parseLine(line: String): Um980AsciiSolution? {
        if (!line.startsWith("#") || !line.contains(';')) {
            return null
        }
        val logName = line.substringAfter('#').substringBefore(',').uppercase()
        if (logName !in SUPPORTED_SOLUTION_LOGS) {
            return null
        }
        val body = line.substringAfter(';').substringBefore('*')
        val fields = body.split(',')
        if (fields.size < 5) {
            return null
        }
        return Um980AsciiSolution(
            logName = logName,
            solutionStatus = fields.getOrNull(0)?.takeIf(String::isNotBlank),
            positionType = fields.getOrNull(1)?.takeIf(String::isNotBlank),
            latDeg = fields.getOrNull(2)?.toDoubleOrNull(),
            lonDeg = fields.getOrNull(3)?.toDoubleOrNull(),
            heightM = fields.getOrNull(4)?.toDoubleOrNull(),
        )
    }

    private companion object {
        val SUPPORTED_SOLUTION_LOGS = setOf("BESTNAVA", "PPPNAVA", "ADRNAVA")
    }
}
