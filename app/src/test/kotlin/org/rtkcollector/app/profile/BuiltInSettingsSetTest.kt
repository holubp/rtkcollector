package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuiltInSettingsSetTest {
    @Test
    fun `built in settings sets expose focused UM980 workflows`() {
        val defaults = RecordingSettingsSet.builtInDefaults()

        assertEquals(
            listOf(
                "um980-plain-rover",
                "um980-rover-ntrip",
                "um980-temporary-base",
                "um980-fixed-base",
            ),
            defaults.map(RecordingSettingsSet::id),
        )
        assertEquals(
            listOf("plain-rover", "rover-ntrip", "base-calibration", "fixed-base"),
            defaults.map(RecordingSettingsSet::workflowId),
        )
        assertTrue(defaults.all { it.receiverProfileId == "um980-n4" })
        assertTrue(defaults.all(RecordingSettingsSet::isProtected))
    }

    @Test
    fun `built in settings sets keep ntrip upload disabled by default`() {
        val defaults = RecordingSettingsSet.builtInDefaults()

        assertTrue(defaults.all { it.ntripCasterUploadProfileRef == null })
        assertTrue(defaults.all { !it.baseCasterUploadEnabled })
    }

    @Test
    fun `built in rover variants select no ntrip caster upload`() {
        val roverDefaults = RecordingSettingsSet.builtInDefaults().filter {
            it.workflowId == "plain-rover" || it.workflowId.startsWith("rover-")
        }

        assertTrue(roverDefaults.isNotEmpty())
        assertTrue(roverDefaults.all { it.ntripCasterUploadProfileRef == null })
        assertTrue(roverDefaults.all { !it.baseCasterUploadEnabled })
        assertTrue(roverDefaults.all { it.overrides.ntripCasterUploadProfileRef == null })
        assertTrue(roverDefaults.all { it.overrides.baseCasterUploadEnabled == null })
        assertTrue(roverDefaults.all { it.overrides.ntripCasterUpload == null })
    }

    @Test
    fun `built in um980 rover ntrip requires explicit mountpoint selection`() {
        val roverNtrip = RecordingSettingsSet.builtInRoverNtrip()

        assertNull(roverNtrip.ntripMountpointProfileRef)
        assertNull(roverNtrip.overrides.ntripMountpointProfileRef)
        assertNull(roverNtrip.overrides.ntripMountpoint)
    }

    @Test
    fun `plain rover and fixed base defaults do not configure correction download`() {
        val defaults = RecordingSettingsSet.builtInDefaults().associateBy(RecordingSettingsSet::id)

        assertNull(defaults.getValue("um980-plain-rover").ntripCasterProfileRef)
        assertNull(defaults.getValue("um980-fixed-base").ntripCasterProfileRef)
        assertFalse(defaults.getValue("um980-plain-rover").workflowId.contains("ntrip", ignoreCase = true))
        assertFalse(defaults.getValue("um980-fixed-base").workflowId.contains("ntrip", ignoreCase = true))
    }

    @Test
    fun `temporary base and rover ntrip defaults keep correction download selectable`() {
        val defaults = RecordingSettingsSet.builtInDefaults().associateBy(RecordingSettingsSet::id)

        assertEquals("ntrip-caster-default", defaults.getValue("um980-rover-ntrip").ntripCasterProfileRef?.id)
        assertEquals("ntrip-caster-default", defaults.getValue("um980-temporary-base").ntripCasterProfileRef?.id)
    }
}
