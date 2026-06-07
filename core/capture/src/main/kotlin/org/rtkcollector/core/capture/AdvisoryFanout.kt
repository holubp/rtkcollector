package org.rtkcollector.core.capture

import java.time.Instant

data class AdvisoryConsumer(
    val id: String,
    val accept: (ByteArray) -> Unit,
)

class AdvisoryFanout(
    private val eventSink: CaptureEventSink,
    private val consumers: List<AdvisoryConsumer>,
) {
    fun accept(bytes: ByteArray) {
        consumers.forEach { consumer ->
            runCatching { consumer.accept(bytes) }
                .onFailure { error ->
                    eventSink.recordEvent(
                        CaptureEvent(
                            timestamp = Instant.now().toString(),
                            type = "advisory-consumer-error",
                            message = "${consumer.id}: ${error.message ?: error.javaClass.simpleName}",
                        ),
                    )
                }
        }
    }
}
