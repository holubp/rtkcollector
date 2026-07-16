package org.rtkcollector.core.rtklib

interface RtklibEngine : AutoCloseable {
    fun start(config: RtklibConfig): RtklibStartResult
    fun offerRoverBytes(bytes: ByteArray, timestampMillis: Long, sessionOffsetBytes: Long? = null): RtklibOfferResult
    fun offerCorrectionBytes(bytes: ByteArray, timestampMillis: Long, sessionOffsetBytes: Long? = null): RtklibOfferResult
    fun snapshot(): RtklibEngineSnapshot

    /**
     * Stops advisory intake and waits at most [timeoutMillis] for termination and finalization.
     *
     * Returns `true` only after worker-owned resources are finalized. Implementations that cannot
     * provide a bounded shutdown retain the legacy [stop] behaviour.
     */
    fun shutdown(timeoutMillis: Long): Boolean {
        stop()
        return true
    }

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
