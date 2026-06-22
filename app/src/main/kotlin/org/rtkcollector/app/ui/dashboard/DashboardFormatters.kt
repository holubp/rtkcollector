package org.rtkcollector.app.ui.dashboard

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

fun formatDistance(meters: Double?): String {
    val value = meters ?: return "n/a"
    val absValue = abs(value)
    return when {
        absValue < 0.01 -> "${(value * 1000).roundToInt()} mm"
        absValue < 1.0 -> "${(value * 100).roundToInt()} cm"
        absValue < 1000.0 -> "${value.oneDecimal()} m"
        else -> "${(value / 1000.0).oneDecimal()} km"
    }
}

fun formatDashboardDistance(
    value: String,
    unitPreference: DashboardDistanceUnitPreference,
): String {
    val meters = value.distanceMetersOrNull() ?: return value
    return when (unitPreference) {
        DashboardDistanceUnitPreference.DYNAMIC -> formatDistance(meters)
        DashboardDistanceUnitPreference.STATIC_METERS -> String.format(Locale.US, "%.3f m", meters)
    }
}

fun formatDashboardDistancePair(
    value: String,
    unitPreference: DashboardDistanceUnitPreference,
): String {
    val parts = value.split("/")
    if (parts.size != 2) return formatDashboardDistance(value, unitPreference)
    return parts
        .joinToString(" / ") { part -> formatDashboardDistance(part.trim(), unitPreference) }
}

fun formatBytes(bytes: Long?): String {
    val value = bytes ?: return "n/a"
    return when {
        value < 1000 -> "$value B"
        value < 1_000_000 -> "${(value / 1000.0).oneDecimal()} kB"
        value < 1_000_000_000 -> "${(value / 1_000_000.0).oneDecimal()} MB"
        else -> "${(value / 1_000_000_000.0).oneDecimal()} GB"
    }
}

fun formatRate(bytesPerSecond: Double?): String {
    val value = bytesPerSecond ?: return "n/a"
    return when {
        value < 1000 -> "${value.roundToInt()} B/s"
        value < 1_000_000 -> "${(value / 1000.0).oneDecimal()} kB/s"
        else -> "${(value / 1_000_000.0).oneDecimal()} MB/s"
    }
}

fun interpretGgaFixQuality(quality: Int?): String =
    when (quality) {
        null -> "n/a"
        0 -> "Invalid"
        1 -> "Single"
        2 -> "DGPS"
        4 -> "RTK fix"
        5 -> "RTK float"
        6 -> "Estimated"
        7 -> "Base/manual"
        9 -> "PPP"
        else -> "Unknown GGA $quality"
    }

internal fun displayUtcTime(value: String?): String {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank() || trimmed.equals("n/a", ignoreCase = true)) return "n/a"
    formatNmeaUtcTime(trimmed)?.let { return it }
    return runCatching {
        FixedMillisUtcFormatter.format(Instant.parse(trimmed))
    }.getOrDefault(trimmed)
}

internal fun displayEpochMillisUtc(value: Long?): String =
    value
        ?.takeIf { it > 0L }
        ?.let { FixedMillisUtcFormatter.format(Instant.ofEpochMilli(it)) }
        ?: "n/a"

internal fun receiverFrequencyForFamily(receiverFamily: String?): String =
    when {
        receiverFamily?.startsWith("ublox", ignoreCase = true) == true -> DefaultUbloxReceiverFrequency
        else -> DefaultUm980ReceiverFrequency
    }

private fun Double.oneDecimal(): String =
    String.format(Locale.US, "%.1f", this)

private val DistancePattern = Regex("""^([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*(mm|cm|m|km)$""", RegexOption.IGNORE_CASE)

private fun String.distanceMetersOrNull(): Double? {
    val match = DistancePattern.matchEntire(trim()) ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    return when (match.groupValues[2].lowercase(Locale.US)) {
        "mm" -> value / 1000.0
        "cm" -> value / 100.0
        "m" -> value
        "km" -> value * 1000.0
        else -> null
    }
}

private val NmeaUtcPattern = Regex("""^(\d{6})(?:\.(\d+))?$""")

private val FixedMillisUtcFormatter: DateTimeFormatter =
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
        .appendLiteral('Z')
        .toFormatter(Locale.US)
        .withZone(ZoneOffset.UTC)

private fun formatNmeaUtcTime(value: String): String? {
    val match = NmeaUtcPattern.matchEntire(value) ?: return null
    val base = match.groupValues[1]
    val millis = match.groupValues.getOrNull(2)
        .orEmpty()
        .padEnd(3, '0')
        .take(3)
    return "$base.$millis"
}
