package org.rtkcollector.receiver.api

data class ReceiverCapabilities(
    val supportsRoverMode: Boolean = false,
    val supportsBaseMode: Boolean = false,
    val supportsFixedBaseMode: Boolean = false,
    val supportsRtcmInput: Boolean = false,
    val supportsRtcmOutput: Boolean = false,
    val supportsNativeRawObservation: Boolean = false,
    val supportsCustomInitScripts: Boolean = false,
)

data class ReceiverIdentification(
    val manufacturer: String,
    val model: String,
    val firmwareVersion: String? = null,
    val serialNumber: String? = null,
)

data class ReceiverCommand(
    val label: String,
    val payload: ByteArray,
    val recordsToTxSidecar: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReceiverCommand) return false
        return label == other.label &&
            payload.contentEquals(other.payload) &&
            recordsToTxSidecar == other.recordsToTxSidecar
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + recordsToTxSidecar.hashCode()
        return result
    }
}

data class ReceiverProfile(
    val id: String,
    val displayName: String,
    val initScript: String? = null,
    val parameters: Map<String, String> = emptyMap(),
)

data class RoverConfig(
    val correctionInputEnabled: Boolean = true,
    val requestedBaudRate: Int? = null,
)

data class BaseConfig(
    val rtcmOutputEnabled: Boolean = false,
    val surveyInMinimumDurationSeconds: Long? = null,
    val surveyInAccuracyLimitMeters: Double? = null,
)

data class BasePosition(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val heightMeters: Double,
    val ecefXMeters: Double,
    val ecefYMeters: Double,
    val ecefZMeters: Double,
    val frame: String,
    val epoch: String? = null,
    val method: String,
    val durationSeconds: Long? = null,
    val uncertaintyMeters: Double? = null,
    val antennaHeightMeters: Double? = null,
    val antennaReferencePoint: String? = null,
    val sourceSessionReference: String? = null,
)

data class SolutionEvent(
    val source: String,
    val fixType: String,
    val fixQuality: Int? = null,
    val satellitesUsed: Int? = null,
    val horizontalDilution: Double? = null,
)

data class QualityEvent(
    val source: String,
    val name: String,
    val value: String,
)

data class RtcmFrame(
    val messageType: Int? = null,
    val payload: ByteArray,
    val crc24q: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RtcmFrame) return false
        return messageType == other.messageType &&
            payload.contentEquals(other.payload) &&
            crc24q == other.crc24q
    }

    override fun hashCode(): Int {
        var result = messageType ?: 0
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (crc24q ?: 0)
        return result
    }
}

interface GnssReceiverDriver {
    val id: String
    val displayName: String
    val capabilities: ReceiverCapabilities

    fun identify(sample: ByteArray): ReceiverIdentification?
    fun buildInitCommands(profile: ReceiverProfile): List<ReceiverCommand>
    fun buildRoverCommands(config: RoverConfig): List<ReceiverCommand>
    fun buildBaseCommands(config: BaseConfig): List<ReceiverCommand>
    fun buildFixedBaseCommands(position: BasePosition): List<ReceiverCommand>

    fun parseSolution(input: ByteArray): List<SolutionEvent>
    fun parseQuality(input: ByteArray): List<QualityEvent>
    fun extractRtcmFrames(input: ByteArray): List<RtcmFrame>
}
