package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveSelectionContractTest {
    @Test
    fun `choose once starts unchosen and survives failed start until reapply`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER),
        )
        val initial = ActiveSetupSelections(settingsSetId = set.id)
        assertTrue(ActiveSetupResolver.resolve(set, initial).option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).requiresUserSelection)

        val chosen = initial.choose(set, ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SelectionChoice.profile("source-b"))
        assertEquals("source-b", ActiveSetupResolver.resolve(set, chosen.afterStopOrFailedStart()).option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).effectiveValueId)
        assertTrue(ActiveSetupResolver.resolve(set, chosen.reapply(set)).option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).requiresUserSelection)
    }

    @Test
    fun `explicit none differs from unchosen and ask choice is per run`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.SOLUTION_POLICY, SettingsSetOptionPolicy.ASK_EVERY_TIME),
        )
        val initial = ActiveSetupSelections(settingsSetId = set.id)
        val chosen = initial.choose(set, ActiveSetupOptionKey.SOLUTION_POLICY, SelectionChoice.none())
        assertFalse(ActiveSetupResolver.resolve(set, chosen).option(ActiveSetupOptionKey.SOLUTION_POLICY).requiresUserSelection)
        assertTrue(ActiveSetupResolver.resolve(set, chosen.afterStopOrFailedStart()).option(ActiveSetupOptionKey.SOLUTION_POLICY).requiresUserSelection)
        assertTrue(ActiveSetupResolver.resolve(set, ActiveSetupSelections.fromJson(chosen.toJson())).option(ActiveSetupOptionKey.SOLUTION_POLICY).requiresUserSelection)
    }

    @Test
    fun `fixed upload locks enabled and profile as one pair`() {
        val set = RecordingSettingsSet.builtInFixedBase().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD, SettingsSetOptionPolicy.LOCKED),
        )
        val initial = ActiveSetupSelections(settingsSetId = set.id)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            initial.chooseUpload(set, UploadSelection(true, SelectionChoice.profile("upload-a")))
        }
    }

    @Test
    fun `selection state is isolated by settings set identity`() {
        val set = RecordingSettingsSet.builtInRoverNtrip()
        val chosen = ActiveSetupSelections(settingsSetId = set.id).choose(
            set, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("command-b"),
        )
        val other = RecordingSettingsSet.builtInPlainRover()
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            ActiveSetupResolver.resolve(other, chosen)
        }
    }

    @Test
    fun `leave intact workflow becomes reapply baseline rather than modification`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            workflowApplicationPolicy = WorkflowApplicationPolicy.LEAVE_INTACT,
        )
        val applied = ActiveSetupSelections(set.id).reapply(set, currentWorkflowId = "plain-rover")
        val setup = ActiveSetupResolver.resolve(set, applied)
        assertEquals("plain-rover", setup.snapshot().profileId(ActiveSetupOptionKey.WORKFLOW))
        assertFalse(setup.isModified)
    }

    @Test
    fun `ask upload requires explicit enable choice even when default is off`() {
        val set = RecordingSettingsSet.builtInFixedBase().copy(
            ntripCasterUploadProfileRef = ProfileReference("upload-default", "Default upload"),
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD, SettingsSetOptionPolicy.ASK_EVERY_TIME),
        )
        val initial = ActiveSetupSelections(set.id)
        assertTrue(ActiveSetupResolver.resolve(set, initial).option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD).requiresUserSelection)
        val off = initial.chooseUpload(set, UploadSelection(false, SelectionChoice.profile("upload-other")))
        val setup = ActiveSetupResolver.resolve(set, off)
        assertFalse(setup.snapshot().uploadEnabled)
        assertFalse(setup.option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD).requiresUserSelection)
        assertTrue(setup.snapshot().profileId(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD) == null)
        assertFalse(ActiveSetupResolver.resolve(set, off.afterStopOrFailedStart()).snapshot().uploadEnabled)
        assertTrue(ActiveSetupResolver.resolve(set, off.afterStopOrFailedStart()).option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD).requiresUserSelection)
    }

    @Test
    fun `inactive missing correction and rtklib choices do not block plain rover`() {
        val set = RecordingSettingsSet.builtInPlainRover().copy(
            ntripMountpointProfileRef = ProfileReference("missing-source", "Missing source"),
            rtklibProfileRef = ProfileReference("missing-rtklib", "Missing RTKLIB"),
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER)
                .withPolicy(ActiveSetupOptionKey.RTKLIB, SettingsSetOptionPolicy.ASK_EVERY_TIME),
        )
        val setup = ActiveSetupResolver.resolve(set, ActiveSetupSelections(set.id))
        assertTrue(setup.canStart)
        assertFalse(setup.option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).applicable)
        assertFalse(setup.option(ActiveSetupOptionKey.RTKLIB).applicable)
    }

    @Test
    fun `projection replaces complete references and discards legacy field overlays`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripMountpointProfileRef = ProfileReference("source-default", "Default source"),
            overrides = SettingsSetOverrides(command = CommandProfileOverride(initScript = "DANGEROUS")),
        )
        val selections = ActiveSetupSelections(set.id).choose(
            set, ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SelectionChoice.profile("source-other"),
        )
        val setup = ActiveSetupResolver.resolve(set, selections)
        val projected = setup.projectSettingsSet(set) { key, id ->
            if (key == ActiveSetupOptionKey.NTRIP_MOUNTPOINT && id == "source-other") {
                ProfileReference(id, "Other source")
            } else null
        }
        assertEquals("source-default", set.ntripMountpointProfileRef?.id)
        assertEquals("source-other", projected.ntripMountpointProfileRef?.id)
        assertEquals("Other source", projected.ntripMountpointProfileRef?.name)
        assertFalse(projected.overrides.hasChanges)
        assertEquals("source-other", setup.snapshot().profileId(ActiveSetupOptionKey.NTRIP_MOUNTPOINT))
    }

    @Test
    fun `choose once unchosen is starting state and explicit choice beats older memory`() {
        val set = RecordingSettingsSet.builtInPlainRover().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER),
        )
        val initial = ActiveSetupSelections(set.id)
        assertFalse(ActiveSetupResolver.resolve(set, initial).isModified)
        val remembered = initial.choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("old"))
        val current = remembered.choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("new"))
        assertEquals("new", ActiveSetupResolver.resolve(set, current).option(ActiveSetupOptionKey.RECEIVER_COMMAND).effectiveValueId)
    }

    @Test
    fun `policy mutation and copying never awaken dormant choices`() {
        val set = RecordingSettingsSet.builtInPlainRover()
        val choice = ActiveSetupSelections(set.id).choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("other"))
        val locked = set.copy(optionPolicies = set.optionPolicies.withPolicy(ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.LOCKED))
        val dormant = choice.afterPolicyChange(set, locked)
        val restored = dormant.afterPolicyChange(locked, set)
        assertEquals(set.commandProfileRef.id, ActiveSetupResolver.resolve(set, restored).option(ActiveSetupOptionKey.RECEIVER_COMMAND).effectiveValueId)
        val copiedSet = set.copySet("copied", "Copied")
        assertEquals(copiedSet.commandProfileRef.id, ActiveSetupResolver.resolve(copiedSet, choice.copyForSettingsSet(copiedSet.id)).option(ActiveSetupOptionKey.RECEIVER_COMMAND).effectiveValueId)
    }

    @Test
    fun `workflow activation policy edits do not awaken old choose memory`() {
        val invalid = RecordingSettingsSet.builtInPlainRover().copy(optionPolicies = SettingsSetOptionPolicies.defaults()
            .withPolicy(ActiveSetupOptionKey.WORKFLOW, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER))
        val old = ActiveSetupSelections(invalid.id, rememberedChoices = mapOf(ActiveSetupOptionKey.WORKFLOW to SelectionChoice.profile("rover-ntrip")))
        assertFalse(ActiveSetupResolver.resolve(invalid, old).canStart)
        val valid = invalid.copy(workflowApplicationPolicy = WorkflowApplicationPolicy.LET_USER_SELECT)
        val updated = old.afterPolicyChange(invalid, valid)
        assertTrue(ActiveSetupResolver.resolve(valid, updated).option(ActiveSetupOptionKey.WORKFLOW).requiresUserSelection)
    }

    @Test
    fun `rtklib rover needs correction source and base defaults precede global memory`() {
        val set = RecordingSettingsSet.builtInPlainRover().copy(
            workflowId = "rover-rtklib",
            basePositionProfileRef = ProfileReference("set-base", "Set base"),
        )
        assertTrue(ActiveSetupResolver.resolve(set, ActiveSetupSelections(set.id)).option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).applicable)
        val baseSet = set.copy(workflowId = "fixed-base")
        assertEquals("set-base", baseSet.effectiveBaseCoordinateId("global-base"))
    }

    @Test
    fun `incomplete workflow display shows unchosen without substituting default`() {
        val set = RecordingSettingsSet.builtInPlainRover().copy(optionPolicies = SettingsSetOptionPolicies.defaults()
            .withPolicy(ActiveSetupOptionKey.WORKFLOW, SettingsSetOptionPolicy.ASK_EVERY_TIME))
        val setup = ActiveSetupResolver.resolve(set, ActiveSetupSelections(set.id))
        assertFalse(setup.isModified)
        val display = setup.displayProjection(set) { _, id -> ProfileReference(id, id) }
        assertEquals(null, display.snapshot.profileId(ActiveSetupOptionKey.WORKFLOW))
        assertEquals(null, display.references[ActiveSetupOptionKey.WORKFLOW])
        assertTrue(display.messages.any { it.key == ActiveSetupOptionKey.WORKFLOW })
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            setup.projectSettingsSet(set) { _, id -> ProfileReference(id, id) }
        }
    }

    @Test
    fun `choose once upload off persists as explicit pair while enabled unchosen profile blocks`() {
        val set = RecordingSettingsSet.builtInTemporaryBase().copy(optionPolicies = SettingsSetOptionPolicies.defaults()
            .withPolicy(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER))
        val initial = ActiveSetupSelections(set.id)
        assertTrue(ActiveSetupResolver.resolve(set, initial).option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD).requiresUserSelection)
        val off = initial.chooseUpload(set, UploadSelection(false, SelectionChoice.profile("remembered-upload")))
        val restored = ActiveSetupSelections.fromJson(off.afterStopOrFailedStart().toJson())
        assertEquals(off.uploadSelection, restored.uploadSelection)
        assertFalse(ActiveSetupResolver.resolve(set, restored).option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD).requiresUserSelection)
        val incomplete = initial.chooseUpload(set, UploadSelection(true, SelectionChoice.unchosen()))
        assertTrue(ActiveSetupResolver.resolve(set, incomplete).option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD).requiresUserSelection)
    }
}
