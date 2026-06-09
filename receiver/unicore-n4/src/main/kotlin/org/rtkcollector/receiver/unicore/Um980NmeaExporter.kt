package org.rtkcollector.receiver.unicore

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.absoluteValue

object Um980NmeaExporter {
    fun export(telemetry: Um980Telemetry): List<String> {
        if (telemetry.latDeg == null || telemetry.lonDeg == null || telemetry.utcTime == null) {
            return emptyList()
        }
        return listOfNotNull(
            gga(telemetry),
            rmc(telemetry),
            vtg(telemetry),
        )
    }

    fun gga(telemetry: Um980Telemetry): String? {
        val lat = telemetry.latDeg ?: return null
        val lon = telemetry.lonDeg ?: return null
        val time = nmeaTime(telemetry.utcTime) ?: return null
        val altitude = telemetry.altitudeM ?: 0.0
        val ellipsoidal = telemetry.ellipsoidalHeightM
        val geoidSeparation = if (ellipsoidal == null) 0.0 else ellipsoidal - altitude
        val body = listOf(
            "GPGGA",
            time,
            nmeaLatitude(lat),
            if (lat >= 0.0) "N" else "S",
            nmeaLongitude(lon),
            if (lon >= 0.0) "E" else "W",
            fixQuality(telemetry.positionType).toString(),
            (telemetry.satellitesUsed ?: 0).toString().padStart(2, '0'),
            nmeaDecimal(telemetry.hdop),
            nmeaDecimal(altitude),
            "M",
            nmeaDecimal(geoidSeparation),
            "M",
            nmeaDecimal(telemetry.differentialAgeS),
            telemetry.stationId.orEmpty(),
        ).joinToString(",")
        return sentence(body)
    }

    fun rmc(telemetry: Um980Telemetry): String? {
        val lat = telemetry.latDeg ?: return null
        val lon = telemetry.lonDeg ?: return null
        val instant = instant(telemetry.utcTime) ?: return null
        val dateTime = instant.atOffset(ZoneOffset.UTC)
        val body = listOf(
            "GPRMC",
            "%02d%02d%05.2f".format(Locale.US, dateTime.hour, dateTime.minute, dateTime.second.toDouble()),
            if (telemetry.solutionStatus == "SOL_COMPUTED") "A" else "V",
            nmeaLatitude(lat),
            if (lat >= 0.0) "N" else "S",
            nmeaLongitude(lon),
            if (lon >= 0.0) "E" else "W",
            nmeaDecimal(telemetry.horizontalSpeedMps?.let { it * MPS_TO_KNOTS }),
            nmeaDecimal(telemetry.trackDeg),
            "%02d%02d%02d".format(Locale.US, dateTime.dayOfMonth, dateTime.monthValue, dateTime.year % 100),
            "",
            "",
            "A",
        ).joinToString(",")
        return sentence(body)
    }

    fun vtg(telemetry: Um980Telemetry): String? {
        val body = listOf(
            "GPVTG",
            nmeaDecimal(telemetry.trackDeg),
            "T",
            "",
            "M",
            nmeaDecimal(telemetry.horizontalSpeedMps?.let { it * MPS_TO_KNOTS }),
            "N",
            nmeaDecimal(telemetry.horizontalSpeedMps?.let { it * MPS_TO_KMH }),
            "K",
            "A",
        ).joinToString(",")
        return sentence(body)
    }

    private fun nmeaLatitude(value: Double): String {
        val absolute = value.absoluteValue
        val degrees = absolute.toInt()
        val minutes = (absolute - degrees) * 60.0
        return "%02d%09.6f".format(Locale.US, degrees, minutes)
    }

    private fun nmeaLongitude(value: Double): String {
        val absolute = value.absoluteValue
        val degrees = absolute.toInt()
        val minutes = (absolute - degrees) * 60.0
        return "%03d%09.6f".format(Locale.US, degrees, minutes)
    }

    private fun nmeaTime(utcTime: String?): String? =
        instant(utcTime)
            ?.atOffset(ZoneOffset.UTC)
            ?.let { "%02d%02d%05.2f".format(Locale.US, it.hour, it.minute, it.second.toDouble()) }

    private fun instant(utcTime: String?): Instant? =
        utcTime?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun nmeaDecimal(value: Double?): String =
        value?.let { "%.3f".format(Locale.US, it) }.orEmpty()

    private fun fixQuality(positionType: String?): Int =
        when (positionType) {
            "NARROW_INT", "WIDE_INT", "L1_INT", "INS_RTKFIXED" -> 4
            "NARROW_FLOAT", "IONOFREE_FLOAT", "L1_FLOAT", "INS_RTKFLOAT" -> 5
            "PSRDIFF", "INS_PSRDIFF" -> 2
            "SBAS" -> 2
            "PPP", "PPP_CONVERGING" -> 6
            "SINGLE", "INS_PSRSP" -> 1
            else -> 0
        }

    private fun sentence(body: String): String {
        val checksum = body.fold(0) { acc, char -> acc xor char.code }
        return "$$body*%02X\r\n".format(Locale.US, checksum)
    }

    private const val MPS_TO_KNOTS = 1.94384449
    private const val MPS_TO_KMH = 3.6
}
