package org.rtkcollector.core.rtklib

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

internal object RtklibSolutionOutputSynthesizer {
    private val posTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS", Locale.US)
            .withZone(ZoneOffset.UTC)

    fun withSyntheticOutputsIfNeeded(
        batch: RtklibNativeOutputBatch,
        outputNmea: Boolean,
        outputPos: Boolean,
    ): RtklibNativeOutputBatch {
        val solution = batch.solution ?: return batch
        return batch.copy(
            nmeaLines = if (outputNmea && batch.nmeaLines.isEmpty()) {
                syntheticGga(solution)?.let(::listOf).orEmpty()
            } else {
                batch.nmeaLines
            },
            posLines = if (outputPos && batch.posLines.isEmpty()) {
                syntheticPos(solution)?.let(::listOf).orEmpty()
            } else {
                batch.posLines
            },
        )
    }

    private fun syntheticGga(solution: RtklibSolutionSnapshot): String? {
        val lat = solution.latDeg ?: return null
        val lon = solution.lonDeg ?: return null
        val body = listOf(
            "GPGGA",
            ggaTime(solution.timestampMillis),
            nmeaCoordinate(lat, coordinateWidth = 2),
            if (lat < 0.0) "S" else "N",
            nmeaCoordinate(lon, coordinateWidth = 3),
            if (lon < 0.0) "W" else "E",
            nmeaQuality(solution.fixClass).toString(),
            (solution.satellitesUsed ?: 0).coerceIn(0, 99).toString().padStart(2, '0'),
            "",
            solution.ellipsoidalHeightM?.format(3).orEmpty(),
            "M",
            "0.000",
            "M",
            "",
            "",
        ).joinToString(",")
        return "\$$body*${nmeaChecksum(body)}"
    }

    private fun syntheticPos(solution: RtklibSolutionSnapshot): String? {
        val lat = solution.latDeg ?: return null
        val lon = solution.lonDeg ?: return null
        return listOf(
            posTimeFormatter.format(Instant.ofEpochMilli(solution.timestampMillis)),
            lat.format(9),
            lon.format(9),
            solution.ellipsoidalHeightM?.format(4).orEmpty(),
            solution.fixClass.name,
            solution.horizontalAccuracyM?.format(4).orEmpty(),
            solution.verticalAccuracyM?.format(4).orEmpty(),
            solution.satellitesUsed?.toString().orEmpty(),
        ).joinToString(" ").trimEnd()
    }

    private fun ggaTime(timestampMillis: Long): String {
        val instant = Instant.ofEpochMilli(timestampMillis)
        val utc = instant.atZone(ZoneOffset.UTC)
        return "%02d%02d%02d.%03d".format(
            Locale.US,
            utc.hour,
            utc.minute,
            utc.second,
            utc.nano / 1_000_000,
        )
    }

    private fun nmeaCoordinate(value: Double, coordinateWidth: Int): String {
        val absolute = value.absoluteValue
        val degrees = absolute.toInt()
        val minutes = (absolute - degrees) * 60.0
        return "%0${coordinateWidth}d%010.7f".format(Locale.US, degrees, minutes)
    }

    private fun nmeaQuality(fixClass: RtklibFixClass): Int =
        when (fixClass) {
            RtklibFixClass.RTK_FIXED -> 4
            RtklibFixClass.RTK_FLOAT -> 5
            RtklibFixClass.DGPS,
            RtklibFixClass.PPP -> 2
            RtklibFixClass.SINGLE -> 1
            RtklibFixClass.NONE,
            RtklibFixClass.INVALID -> 0
        }

    private fun nmeaChecksum(body: String): String {
        val checksum = body.fold(0) { acc, char -> acc xor char.code }
        return "%02X".format(Locale.US, checksum)
    }

    private fun Double.format(decimals: Int): String =
        "%.${decimals}f".format(Locale.US, this)
}
