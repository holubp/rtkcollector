package org.rtkcollector.app.recording

import org.rtkcollector.app.profile.SatelliteTelemetryCapability

internal fun satelliteMonitorUnavailableMessage(
    capability: SatelliteTelemetryCapability,
    telemetryObserved: Boolean,
): String =
    when {
        !capability.isSupported -> "Satellite telemetry not supported by active command profile"
        telemetryObserved ->
            "${capability.displayName} received; per-frequency monitor rows are not available yet"
        else -> "Waiting for ${capability.displayName}"
    }
