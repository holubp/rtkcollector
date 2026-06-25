package org.rtkcollector.receiver.ublox

import org.rtkcollector.core.quality.SatelliteMonitorSource
import org.rtkcollector.core.quality.SatelliteSignalKey
import org.rtkcollector.core.quality.SatelliteSignalObservation

object UbloxRawxParser {
    fun parse(frame: ByteArray, observedAtMillis: Long): List<SatelliteSignalObservation> {
        if (frame.size < 8) return emptyList()
        if (frame[2].toInt() and 0xff != 0x02 || frame[3].toInt() and 0xff != 0x15) return emptyList()
        if (!UbloxFrame.isValid(frame)) return emptyList()
        val payloadLength = (frame[4].toInt() and 0xff) or ((frame[5].toInt() and 0xff) shl 8)
        if (payloadLength < HEADER_LENGTH) return emptyList()
        return parsePayload(frame.copyOfRange(6, 6 + payloadLength), observedAtMillis)
    }

    private fun parsePayload(payload: ByteArray, observedAtMillis: Long): List<SatelliteSignalObservation> {
        if (payload.size < HEADER_LENGTH) return emptyList()
        val measurementCount = payload[NUM_MEASUREMENTS_OFFSET].toInt() and 0xff
        val requiredLength = HEADER_LENGTH + (measurementCount * MEASUREMENT_LENGTH)
        if (requiredLength > payload.size) return emptyList()

        val observations = ArrayList<SatelliteSignalObservation>()
        repeat(measurementCount) { index ->
            val offset = HEADER_LENGTH + (index * MEASUREMENT_LENGTH)
            val gnssId = payload[offset + GNSS_ID_OFFSET].toInt() and 0xff
            val svid = payload[offset + SV_ID_OFFSET].toInt() and 0xff
            val sigId = payload[offset + SIG_ID_OFFSET].toInt() and 0xff

            val constellation = UbloxSatelliteMapping.constellationFor(gnssId)
            val bandInfo = UbloxSatelliteMapping.bandAndSignalFor(constellation, sigId) ?: return@repeat
            if (svid <= 0) return@repeat
            if (!isVisible(payload, offset)) return@repeat

            observations.add(
                SatelliteSignalObservation(
                    key = SatelliteSignalKey(
                        constellation = constellation,
                        svid = svid,
                        band = bandInfo.band,
                        signalCode = bandInfo.signalCode,
                    ),
                    source = SatelliteMonitorSource.ROVER,
                    observedAtEpochMillis = observedAtMillis,
                    cn0DbHz = payload[offset + CN0_OFFSET].toInt().and(0xff).toDouble(),
                    used = false,
                ),
            )
        }
        return observations
    }

    private fun isVisible(payload: ByteArray, blockOffset: Int): Boolean {
        val status = payload[blockOffset + TRACKING_STATUS_OFFSET].toInt() and 0xff
        if (status and PSEUDORANGE_VALID_BIT != 0 || status and CARRIER_PHASE_VALID_BIT != 0) return true

        val pseudoRangeStd = payload[blockOffset + PSEUDORANGE_STD_OFFSET].toInt().and(0xff)
        if (pseudoRangeStd > 0) return true

        val carrierPhaseStd = payload[blockOffset + CARRIER_PHASE_STD_OFFSET].toInt().and(0xff)
        return carrierPhaseStd > 0
    }

    private const val HEADER_LENGTH = 16
    private const val MEASUREMENT_LENGTH = 32
    private const val NUM_MEASUREMENTS_OFFSET = 11
    private const val GNSS_ID_OFFSET = 20
    private const val SV_ID_OFFSET = 21
    private const val SIG_ID_OFFSET = 22
    private const val CARRIER_PHASE_STD_OFFSET = 28
    private const val PSEUDORANGE_STD_OFFSET = 27
    private const val TRACKING_STATUS_OFFSET = 30
    private const val CN0_OFFSET = 26
    private const val PSEUDORANGE_VALID_BIT = 0x01
    private const val CARRIER_PHASE_VALID_BIT = 0x02
}
