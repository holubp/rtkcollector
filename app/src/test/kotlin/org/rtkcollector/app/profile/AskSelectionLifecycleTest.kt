package org.rtkcollector.app.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AskSelectionLifecycleTest {
    private val set = RecordingSettingsSet.builtInRoverNtrip().copy(id = "set-a",
        optionPolicies = SettingsSetOptionPolicies.defaults()
            .withPolicy(ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.ASK_EVERY_TIME)
            .withPolicy(ActiveSetupOptionKey.RECORDING_OUTPUT, SettingsSetOptionPolicy.ASK_EVERY_TIME)
            .withPolicy(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER)
            .withPolicy(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD, SettingsSetOptionPolicy.ASK_EVERY_TIME))

    @Test
    fun `terminal cleanup clears only consumed answers and preserves newer equal answer and other set`() {
        val cache = ActiveSelectionsMemory()
        val owner = Any()
        val consumed = ActiveSetupSelections(set.id)
            .choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("X"))
            .choose(set, ActiveSetupOptionKey.RECORDING_OUTPUT, SelectionChoice.profile("output"))
            .choose(set, ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SelectionChoice.profile("remembered"))
            .chooseUpload(set, UploadSelection(false, SelectionChoice.none()))
        cache.remember(owner, consumed)
        val lease = cache.captureStart(owner, consumed, 7L, ActiveSetupOptionKey.entries.toSet())
        val other = set.copy(id = "set-b")
        val stateB = ActiveSetupSelections(other.id).choose(other, ActiveSetupOptionKey.RECEIVER_COMMAND,
            SelectionChoice.profile("B"))
        cache.remember(owner, stateB)
        val newer = consumed.choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("X"))
        cache.remember(owner, newer)

        lease.finish()
        lease.finish()

        val result = cache.cached(owner, ActiveSetupSelections.fromJson(newer.toJson()))!!
        assertEquals("X", result.transientChoices[ActiveSetupOptionKey.RECEIVER_COMMAND]?.profileId)
        assertTrue(ActiveSetupOptionKey.RECORDING_OUTPUT !in result.transientChoices)
        assertEquals(null, result.transientUploadSelection)
        assertEquals("remembered", result.rememberedChoices[ActiveSetupOptionKey.NTRIP_MOUNTPOINT]?.profileId)
        assertEquals(stateB, cache.cached(owner, ActiveSetupSelections.fromJson(stateB.toJson())))
        assertEquals(7L, lease.selectionRevision)
    }

    @Test
    fun `failed start cleanup retains dormant answers and newer equal upload choice`() {
        val cache = ActiveSelectionsMemory()
        val owner = Any()
        val state = ActiveSetupSelections(set.id)
            .choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("X"))
            .choose(set, ActiveSetupOptionKey.RECORDING_OUTPUT, SelectionChoice.profile("dormant"))
            .chooseUpload(set, UploadSelection(false, SelectionChoice.none()))
        cache.remember(owner, state)
        val lease = cache.captureStart(owner, state, 1L,
            setOf(ActiveSetupOptionKey.RECEIVER_COMMAND, ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD))
        val newer = state.chooseUpload(set, UploadSelection(false, SelectionChoice.none()))
        cache.remember(owner, newer)
        lease.finish()
        val result = cache.cached(owner, ActiveSetupSelections.fromJson(newer.toJson()))!!
        assertTrue(ActiveSetupOptionKey.RECEIVER_COMMAND !in result.transientChoices)
        assertEquals("dormant", result.transientChoices[ActiveSetupOptionKey.RECORDING_OUTPUT]?.profileId)
        assertEquals(newer.transientUploadSelection, result.transientUploadSelection)
    }
}
