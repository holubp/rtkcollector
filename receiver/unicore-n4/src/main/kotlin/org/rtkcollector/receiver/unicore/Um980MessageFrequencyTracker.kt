package org.rtkcollector.receiver.unicore

import java.util.Locale

enum class Um980MessageKind(val label: String) {
    BESTNAV("BESTNAV"),
    GGA("GGA"),
    PPPNAV("PPPNAV"),
    ADRNAV("ADRNAV"),
    RTKSTATUS("RTKSTATUS"),
    OBSVM("OBSVM"),
    BESTSAT("BESTSAT"),
}

class Um980MessageFrequencyTracker(private val windowMillis: Long = 10_000L) {
    private data class FrequencySample(
        val processingMillis: Long,
        val receiverMillis: Long?,
    )

    private val samples = mutableMapOf<Um980MessageKind, ArrayDeque<FrequencySample>>()
    private val order = listOf(
        Um980MessageKind.BESTNAV,
        Um980MessageKind.GGA,
        Um980MessageKind.PPPNAV,
        Um980MessageKind.ADRNAV,
        Um980MessageKind.RTKSTATUS,
        Um980MessageKind.OBSVM,
        Um980MessageKind.BESTSAT,
    )

    fun record(
        kind: Um980MessageKind,
        timestampMillis: Long,
        receiverTimestampMillis: Long? = null,
    ) {
        val queue = samples.getOrPut(kind) { ArrayDeque() }
        queue.addLast(FrequencySample(timestampMillis, receiverTimestampMillis))
        prune(timestampMillis, receiverTimestampMillis)
    }

    fun display(
        timestampMillis: Long,
        receiverTimestampMillis: Long? = null,
    ): String {
        prune(timestampMillis, receiverTimestampMillis)
        val values = order.joinToString("/") { kind ->
            val count = samples[kind]?.size ?: 0
            if (count == 0) "-" else formatHz(count * 1_000.0 / windowMillis)
        }
        return "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM/BESTSAT $values Hz"
    }

    private fun prune(now: Long, receiverNow: Long?) {
        val cutoff = now - windowMillis
        val receiverCutoff = receiverNow?.minus(windowMillis)
        samples.values.forEach { queue ->
            while (queue.isNotEmpty() && queue.first().isOlderThan(cutoff, receiverCutoff)) {
                queue.removeFirst()
            }
        }
    }

    private fun FrequencySample.isOlderThan(processingCutoff: Long, receiverCutoff: Long?): Boolean =
        if (receiverCutoff != null && receiverMillis != null) {
            receiverMillis < receiverCutoff
        } else {
            processingMillis < processingCutoff
        }

    private fun formatHz(value: Double): String =
        if (value >= 10.0 || value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
}
