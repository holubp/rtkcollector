package org.rtkcollector.app.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AcceptedBaseCoordinateStoreTest {
    @Test
    fun savesAndReloadsCoordinates() {
        val preferences = FakeAcceptedBaseCoordinatePreferences()
        val store = AcceptedBaseCoordinateStore(preferences)
        val coordinate = sampleCoordinate("base-1")

        store.saveCoordinates(listOf(coordinate))

        assertEquals(listOf(coordinate), AcceptedBaseCoordinateStore(preferences).coordinates())
    }

    @Test
    fun upsertReplacesExistingCoordinateById() {
        val store = AcceptedBaseCoordinateStore(FakeAcceptedBaseCoordinatePreferences())

        store.upsert(sampleCoordinate("base-1", name = "Old"))
        store.upsert(sampleCoordinate("base-1", name = "New"))

        assertEquals(listOf("New"), store.coordinates().map { it.name })
    }

    @Test
    fun deleteClearsSelectedCoordinateId() {
        val store = AcceptedBaseCoordinateStore(FakeAcceptedBaseCoordinatePreferences())
        store.upsert(sampleCoordinate("base-1"))
        store.saveSelectedCoordinateId("base-1")

        store.delete("base-1")

        assertEquals(emptyList<AcceptedBaseCoordinate>(), store.coordinates())
        assertNull(store.selectedCoordinateId())
        assertNull(store.selectedCoordinate())
    }

    @Test
    fun ignoresSelectedCoordinateWhenMissing() {
        val store = AcceptedBaseCoordinateStore(FakeAcceptedBaseCoordinatePreferences())
        store.saveSelectedCoordinateId("missing")

        assertNull(store.selectedCoordinate())
    }

    private fun sampleCoordinate(
        id: String,
        name: String = "Base",
    ): AcceptedBaseCoordinate =
        AcceptedBaseCoordinate(
            id = id,
            name = name,
            latDeg = 50.0,
            lonDeg = 14.0,
            ellipsoidalHeightM = 300.0,
            frame = "ETRS89",
            epoch = null,
            method = "MANUAL_KNOWN_POINT",
            durationSeconds = null,
            horizontalUncertaintyM = null,
            verticalUncertaintyM = null,
            antennaHeightM = null,
            antennaReferencePoint = null,
            sourceSessionId = null,
            sourceDescription = "Manual entry",
        )

    private class FakeAcceptedBaseCoordinatePreferences : AcceptedBaseCoordinatePreferences {
        private val values = mutableMapOf<String, String>()

        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }
}
