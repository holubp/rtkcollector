package org.rtkcollector.app.ui.dashboard

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
        9 -> "PPP"
        else -> "Quality $quality"
    }

private fun Double.oneDecimal(): String =
    String.format(Locale.US, "%.1f", this)
