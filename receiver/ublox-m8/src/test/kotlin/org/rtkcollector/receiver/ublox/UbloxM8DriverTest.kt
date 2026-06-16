package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UbloxM8DriverTest {
    @Test
    fun `m8p advertises rover base rtcm and raw capabilities`() {
        val capabilities = UbloxM8Driver(UbloxM8Profile.M8P).capabilities

        assertTrue(capabilities.supportsRoverMode)
        assertTrue(capabilities.supportsBaseMode)
        assertTrue(capabilities.supportsFixedBaseMode)
        assertTrue(capabilities.supportsRtcmInput)
        assertTrue(capabilities.supportsRtcmOutput)
        assertTrue(capabilities.supportsNativeRawObservation)
    }

    @Test
    fun `m8t advertises raw timing role without fixed base correction mode`() {
        val capabilities = UbloxM8Driver(UbloxM8Profile.M8T).capabilities

        assertFalse(capabilities.supportsRoverMode)
        assertFalse(capabilities.supportsBaseMode)
        assertFalse(capabilities.supportsFixedBaseMode)
        assertFalse(capabilities.supportsRtcmInput)
        assertFalse(capabilities.supportsRtcmOutput)
        assertTrue(capabilities.supportsNativeRawObservation)
    }
}
