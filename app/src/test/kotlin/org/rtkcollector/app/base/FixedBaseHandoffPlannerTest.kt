package org.rtkcollector.app.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.ProfileDeviceFilter
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.SettingsSetOptionPolicies
import org.rtkcollector.app.profile.SettingsSetOptionPolicy
import org.rtkcollector.app.profile.SettingsSetOverrides
import org.rtkcollector.app.profile.ActiveSetupSelections
import org.rtkcollector.app.profile.SelectionChoice
import org.rtkcollector.app.profile.WorkflowActivationMode
import org.rtkcollector.app.profile.withWorkflowActivationMode
import org.rtkcollector.app.profile.modelTestBaseCoordinate
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
    fun `effective active command is the advertised fixed-base template`() {
        val candidates = FixedBaseHandoffPlanner.eligibleSettingsSets(
            settingsSets = listOf(fixedBaseSet("fixed", "rover-command")),
            commandProfiles = listOf(
                command("rover-command", "MODE ROVER SURVEY"),
                command("active-base", "MODE BASE 49 15 707"),
            ),
            filter = ProfileDeviceFilter.ANY,
            effectiveCommandId = { "active-base" },
        )
        assertEquals("active-base", candidates.single().commandProfile?.id)
    }

    @Test
    fun `shared editable command offers derivation without forcing it`() {
        val candidates = FixedBaseHandoffPlanner.eligibleSettingsSets(
            settingsSets = listOf(fixedBaseSet("target", "shared"), fixedBaseSet("other", "shared")),
            commandProfiles = listOf(command("shared", "MODE BASE 49 15 707")),
            filter = ProfileDeviceFilter.ANY,
        )
        assertTrue(candidates.first().requiresDerivedCommandProfile)
        assertFalse(candidates.first().requiresDerivedSettingsSet)
        assertEquals(setOf("other"), candidates.first().affectedSettingsSetIds)
    }

    @Test
    fun `affected sets include active and remembered command references`() {
        val sets = listOf(
            fixedBaseSet("target", "shared"),
            fixedBaseSet("active", "other-command"),
            fixedBaseSet("remembered", "other-command"),
        )
        val selections = mapOf(
            "active" to ActiveSetupSelections("active", activeChoices = mapOf(
                ActiveSetupOptionKey.RECEIVER_COMMAND to SelectionChoice.profile("shared"))),
            "remembered" to ActiveSetupSelections("remembered", rememberedChoices = mapOf(
                ActiveSetupOptionKey.RECEIVER_COMMAND to SelectionChoice.profile("shared"))),
        )
        val candidates = FixedBaseHandoffPlanner.eligibleSettingsSets(
            sets, listOf(command("shared", "MODE BASE TIME 120 2.5")), ProfileDeviceFilter.ANY,
            commandUsageIds = { set -> setOf(set.commandProfileRef.id) +
                listOfNotNull(
                    selections[set.id]?.activeChoices?.get(ActiveSetupOptionKey.RECEIVER_COMMAND)?.profileId,
                    selections[set.id]?.rememberedChoices?.get(ActiveSetupOptionKey.RECEIVER_COMMAND)?.profileId,
                ) },
        )
        assertEquals(setOf("active", "remembered"), candidates.single().affectedSettingsSetIds)
    }

    @Test
    fun `explicit update of shared command retains other set and preserves non mode commands`() {
        val sets = listOf(fixedBaseSet("target", "shared"), fixedBaseSet("other", "shared"))
        val commands = listOf(command("shared", "UNLOG COM1\nMODE BASE TIME 120 2.5\nGNGGA 1"))
        val candidate = FixedBaseHandoffPlanner.eligibleSettingsSets(sets, commands, ProfileDeviceFilter.ANY).first()

        val plan = FixedBaseHandoffPlanner.prepare(candidate, sets, commands, modelTestBaseCoordinate(),
            "new-set", "new-command", FixedBaseCommandAction.UPDATE_EXISTING)

        assertEquals("shared", plan.overwritesCommandId)
        assertEquals(setOf("other"), plan.confirmedAffectedSettingsSetIds)
        assertEquals("shared", plan.targetSet.commandProfileRef.id)
        assertEquals("shared", plan.settingsSets.last().commandProfileRef.id)
        assertEquals("UNLOG COM1\n${modelTestBaseCoordinate().toFixedBaseModeCommand()}\nGNGGA 1",
            plan.commands.single().runtimeScript)
        assertEquals("UNLOG COM1\nMODE BASE TIME 120 2.5\nGNGGA 1", commands.single().runtimeScript)
    }

    @Test
    fun `explicit copy of shared command changes only target reference`() {
        val sets = listOf(fixedBaseSet("target", "shared"), fixedBaseSet("other", "shared"))
        val commands = listOf(command("shared", "MODE BASE TIME 120 2.5\nGNGGA 1"))
        val candidate = FixedBaseHandoffPlanner.eligibleSettingsSets(sets, commands, ProfileDeviceFilter.ANY).first()

        val plan = FixedBaseHandoffPlanner.prepare(candidate, sets, commands, modelTestBaseCoordinate(),
            "new-set", "new-command", FixedBaseCommandAction.COPY_COMMAND)

        assertEquals(null, plan.overwritesCommandId)
        assertTrue(plan.confirmedAffectedSettingsSetIds.isEmpty())
        assertEquals("new-command", plan.targetSet.commandProfileRef.id)
        assertEquals("shared", plan.settingsSets.last().commandProfileRef.id)
        assertEquals(commands.single(), plan.commands.first())
    }

    @Test
    fun `handoff preserves workflow activation policy and selects fixed base`() {
        listOf(WorkflowActivationMode.SELECT_CHANGEABLE, WorkflowActivationMode.SELECT_LOCKED,
            WorkflowActivationMode.LET_USER_SELECT_BEFORE_START,
            WorkflowActivationMode.LET_USER_SELECT_EACH_RECORDING,
            WorkflowActivationMode.LEAVE_CURRENT_INTACT).forEach { mode ->
            val set = fixedBaseSet("target", "base-command").withWorkflowActivationMode(mode)
            val commands = listOf(command("base-command", "MODE BASE TIME 120 2.5"))
            val candidate = FixedBaseHandoffPlanner.eligibleSettingsSets(listOf(set), commands,
                ProfileDeviceFilter.ANY).single()
            val plan = FixedBaseHandoffPlanner.prepare(candidate, listOf(set), commands,
                modelTestBaseCoordinate(), "new-set", "new-command", FixedBaseCommandAction.UPDATE_EXISTING)
            assertEquals(set.workflowApplicationPolicy, plan.targetSet.workflowApplicationPolicy)
            assertEquals("fixed-base", plan.selections.workflowBaselineId
                ?: plan.selections.activeChoices[ActiveSetupOptionKey.WORKFLOW]?.profileId
                ?: plan.selections.rememberedChoices[ActiveSetupOptionKey.WORKFLOW]?.profileId
                ?: plan.selections.transientChoices[ActiveSetupOptionKey.WORKFLOW]?.profileId
                ?: plan.targetSet.workflowId)
        }
    }

    @Test
    fun `locked command profile ignores a stale local override`() {
        val set = fixedBaseSet("fixed", "base-command").copy(
            optionPolicies = SettingsSetOptionPolicies.defaults().withPolicy(
                ActiveSetupOptionKey.RECEIVER_COMMAND,
                SettingsSetOptionPolicy.LOCKED,
            ),
            overrides = SettingsSetOverrides(commandProfileRef = ProfileReference("rover-command", "rover-command")),
        )
        val candidates = FixedBaseHandoffPlanner.eligibleSettingsSets(
            settingsSets = listOf(set),
            commandProfiles = listOf(
                command("base-command", "MODE BASE 49 15 707"),
                command("rover-command", "MODE ROVER SURVEY"),
            ),
            filter = ProfileDeviceFilter.ANY,
        )

        assertEquals("base-command", candidates.single().commandProfile?.id)
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
