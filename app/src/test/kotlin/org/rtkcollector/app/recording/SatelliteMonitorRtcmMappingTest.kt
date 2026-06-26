package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.core.correction.SatelliteFrequencyBand

class SatelliteMonitorRtcmMappingTest {
    @Test
    fun `unknown RTCM MSM bands remain visible to the monitor`() {
        assertEquals("UNK", SatelliteFrequencyBand.UNKNOWN.toSatelliteMonitorBandLabel())
    }

    @Test
    fun `known RTCM MSM bands keep their frequency labels`() {
        assertEquals("L1", SatelliteFrequencyBand.L1.toSatelliteMonitorBandLabel())
        assertEquals("L2", SatelliteFrequencyBand.L2.toSatelliteMonitorBandLabel())
        assertEquals("L7", SatelliteFrequencyBand.L7.toSatelliteMonitorBandLabel())
    }
}
