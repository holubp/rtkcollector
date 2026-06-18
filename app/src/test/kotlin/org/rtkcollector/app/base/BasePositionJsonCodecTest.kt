package org.rtkcollector.app.base

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BasePositionJsonCodecTest {
    @Test
    fun roundTripsFullAcceptedCoordinate() {
        val coordinate = AcceptedBaseCoordinate(
            id = "base-1",
            name = "Tripod base",
            latDeg = 50.1,
            lonDeg = 14.2,
            ellipsoidalHeightM = 300.3,
            frame = "ETRS89",
            epoch = "2026.46",
            method = "STATIC_RTK",
            durationSeconds = 1200,
            horizontalUncertaintyM = 0.01,
            verticalUncertaintyM = 0.02,
            antennaHeightM = 1.234,
            antennaReferencePoint = "ARP",
            sourceSessionId = "session-uuid",
            sourceDescription = "Imported precise base",
        )

        val decoded = BasePositionJsonCodec.decode(
            BasePositionJsonCodec.encode(coordinate),
            fallbackId = "ignored",
            fallbackName = "Ignored",
        )

        assertEquals(coordinate, decoded)
    }

    @Test
    fun importsLegacyDashboardBasePositionJson() {
        val legacyJson = JSONObject()
            .put("latDeg", 49.9)
            .put("lonDeg", 15.1)
            .put("heightM", 280.5)
            .put("frame", "UNKNOWN")
            .put("method", "MANUAL_KNOWN_POINT")
            .put("source", "TEMPORARY_BASE_AVERAGE")
            .put("sampleCount", 42)
            .toString()

        val decoded = BasePositionJsonCodec.decode(
            legacyJson,
            fallbackId = "legacy-1",
            fallbackName = "Legacy import",
        )

        assertEquals("legacy-1", decoded.id)
        assertEquals("Legacy import", decoded.name)
        assertEquals(49.9, decoded.latDeg)
        assertEquals(15.1, decoded.lonDeg)
        assertEquals(280.5, decoded.ellipsoidalHeightM)
        assertEquals("UNKNOWN", decoded.frame)
        assertEquals("MANUAL_KNOWN_POINT", decoded.method)
        assertEquals("TEMPORARY_BASE_AVERAGE", decoded.sourceDescription)
        assertEquals(42, decoded.durationSeconds)
        assertNull(decoded.epoch)
    }
}
