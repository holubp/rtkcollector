package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActiveSetupSettingsSetJsonTest {
    @Test
    fun `settings set json round trips option policies and solution policy reference`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.WORKFLOW, SettingsSetOptionPolicy.LOCKED)
                .withPolicy(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER),
            solutionPolicyProfileRef = ProfileReference("solution-auto-best", "Automatic best solution"),
        )

        val decoded = RecordingSettingsSet.fromJson(settingsSet.toJson())

        assertEquals(SettingsSetOptionPolicy.LOCKED, decoded.optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW))
        assertEquals(
            SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER,
            decoded.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_MOUNTPOINT),
        )
        assertEquals("solution-auto-best", decoded.solutionPolicyProfileRef?.id)
    }

    @Test
    fun `older settings set json defaults option policies to overridable`() {
        val decoded = RecordingSettingsSet.fromJson(RecordingSettingsSet.builtInRoverNtrip().toJson())

        ActiveSetupOptionKey.entries.forEach { key ->
            assertEquals(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE, decoded.optionPolicies.policyFor(key))
        }
    }
}
