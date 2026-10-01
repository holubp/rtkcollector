package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.SettingsSetOptionPolicies
import org.rtkcollector.app.profile.SettingsSetOptionPolicy

class SettingsSetLockEditorTest {
    @Test
    fun `editor exposes fixed flags for home selectors and other supported options`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SettingsSetOptionPolicy.LOCKED),
        )

        val fields = settingsSetLockFields(set)

        assertTrue(fields.first { it.key == "fixedNTRIP_MOUNTPOINT" }.boolean)
        assertEquals("true", fields.first { it.key == "fixedNTRIP_MOUNTPOINT" }.value)
        assertEquals("false", fields.first { it.key == "fixedSTORAGE" }.value)
        assertFalse(fields.any { it.key == "fixedWORKFLOW" })
        assertEquals("false", fields.first { it.key == "fixedBASE_COORDINATE" }.value)
    }

    @Test
    fun `saving fixed flags changes only explicitly edited policies`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.LOCKED)
                .withPolicy(ActiveSetupOptionKey.STORAGE, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER),
        )

        val updated = set.withSettingsSetLockSelections(
            mapOf("fixedRECEIVER_COMMAND" to "false", "fixedNTRIP_CASTER_UPLOAD" to "true"),
        )

        assertEquals(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE,
            updated.optionPolicies.policyFor(ActiveSetupOptionKey.RECEIVER_COMMAND))
        assertEquals(SettingsSetOptionPolicy.LOCKED,
            updated.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD))
        assertEquals(SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER,
            updated.optionPolicies.policyFor(ActiveSetupOptionKey.STORAGE))
        assertEquals(SettingsSetOptionPolicy.LOCKED,
            set.optionPolicies.policyFor(ActiveSetupOptionKey.RECEIVER_COMMAND))
        val restored = RecordingSettingsSet.fromJson(updated.toJson())
        assertEquals(updated.optionPolicies, restored.optionPolicies)
        assertThrows(IllegalArgumentException::class.java) {
            restored.withSettingsSetLockSelections(mapOf("fixedBASE_COORDINATE" to "true"))
        }
        val coordinateLocked = restored.copy(
            basePositionProfileRef = ProfileReference("known-base", "Known base"),
        ).withSettingsSetLockSelections(mapOf("fixedBASE_COORDINATE" to "true"))
        assertEquals(SettingsSetOptionPolicy.LOCKED,
            RecordingSettingsSet.fromJson(coordinateLocked.toJson())
                .optionPolicies.policyFor(ActiveSetupOptionKey.BASE_COORDINATE))
    }
}
