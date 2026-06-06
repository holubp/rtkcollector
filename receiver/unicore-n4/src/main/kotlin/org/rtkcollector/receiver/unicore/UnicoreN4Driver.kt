package org.rtkcollector.receiver.unicore

import org.rtkcollector.receiver.api.BaseConfig
import org.rtkcollector.receiver.api.BasePosition
import org.rtkcollector.receiver.api.GnssReceiverDriver
import org.rtkcollector.receiver.api.QualityEvent
import org.rtkcollector.receiver.api.ReceiverCapabilities
import org.rtkcollector.receiver.api.ReceiverCommand
import org.rtkcollector.receiver.api.ReceiverIdentification
import org.rtkcollector.receiver.api.ReceiverProfile
import org.rtkcollector.receiver.api.RoverConfig
import org.rtkcollector.receiver.api.RtcmFrame
import org.rtkcollector.receiver.api.SolutionEvent

class UnicoreN4Driver : GnssReceiverDriver {
    override val id: String = "unicore-n4"
    override val displayName: String = "Unicore UM980 / N4"
    override val capabilities: ReceiverCapabilities = ReceiverCapabilities(
        supportsRoverMode = true,
        supportsBaseMode = true,
        supportsFixedBaseMode = true,
        supportsRtcmInput = true,
        supportsRtcmOutput = true,
        supportsNativeRawObservation = true,
        supportsCustomInitScripts = true,
    )

    override fun identify(sample: ByteArray): ReceiverIdentification? = null

    override fun buildInitCommands(profile: ReceiverProfile): List<ReceiverCommand> =
        profile.initScript?.lineSequence()
            ?.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            ?.map { line -> ReceiverCommand(label = "profile-script", payload = line.trimEnd().encodeToByteArray()) }
            ?.toList()
            ?: emptyList()

    override fun buildRoverCommands(config: RoverConfig): List<ReceiverCommand> = emptyList()

    override fun buildBaseCommands(config: BaseConfig): List<ReceiverCommand> = emptyList()

    override fun buildFixedBaseCommands(position: BasePosition): List<ReceiverCommand> = emptyList()

    override fun parseSolution(input: ByteArray): List<SolutionEvent> = emptyList()

    override fun parseQuality(input: ByteArray): List<QualityEvent> = emptyList()

    override fun extractRtcmFrames(input: ByteArray): List<RtcmFrame> = emptyList()
}
