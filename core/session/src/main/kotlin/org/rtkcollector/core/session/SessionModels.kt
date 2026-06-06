package org.rtkcollector.core.session

data class SessionMetadata(
    val appVersion: String,
    val androidDeviceModel: String,
    val androidVersion: String,
    val receiverDriverId: String,
    val receiverIdentification: ReceiverIdentificationMetadata?,
    val usbVid: Int?,
    val usbPid: Int?,
    val baudRate: Int,
    val serialParameters: SerialParameters,
    val mode: SessionMode,
    val startedAt: String,
    val stoppedAt: String?,
    val ntrip: NtripSessionMetadata?,
    val antenna: AntennaMetadata,
    val sessionUuid: String,
    val linkedBaseSessionUuid: String?,
)

data class ReceiverIdentificationMetadata(
    val manufacturer: String,
    val model: String,
    val firmwareVersion: String? = null,
    val serialNumber: String? = null,
)

data class SerialParameters(
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: String = "none",
)

enum class SessionMode {
    ROVER,
    FIXED_BASE,
    TEMPORARY_BASE_PREPARATION,
    REPLAY_TEST,
}

data class NtripSessionMetadata(
    val casterHost: String,
    val casterPort: Int = 2101,
    val mountpoint: String,
    val usernamePresent: Boolean = false,
    val ggaUploadEnabled: Boolean = false,
)

data class AntennaMetadata(
    val antennaType: String? = null,
    val antennaSerialNumber: String? = null,
    val antennaHeightMeters: Double? = null,
    val antennaReferencePoint: String? = null,
)

data class BasePositionMetadata(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val heightMeters: Double,
    val ecefXMeters: Double,
    val ecefYMeters: Double,
    val ecefZMeters: Double,
    val frame: String,
    val epoch: String?,
    val method: BasePositionMethod,
    val durationSeconds: Long?,
    val uncertaintyMeters: Double?,
    val antennaHeightMeters: Double?,
    val antennaReferencePoint: String?,
    val sourceSessionReference: String?,
)

enum class BasePositionMethod {
    MANUAL_KNOWN_POINT,
    LONG_AVERAGE,
    STATIC_RTK,
    PPP_STATIC,
    RECEIVER_PPP,
    RECEIVER_SURVEY_IN,
    EXTERNAL_BASE_POSITION_JSON,
}
