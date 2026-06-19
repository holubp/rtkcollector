package org.rtkcollector.core.rtklib

class RtklibNativeBridge(
    private val loadLibrary: () -> Unit = { System.loadLibrary(LIBRARY_NAME) },
) : RtklibBackendFactory {
    override fun create(): RtklibBackend {
        loadLibrary()
        return UnimplementedNativeBackend
    }

    private object UnimplementedNativeBackend : RtklibBackend {
        override fun start(config: RtklibConfig): RtklibStartResult =
            RtklibStartResult.failed("RTKLIB native bridge is not implemented in this build")

        override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch =
            RtklibNativeOutputBatch(warning = "RTKLIB native bridge is not implemented")

        override fun snapshot(): RtklibEngineSnapshot =
            RtklibEngineSnapshot(
                state = RtklibEngineState.FAILED,
                lastError = "RTKLIB native bridge is not implemented in this build",
            )

        override fun stop() = Unit
    }

    companion object {
        const val LIBRARY_NAME: String = "rtkcollector_rtklib"
    }
}
