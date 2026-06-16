package org.rtkcollector.receiver.ublox

enum class UbloxMessageKind(val label: String) {
    RAWX("RAWX"),
    SFRBX("SFRBX"),
    TM2("TM2"),
    NAV_PVT("NAV-PVT"),
    GGA("GGA"),
}

/**
 * Tracks per-kind sample timestamps over a one-second sliding window and
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
            val count = recent[kind].orEmpty().count { nowMillis - it < WINDOW_MILLIS }
            if (count == 0) "-" else count.toString()
        }
        return "Frequency $header $values Hz"
    }

    private companion object {
        const val WINDOW_MILLIS = 1_000L
    }
}
