package org.rtkcollector.receiver.ublox

import org.rtkcollector.core.solution.FixClass
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

object UbloxNmeaExporter {
    fun exportGga(telemetry: UbloxTelemetry): String? {
        val lat = telemetry.latDeg ?: return null
        val lon = telemetry.lonDeg ?: return null
        val fix = telemetry.fixClass ?: return null
        val altitude = telemetry.mslAltitudeM ?: return null
        val time = telemetry.utcTime?.let(::nmeaTime).orEmpty()
        val geoidSeparation = if (telemetry.ellipsoidalHeightM != null) {
            telemetry.ellipsoidalHeightM - altitude
        } else {
            null
        }
        val body = listOf(
            "GNGGA",
            time,
            nmeaLatitude(lat),
            if (lat < 0.0) "S" else "N",
            nmeaLongitude(lon),
            if (lon < 0.0) "W" else "E",
            nmeaQuality(fix).toString(),
            telemetry.satellitesUsed?.let { "%02d".format(Locale.US, it.coerceIn(0, 99)) }.orEmpty(),
            "",
            "%.3f".format(Locale.US, altitude),
            "M",
            geoidSeparation?.let { "%.3f".format(Locale.US, it) }.orEmpty(),
            "M",
            "",
            "",
        ).joinToString(",")
        return "\$$body*${checksum(body)}\r\n"
    }

    private fun nmeaTime(utcTime: String): String? {
        val time = utcTime.substringAfter('T', missingDelimiterValue = "")
            .removeSuffix("Z")
        val parts = time.split(':')
        if (parts.size < 3) return null
        val second = parts[2].substringBefore('.')
        return parts[0] + parts[1] + second.padStart(2, '0') + ".000"
    }

    private fun nmeaLatitude(value: Double): String {
        val coordinate = nmeaCoordinate(value)
        return "%02d%010.7f".format(Locale.US, coordinate.degrees, coordinate.minutes)
    }

    private fun nmeaLongitude(value: Double): String {
        val coordinate = nmeaCoordinate(value)
        return "%03d%010.7f".format(Locale.US, coordinate.degrees, coordinate.minutes)
    }

    private fun nmeaCoordinate(value: Double): NmeaCoordinate {
        val absValue = abs(value)
        var degrees = floor(absValue).toInt()
        var minutes = round((absValue - degrees) * 60.0 * 10_000_000.0) / 10_000_000.0
        if (minutes >= 60.0) {
            degrees += 1
            minutes = 0.0
        }
        return NmeaCoordinate(degrees = degrees, minutes = minutes)
    }

    private data class NmeaCoordinate(val degrees: Int, val minutes: Double)

    private fun nmeaQuality(fix: FixClass): Int =
        when (fix) {
            FixClass.NONE -> 0
            FixClass.SINGLE -> 1
            FixClass.DGPS, FixClass.SBAS, FixClass.PPP_CONVERGED, FixClass.PPP_CONVERGING -> 2
            FixClass.RTK_FIXED -> 4
            FixClass.RTK_FLOAT -> 5
        }

    private fun checksum(body: String): String {
        val value = body.fold(0) { checksum, character -> checksum xor character.code }
        return "%02X".format(Locale.US, value)
    }
}
