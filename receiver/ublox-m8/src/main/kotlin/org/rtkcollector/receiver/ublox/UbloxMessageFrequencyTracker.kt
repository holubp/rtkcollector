package org.rtkcollector.receiver.ublox

enum class UbloxMessageKind(val label: String) {
    RAWX("RAWX"),
    SFRBX("SFRBX"),
    TM2("TM2"),
    NAV_PVT("NAV-PVT"),
    GGA("GGA"),
}

class UbloxMessageFrequencyTracker {
    private val recent = mutableMapOf<UbloxMessageKind, MutableList<Long>>()

    fun record(kind: UbloxMessageKind, timestampMillis: Long) {
        val values = recent.getOrPut(kind) { mutableListOf() }
        values += timestampMillis
        values.removeAll { timestampMillis - it > WINDOW_MILLIS }
    }

    fun display(nowMillis: Long): String {
        val values = UbloxMessageKind.entries.joinToString("/") { kind ->
            val count = recent[kind].orEmpty().count { nowMillis - it < WINDOW_MILLIS }
            if (count == 0) "-" else count.toString()
        }
        return "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA $values Hz"
    }

    private companion object {
        const val WINDOW_MILLIS = 1_500L
    }
}
