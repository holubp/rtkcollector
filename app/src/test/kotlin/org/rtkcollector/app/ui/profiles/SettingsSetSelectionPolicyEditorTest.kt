package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.SettingsSetOptionPolicy

class SettingsSetSelectionPolicyEditorTest {
    @Test
    fun `each profile selection offers all four policies without nested caster selection`() {
        val fields = settingsSetSelectionPolicyFields(RecordingSettingsSet.builtInRoverNtrip())

        assertFalse(fields.any { it.key == "policyNTRIP_CASTER" || it.key == "policyWORKFLOW" })
        assertEquals(9, fields.size)
        for (field in fields) {
            assertFalse(field.boolean)
            assertEquals(SettingsSetOptionPolicy.entries.map { it.name }, field.optionItems.map { it.value })
            assertEquals(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE.name, field.value)
        }
    }

    @Test
    fun `policy editor preserves explicit choose and ask selections across serialization`() {
        val source = RecordingSettingsSet.builtInRoverNtrip()
        val updated = source.withSettingsSetSelectionPolicies(mapOf(
            "policySTORAGE" to SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER.name,
            "policyRECORDING_OUTPUT" to SettingsSetOptionPolicy.ASK_EVERY_TIME.name,
            "policyNTRIP_MOUNTPOINT" to SettingsSetOptionPolicy.LOCKED.name,
        ))
        val restored = RecordingSettingsSet.fromJson(updated.toJson())

        assertEquals(SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER,
            restored.optionPolicies.policyFor(ActiveSetupOptionKey.STORAGE))
        assertEquals(SettingsSetOptionPolicy.ASK_EVERY_TIME,
            restored.optionPolicies.policyFor(ActiveSetupOptionKey.RECORDING_OUTPUT))
        assertEquals(SettingsSetOptionPolicy.LOCKED,
            restored.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_MOUNTPOINT))
        assertEquals(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE,
            source.optionPolicies.policyFor(ActiveSetupOptionKey.STORAGE))
        assertEquals(updated.optionPolicies, updated.withSettingsSetSelectionPolicies(emptyMap()).optionPolicies)
    }

    @Test
    fun `upload policy applies to one enable and profile pair`() {
        val updated = RecordingSettingsSet.builtInRoverNtrip().withSettingsSetSelectionPolicies(
            mapOf("policyNTRIP_CASTER_UPLOAD" to SettingsSetOptionPolicy.ASK_EVERY_TIME.name),
        )
        assertEquals(SettingsSetOptionPolicy.ASK_EVERY_TIME,
            updated.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD))
        assertTrue(settingsSetSelectionPolicyFields(updated).single { it.key == "policyNTRIP_CASTER_UPLOAD" }
            .label.contains("enable and profile", ignoreCase = true))
    }

    @Test
    fun `invalid policy is rejected and locked coordinate requires identity`() {
        val source = RecordingSettingsSet.builtInRoverNtrip()
        assertThrows(IllegalArgumentException::class.java) {
            source.withSettingsSetSelectionPolicies(mapOf("policySTORAGE" to "bogus"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            source.withSettingsSetSelectionPolicies(mapOf("policyBASE_COORDINATE" to "LOCKED"))
        }
        val updated = source.copy(basePositionProfileRef = ProfileReference("base", "Base"))
            .withSettingsSetSelectionPolicies(mapOf("policyBASE_COORDINATE" to "LOCKED"))
        assertEquals(SettingsSetOptionPolicy.LOCKED,
            updated.optionPolicies.policyFor(ActiveSetupOptionKey.BASE_COORDINATE))
    }
}
