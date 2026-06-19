package org.rtkcollector.core.rtklib

enum class RtklibInputStreamKind {
    ROVER,
    CORRECTION,
}

data class RtklibInputChunk(
    val streamKind: RtklibInputStreamKind,
    val bytes: ByteArray,
    val timestampMillis: Long,
    val sessionOffsetBytes: Long? = null,
) {
    init {
        require(bytes.isNotEmpty()) { "RTKLIB input chunk must not be empty" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RtklibInputChunk

        if (streamKind != other.streamKind) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (timestampMillis != other.timestampMillis) return false
        return sessionOffsetBytes == other.sessionOffsetBytes
    }

    override fun hashCode(): Int {
        var result = streamKind.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + timestampMillis.hashCode()
        result = 31 * result + (sessionOffsetBytes?.hashCode() ?: 0)
        return result
    }
}

enum class RtklibOfferStatus {
    ACCEPTED,
    DROPPED_FULL,
    STOPPED,
    FAILED,
}

data class RtklibOfferResult(
    val status: RtklibOfferStatus,
    val acceptedBytes: Int = 0,
    val droppedBytes: Int = 0,
    val message: String? = null,
) {
    val accepted: Boolean
        get() = status == RtklibOfferStatus.ACCEPTED
}

data class RtklibStartResult(
    val started: Boolean,
    val message: String? = null,
) {
    companion object {
        fun started(): RtklibStartResult = RtklibStartResult(started = true)
        fun failed(message: String): RtklibStartResult = RtklibStartResult(started = false, message = message)
    }
}
