package org.rtkcollector.app.profile

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RecordingPolicyProfileTest {
    @Test
    fun `recording policy round trip preserves ppp nmea quality`() {
        val profile = RecordingPolicyProfile(
            id = "recording",
            name = "Recording",
            pppNmeaGgaQuality = 9,
        )

        val decoded = RecordingPolicyProfile.fromJson(profile.toJson())

        assertEquals(9, decoded.pppNmeaGgaQuality)
    }

    @Test
    fun `recording policy defaults ppp nmea quality to dgps compatibility`() {
        val decoded = RecordingPolicyProfile.fromJson(
            JSONObject()
                .put("id", "old")
                .put("name", "Old recording profile"),
        )

        assertEquals(2, decoded.pppNmeaGgaQuality)
    }

    @Test
    fun `recording policy defaults mock location publishing to disabled`() {
        val decoded = RecordingPolicyProfile.fromJson(
            JSONObject()
                .put("id", "old")
                .put("name", "Old recording profile"),
        )

        assertEquals(false, decoded.enableMockLocation)
    }

    @Test
    fun `recording policy round trip preserves mock location publishing`() {
        val profile = RecordingPolicyProfile(
            id = "recording",
            name = "Recording",
            enableMockLocation = true,
        )

        val decoded = RecordingPolicyProfile.fromJson(profile.toJson())

        assertEquals(true, decoded.enableMockLocation)
    }

    @Test
    fun `recording policy rejects unsupported ppp nmea quality`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordingPolicyProfile(
                id = "bad",
                name = "Bad",
                pppNmeaGgaQuality = 4,
            ).validate()
        }
    }
}
