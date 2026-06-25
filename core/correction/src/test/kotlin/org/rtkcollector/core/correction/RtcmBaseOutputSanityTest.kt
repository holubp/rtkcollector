package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RtcmBaseOutputSanityTest {
    @Test
    fun `um980 base config equivalent can upload`() {
        val result = Um980RtcmBaseOutputSanity.validateCommands(
            listOf(
                "UNLOG COM1",
                "MODE BASE TIME 120 2.5",
                "BESTNAVB COM1 1",
                "RTCM1006 COM1 10",
                "RTCM1033 COM1 10",
                "RTCM1074 COM1 1",
                "RTCM1084 COM1 1",
                "RTCM1094 COM1 1",
                "RTCM1114 COM1 1",
                "RTCM1124 COM1 1",
                "RTCM1230 COM1 10",
            ),
        )

        assertTrue(result.canUpload)
        assertEquals(emptyList<String>(), result.errors)
        assertTrue(result.detectedMessageTypes.contains(1006))
        assertTrue(result.detectedMessageTypes.contains(1074))
    }

    @Test
    fun `rover binary monitoring profile fails`() {
        val result = Um980RtcmBaseOutputSanity.validateCommands(
            listOf(
                "UNLOG COM1",
                "MODE ROVER",
                "BESTNAVB COM1 0.05",
                "OBSVMCMPB COM1 0.2",
                "STADOPB COM1 1",
            ),
        )

        assertFalse(result.canUpload)
        assertTrue(result.errors.any { it.contains("monitoring logs") })
    }

    @Test
    fun `only base position message fails`() {
        val result = Um980RtcmBaseOutputSanity.validateCommands(listOf("RTCM1006 COM1 10"))

        assertFalse(result.canUpload)
        assertTrue(result.errors.any { it.contains("MSM observation") })
    }

    @Test
    fun `only msm message fails`() {
        val result = Um980RtcmBaseOutputSanity.validateCommands(listOf("RTCM1074 COM1 1"))

        assertFalse(result.canUpload)
        assertTrue(result.errors.any { it.contains("base-position") })
    }

    @Test
    fun `glonass msm without 1230 warns`() {
        val result = Um980RtcmBaseOutputSanity.validateCommands(
            listOf(
                "RTCM1006 COM1 10",
                "RTCM1074 COM1 1",
                "RTCM1084 COM1 1",
            ),
        )

        assertTrue(result.canUpload)
        assertTrue(result.warnings.any { it.contains("RTCM1230") })
    }
}
