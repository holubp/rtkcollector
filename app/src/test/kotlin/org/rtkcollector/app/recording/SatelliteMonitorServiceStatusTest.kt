package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.SatelliteTelemetryCapability

class SatelliteMonitorServiceStatusTest {
    @Test
    fun `unsupported profiles report unsupported telemetry`() {
        assertEquals(
            "Satellite telemetry not supported by active command profile",
            satelliteMonitorUnavailableMessage(
                capability = SatelliteTelemetryCapability.NONE,
                telemetryObserved = false,
            ),
        )
    }

    @Test
    fun `supported profiles report waiting before telemetry is observed`() {
        assertEquals(
            "Waiting for UM980 binary satellite telemetry",
            satelliteMonitorUnavailableMessage(
                capability = SatelliteTelemetryCapability.UM980_BINARY,
                telemetryObserved = false,
            ),
        )
    }

    @Test
    fun `supported profiles stop waiting once telemetry is observed`() {
        assertEquals(
            "UM980 binary satellite telemetry received; per-frequency monitor rows are not available yet",
            satelliteMonitorUnavailableMessage(
                capability = SatelliteTelemetryCapability.UM980_BINARY,
                telemetryObserved = true,
            ),
        )
    }
}
