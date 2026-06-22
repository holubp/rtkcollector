package org.rtkcollector.core.rtklib

import java.nio.file.Path

enum class RtklibPostprocessMode {
    FORWARD,
    FORWARD_BACKWARD,
}

data class RtklibPostprocessRequest(
    val preset: RtklibPreset,
    val roverFormat: String,
    val frequencyCount: Int,
    val mode: RtklibPostprocessMode,
    val receiverRxRaw: Path,
    val correctionRtcm3: Path,
    val outputNmea: Path,
    val outputPos: Path,
) {
    init {
        require(roverFormat.isNotBlank()) { "RTKLIB postprocess rover format is required." }
        require(frequencyCount in 1..3) { "RTKLIB postprocess frequency count must be 1, 2 or 3." }
    }
}

data class RtklibPostprocessResult(
    val success: Boolean,
    val message: String? = null,
) {
    companion object {
        fun success(): RtklibPostprocessResult = RtklibPostprocessResult(success = true)
        fun failed(message: String): RtklibPostprocessResult = RtklibPostprocessResult(success = false, message = message)
    }
}

interface RtklibPostprocessBackend {
    fun postprocess(request: RtklibPostprocessRequest): RtklibPostprocessResult
}
