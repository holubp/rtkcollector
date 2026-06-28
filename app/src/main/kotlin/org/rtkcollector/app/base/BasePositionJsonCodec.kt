package org.rtkcollector.app.base

import org.json.JSONObject

object BasePositionJsonCodec {
    fun encode(coordinate: AcceptedBaseCoordinate): String {
        coordinate.validate()
        return JSONObject()
            .put("id", coordinate.id)
            .put("name", coordinate.name)
            .put("latDeg", coordinate.latDeg)
            .put("lonDeg", coordinate.lonDeg)
            .putNullable("mslAltitudeM", coordinate.mslAltitudeM)
            .putNullable("ellipsoidalHeightM", coordinate.ellipsoidalHeightM)
            .putNullable("geoidSeparationM", coordinate.geoidSeparationM)
            .put("frame", coordinate.frame)
            .putNullable("epoch", coordinate.epoch)
            .put("method", coordinate.method)
            .putNullable("durationSeconds", coordinate.durationSeconds)
            .putNullable("horizontalUncertaintyM", coordinate.horizontalUncertaintyM)
            .putNullable("verticalUncertaintyM", coordinate.verticalUncertaintyM)
            .putNullable("antennaHeightM", coordinate.antennaHeightM)
            .putNullable("antennaReferencePoint", coordinate.antennaReferencePoint)
            .putNullable("sourceSessionId", coordinate.sourceSessionId)
            .put("sourceDescription", coordinate.sourceDescription)
            .toString()
    }

    fun decode(
        json: String,
        fallbackId: String,
        fallbackName: String,
    ): AcceptedBaseCoordinate {
        val parsed = JSONObject(json)
        val coordinate = AcceptedBaseCoordinate(
            id = parsed.optString("id", fallbackId).takeIf(String::isNotBlank) ?: fallbackId,
            name = parsed.optString("name", fallbackName).takeIf(String::isNotBlank) ?: fallbackName,
            latDeg = parsed.getDouble("latDeg"),
            lonDeg = parsed.getDouble("lonDeg"),
            ellipsoidalHeightM = parsed.optNullableDouble("ellipsoidalHeightM") ?: parsed.optNullableDouble("heightM"),
            mslAltitudeM = parsed.optNullableDouble("mslAltitudeM")
                ?: parsed.optNullableDouble("altitudeM")
                ?: deriveMslAltitude(
                    ellipsoidalHeightM = parsed.optNullableDouble("ellipsoidalHeightM")
                        ?: parsed.optNullableDouble("heightM"),
                    geoidSeparationM = parsed.optNullableDouble("geoidSeparationM"),
                ),
            geoidSeparationM = parsed.optNullableDouble("geoidSeparationM"),
            frame = parsed.optString("frame", "UNKNOWN").takeIf(String::isNotBlank) ?: "UNKNOWN",
            epoch = parsed.optNullableString("epoch"),
            method = parsed.optString("method", "UNKNOWN").takeIf(String::isNotBlank) ?: "UNKNOWN",
            durationSeconds = parsed.optNullableLong("durationSeconds")
                ?: parsed.optNullableLong("sampleCount"),
            horizontalUncertaintyM = parsed.optNullableDouble("horizontalUncertaintyM")
                ?: parsed.optNullableDouble("uncertaintyM"),
            verticalUncertaintyM = parsed.optNullableDouble("verticalUncertaintyM"),
            antennaHeightM = parsed.optNullableDouble("antennaHeightM"),
            antennaReferencePoint = parsed.optNullableString("antennaReferencePoint"),
            sourceSessionId = parsed.optNullableString("sourceSessionId"),
            sourceDescription = parsed.optString("sourceDescription", "")
                .takeIf(String::isNotBlank)
                ?: parsed.optString("source", "Imported base position")
                    .takeIf(String::isNotBlank)
                ?: "Imported base position",
        )
        coordinate.validate()
        return coordinate
    }
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    if (value == null) put(name, JSONObject.NULL) else put(name, value)

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).takeIf(String::isNotBlank) else null

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeIf(Double::isFinite) else null

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun deriveMslAltitude(ellipsoidalHeightM: Double?, geoidSeparationM: Double?): Double? {
    return if (ellipsoidalHeightM != null && geoidSeparationM != null) {
        ellipsoidalHeightM - geoidSeparationM
    } else {
        null
    }
}
