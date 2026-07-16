package org.rtkcollector.core.capture

class RawRecordingStorageException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

enum class CaptureFailureDisposition {
    FATAL_STORAGE_STOP,
    RETRY_USB,
}

fun captureFailureDisposition(error: Throwable): CaptureFailureDisposition =
    if (error is RawRecordingStorageException) {
        CaptureFailureDisposition.FATAL_STORAGE_STOP
    } else {
        CaptureFailureDisposition.RETRY_USB
    }

data class RawRecordingFlushPolicy(
    val maxUnflushedBytes: Long = DEFAULT_MAX_UNFLUSHED_BYTES,
    val maxUnflushedMillis: Long = DEFAULT_MAX_UNFLUSHED_MILLIS,
) {
    init {
        require(maxUnflushedBytes > 0) { "Raw flush byte limit must be positive." }
        require(maxUnflushedMillis > 0) { "Raw flush interval must be positive." }
    }

    fun isFlushDue(unflushedBytes: Long, elapsedSinceFlushMillis: Long): Boolean {
        require(unflushedBytes >= 0) { "Unflushed byte count must not be negative." }
        require(elapsedSinceFlushMillis >= 0) { "Elapsed flush time must not be negative." }
        return unflushedBytes > 0 &&
            (unflushedBytes >= maxUnflushedBytes || elapsedSinceFlushMillis >= maxUnflushedMillis)
    }

    companion object {
        const val DEFAULT_MAX_UNFLUSHED_BYTES = 256L * 1024L
        const val DEFAULT_MAX_UNFLUSHED_MILLIS = 2_000L
    }
}
