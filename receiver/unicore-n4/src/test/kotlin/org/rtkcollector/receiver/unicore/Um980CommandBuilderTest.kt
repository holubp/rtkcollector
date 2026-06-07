package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Um980CommandBuilderTest {
    @Test
    fun roverNtripProfileConfiguresRoverAndRawSolutionLogs() {
        val commands = Um980CommandBuilder().build(
            Um980CommandProfileRequest(mode = Um980WorkflowMode.ROVER_NTRIP),
        )

        assertTrue("UNLOG COM1" in commands)
        assertTrue("MODE ROVER" in commands)
        assertTrue(commands.any { it.startsWith("BESTNAVB COM1") })
        assertTrue(commands.any { it.startsWith("GPGGA COM1") })
        assertTrue(commands.any { it.startsWith("OBSVMCMPB COM1") })
        assertTrue(commands.none { it.equals("SAVECONFIG", ignoreCase = true) })
    }

    @Test
    fun temporaryBaseProfileEnablesPppOutput() {
        val commands = Um980CommandBuilder().build(
            Um980CommandProfileRequest(mode = Um980WorkflowMode.TEMPORARY_BASE, enablePpp = true),
        )

        assertTrue(commands.any { it.startsWith("CONFIG PPP ENABLE", ignoreCase = true) })
        assertTrue(commands.any { it.contains("PPP", ignoreCase = true) })
    }

    @Test
    fun fixedBaseRtcmProfileRequiresCoordinateAndOutputsRtcm() {
        val coordinate = Um980BaseCoordinate(
            latDeg = 50.0,
            lonDeg = 14.0,
            heightM = 300.0,
            frame = "ETRF2000",
            epoch = "2026.4",
            antennaHeightM = 1.5,
            antennaReferencePoint = "ARP",
            source = Um980CoordinateSource.MANUAL,
        )

        val commands = Um980CommandBuilder().build(
            Um980CommandProfileRequest(
                mode = Um980WorkflowMode.FIXED_BASE_RTCM_OUTPUT,
                baseCoordinate = coordinate,
            ),
        )

        assertTrue(commands.any { it.startsWith("MODE BASE") })
        assertTrue(commands.any { it.startsWith("RTCM1006") })
        assertTrue(commands.any { it.startsWith("RTCM107") })
    }

    @Test
    fun fixedBaseModeRejectsMissingCoordinate() {
        assertThrows(IllegalArgumentException::class.java) {
            Um980CommandBuilder().build(
                Um980CommandProfileRequest(mode = Um980WorkflowMode.FIXED_BASE_STATUS),
            )
        }
    }
}
