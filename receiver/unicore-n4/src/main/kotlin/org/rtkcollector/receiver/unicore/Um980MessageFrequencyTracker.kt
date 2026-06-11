package org.rtkcollector.receiver.unicore

import java.util.Locale

enum class Um980MessageKind(val label: String) {
    BESTNAV("BESTNAV"),
    GGA("GGA"),
    PPPNAV("PPPNAV"),
    ADRNAV("ADRNAV"),
    RTKSTATUS("RTKSTATUS"),
    OBSVM("OBSVM"),
}

class Um980MessageFrequencyTracker(private val windowMillis: Long = 10_000L) {
    private val samples = mutableMapOf<Um980MessageKind, ArrayDeque<Long>>()
    private val order = listOf(
        Um980MessageKind.BESTNAV,
        Um980MessageKind.GGA,
        Um980MessageKind.PPPNAV,
        Um980MessageKind.ADRNAV,
        Um980MessageKind.RTKSTATUS,
        Um980MessageKind.OBSVM,
    )

    fun record(kind: Um980MessageKind, timestampMillis: Long) {
        val queue = samples.getOrPut(kind) { ArrayDeque() }
        queue.addLast(timestampMillis)
        prune(timestampMillis)
    }

    fun display(timestampMillis: Long): String {
        prune(timestampMillis)
        val values = order.joinToString("/") { kind ->
            val count = samples[kind]?.size ?: 0
            if (count == 0) "-" else formatHz(count * 1_000.0 / windowMillis)
        }
        return "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM $values Hz"
    }

    private fun prune(now: Long) {
        val cutoff = now - windowMillis
        samples.values.forEach { queue ->
            while (queue.isNotEmpty() && queue.first() < cutoff) {
                queue.removeFirst()
            }
        }
    }

    private fun formatHz(value: Double): String =
        if (value >= 10.0 || value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
}
