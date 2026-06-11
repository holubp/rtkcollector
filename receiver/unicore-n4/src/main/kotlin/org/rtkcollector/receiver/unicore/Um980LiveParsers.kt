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
    val geoidSeparationM: Double?,
    val differentialAgeS: Double?,
    val stationId: String?,
) {
    val ellipsoidalHeightM: Double?
        get() = if (altitudeM == null || geoidSeparationM == null) null else altitudeM + geoidSeparationM
}

data class NmeaGsaDop(
    val talker: String,
    val fixMode: Int?,
    val satellitesUsed: Int?,
    val pdop: Double?,
    val hdop: Double?,
    val vdop: Double?,
)

data class NmeaGstError(
    val talker: String,
    val utcTime: String,
    val latErrorM: Double?,
    val lonErrorM: Double?,
    val heightErrorM: Double?,
)

data class NmeaGsvView(
    val talker: String,
    val satellitesInView: Int?,
    val signalId: String? = null,
)

class NmeaGsvInViewTracker {
    private val inViewBySignal = linkedMapOf<String, Int>()

    val satellitesInView: Int?
        get() = inViewBySignal.entries
            .takeIf { it.isNotEmpty() }
            ?.groupBy(
                keySelector = { it.key.substringBefore(SIGNAL_KEY_SEPARATOR) },
                valueTransform = { it.value },
            )
            ?.values
            ?.sumOf { counts -> counts.maxOrNull() ?: 0 }

    fun accept(view: NmeaGsvView): Int? {
        val count = view.satellitesInView ?: return satellitesInView
        inViewBySignal[view.trackerKey] = count
        return satellitesInView
    }

    private val NmeaGsvView.trackerKey: String
        get() = if (signalId.isNullOrBlank()) talker else "$talker$SIGNAL_KEY_SEPARATOR$signalId"

    private companion object {
        const val SIGNAL_KEY_SEPARATOR = "\u0000"
    }
}

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
            geoidSeparationM = fields.getOrNull(11)?.toDoubleOrNull(),
            differentialAgeS = fields.getOrNull(13)?.toDoubleOrNull(),
            stationId = fields.getOrNull(14)?.takeIf(String::isNotBlank),
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

class NmeaGsvParser {
    private val lineBuffer = StringBuilder()

    fun accept(bytes: ByteArray): List<NmeaGsvView> =
        acceptText(bytes.toString(StandardCharsets.US_ASCII))

    fun acceptText(text: String): List<NmeaGsvView> {
        val views = mutableListOf<NmeaGsvView>()
        text.forEach { character ->
            if (character == '\n') {
                parseLine(lineBuffer.toString().trim())?.let(views::add)
                lineBuffer.clear()
            } else if (character != '\r') {
                lineBuffer.append(character)
            }
        }
        return views
    }

    fun parseLine(line: String): NmeaGsvView? {
        if (!line.startsWith("$") || line.length < 6 || !line.substring(3, 6).equals("GSV", ignoreCase = true)) {
            return null
        }
        val fields = line.substringBefore('*').split(',')
        if (fields.size < 4) return null
        val payloadFieldCount = fields.size - 4
        return NmeaGsvView(
            talker = fields[0].removePrefix("$"),
            satellitesInView = fields[3].toIntOrNull(),
            signalId = fields.lastOrNull()
                ?.takeIf { payloadFieldCount > 0 && payloadFieldCount % 4 == 1 }
                ?.takeIf(String::isNotBlank),
        )
    }
}

class NmeaGsaParser {
    private val lineBuffer = StringBuilder()

    fun accept(bytes: ByteArray): List<NmeaGsaDop> =
        acceptText(bytes.toString(StandardCharsets.US_ASCII))

    fun acceptText(text: String): List<NmeaGsaDop> {
        val dop = mutableListOf<NmeaGsaDop>()
        text.forEach { character ->
            if (character == '\n') {
                parseLine(lineBuffer.toString().trim())?.let(dop::add)
                lineBuffer.clear()
            } else if (character != '\r') {
                lineBuffer.append(character)
            }
        }
        return dop
    }

    fun parseLine(line: String): NmeaGsaDop? {
        if (!line.startsWith("$") || line.length < 6 || !line.substring(3, 6).equals("GSA", ignoreCase = true)) {
            return null
        }
        val fields = line.substringBefore('*').split(',')
        if (fields.size < 18) return null
        return NmeaGsaDop(
            talker = fields[0].removePrefix("$"),
            fixMode = fields[2].toIntOrNull(),
            satellitesUsed = fields.drop(3).take(12).count(String::isNotBlank),
            pdop = fields[15].toDoubleOrNull(),
            hdop = fields[16].toDoubleOrNull(),
            vdop = fields[17].toDoubleOrNull(),
        )
    }
}

class NmeaGstParser {
    private val lineBuffer = StringBuilder()

    fun accept(bytes: ByteArray): List<NmeaGstError> =
        acceptText(bytes.toString(StandardCharsets.US_ASCII))

    fun acceptText(text: String): List<NmeaGstError> {
        val errors = mutableListOf<NmeaGstError>()
        text.forEach { character ->
            if (character == '\n') {
                parseLine(lineBuffer.toString().trim())?.let(errors::add)
                lineBuffer.clear()
            } else if (character != '\r') {
                lineBuffer.append(character)
            }
        }
        return errors
    }

    fun parseLine(line: String): NmeaGstError? {
        if (!line.startsWith("$") || line.length < 6 || !line.substring(3, 6).equals("GST", ignoreCase = true)) {
            return null
        }
        val fields = line.substringBefore('*').split(',')
        if (fields.size < 9) return null
        return NmeaGstError(
            talker = fields[0].removePrefix("$"),
            utcTime = fields[1],
            latErrorM = fields[6].toDoubleOrNull(),
            lonErrorM = fields[7].toDoubleOrNull(),
            heightErrorM = fields[8].toDoubleOrNull(),
        )
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
