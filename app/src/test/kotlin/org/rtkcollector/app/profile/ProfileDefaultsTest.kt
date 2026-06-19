package org.rtkcollector.app.profile

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
    fun `m8t mock profile enables timing marker output`() {
        assertTrue(ProfileStores.UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT.contains("!UBX CFG-MSG 13 3 0 0 0 1 0 0"))
    }
}
