package org.rtkcollector.receiver.unicore

import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteMonitorSource
import org.rtkcollector.core.quality.SatelliteSignalKey
import org.rtkcollector.core.quality.SatelliteSignalObservation
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
    val usedSatelliteIds: List<Int> = emptyList(),
    val systemId: String? = null,
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
    val satellites: List<NmeaGsvSatellite> = emptyList(),
)

data class NmeaGsvSatellite(
    val svid: Int,
    val cn0DbHz: Double?,
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

data class Um980ObsvmaEpoch(
    val observedAtEpochMillis: Long,
    val observations: List<SatelliteSignalObservation>,
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
        val signalId = fields.lastOrNull()
            ?.takeIf { payloadFieldCount > 0 && payloadFieldCount % 4 == 1 }
            ?.takeIf(String::isNotBlank)
        val satelliteFieldEnd = fields.size - if (signalId == null) 0 else 1
        val satellites = fields
            .subList(4, satelliteFieldEnd)
            .chunked(4)
            .mapNotNull { satelliteFields ->
                val svid = satelliteFields.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                NmeaGsvSatellite(
                    svid = svid,
                    cn0DbHz = satelliteFields.getOrNull(3)?.toDoubleOrNull(),
                )
            }
        return NmeaGsvView(
            talker = fields[0].removePrefix("$"),
            satellitesInView = fields[3].toIntOrNull(),
            signalId = signalId,
            satellites = satellites,
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
            usedSatelliteIds = fields.drop(3).take(12).mapNotNull { it.toIntOrNull() },
            systemId = fields.getOrNull(18)?.takeIf(String::isNotBlank),
        )
    }
}

object NmeaSatelliteMonitorMapper {
    fun visibleObservations(
        view: NmeaGsvView,
        observedAtEpochMillis: Long,
    ): List<SatelliteSignalObservation> {
        val constellation = constellationForTalker(view.talker) ?: return emptyList()
        val band = bandFor(constellation, view.signalId) ?: SatelliteSignalKey.BAND_ANY
        val signalCode = view.signalId?.takeIf(String::isNotBlank)?.let { "NMEA-$it" }
        return view.satellites.map { satellite ->
            SatelliteSignalObservation(
                key = SatelliteSignalKey(
                    constellation = constellation,
                    svid = satellite.svid,
                    band = band,
                    signalCode = signalCode,
                ),
                source = SatelliteMonitorSource.ROVER,
                observedAtEpochMillis = observedAtEpochMillis,
                cn0DbHz = satellite.cn0DbHz,
            )
        }
    }

    fun solutionUsageObservations(
        dop: NmeaGsaDop,
        observedAtEpochMillis: Long,
    ): List<SatelliteSignalObservation> {
        val constellation = dop.systemId
            ?.let(::constellationForSystemId)
            ?: constellationForTalker(dop.talker)
            ?: return emptyList()
        return dop.usedSatelliteIds.map { svid ->
            SatelliteSignalObservation(
                key = SatelliteSignalKey(
                    constellation = constellation,
                    svid = svid,
                    band = SatelliteSignalKey.BAND_ANY,
                ),
                source = SatelliteMonitorSource.SOLUTION,
                observedAtEpochMillis = observedAtEpochMillis,
                used = true,
            )
        }
    }

    private fun constellationForTalker(talker: String): SatelliteConstellation? =
        when (talker.uppercase().take(2)) {
            "GP" -> SatelliteConstellation.GPS
            "GL" -> SatelliteConstellation.GLONASS
            "GA" -> SatelliteConstellation.GALILEO
            "GB", "BD" -> SatelliteConstellation.BEIDOU
            "GQ" -> SatelliteConstellation.QZSS
            else -> null
        }

    private fun constellationForSystemId(systemId: String): SatelliteConstellation? =
        when (systemId.trim()) {
            "1" -> SatelliteConstellation.GPS
            "2" -> SatelliteConstellation.GLONASS
            "3" -> SatelliteConstellation.GALILEO
            "4" -> SatelliteConstellation.BEIDOU
            "5" -> SatelliteConstellation.QZSS
            else -> null
        }

    private fun bandFor(constellation: SatelliteConstellation, signalId: String?): String? =
        when (constellation) {
            SatelliteConstellation.GPS,
            SatelliteConstellation.QZSS -> when (signalId) {
                null, "1", "2", "3", "9" -> "L1"
                "4", "5", "6" -> "L2"
                "7", "8" -> "L5"
                else -> null
            }

            SatelliteConstellation.GLONASS -> when (signalId) {
                null, "1", "2" -> "L1"
                "3", "4" -> "L2"
                "5" -> "L3"
                else -> null
            }

            SatelliteConstellation.GALILEO -> when (signalId) {
                null, "1" -> "E1"
                "2", "3" -> "E5A"
                "4", "5" -> "E5B"
                "6" -> "E6"
                "7" -> "E5"
                else -> null
            }

            SatelliteConstellation.BEIDOU -> when (signalId) {
                null, "1", "2", "8" -> "B1"
                "3", "4", "5" -> "B2"
                "6", "7" -> "B3"
                else -> null
            }

            SatelliteConstellation.SBAS -> when (signalId) {
                null, "1" -> "L1"
                "7", "8" -> "L5"
                else -> null
            }

            SatelliteConstellation.UNKNOWN -> null
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

class Um980ObsvmaParser {
    private val lineBuffer = StringBuilder()

    fun accept(bytes: ByteArray): List<Um980ObsvmaEpoch> =
        acceptText(bytes.toString(StandardCharsets.US_ASCII))

    fun acceptText(text: String): List<Um980ObsvmaEpoch> {
        val epochs = mutableListOf<Um980ObsvmaEpoch>()
        text.forEach { character ->
            if (character == '\n') {
                parseLine(lineBuffer.toString().trim())?.let(epochs::add)
                lineBuffer.clear()
            } else if (character != '\r') {
                lineBuffer.append(character)
            }
        }
        return epochs
    }

    fun parseLine(line: String): Um980ObsvmaEpoch? {
        if (!line.startsWith("#OBSVMA", ignoreCase = true) || !line.contains(';')) {
            return null
        }
        val headerFields = line.substringBefore(';').substringBefore('*').split(',')
        val gpsWeek = headerFields.getOrNull(4)?.toLongOrNull()
        val towMillis = headerFields.getOrNull(5)?.toLongOrNull()
        val observedAt = if (gpsWeek != null && towMillis != null) {
            gpsWeek * GPS_WEEK_MILLIS + towMillis
        } else {
            0L
        }
        val payloadFields = line.substringAfter(';')
            .substringBefore('*')
            .replace('|', ',')
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        val declaredCount = payloadFields.firstOrNull()?.toIntOrNull() ?: return null
        val recordFields = payloadFields.drop(1)
        if (declaredCount < 0 || recordFields.size < OBSVMA_RECORD_FIELD_COUNT) {
            return Um980ObsvmaEpoch(observedAt, emptyList())
        }
        val observations = buildList {
            recordFields
                .chunked(OBSVMA_RECORD_FIELD_COUNT)
                .take(declaredCount)
                .forEach { fields ->
                    if (fields.size < OBSVMA_RECORD_FIELD_COUNT) {
                        return@forEach
                    }
                    val svid = fields.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val cn0DbHz = fields.getOrNull(7)?.toDoubleOrNull()?.let { raw ->
                        if (raw > 100.0) raw / 100.0 else raw
                    }
                    val trackingStatus = fields.getOrNull(10)?.toIntAutoOrNull() ?: return@forEach
                    val key = Um980SatelliteMapping.signalKeyFromTrackingStatus(
                        svid = svid,
                        trackingStatus = trackingStatus,
                    ) ?: return@forEach
                    add(
                        SatelliteSignalObservation(
                            key = key,
                            source = SatelliteMonitorSource.ROVER,
                            observedAtEpochMillis = observedAt,
                            cn0DbHz = cn0DbHz,
                        ),
                    )
                }
        }
        return Um980ObsvmaEpoch(observedAt, observations)
    }

    private fun String.toIntAutoOrNull(): Int? =
        trim().let { value ->
            when {
                value.startsWith("0x", ignoreCase = true) -> value.drop(2).toUIntOrNull(16)?.toInt()
                else -> value.toIntOrNull()
            }
        }

    private companion object {
        const val GPS_WEEK_MILLIS = 604_800_000L
        const val OBSVMA_RECORD_FIELD_COUNT = 11
    }
}
