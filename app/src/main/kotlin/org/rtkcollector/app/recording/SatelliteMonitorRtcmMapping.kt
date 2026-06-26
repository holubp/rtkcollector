package org.rtkcollector.app.recording

import org.rtkcollector.core.correction.SatelliteFrequencyBand

internal fun SatelliteFrequencyBand.toSatelliteMonitorBandLabel(): String =
    when (this) {
        SatelliteFrequencyBand.L1 -> "L1"
        SatelliteFrequencyBand.L2 -> "L2"
        SatelliteFrequencyBand.L3 -> "L3"
        SatelliteFrequencyBand.L4 -> "L4"
        SatelliteFrequencyBand.L5 -> "L5"
        SatelliteFrequencyBand.L6 -> "L6"
        SatelliteFrequencyBand.L7 -> "L7"
        SatelliteFrequencyBand.L8 -> "L8"
        SatelliteFrequencyBand.L9 -> "L9"
        SatelliteFrequencyBand.UNKNOWN -> "UNK"
    }
