package org.rtkcollector.core.capture

sealed class RecordingHealthEvent {
    data class ReceiverRxStalled(val staleForMillis: Long) : RecordingHealthEvent()
    data object ReceiverRxRecovered : RecordingHealthEvent()
    data class ReceiverProtocolStalled(
        val receiverFamily: String,
        val staleForMillis: Long,
        val bytesSinceLastValidFrame: Long,
    ) : RecordingHealthEvent()
    data class ReceiverProtocolRecovered(val receiverFamily: String) : RecordingHealthEvent()
    data class CorrectionsStalled(val staleForMillis: Long) : RecordingHealthEvent()
    data object CorrectionsRecovered : RecordingHealthEvent()
}

class RecordingHealthMonitor(
    private val receiverStallMillis: Long = DEFAULT_RECEIVER_STALL_MILLIS,
    private val receiverProtocolStallMillis: Long = DEFAULT_RECEIVER_PROTOCOL_STALL_MILLIS,
    private val correctionStallMillis: Long = DEFAULT_CORRECTION_STALL_MILLIS,
    private val repeatMillis: Long = DEFAULT_REPEAT_MILLIS,
) {
    private var lastReceiverBytesAtMillis: Long = 0L
    private var lastReceiverProtocolFrameAtMillis: Long = 0L
    private var receiverBytesSinceLastProtocolFrame: Long = 0L
    private var lastCorrectionBytesAtMillis: Long = 0L
    private var lastReceiverStallEventAtMillis: Long? = null
    private var lastReceiverProtocolStallEventAtMillis: Long? = null
    private var lastCorrectionStallEventAtMillis: Long? = null
    private var receiverStalled: Boolean = false
    private var receiverProtocolStalled: Boolean = false
    private var correctionsStalled: Boolean = false

    @Synchronized
    fun reset(nowMillis: Long) {
        lastReceiverBytesAtMillis = nowMillis
        lastReceiverProtocolFrameAtMillis = nowMillis
        receiverBytesSinceLastProtocolFrame = 0L
        lastCorrectionBytesAtMillis = nowMillis
        lastReceiverStallEventAtMillis = null
        lastReceiverProtocolStallEventAtMillis = null
        lastCorrectionStallEventAtMillis = null
        receiverStalled = false
        receiverProtocolStalled = false
        correctionsStalled = false
    }

    @Synchronized
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

    @Synchronized
    fun recordReceiverProtocolBytes(byteCount: Int, nowMillis: Long) {
        if (byteCount > 0) {
            receiverBytesSinceLastProtocolFrame += byteCount.toLong()
        }
    }

    @Synchronized
    fun recordValidReceiverProtocolFrame(
        nowMillis: Long,
        receiverFamily: String,
    ): List<RecordingHealthEvent> {
        lastReceiverProtocolFrameAtMillis = nowMillis
        receiverBytesSinceLastProtocolFrame = 0L
        lastReceiverProtocolStallEventAtMillis = null
        return if (receiverProtocolStalled) {
            receiverProtocolStalled = false
            listOf(RecordingHealthEvent.ReceiverProtocolRecovered(receiverFamily))
        } else {
            emptyList()
        }
    }

    @Synchronized
    fun checkReceiverProtocol(
        nowMillis: Long,
        protocolFramesExpected: Boolean,
        receiverFamily: String,
    ): List<RecordingHealthEvent> {
        if (!protocolFramesExpected || receiverBytesSinceLastProtocolFrame <= 0L) {
            return emptyList()
        }
        val staleForMillis = (nowMillis - lastReceiverProtocolFrameAtMillis).coerceAtLeast(0L)
        if (staleForMillis < receiverProtocolStallMillis ||
            !shouldRepeat(lastReceiverProtocolStallEventAtMillis, nowMillis)
        ) {
            return emptyList()
        }
        receiverProtocolStalled = true
        lastReceiverProtocolStallEventAtMillis = nowMillis
        return listOf(
            RecordingHealthEvent.ReceiverProtocolStalled(
                receiverFamily = receiverFamily,
                staleForMillis = staleForMillis,
                bytesSinceLastValidFrame = receiverBytesSinceLastProtocolFrame,
            ),
        )
    }

    @Synchronized
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

    @Synchronized
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
        const val DEFAULT_RECEIVER_PROTOCOL_STALL_MILLIS: Long = 15_000L
        const val DEFAULT_CORRECTION_STALL_MILLIS: Long = 15_000L
        const val DEFAULT_REPEAT_MILLIS: Long = 10_000L
    }
}
