package org.rtkcollector.app.profile

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ActiveSelectionsStoreTest {
    @Test
    fun externalOwnerInvalidationIsDurableAndScoped() {
        assertTrue(context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE).edit().clear().commit())
        val first = ProfileStores(context)
        val state = ActiveSetupSelections("coordinate-set").copy(transientChoices = mapOf(
            ActiveSetupOptionKey.RECEIVER_COMMAND to SelectionChoice.profile("ask")))
        val revision = first.saveActiveSelections(state)
        val unrelated = first.selectionRevision("other")
        first.invalidateNextStartConfiguration(setOf(state.settingsSetId))
        val second = ProfileStores(context)
        assertTrue(second.selectionRevision(state.settingsSetId) > revision)
        assertEquals(unrelated, second.selectionRevision("other"))
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(id = state.settingsSetId)
        assertTrue(second.activeSelections(set).transientChoices.isEmpty())
        assertThrows(SelectionRevisionConflictException::class.java) { second.saveActiveSelections(state, revision) }
    }
    @Test
    fun sameIdOwnerEditInvalidatesRevisionAndAskCache() {
        assertTrue(context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE).edit().clear().commit())
        val store = ProfileStores(context)
        val set = store.settingsSets().first().copy(optionPolicies = SettingsSetOptionPolicies.defaults().withPolicy(
            ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.ASK_EVERY_TIME))
        store.saveSettingsSets(listOf(set))
        val choice = store.activeSelections(set).choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND,
            SelectionChoice.profile(set.commandProfileRef.id))
        val revision = store.saveActiveSelections(choice)
        store.saveCommandProfiles(store.commandProfiles().map {
            if (it.id == set.commandProfileRef.id) it.copy(shutdownScript = "UNLOG COM1") else it
        })
        val second = ProfileStores(context)
        assertTrue(second.selectionRevision(set.id) > revision)
        assertTrue(second.activeSelections(set).transientChoices.isEmpty())
        assertThrows(SelectionRevisionConflictException::class.java) { second.saveActiveSelections(choice, revision) }
    }

    @Test
    fun sameIdPolicyEditClearsIncompatibleDurableMemory() {
        assertTrue(context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE).edit().clear().commit())
        val store = ProfileStores(context)
        val set = store.settingsSets().first().copy(optionPolicies = SettingsSetOptionPolicies.defaults().withPolicy(
            ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER))
        store.saveSettingsSets(listOf(set))
        val state = store.activeSelections(set).choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND,
            SelectionChoice.profile("old-choice"))
        val revision = store.saveActiveSelections(state)
        val locked = set.copy(optionPolicies = set.optionPolicies.withPolicy(
            ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.LOCKED))
        store.saveSettingsSets(listOf(locked))
        val second = ProfileStores(context)
        assertTrue(second.selectionRevision(set.id) > revision)
        assertTrue(second.activeSelections(locked).rememberedChoices.isEmpty())
        val unlocked = locked.copy(optionPolicies = locked.optionPolicies.withPolicy(
            ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE))
        second.saveSettingsSets(listOf(unlocked))
        assertTrue(ProfileStores(context).activeSelections(unlocked).rememberedChoices.isEmpty())
    }
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun staleAcknowledgementFromAnotherStoreCannotOverwriteNewChoice() {
        assertTrue(context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE).edit().clear().commit())
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(id = "revision-set",
            optionPolicies = SettingsSetOptionPolicies.defaults().withPolicy(
                ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.ASK_EVERY_TIME))
        val first = ProfileStores(context)
        val second = ProfileStores(context)
        val originalRevision = first.selectionRevision(set.id)
        val oldState = first.activeSelections(set)
        val newer = oldState.choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("new-command"))
        val newRevision = second.saveActiveSelections(newer, originalRevision)
        assertTrue(newRevision > originalRevision)
        assertThrows(SelectionRevisionConflictException::class.java) {
            first.saveActiveSelections(oldState, originalRevision)
        }
        assertEquals(newer, ProfileStores(context).activeSelections(set))
        assertEquals(newRevision, first.selectionRevision(set.id))
    }

    @Test
    fun sameIdImportInvalidatesRevisionAndTransientChoice() {
        assertTrue(context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE).edit().clear().commit())
        val store = ProfileStores(context)
        val set = store.settingsSets().first().copy(optionPolicies = SettingsSetOptionPolicies.defaults().withPolicy(
            ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.ASK_EVERY_TIME))
        store.saveSettingsSets(listOf(set))
        val state = store.activeSelections(set).choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND,
            SelectionChoice.profile("old-ask"))
        val revision = store.saveActiveSelections(state)
        val backup = store.buildCommittedBackup(SettingsSetExportOptions(), org.rtkcollector.app.secrets.NtripSecretStore(context))
        store.replaceImportedSettings(backup, listOf(set), set.id, null, null)
        val second = ProfileStores(context)
        assertTrue(second.selectionRevision(set.id) > revision)
        assertTrue(second.activeSelections(set).transientChoices.isEmpty())
        assertThrows(SelectionRevisionConflictException::class.java) { second.saveActiveSelections(state, revision) }
    }

    @Test
    fun stopClearsAskAndResetCapturesLeaveWorkflowBaseline() {
        assertTrue(context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE).edit().clear().commit())
        val store = ProfileStores(context)
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(id = "leave-set",
            workflowApplicationPolicy = WorkflowApplicationPolicy.LEAVE_INTACT,
            optionPolicies = SettingsSetOptionPolicies.defaults().withPolicy(
                ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.ASK_EVERY_TIME))
        store.saveActiveSelections(store.activeSelections(set).choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND,
            SelectionChoice.profile("ask")))
        store.afterStopOrFailedStart(set)
        assertTrue(ProfileStores(context).activeSelections(set).transientChoices.isEmpty())
        store.resetActiveSelections(set, "plain-rover")
        assertEquals("plain-rover", ProfileStores(context).activeSelections(set).workflowBaselineId)
    }

    @Test
    fun selectionsAreStoredPerSetWithoutChangingDefaults() {
        val prefs = context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
        val store = ProfileStores(context)
        val first = RecordingSettingsSet.builtInRoverNtrip().copy(id = "first")
        val second = RecordingSettingsSet.builtInRoverNtrip().copy(id = "second")
        val chosen = store.activeSelections(first).choose(
            first, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("custom-command"),
        )

        store.saveActiveSelections(chosen)

        assertEquals(chosen, ProfileStores(context).activeSelections(first))
        assertEquals(ActiveSetupSelections("second"), store.activeSelections(second))
        assertEquals(first.commandProfileRef, RecordingSettingsSet.builtInRoverNtrip().commandProfileRef)
    }

    @Test
    fun askChoiceSurvivesNewStoreInProcessButNotProcessRestart() {
        val prefs = context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            id = "ask-set",
            optionPolicies = SettingsSetOptionPolicies.defaults().withPolicy(
                ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.ASK_EVERY_TIME,
            ),
        )
        val firstStore = ProfileStores(context)
        val choice = firstStore.activeSelections(set).choose(
            set, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("ask-command"),
        )

        firstStore.saveActiveSelections(choice)

        assertEquals(choice, ProfileStores(context).activeSelections(set))
        val durable = ActiveSetupSelections.fromJson(choice.toJson())
        assertNotEquals(choice, durable)
        val restartedProcessCache = ActiveSelectionsMemory()
        assertEquals(null, restartedProcessCache.cached(prefs, durable))
    }

    @Test
    fun bulkPublicationInvalidatesOldAskChoiceEvenWhenSetIdIsReused() {
        val owner = Any()
        val cache = ActiveSelectionsMemory()
        val active = ActiveSetupSelections("same-set").copy(
            transientChoices = mapOf(ActiveSetupOptionKey.RECEIVER_COMMAND to SelectionChoice.profile("old-command")),
        )
        cache.remember(owner, active)
        assertEquals(active, cache.cached(owner, ActiveSetupSelections.fromJson(active.toJson())))

        cache.invalidate(owner)

        assertEquals(null, cache.cached(owner, ActiveSetupSelections.fromJson(active.toJson())))
    }
}
