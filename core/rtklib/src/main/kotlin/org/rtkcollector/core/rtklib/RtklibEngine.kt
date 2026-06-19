package org.rtkcollector.core.rtklib

interface RtklibEngine : AutoCloseable {
    fun start(config: RtklibConfig): RtklibStartResult
    fun offerRoverBytes(bytes: ByteArray, timestampMillis: Long, sessionOffsetBytes: Long? = null): RtklibOfferResult
    fun offerCorrectionBytes(bytes: ByteArray, timestampMillis: Long, sessionOffsetBytes: Long? = null): RtklibOfferResult
    fun snapshot(): RtklibEngineSnapshot
    fun stop()

    override fun close() {
        stop()
    }
}

interface RtklibBackend : AutoCloseable {
    fun start(config: RtklibConfig): RtklibStartResult
    fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch
    fun snapshot(): RtklibEngineSnapshot
    fun stop()

    override fun close() {
        stop()
    }
}

interface RtklibBackendFactory {
    fun create(): RtklibBackend
}
