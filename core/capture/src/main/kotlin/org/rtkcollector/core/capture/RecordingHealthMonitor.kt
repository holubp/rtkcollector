package org.rtkcollector.core.capture

sealed class RecordingHealthEvent {
    data class ReceiverRxStalled(val staleForMillis: Long) : RecordingHealthEvent()
    data object ReceiverRxRecovered : RecordingHealthEvent()
    data class CorrectionsStalled(val staleForMillis: Long) : RecordingHealthEvent()
    data object CorrectionsRecovered : RecordingHealthEvent()
}

class RecordingHealthMonitor(
    private val receiverStallMillis: Long = DEFAULT_RECEIVER_STALL_MILLIS,
    private val correctionStallMillis: Long = DEFAULT_CORRECTION_STALL_MILLIS,
    private val repeatMillis: Long = DEFAULT_REPEAT_MILLIS,
) {
    private var lastReceiverBytesAtMillis: Long = 0L
    private var lastCorrectionBytesAtMillis: Long = 0L
    private var lastReceiverStallEventAtMillis: Long? = null
    private var lastCorrectionStallEventAtMillis: Long? = null
    private var receiverStalled: Boolean = false
    private var correctionsStalled: Boolean = false

    fun reset(nowMillis: Long) {
        lastReceiverBytesAtMillis = nowMillis
        lastCorrectionBytesAtMillis = nowMillis
        lastReceiverStallEventAtMillis = null
        lastCorrectionStallEventAtMillis = null
        receiverStalled = false
        correctionsStalled = false
    }

    fun recordReceiverRead(byteCount: Int, nowMillis: Long): List<RecordingHealthEvent> {
        if (byteCount > 0) {
            lastReceiverBytesAtMillis = nowMillis
            lastReceiverStallEventAtMillis = null
            return if (receiverStalled) {
                receiverStalled = false
                listOf(RecordingHealthEvent.ReceiverRxRecovered)
            } else {
                emptyList()
            }
        }
        val staleForMillis = (nowMillis - lastReceiverBytesAtMillis).coerceAtLeast(0L)
        if (staleForMillis < receiverStallMillis || !shouldRepeat(lastReceiverStallEventAtMillis, nowMillis)) {
            return emptyList()
        }
        receiverStalled = true
        lastReceiverStallEventAtMillis = nowMillis
        return listOf(RecordingHealthEvent.ReceiverRxStalled(staleForMillis))
    }

    fun recordCorrectionBytes(byteCount: Int, nowMillis: Long): List<RecordingHealthEvent> {
        if (byteCount <= 0) return emptyList()
        lastCorrectionBytesAtMillis = nowMillis
        lastCorrectionStallEventAtMillis = null
        return if (correctionsStalled) {
            correctionsStalled = false
            listOf(RecordingHealthEvent.CorrectionsRecovered)
        } else {
            emptyList()
        }
    }

    fun checkCorrections(nowMillis: Long, correctionsExpected: Boolean): List<RecordingHealthEvent> {
        if (!correctionsExpected) {
            return emptyList()
        }
        val staleForMillis = (nowMillis - lastCorrectionBytesAtMillis).coerceAtLeast(0L)
        if (staleForMillis < correctionStallMillis || !shouldRepeat(lastCorrectionStallEventAtMillis, nowMillis)) {
            return emptyList()
        }
        correctionsStalled = true
        lastCorrectionStallEventAtMillis = nowMillis
        return listOf(RecordingHealthEvent.CorrectionsStalled(staleForMillis))
    }

    private fun shouldRepeat(lastEventAtMillis: Long?, nowMillis: Long): Boolean =
        lastEventAtMillis == null || nowMillis - lastEventAtMillis >= repeatMillis

    companion object {
        const val DEFAULT_RECEIVER_STALL_MILLIS: Long = 10_000L
        const val DEFAULT_CORRECTION_STALL_MILLIS: Long = 15_000L
        const val DEFAULT_REPEAT_MILLIS: Long = 10_000L
    }
}
