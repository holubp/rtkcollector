package org.rtkcollector.receiver.ublox

enum class UbloxMessageKind(val label: String) {
    RAWX("RAWX"),
    SFRBX("SFRBX"),
    TM2("TM2"),
    NAV_PVT("NAV-PVT"),
    NAV_SAT("NAV-SAT"),
    GGA("GGA"),
}

/**
 * Tracks per-kind sample timestamps over a short sliding window and
 * formats them as a compact "X/Y/Z/A/B Hz" line for the dashboard.
 *
 * Not thread-safe. Confine to a single advisory-consumer thread or wrap
 * `record()` and `display()` in external synchronization.
 */
class UbloxMessageFrequencyTracker {
    private val recent = mutableMapOf<UbloxMessageKind, MutableList<Long>>()

    fun record(kind: UbloxMessageKind, timestampMillis: Long) {
        val values = recent.getOrPut(kind) { mutableListOf() }
        values += timestampMillis
        values.removeAll { timestampMillis - it > WINDOW_MILLIS }
    }

    fun display(nowMillis: Long): String {
        val header = UbloxMessageKind.entries.joinToString("/") { it.label }
        val values = UbloxMessageKind.entries.joinToString("/") { kind ->
            displayRate(recent[kind].orEmpty().filter { nowMillis - it < WINDOW_MILLIS })
        }
        return "Frequency $header $values Hz"
    }

    private fun displayRate(values: List<Long>): String {
        if (values.isEmpty()) return "-"
        if (values.size == 1) return "-"
        val span = (values.last() - values.first()).coerceAtLeast(1L)
        val rate = (values.size - 1) * 1000.0 / span.toDouble()
        val rounded = kotlin.math.round(rate)
        return if (kotlin.math.abs(rate - rounded) < 0.05) {
            rounded.toInt().toString()
        } else {
            "%.1f".format(java.util.Locale.US, rate)
        }
    }

    private companion object {
        const val WINDOW_MILLIS = 5_000L
    }
}
