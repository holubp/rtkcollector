package org.rtkcollector.app.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.ProfileDeviceFilter
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet
import java.util.Date

class FixedBaseHandoffPlannerTest {
    @Test
    fun `eligible settings sets are fixed-base only and require MODE BASE command profile`() {
        val candidates = FixedBaseHandoffPlanner.eligibleSettingsSets(
            settingsSets = listOf(
                fixedBaseSet("fixed", "base-command"),
                fixedBaseSet("bad", "rover-command"),
                fixedBaseSet("m8t", "base-command", receiverProfileId = "ublox-m8t"),
                fixedBaseSet("rover", "base-command").copy(workflowId = "rover-ntrip"),
            ),
            commandProfiles = listOf(
                command("base-command", "MODE BASE 49 15 707"),
                command("rover-command", "MODE ROVER SURVEY"),
            ),
            filter = ProfileDeviceFilter.UM980,
        )

        assertEquals(listOf("fixed"), candidates.map { it.settingsSet.id })
    }

    @Test
    fun `built-in settings set is routed to derive new`() {
        val candidates = FixedBaseHandoffPlanner.eligibleSettingsSets(
            settingsSets = listOf(fixedBaseSet("built-in", "base-command", protected = true)),
            commandProfiles = listOf(command("base-command", "MODE BASE 49 15 707")),
            filter = ProfileDeviceFilter.ANY,
        )

        assertTrue(candidates.single().requiresDerivedSettingsSet)
        assertEquals("Immutable settings set: derive a new set", candidates.single().reason)
    }

    @Test
    fun `editable settings set can be used directly`() {
        val candidates = FixedBaseHandoffPlanner.eligibleSettingsSets(
            settingsSets = listOf(fixedBaseSet("editable", "base-command")),
            commandProfiles = listOf(command("base-command", "MODE BASE 49 15 707")),
            filter = ProfileDeviceFilter.ANY,
        )

        assertFalse(candidates.single().requiresDerivedSettingsSet)
    }

    @Test
    fun `preferred settings set uses remembered id before first editable`() {
        val candidates = FixedBaseHandoffPlanner.eligibleSettingsSets(
            settingsSets = listOf(
                fixedBaseSet("first", "base-command"),
                fixedBaseSet("remembered", "base-command"),
            ),
            commandProfiles = listOf(command("base-command", "MODE BASE 49 15 707")),
            filter = ProfileDeviceFilter.ANY,
        )

        assertEquals("remembered", FixedBaseHandoffPlanner.preferredSettingsSetId(candidates, "remembered"))
    }

    @Test
    fun `derived names include compact datetime suffix`() {
        val name = FixedBaseHandoffPlanner.derivedName("UM980 fixed base", Date(1_780_660_500_000L))

        assertTrue(name.matches(Regex("UM980 fixed base 2026-06-05T\\d{4}")))
    }

    private fun fixedBaseSet(
        id: String,
        commandProfileId: String,
        receiverProfileId: String = "um980-n4",
        protected: Boolean = false,
    ): RecordingSettingsSet =
        RecordingSettingsSet.builtInFixedBase().copy(
            id = id,
            name = id,
            receiverProfileId = receiverProfileId,
            commandProfileRef = ProfileReference(commandProfileId, commandProfileId),
            isProtected = protected,
        )

    private fun command(id: String, runtimeScript: String): CommandProfile =
        CommandProfile(id = id, name = id, runtimeScript = runtimeScript)
}
