package org.rtkcollector.app.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SettingsBackupSharePolicyTest {
    @Test
    fun `plaintext backup filename is clearly marked`() {
        val name = settingsBackupFileName(epochMillis = 1234L, includesPlaintextPasswords = true)

        assertEquals("rtkcollector-settings-plaintext-passwords-1234.json", name)
    }

    @Test
    fun `redacted backup filename does not mention passwords`() {
        val name = settingsBackupFileName(epochMillis = 1234L, includesPlaintextPasswords = false)

        assertEquals("rtkcollector-settings-1234.json", name)
    }

    @Test
    fun `cleanup only targets settings backup json files`() {
        assertTrue(isSettingsBackupCacheFile("rtkcollector-settings-1234.json"))
        assertTrue(isSettingsBackupCacheFile("rtkcollector-settings-plaintext-passwords-1234.json"))
        assertFalse(isSettingsBackupCacheFile("session.zip"))
        assertFalse(isSettingsBackupCacheFile("../rtkcollector-settings-1234.json"))
        assertFalse(isSettingsBackupCacheFile("rtkcollector-settings/1234.json"))
        assertFalse(isSettingsBackupCacheFile("foo/rtkcollector-settings-1234.json"))
    }

    @Test
    fun `reject negative timestamp`() {
        assertThrows(IllegalArgumentException::class.java) {
            settingsBackupFileName(epochMillis = -1L, includesPlaintextPasswords = false)
        }
    }
}
