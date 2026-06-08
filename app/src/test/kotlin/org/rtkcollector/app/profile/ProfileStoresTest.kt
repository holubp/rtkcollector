package org.rtkcollector.app.profile

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileStoresTest {
    @Test
    fun `settings set rename rejects protected built in`() {
        val profiles = listOf(RecordingSettingsSet.builtInRoverNtrip())

        assertThrows(IllegalArgumentException::class.java) {
            renameProfile(
                profiles = profiles,
                profileId = "um980-rover-ntrip",
                newName = "Renamed",
                idOf = RecordingSettingsSet::id,
                isProtectedOf = RecordingSettingsSet::isProtected,
            ) { profile, name -> profile.copy(name = name) }
        }
    }

    @Test
    fun `settings set json redacts secret material`() {
        val json = RecordingSettingsSet.builtInRoverNtrip()
            .copy(
                overrides = SettingsSetOverrides(
                    ntripCaster = NtripCasterOverride(secretId = "secret-1"),
                ),
            )
            .toJson()
            .toString()

        assertTrue(json.contains("secret-1"))
        assertTrue(!json.contains("password"))
    }

    @Test
    fun `renaming editable command profile updates name`() {
        val profiles = listOf(CommandProfile(id = "commands", name = "Original"))

        val renamed = renameProfile(
            profiles = profiles,
            profileId = "commands",
            newName = "Renamed",
            idOf = CommandProfile::id,
            isProtectedOf = CommandProfile::isProtected,
        ) { profile, name -> profile.copy(name = name) }

        assertEquals("Renamed", renamed.single().name)
    }

    @Test
    fun `protected command profile cannot be renamed`() {
        val profiles = listOf(CommandProfile(id = "commands", name = "Built-in", isProtected = true))

        assertThrows(IllegalArgumentException::class.java) {
            renameProfile(
                profiles = profiles,
                profileId = "commands",
                newName = "Renamed",
                idOf = CommandProfile::id,
                isProtectedOf = CommandProfile::isProtected,
            ) { profile, name -> profile.copy(name = name) }
        }
    }

    @Test
    fun `workflow profile defaults carry profile references`() {
        val defaults = WorkflowProfileDefaults(
            workflowId = "rover-ntrip",
            receiverProfileId = "um980",
            commandProfileId = "um980-default-commands",
            usbBaudProfileId = "um980-230400",
            ntripCasterProfileId = "caster",
            ntripMountpointProfileId = "mountpoint",
            recordingOutputProfileId = "default-record-everything",
            storageProfileId = "app-private",
        )

        defaults.validate()

        assertEquals("um980", defaults.receiverProfileId)
        assertEquals("um980-default-commands", defaults.commandProfileId)
        assertEquals("um980-230400", defaults.usbBaudProfileId)
        assertEquals("caster", defaults.ntripCasterProfileId)
        assertEquals("mountpoint", defaults.ntripMountpointProfileId)
        assertEquals("default-record-everything", defaults.recordingOutputProfileId)
        assertEquals("app-private", defaults.storageProfileId)
    }

    @Test
    fun `command profile json round trip preserves protected built in marker`() {
        val profile = CommandProfile(
            id = "um980-default-commands",
            name = "UM980 default commands",
            isProtected = true,
        )

        val decoded = CommandProfile.fromJson(profile.toJson())

        assertTrue(decoded.isProtected)
    }

    @Test
    fun `command profile reads legacy protected json field`() {
        val decoded = CommandProfile.fromJson(
            JSONObject()
                .put("id", "legacy")
                .put("name", "Legacy")
                .put("protected", true),
        )

        assertTrue(decoded.isProtected)
    }
}
