package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.receiver.ublox.UbloxScriptCompiler

class ProfileDefaultsTest {
    @Test
    fun `m8t default scripts compile to ubx commands`() {
        val scripts = listOf(
            ProfileStores.UBLOX_M8T_RAW_1HZ_SCRIPT,
            ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT,
            ProfileStores.UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT,
        )

        scripts.forEach { script ->
            assertTrue(UbloxScriptCompiler.compile(script).isNotEmpty())
        }
    }

    @Test
    fun `m8t high rate profile contains cfg rate 5 hz`() {
        assertTrue(ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT.contains("!UBX CFG-RATE 200 1 1"))
    }

    @Test
    fun `m8t safe raw profile enables nav pvt for monitoring`() {
        assertTrue(ProfileStores.UBLOX_M8T_RAW_1HZ_SCRIPT.contains("!UBX CFG-MSG 1 7 0 0 0 1 0 0"))
    }

    @Test
    fun `m8t safe raw profile enables nav sat for visible satellite counts`() {
        assertTrue(ProfileStores.UBLOX_M8T_RAW_1HZ_SCRIPT.contains("!UBX CFG-MSG 1 53 0 0 0 1 0 0"))
    }

    @Test
    fun `m8t high rate profile enables nav pvt and nav sat for monitoring`() {
        assertTrue(ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT.contains("!UBX CFG-MSG 1 7 0 0 0 1 0 0"))
        assertTrue(ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT.contains("!UBX CFG-MSG 1 53 0 0 0 1 0 0"))
    }

    @Test
    fun `m8t live monitoring profiles enable nav dop for dop display`() {
        val navDopCommand = "!UBX CFG-MSG 1 4 0 0 0 1 0 0"

        assertTrue(ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT.contains(navDopCommand))
        assertTrue(ProfileStores.UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT.contains(navDopCommand))
    }

    @Test
    fun `m8t safe raw profile does not enable nav dop in this pass`() {
        assertTrue(!ProfileStores.UBLOX_M8T_RAW_1HZ_SCRIPT.contains("!UBX CFG-MSG 1 4 0 0 0 1 0 0"))
    }

    @Test
    fun `m8t mock profile enables timing marker output`() {
        assertTrue(ProfileStores.UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT.contains("!UBX CFG-MSG 13 3 0 0 0 1 0 0"))
    }

    @Test
    fun `built in command profiles declare satellite telemetry capabilities explicitly`() {
        val defaults = ProfileStores.defaultCommandProfilesForTests().associateBy { it.id }

        assertEquals(
            SatelliteTelemetryCapability.UM980_BINARY,
            defaults.getValue(ProfileStores.UM980_BINARY_MULTI_HZ_PROFILE_ID).satelliteTelemetry,
        )
        assertEquals(
            SatelliteTelemetryCapability.UM980_BINARY,
            defaults.getValue(ProfileStores.UM980_BINARY_MULTI_HZ_RTKLIB_OBSVMB_PROFILE_ID).satelliteTelemetry,
        )
        assertEquals(
            SatelliteTelemetryCapability.UM980_ASCII_NMEA,
            defaults.getValue(ProfileStores.UM980_ASCII_PPP_NMEA_PROFILE_ID).satelliteTelemetry,
        )
        assertEquals(
            SatelliteTelemetryCapability.UM980_ASCII_NMEA,
            defaults.getValue(ProfileStores.UM980_ASCII_1HZ_RTK_PPP_PROFILE_ID).satelliteTelemetry,
        )
        assertEquals(
            SatelliteTelemetryCapability.UM980_BINARY,
            defaults.getValue(ProfileStores.UM980_BASE_CONFIG_PROFILE_ID).satelliteTelemetry,
        )
        assertEquals(
            SatelliteTelemetryCapability.UBLOX_NAV_SAT,
            defaults.getValue(ProfileStores.UBLOX_M8T_RAW_1HZ_PROFILE_ID).satelliteTelemetry,
        )
        assertEquals(
            SatelliteTelemetryCapability.UBLOX_NAV_SAT,
            defaults.getValue(ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_PROFILE_ID).satelliteTelemetry,
        )
        assertEquals(
            SatelliteTelemetryCapability.UBLOX_NAV_SAT,
            defaults.getValue(ProfileStores.UBLOX_M8T_RAW_STATUS_MOCK_PROFILE_ID).satelliteTelemetry,
        )
    }

    @Test
    fun `m8t command profiles are protected built ins`() {
        val defaults = ProfileStores.defaultCommandProfilesForTests().associateBy { it.id }

        assertTrue(defaults.getValue(ProfileStores.UBLOX_M8T_RAW_1HZ_PROFILE_ID).isProtected)
        assertTrue(defaults.getValue(ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_PROFILE_ID).isProtected)
        assertTrue(defaults.getValue(ProfileStores.UBLOX_M8T_RAW_STATUS_MOCK_PROFILE_ID).isProtected)
    }
}
