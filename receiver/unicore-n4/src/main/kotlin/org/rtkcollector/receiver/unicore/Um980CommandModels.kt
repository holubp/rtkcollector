package org.rtkcollector.receiver.unicore

enum class Um980WorkflowMode {
    ROVER,
    ROVER_NTRIP,
    TEMPORARY_BASE,
    TEMPORARY_BASE_NTRIP,
    FIXED_BASE_STATUS,
    FIXED_BASE_RTCM_OUTPUT,
}

enum class Um980CoordinateSource {
    NONE,
    MANUAL,
    IMPORTED_BASE_POSITION_JSON,
}

data class Um980BaseCoordinate(
    val latDeg: Double,
    val lonDeg: Double,
    val heightM: Double,
    val frame: String,
    val epoch: String?,
    val antennaHeightM: Double?,
    val antennaReferencePoint: String?,
    val source: Um980CoordinateSource,
) {
    init {
        require(latDeg in -90.0..90.0) { "Latitude must be -90..90 degrees." }
        require(lonDeg in -180.0..180.0) { "Longitude must be -180..180 degrees." }
        require(frame.isNotBlank()) { "Coordinate frame must not be blank." }
    }
}

data class Um980CommandProfileRequest(
    val mode: Um980WorkflowMode,
    val comPort: String = "COM1",
    val outputIntervalSeconds: Double = 1.0,
    val enablePpp: Boolean = false,
    val baseCoordinate: Um980BaseCoordinate? = null,
    val runtimeBaud: Int? = null,
) {
    init {
        require(comPort.matches(Regex("COM[1-3]"))) { "UM980 COM port must be COM1, COM2 or COM3." }
        require(outputIntervalSeconds > 0.0) { "Output interval must be positive." }
        runtimeBaud?.let { require(it in 9600..921600) { "Runtime baud must be 9600..921600." } }
    }
}
