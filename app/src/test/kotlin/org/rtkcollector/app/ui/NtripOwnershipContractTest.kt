package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.ActiveSetupResolver
import org.rtkcollector.app.profile.ActiveSetupSelections
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.SelectionChoice

class NtripOwnershipContractTest {
    @Test
    fun `same host accounts and duplicate mount names resolve by IDs only`() {
        val first = NtripCasterProfile("account-a", "A", host = "same.example", username = "a")
        val second = NtripCasterProfile("account-b", "B", host = "same.example", username = "b")
        val sourceA = NtripMountpointProfile("source-a", "Shared", "account-a", mountpoint = "MOUNT")
        val sourceB = NtripMountpointProfile("source-b", "Shared", "account-b", mountpoint = "MOUNT")
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripMountpointProfileRef = ProfileReference("source-a", "Shared"),
        )
        val selections = ActiveSetupSelections(set.id).choose(
            set, ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SelectionChoice.profile("source-b"),
        )
        val result = set.resolveNtripProfiles(ActiveSetupResolver.resolve(set, selections),
            listOf(first, second), listOf(sourceA, sourceB))
        assertEquals("source-b", result.mountpoint?.id)
        assertEquals("account-b", result.caster?.id)
        assertEquals("b", result.caster?.username)
        assertNull(result.problem)
    }

    @Test
    fun `fixed caster restricts source but never substitutes its endpoint`() {
        val fixed = NtripCasterProfile("fixed", "Fixed", host = "same.example", username = "fixed")
        val other = NtripCasterProfile("other", "Other", host = "same.example", username = "other")
        val source = NtripMountpointProfile("foreign", "Foreign", "other", mountpoint = "MOUNT")
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripCasterRestrictionRef = ProfileReference("fixed", "Fixed"),
            ntripMountpointProfileRef = ProfileReference("foreign", "Foreign"),
        )
        val result = set.resolveNtripProfiles(ActiveSetupResolver.resolve(set, ActiveSetupSelections(set.id)),
            listOf(fixed, other), listOf(source))
        assertEquals("other", result.caster?.id)
        assertNotNull(result.problem)
        assertEquals(listOf<String>(), set.selectableNtripMountpoints(listOf(source)).map { it.id })
    }

    @Test
    fun `missing source caster never falls back to same mount name`() {
        val available = NtripCasterProfile("other", "Other", host = "same.example", sourcetableMountpoints = listOf("MOUNT"))
        val source = NtripMountpointProfile("source", "Source", "missing", mountpoint = "MOUNT")
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripMountpointProfileRef = ProfileReference("source", "Source"),
        )
        val result = set.resolveNtripProfiles(ActiveSetupResolver.resolve(set, ActiveSetupSelections(set.id)),
            listOf(available), listOf(source))
        assertNull(result.caster)
        assertTrue(result.problem.orEmpty().contains("missing"))
    }
}
