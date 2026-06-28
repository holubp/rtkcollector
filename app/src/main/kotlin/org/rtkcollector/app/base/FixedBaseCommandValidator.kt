package org.rtkcollector.app.base

import kotlin.math.abs
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.RecordingSettingsSet

private const val FIXED_BASE_LAT_LON_TOLERANCE_DEGREES = 1e-9
private const val FIXED_BASE_ALTITUDE_TOLERANCE_METERS = 0.0005
private const val FIXED_BASE_FAMILY_ERROR = "Fixed base requires a UM980/Unicore command profile."
private const val FIXED_BASE_MODE_BASE_ERROR =
    "Fixed base command profile must contain MODE BASE <lat> <lon> <msl-altitude>."
private const val FIXED_BASE_MISMATCH_ERROR =
    "Selected base coordinate does not match command profile MODE BASE."

internal data class ParsedFixedBaseModeBase(
    val latDeg: Double,
    val lonDeg: Double,
    val mslAltitudeM: Double,
)

object FixedBaseCommandValidator {
    fun requireSupportedReceiverFamily(receiverFamily: String?) {
        require(receiverFamily.supportsFixedBaseUm980Commands()) { FIXED_BASE_FAMILY_ERROR }
    }

    fun validateSelectedCoordinateMatchesProfile(
        commandProfile: CommandProfile,
        selectedBaseCoordinate: AcceptedBaseCoordinate,
    ) {
        requireSupportedReceiverFamily(commandProfile.receiverFamily)
        val parsedModeBase = parseVisibleFixedCoordinateModeBase(commandProfile.runtimeScript)
            ?: throw IllegalArgumentException(FIXED_BASE_MODE_BASE_ERROR)
        require(parsedModeBase.matches(selectedBaseCoordinate)) { FIXED_BASE_MISMATCH_ERROR }
    }

    fun isCommandProfileUsedByOtherSettingsSet(
        settingsSets: List<RecordingSettingsSet>,
        selectedSettingsSetId: String,
        commandProfileId: String,
    ): Boolean =
        settingsSets.any { set ->
            set.id != selectedSettingsSetId && set.commandProfileRef.id == commandProfileId
        }

    internal fun parseVisibleFixedCoordinateModeBase(runtimeScript: String): ParsedFixedBaseModeBase? =
        runtimeScript.lineSequence()
            .map(String::trim)
            .filter { line -> line.startsWith("MODE BASE ", ignoreCase = true) }
            .mapNotNull(::parseFixedCoordinateModeBaseLine)
            .firstOrNull()

    private fun parseFixedCoordinateModeBaseLine(line: String): ParsedFixedBaseModeBase? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size != 5) return null
        if (!parts[0].equals("MODE", ignoreCase = true) || !parts[1].equals("BASE", ignoreCase = true)) {
            return null
        }
        val latDeg = parts[2].toDoubleOrNull() ?: return null
        val lonDeg = parts[3].toDoubleOrNull() ?: return null
        val mslAltitudeM = parts[4].toDoubleOrNull() ?: return null
        if (!latDeg.isFinite() || !lonDeg.isFinite() || !mslAltitudeM.isFinite()) return null
        return ParsedFixedBaseModeBase(
            latDeg = latDeg,
            lonDeg = lonDeg,
            mslAltitudeM = mslAltitudeM,
        )
    }
}

private fun ParsedFixedBaseModeBase.matches(coordinate: AcceptedBaseCoordinate): Boolean {
    val mslAltitude = coordinate.mslAltitudeM ?: return false
    return abs(latDeg - coordinate.latDeg) <= FIXED_BASE_LAT_LON_TOLERANCE_DEGREES &&
        abs(lonDeg - coordinate.lonDeg) <= FIXED_BASE_LAT_LON_TOLERANCE_DEGREES &&
        abs(mslAltitudeM - mslAltitude) <= FIXED_BASE_ALTITUDE_TOLERANCE_METERS
}

private fun String?.supportsFixedBaseUm980Commands(): Boolean =
    this?.startsWith("um980", ignoreCase = true) == true ||
        this?.startsWith("unicore", ignoreCase = true) == true
