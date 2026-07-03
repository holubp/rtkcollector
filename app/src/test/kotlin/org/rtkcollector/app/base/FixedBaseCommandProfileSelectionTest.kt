package org.rtkcollector.app.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet

class FixedBaseCommandProfileSelectionTest {
    @Test
    fun `template profiles include only MODE BASE command profiles`() {
        val profiles = listOf(
            commandProfile("rover", "MODE ROVER SURVEY\nBESTNAVB COM1 1"),
            commandProfile("base-time", "UNLOG COM1\nMODE BASE TIME 120 2.5\nRTCM1074 COM1 1"),
            commandProfile("base-fixed", "UNLOG COM1\nMODE BASE 49.0 15.0 700.0\nRTCM1074 COM1 1"),
        )

        val selected = FixedBaseCommandProfileSelection.templateProfiles(profiles)

        assertEquals(listOf("base-time", "base-fixed"), selected.map { it.id })
    }

    @Test
    fun `overwrite profiles exclude protected and shared profiles`() {
        val profiles = listOf(
            commandProfile("base-protected", "MODE BASE TIME 120 2.5", isProtected = true),
            commandProfile("base-shared", "MODE BASE TIME 120 2.5"),
            commandProfile("base-editable", "MODE BASE TIME 120 2.5"),
            commandProfile("rover", "MODE ROVER SURVEY"),
        )
        val settingsSets = listOf(
            settingsSet("current", "rover"),
            settingsSet("other", "base-shared"),
        )

        val selected = FixedBaseCommandProfileSelection.overwriteProfiles(
            commandProfiles = profiles,
            settingsSets = settingsSets,
            selectedSettingsSetId = "current",
        )

        assertEquals(listOf("base-editable"), selected.map { it.id })
    }

    private fun commandProfile(
        id: String,
        runtimeScript: String,
        isProtected: Boolean = false,
    ): CommandProfile =
        CommandProfile(
            id = id,
            name = id,
            runtimeScript = runtimeScript,
            isProtected = isProtected,
        )

    private fun settingsSet(
        id: String,
        commandProfileId: String,
    ): RecordingSettingsSet =
        RecordingSettingsSet(
            id = id,
            name = id,
            workflowId = "workflow-rover",
            receiverProfileId = "receiver-1",
            commandProfileRef = ProfileReference(commandProfileId, commandProfileId),
            usbBaudProfileRef = ProfileReference("baud-1", "Baud"),
            recordingOutputProfileRef = ProfileReference("output-1", "Output"),
            storageProfileRef = ProfileReference("storage-1", "Storage"),
        )
}
