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
            commandProfileRef = ProfileReference("um980-binary-multihz", "UM980 multi-Hz binary RTK+PPP"),
            usbBaudProfileRef = ProfileReference("um980-230400", "UM980 230400"),
            ntripCasterProfileRef = ProfileReference("caster", "EUREF"),
            ntripMountpointProfileRef = ProfileReference("mount", "TUBO00CZE0"),
            rtklibProfileRef = ProfileReference("rtklib-rover", "RTKLIB rover"),
            recordingOutputProfileRef = ProfileReference("default-record-everything", "Default V1 recording outputs"),
            storageProfileRef = ProfileReference("app-private", "App-private external storage"),
            overrides = SettingsSetOverrides(
                commandProfileRef = ProfileReference("custom-command", "Custom command"),
                storageProfileRef = ProfileReference("custom-storage", "Custom storage"),
                baseCasterUploadEnabled = true,
                command = CommandProfileOverride(initScript = "UNLOG COM1", shutdownScript = "UNLOG COM1"),
                ntripMountpoint = NtripMountpointOverride(mountpoint = "TUBO00CZE0"),
            ),
        )

        val decoded = RecordingSettingsSet.fromJson(settingsSet.toJson())

        assertEquals("field-car-roof", decoded.id)
        assertEquals("rover-ntrip", decoded.workflowId)
        assertEquals("um980-binary-multihz", decoded.commandProfileRef.id)
        assertEquals("rtklib-rover", decoded.rtklibProfileRef?.id)
        assertEquals("custom-command", decoded.overrides.commandProfileRef?.id)
        assertEquals("custom-storage", decoded.overrides.storageProfileRef?.id)
        assertEquals(true, decoded.overrides.baseCasterUploadEnabled)
        assertEquals("UNLOG COM1", decoded.overrides.command?.initScript)
        assertTrue(decoded.hasLocalOverrides)
    }

    @Test
    fun `settings set effective references use local profile overrides`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            overrides = SettingsSetOverrides(
                commandProfileRef = ProfileReference("custom-command", "Custom command"),
            ),
        )

        assertTrue(set.hasLocalOverrides)
        assertEquals("custom-command", set.effectiveCommandProfileRef().id)
    }

    @Test
    fun `reapply clears local reference overrides`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            commandProfileRef = ProfileReference("stored-command", "Stored command"),
            overrides = SettingsSetOverrides(
                commandProfileRef = ProfileReference("custom-command", "Custom command"),
                storageProfileRef = ProfileReference("custom-storage", "Custom storage"),
            ),
        )

        val reapplied = set.reapplied()

        assertFalse(reapplied.hasLocalOverrides)
        assertEquals("stored-command", reapplied.effectiveCommandProfileRef().id)
    }

    @Test
    fun `settings set round trip preserves workflow activation policy`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            id = "ask-workflow",
            name = "Ask workflow",
            isProtected = false,
            workflowApplicationPolicy = WorkflowApplicationPolicy.LET_USER_SELECT,
        )

        val decoded = RecordingSettingsSet.fromJson(settingsSet.toJson())

        assertEquals(WorkflowApplicationPolicy.LET_USER_SELECT, decoded.workflowApplicationPolicy)
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

        assertEquals("UM980 rover + NTRIP +", set.displayNameWithOverrides())
    }

    @Test
    fun `settings set round trip preserves ppp nmea quality override`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            id = "ppp-quality",
            name = "PPP quality override",
            isProtected = false,
            overrides = SettingsSetOverrides(
                recordingOutput = RecordingOutputOverride(pppNmeaGgaQuality = 9),
            ),
        )

        val decoded = RecordingSettingsSet.fromJson(settingsSet.toJson())

        assertEquals(9, decoded.overrides.recordingOutput?.pppNmeaGgaQuality)
    }

    @Test
    fun `settings set round trip preserves mock location override`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            id = "mock-location",
            name = "Mock location override",
            isProtected = false,
            overrides = SettingsSetOverrides(
                recordingOutput = RecordingOutputOverride(enableMockLocation = true, mockLocationRateHz = 10),
            ),
        )

        val decoded = RecordingSettingsSet.fromJson(settingsSet.toJson())

        assertEquals(true, decoded.overrides.recordingOutput?.enableMockLocation)
        assertEquals(10, decoded.overrides.recordingOutput?.mockLocationRateHz)
    }

    @Test
    fun `settings set rejects invalid ppp nmea quality override`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsSetOverrides(
                recordingOutput = RecordingOutputOverride(pppNmeaGgaQuality = 4),
            ).validate()
        }
    }

    @Test
    fun `settings set rejects invalid mock location rate override`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsSetOverrides(
                recordingOutput = RecordingOutputOverride(mockLocationRateHz = 3),
            ).validate()
        }
    }
}
