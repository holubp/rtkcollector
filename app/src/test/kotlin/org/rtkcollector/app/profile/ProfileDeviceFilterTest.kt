package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileDeviceFilterTest {
    @Test
    fun `Any includes all settings sets and command profiles`() {
        assertTrue(ProfileDeviceFilter.ANY.matchesSettingsSet(settingsSet("um980-rover", "um980-n4")))
        assertTrue(ProfileDeviceFilter.ANY.matchesSettingsSet(settingsSet("m8t-rover", "ublox-m8t")))
        assertTrue(ProfileDeviceFilter.ANY.matchesCommandProfile(commandProfile("um980", "um980-n4")))
        assertTrue(ProfileDeviceFilter.ANY.matchesCommandProfile(commandProfile("m8t", "ublox-m8t")))
    }

    @Test
    fun `UM980 includes UM980 Unicore and N4 profiles only`() {
        assertTrue(ProfileDeviceFilter.UM980.matchesSettingsSet(settingsSet("um980", "um980-n4")))
        assertTrue(ProfileDeviceFilter.UM980.matchesSettingsSet(settingsSet("unicore", "unicore-n4")))
        assertTrue(ProfileDeviceFilter.UM980.matchesCommandProfile(commandProfile("n4", "unicore-n4")))
        assertFalse(ProfileDeviceFilter.UM980.matchesSettingsSet(settingsSet("m8t", "ublox-m8t")))
        assertFalse(ProfileDeviceFilter.UM980.matchesCommandProfile(commandProfile("m8t", "ublox-m8t")))
    }

    @Test
    fun `u-blox M8T includes M8T profiles only`() {
        assertTrue(ProfileDeviceFilter.UBLOX_M8T.matchesSettingsSet(settingsSet("m8t", "ublox-m8t")))
        assertTrue(ProfileDeviceFilter.UBLOX_M8T.matchesCommandProfile(commandProfile("m8t", "ublox-m8t")))
        assertFalse(ProfileDeviceFilter.UBLOX_M8T.matchesSettingsSet(settingsSet("f9p", "ublox-f9p")))
        assertFalse(ProfileDeviceFilter.UBLOX_M8T.matchesCommandProfile(commandProfile("um980", "um980-n4")))
    }

    @Test
    fun `from storage value falls back to Any`() {
        assertEquals(ProfileDeviceFilter.ANY, ProfileDeviceFilter.fromStorageValue(null))
        assertEquals(ProfileDeviceFilter.ANY, ProfileDeviceFilter.fromStorageValue(""))
        assertEquals(ProfileDeviceFilter.UM980, ProfileDeviceFilter.fromStorageValue("um980"))
        assertEquals(ProfileDeviceFilter.UBLOX_M8T, ProfileDeviceFilter.fromStorageValue("ublox-m8t"))
    }

    private fun commandProfile(id: String, receiverFamily: String): CommandProfile =
        CommandProfile(id = id, name = id, receiverFamily = receiverFamily)

    private fun settingsSet(id: String, receiverProfileId: String): RecordingSettingsSet =
        RecordingSettingsSet.builtInRoverNtrip().copy(
            id = id,
            name = id,
            receiverProfileId = receiverProfileId,
        )
}
