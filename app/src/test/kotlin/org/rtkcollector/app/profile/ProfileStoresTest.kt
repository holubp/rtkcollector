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
            commandProfileId = "um980-binary-multihz",
            usbBaudProfileId = "um980-230400",
            ntripCasterProfileId = "caster",
            ntripMountpointProfileId = "mountpoint",
            recordingOutputProfileId = "default-record-everything",
            storageProfileId = "app-private",
        )

        defaults.validate()

        assertEquals("um980", defaults.receiverProfileId)
        assertEquals("um980-binary-multihz", defaults.commandProfileId)
        assertEquals("um980-230400", defaults.usbBaudProfileId)
        assertEquals("caster", defaults.ntripCasterProfileId)
        assertEquals("mountpoint", defaults.ntripMountpointProfileId)
        assertEquals("default-record-everything", defaults.recordingOutputProfileId)
        assertEquals("app-private", defaults.storageProfileId)
    }

    @Test
    fun `command profile json round trip preserves protected built in marker`() {
        val profile = CommandProfile(
            id = "um980-binary-multihz",
            name = "UM980 binary multi-Hz",
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

    @Test
    fun `command profile json round trip preserves runtime script`() {
        val profile = CommandProfile(
            id = "um980-binary-multihz",
            name = "UM980 binary multi-Hz",
            runtimeScript = ProfileStores.UM980_BINARY_MULTI_HZ_SCRIPT,
        )

        val decoded = CommandProfile.fromJson(profile.toJson())

        assertTrue(decoded.runtimeScript.contains("CONFIG PPP ENABLE E6-HAS"))
        assertTrue(decoded.runtimeScript.contains("BESTNAVB COM1 0.05"))
        assertTrue(decoded.runtimeScript.contains("PPPNAVB COM1 1"))
        assertTrue(decoded.runtimeScript.contains("OBSVMCMPB COM1 0.25"))
        assertTrue(decoded.runtimeScript.contains("STADOPB COM1 1"))
    }

    @Test
    fun `new command profile has no hidden pre runtime init script`() {
        assertEquals("", CommandProfile(id = "commands", name = "Commands").initScript)
    }

    @Test
    fun `ascii ppp nmea profile contains requested ppp and nmea commands`() {
        val script = ProfileStores.UM980_ASCII_PPP_NMEA_SCRIPT

        assertTrue(script.contains("CONFIG PPP ENABLE E6-HAS"))
        assertTrue(script.contains("GNGGA 0.05"))
        assertTrue(script.contains("PPPNAVA 10"))
        assertTrue(script.contains("TROPINFOA ONCHANGED"))
    }

    @Test
    fun `blank gga upload policy remains blank`() {
        val profile = NtripMountpointProfile(
            id = "mount",
            name = "Mount",
            casterProfileId = "caster",
            ggaUploadPolicy = "",
        )

        val decoded = NtripMountpointProfile.fromJson(profile.toJson())

        assertEquals("", decoded.ggaUploadPolicy)
    }

    @Test
    fun `mountpoint display uses actual mountpoint not profile name`() {
        assertEquals(
            "n/a",
            NtripMountpointProfile(
                id = "mount",
                name = "TUBO00CZE0",
                casterProfileId = "caster",
                mountpoint = "",
            ).displayMountpoint(),
        )
        assertEquals(
            "TUBO00CZE0",
            NtripMountpointProfile(
                id = "mount",
                name = "Friendly name",
                casterProfileId = "caster",
                mountpoint = "TUBO00CZE0",
            ).displayMountpoint(),
        )
    }

    @Test
    fun `settings export password option defaults to redacted`() {
        assertTrue(SettingsSetExportOptions().passwordWarning == null)
        assertTrue(SettingsSetExportOptions(includePlaintextPasswords = true).passwordWarning!!.contains("Plaintext"))
    }

    @Test
    fun `migration rewrites old um980 command profile and appends ascii profile`() {
        val migrated = ProfileStoreMigrations.commandProfiles(
            profiles = listOf(
                CommandProfile(
                    id = ProfileStores.OLD_UM980_COMMAND_PROFILE_ID,
                    name = "UM980 default commands",
                    isProtected = true,
                ),
            ),
            defaults = listOf(
                CommandProfile(
                    id = ProfileStores.UM980_BINARY_MULTI_HZ_PROFILE_ID,
                    name = "UM980 binary multi-Hz",
                    runtimeScript = ProfileStores.UM980_BINARY_MULTI_HZ_SCRIPT,
                ),
                CommandProfile(
                    id = ProfileStores.UM980_ASCII_PPP_NMEA_PROFILE_ID,
                    name = "UM980 ASCII PPP/NMEA",
                    runtimeScript = ProfileStores.UM980_ASCII_PPP_NMEA_SCRIPT,
                ),
            ),
        )

        assertTrue(migrated.none { it.id == ProfileStores.OLD_UM980_COMMAND_PROFILE_ID })
        assertTrue(migrated.first { it.id == ProfileStores.UM980_BINARY_MULTI_HZ_PROFILE_ID }.runtimeScript.contains("BESTNAVB COM1 0.05"))
        assertTrue(migrated.first { it.id == ProfileStores.UM980_BINARY_MULTI_HZ_PROFILE_ID }.runtimeScript.contains("PPPNAVB COM1 1"))
        assertTrue(migrated.first { it.id == ProfileStores.UM980_ASCII_PPP_NMEA_PROFILE_ID }.runtimeScript.contains("CONFIG PPP ENABLE E6-HAS"))
        assertTrue(migrated.none { it.isProtected })
    }

    @Test
    fun `migration appends binary ppp status output to ppp enabled script`() {
        val migrated = ProfileStoreMigrations.commandProfiles(
            profiles = listOf(
                CommandProfile(
                    id = ProfileStores.UM980_BINARY_MULTI_HZ_PROFILE_ID,
                    name = "UM980 binary multi-Hz",
                    runtimeScript = """
                        UNLOG COM1
                        MODE ROVER
                        CONFIG PPP ENABLE E6-HAS
                        BESTNAVB COM1 0.05
                    """.trimIndent(),
                ),
            ),
            defaults = emptyList(),
        )

        assertTrue(migrated.single().runtimeScript.contains("PPPNAVB COM1 1"))
    }

    @Test
    fun `migration removes old fake mountpoint and blanks none gga policy`() {
        val migrated = ProfileStoreMigrations.ntripMountpointProfiles(
            profiles = listOf(
                NtripMountpointProfile(
                    id = ProfileStores.OLD_NTRIP_MOUNTPOINT_PROFILE_ID,
                    name = "NTRIP mountpoint",
                    casterProfileId = ProfileStores.DEFAULT_NTRIP_CASTER_PROFILE_ID,
                    isProtected = true,
                ),
                NtripMountpointProfile(
                    id = "real",
                    name = "Real",
                    casterProfileId = ProfileStores.DEFAULT_NTRIP_CASTER_PROFILE_ID,
                    mountpoint = "TUBO00CZE0",
                    ggaUploadPolicy = "NONE",
                ),
            ),
            defaults = emptyList(),
        )

        assertEquals(listOf("real"), migrated.map(NtripMountpointProfile::id))
        assertEquals("", migrated.single().ggaUploadPolicy)
    }

    @Test
    fun `migration rewrites old settings set profile references`() {
        val migrated = ProfileStoreMigrations.settingsSets(
            settingsSets = listOf(
                RecordingSettingsSet.builtInRoverNtrip().copy(
                    commandProfileRef = ProfileReference(ProfileStores.OLD_UM980_COMMAND_PROFILE_ID, "UM980 default commands"),
                    ntripMountpointProfileRef = ProfileReference(ProfileStores.OLD_NTRIP_MOUNTPOINT_PROFILE_ID, "NTRIP mountpoint"),
                    recordingOutputProfileRef = ProfileReference(ProfileStores.DEFAULT_RECORDING_POLICY_ID, "Default V1 recording policy"),
                ),
            ),
            defaults = emptyList(),
        )

        val set = migrated.single()
        assertEquals(ProfileStores.UM980_BINARY_MULTI_HZ_PROFILE_ID, set.commandProfileRef.id)
        assertEquals(null, set.ntripMountpointProfileRef)
        assertEquals("Default V1 recording outputs", set.recordingOutputProfileRef.name)
    }
}
