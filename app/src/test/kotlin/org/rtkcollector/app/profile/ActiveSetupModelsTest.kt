package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveSetupModelsTest {
    @Test
    fun `default policies are overridable for all major options`() {
        val policies = SettingsSetOptionPolicies.defaults()

        ActiveSetupOptionKey.entries.forEach { key ->
            assertEquals(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE, policies.policyFor(key))
        }
    }

    @Test
    fun `ask every time option is missing until transient value is supplied`() {
        val state = EffectiveSetupOption(
            key = ActiveSetupOptionKey.NTRIP_MOUNTPOINT,
            label = "NTRIP mountpoint",
            defaultValueId = null,
            rememberedOverrideValueId = null,
            transientValueId = null,
            policy = SettingsSetOptionPolicy.ASK_EVERY_TIME,
            compatible = true,
        )

        assertFalse(state.canStart)
        assertTrue(state.requiresUserSelection)
        assertEquals("NTRIP mountpoint must be selected for this recording.", state.problem)
    }

    @Test
    fun `locked option ignores remembered override`() {
        val state = EffectiveSetupOption(
            key = ActiveSetupOptionKey.RECEIVER_COMMAND,
            label = "Receiver/init profile",
            defaultValueId = "um980-binary",
            rememberedOverrideValueId = "ublox-m8t",
            transientValueId = null,
            policy = SettingsSetOptionPolicy.LOCKED,
            compatible = true,
        )

        assertEquals("um980-binary", state.effectiveValueId)
        assertFalse(state.isOverridden)
    }
}
