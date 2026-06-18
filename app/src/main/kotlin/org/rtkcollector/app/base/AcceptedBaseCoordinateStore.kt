package org.rtkcollector.app.base

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

interface AcceptedBaseCoordinatePreferences {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class AcceptedBaseCoordinateStore(
    private val preferences: AcceptedBaseCoordinatePreferences,
) {
    constructor(context: Context) : this(
        SharedPreferencesAcceptedBaseCoordinatePreferences(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
    )

    fun coordinates(): List<AcceptedBaseCoordinate> {
        val raw = preferences.getString(KEY_COORDINATES) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                BasePositionJsonCodec.decode(
                    json = array.getJSONObject(index).toString(),
                    fallbackId = "base-coordinate-$index",
                    fallbackName = "Base coordinate ${index + 1}",
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveCoordinates(coordinates: List<AcceptedBaseCoordinate>) {
        val array = JSONArray()
        coordinates.forEach { coordinate ->
            coordinate.validate()
            array.put(org.json.JSONObject(BasePositionJsonCodec.encode(coordinate)))
        }
        preferences.putString(KEY_COORDINATES, array.toString())
        selectedCoordinateId()?.takeIf { selectedId -> coordinates.none { it.id == selectedId } }?.let {
            saveSelectedCoordinateId(null)
        }
    }

    fun selectedCoordinateId(): String? =
        preferences.getString(KEY_SELECTED_COORDINATE_ID)?.takeIf(String::isNotBlank)

    fun saveSelectedCoordinateId(id: String?) {
        if (id.isNullOrBlank()) {
            preferences.remove(KEY_SELECTED_COORDINATE_ID)
        } else {
            preferences.putString(KEY_SELECTED_COORDINATE_ID, id)
        }
    }

    fun selectedCoordinate(): AcceptedBaseCoordinate? {
        val selectedId = selectedCoordinateId() ?: return null
        return coordinates().firstOrNull { it.id == selectedId }
    }

    fun upsert(coordinate: AcceptedBaseCoordinate) {
        coordinate.validate()
        val existing = coordinates()
        val updated = if (existing.any { it.id == coordinate.id }) {
            existing.map { current -> if (current.id == coordinate.id) coordinate else current }
        } else {
            listOf(coordinate) + existing
        }
        saveCoordinates(updated)
    }

    fun delete(id: String) {
        require(id.isNotBlank()) { "Accepted base coordinate id must not be blank." }
        saveCoordinates(coordinates().filterNot { it.id == id })
        if (selectedCoordinateId() == id) {
            saveSelectedCoordinateId(null)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "accepted-base-coordinates"
        const val KEY_COORDINATES = "acceptedBaseCoordinates"
        const val KEY_SELECTED_COORDINATE_ID = "selectedAcceptedBaseCoordinateId"
    }
}

private class SharedPreferencesAcceptedBaseCoordinatePreferences(
    private val preferences: SharedPreferences,
) : AcceptedBaseCoordinatePreferences {
    override fun getString(key: String): String? =
        preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}
