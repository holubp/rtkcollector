package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsSetModelsTest {
    @Test
    fun `settings set round trip preserves profile references and command overrides`() {
        val settingsSet = RecordingSettingsSet(
            id = "field-car-roof",
            name = "Car roof rover",
            workflowId = "rover-ntrip",
            receiverProfileId = "um980-n4",
            commandProfileRef = ProfileReference("um980-binary-multihz", "UM980 binary multi-Hz"),
            usbBaudProfileRef = ProfileReference("um980-230400", "UM980 230400"),
            ntripCasterProfileRef = ProfileReference("caster", "EUREF"),
            ntripMountpointProfileRef = ProfileReference("mount", "TUBO00CZE0"),
            recordingOutputProfileRef = ProfileReference("default-record-everything", "Default V1 recording outputs"),
            storageProfileRef = ProfileReference("app-private", "App-private external storage"),
            overrides = SettingsSetOverrides(
                command = CommandProfileOverride(initScript = "UNLOG COM1", shutdownScript = "UNLOG COM1"),
                ntripMountpoint = NtripMountpointOverride(mountpoint = "TUBO00CZE0"),
            ),
        )

        val decoded = RecordingSettingsSet.fromJson(settingsSet.toJson())

        assertEquals("field-car-roof", decoded.id)
        assertEquals("rover-ntrip", decoded.workflowId)
        assertEquals("um980-binary-multihz", decoded.commandProfileRef.id)
        assertEquals("UNLOG COM1", decoded.overrides.command?.initScript)
        assertTrue(decoded.hasLocalOverrides)
    }

    @Test
    fun `settings set rejects plaintext ntrip password override`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordingSettingsSet(
                id = "bad",
                name = "Bad",
                workflowId = "rover-ntrip",
                receiverProfileId = "um980-n4",
                commandProfileRef = ProfileReference("commands", "Commands"),
                usbBaudProfileRef = ProfileReference("baud", "Baud"),
                ntripCasterProfileRef = ProfileReference("caster", "Caster"),
                ntripMountpointProfileRef = ProfileReference("mount", "Mount"),
                recordingOutputProfileRef = ProfileReference("record", "Record"),
                storageProfileRef = ProfileReference("storage", "Storage"),
                overrides = SettingsSetOverrides(ntripCaster = NtripCasterOverride(password = "plain-text")),
            ).validate()
        }
    }

    @Test
    fun `copy settings set creates editable non protected set`() {
        val copied = RecordingSettingsSet.builtInRoverNtrip().copySet(id = "copy", name = "Copy")

        assertEquals("copy", copied.id)
        assertEquals("Copy", copied.name)
        assertFalse(copied.isProtected)
    }

    @Test
    fun `settings set json rejects plaintext ntrip password in persisted form`() {
        val json = kotlin.runCatching {
            SettingsSetJson.fromJson(
                org.json.JSONObject()
                    .put("id", "bad")
                    .put("name", "Bad")
                    .put("workflowId", "rover-ntrip")
                    .put("receiverProfileId", "um980-n4")
                    .put("commandProfile", org.json.JSONObject().put("id", "commands").put("name", "Commands"))
                    .put("usbBaudProfile", org.json.JSONObject().put("id", "baud").put("name", "Baud"))
                    .put("ntripCasterProfile", org.json.JSONObject().put("id", "caster").put("name", "Caster"))
                    .put("ntripMountpointProfile", org.json.JSONObject().put("id", "mount").put("name", "Mount").put("casterProfileId", "caster"))
                    .put("recordingOutputProfile", org.json.JSONObject().put("id", "record").put("name", "Record"))
                    .put("storageProfile", org.json.JSONObject().put("id", "storage").put("name", "Storage"))
                    .put(
                        "overrides",
                        org.json.JSONObject().put(
                            "ntripCaster",
                            org.json.JSONObject()
                                .put("secretId", "secret-1")
                                .put("password", "plain"),
                        ),
                    ),
            )
        }.exceptionOrNull()

        assertTrue(json is IllegalArgumentException)
    }

    @Test
    fun `display name indicates local changes`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            overrides = SettingsSetOverrides(recordingOutput = RecordingOutputOverride(exportGpx = true)),
        )

        assertEquals("UM980 rover + NTRIP + local changes", set.displayNameWithOverrides())
    }
}
