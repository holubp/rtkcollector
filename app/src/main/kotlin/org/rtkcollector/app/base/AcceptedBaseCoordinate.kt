package org.rtkcollector.app.base

import java.util.Locale

data class AcceptedBaseCoordinate(
    val id: String,
    val name: String,
    val latDeg: Double,
    val lonDeg: Double,
    val ellipsoidalHeightM: Double?,
    val mslAltitudeM: Double?,
    val geoidSeparationM: Double?,
    val frame: String,
    val epoch: String?,
    val method: String,
    val durationSeconds: Long?,
    val horizontalUncertaintyM: Double?,
    val verticalUncertaintyM: Double?,
    val antennaHeightM: Double?,
    val antennaReferencePoint: String?,
    val sourceSessionId: String?,
    val sourceDescription: String,
) {
    fun validate() {
        require(id.isNotBlank()) { "Accepted base coordinate id must not be blank." }
        require(name.isNotBlank()) { "Accepted base coordinate name must not be blank." }
        require(latDeg.isFinite() && latDeg in -90.0..90.0) {
            "Accepted base latitude must be finite and within -90..90 degrees."
        }
        require(lonDeg.isFinite() && lonDeg in -180.0..180.0) {
            "Accepted base longitude must be finite and within -180..180 degrees."
        }
        requireFiniteIfPresent("ellipsoidal height", ellipsoidalHeightM)
        requireFiniteIfPresent("MSL altitude", mslAltitudeM)
        requireFiniteIfPresent("geoid separation", geoidSeparationM)
        requireAtLeastOneFiniteHeight(mslAltitudeM, ellipsoidalHeightM)
        require(frame.isNotBlank()) { "Accepted base coordinate frame must not be blank." }
        require(method.isNotBlank()) { "Accepted base coordinate method must not be blank." }
        require(durationSeconds == null || durationSeconds >= 0) {
            "Accepted base coordinate duration must not be negative."
        }
        requireNonNegativeFinite("horizontal uncertainty", horizontalUncertaintyM)
        requireNonNegativeFinite("vertical uncertainty", verticalUncertaintyM)
        requireNonNegativeFinite("antenna height", antennaHeightM)
        require(sourceDescription.isNotBlank()) { "Accepted base coordinate source description must not be blank." }
    }

    fun toFixedBaseModeCommand(comPort: String = "COM1"): String {
        require(comPort.isNotBlank()) { "UM980 COM port must not be blank." }
        validate()
        return toUm980FixedBaseModeCommand()
    }

    fun toUm980FixedBaseModeCommand(): String {
        validate()
        val altitude = mslAltitudeM ?: throw IllegalArgumentException("UM980 fixed base requires MSL altitude.")
        return "MODE BASE %.10f %.10f %.4f".format(Locale.US, latDeg, lonDeg, altitude)
    }
}

private fun requireFiniteIfPresent(label: String, value: Double?) {
    require(value == null || value.isFinite()) { "Accepted base $label must be finite." }
}

private fun requireAtLeastOneFiniteHeight(mslAltitudeM: Double?, ellipsoidalHeightM: Double?) {
    require(mslAltitudeM != null || ellipsoidalHeightM != null) {
        "Accepted base coordinate requires at least one of mslAltitudeM or ellipsoidalHeightM to be finite."
    }
}

private fun requireNonNegativeFinite(label: String, value: Double?) {
    require(value == null || (value.isFinite() && value >= 0.0)) {
        "Accepted base coordinate $label must be finite and non-negative."
    }
}
