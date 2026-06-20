package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveSetupResolverTest {
    @Test
    fun `locked setting uses settings set default despite remembered override`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.WORKFLOW, SettingsSetOptionPolicy.LOCKED),
        )

        val setup = ActiveSetupResolver.resolve(
            settingsSet = settingsSet,
            rememberedOverrides = mapOf(ActiveSetupOptionKey.WORKFLOW to "plain-rover"),
            transientChoices = emptyMap(),
            compatibility = emptyMap(),
        )

        assertEquals("rover-ntrip", setup.option(ActiveSetupOptionKey.WORKFLOW).effectiveValueId)
        assertFalse(setup.option(ActiveSetupOptionKey.WORKFLOW).isOverridden)
    }

    @Test
    fun `ask every time value is transient and cleared by helper`() {
        val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SettingsSetOptionPolicy.ASK_EVERY_TIME),
        )

        val setup = ActiveSetupResolver.resolve(
            settingsSet = settingsSet,
            rememberedOverrides = mapOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT to "TUBO"),
            transientChoices = mapOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT to "DRES"),
            compatibility = emptyMap(),
        )

        assertEquals("DRES", setup.option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).effectiveValueId)
        assertFalse(ActiveSetupResolver.rememberedAfterStop(settingsSet.optionPolicies, setup.rememberedOverrides)
            .containsKey(ActiveSetupOptionKey.NTRIP_MOUNTPOINT))
    }

    @Test
    fun `incompatible option blocks start with structured message`() {
        val setup = ActiveSetupResolver.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip(),
            rememberedOverrides = emptyMap(),
            transientChoices = emptyMap(),
            compatibility = mapOf(ActiveSetupOptionKey.RECEIVER_COMMAND to false),
        )

        assertFalse(setup.canStart)
        assertTrue(setup.messages.any { it.key == ActiveSetupOptionKey.RECEIVER_COMMAND })
    }
}
