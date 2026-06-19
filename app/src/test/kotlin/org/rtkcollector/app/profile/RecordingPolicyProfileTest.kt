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
        assertEquals(1, decoded.mockLocationRateHz)
    }

    @Test
    fun `recording policy round trip preserves mock location publishing and rate`() {
        val profile = RecordingPolicyProfile(
            id = "recording",
            name = "Recording",
            enableMockLocation = true,
            mockLocationRateHz = 5,
        )

        val decoded = RecordingPolicyProfile.fromJson(profile.toJson())

        assertEquals(true, decoded.enableMockLocation)
        assertEquals(5, decoded.mockLocationRateHz)
    }

    @Test
    fun `rtklib profile round trip preserves preset and outputs`() {
        val profile = RtklibProfile(
            id = "rtklib",
            name = "RTKLIB rover",
            enabled = true,
            preset = "TEMPORARY_BASE_STATIC_RTK",
            outputNmea = true,
            outputPos = false,
            maxRoverQueueBytes = 1234,
            maxCorrectionQueueBytes = 5678,
        )

        val decoded = RtklibProfile.fromJson(profile.toJson())

        assertEquals(profile, decoded)
    }

    @Test
    fun `enabled rtklib profile requires at least one output`() {
        assertThrows(IllegalArgumentException::class.java) {
            RtklibProfile(
                id = "rtklib",
                name = "RTKLIB",
                enabled = true,
                outputNmea = false,
                outputPos = false,
            ).validate()
        }
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

    @Test
    fun `recording policy rejects unsupported mock location rate`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordingPolicyProfile(
                id = "bad",
                name = "Bad",
                mockLocationRateHz = 3,
            ).validate()
        }
    }
}
