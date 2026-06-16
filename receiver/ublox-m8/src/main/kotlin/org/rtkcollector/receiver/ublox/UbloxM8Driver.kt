package org.rtkcollector.receiver.ublox

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

enum class UbloxM8Profile {
    M8P,
    M8T,
}

object M8PProfile {
    val capabilities: ReceiverCapabilities = ReceiverCapabilities(
        supportsRoverMode = true,
        supportsBaseMode = true,
        supportsFixedBaseMode = true,
        supportsRtcmInput = true,
        supportsRtcmOutput = true,
        supportsNativeRawObservation = true,
    )
}

object M8TProfile {
    val capabilities: ReceiverCapabilities = ReceiverCapabilities(
        supportsBaseMode = true,
        supportsFixedBaseMode = true,
        supportsNativeRawObservation = true,
    )
}

class UbloxM8Driver(
    private val profile: UbloxM8Profile,
) : GnssReceiverDriver {
    override val id: String = "ublox-m8-${profile.name.lowercase()}"
    override val displayName: String = when (profile) {
        UbloxM8Profile.M8P -> "u-blox M8P"
        UbloxM8Profile.M8T -> "u-blox M8T"
    }
    override val capabilities: ReceiverCapabilities = when (profile) {
        UbloxM8Profile.M8P -> M8PProfile.capabilities
        UbloxM8Profile.M8T -> M8TProfile.capabilities
    }

    override fun identify(sample: ByteArray): ReceiverIdentification? = null

    override fun buildInitCommands(profile: ReceiverProfile): List<ReceiverCommand> =
        UbloxScriptCompiler.compile(profile.initScript.orEmpty())

    override fun buildRoverCommands(config: RoverConfig): List<ReceiverCommand> = emptyList()

    override fun buildBaseCommands(config: BaseConfig): List<ReceiverCommand> = emptyList()

    override fun buildFixedBaseCommands(position: BasePosition): List<ReceiverCommand> = emptyList()

    override fun parseSolution(input: ByteArray): List<SolutionEvent> = emptyList()

    override fun parseQuality(input: ByteArray): List<QualityEvent> = emptyList()

    override fun extractRtcmFrames(input: ByteArray): List<RtcmFrame> = emptyList()
}
