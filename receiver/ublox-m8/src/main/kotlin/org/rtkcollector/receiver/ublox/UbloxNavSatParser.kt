package org.rtkcollector.receiver.ublox

import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteMonitorSource
import org.rtkcollector.core.quality.SatelliteSignalKey
import org.rtkcollector.core.quality.SatelliteSignalObservation
import java.nio.ByteBuffer
import java.nio.ByteOrder

object UbloxNavSatParser {
    fun parse(frame: ByteArray, nowMillis: Long): UbloxNavSatTelemetry? {
        if (!UbloxFrame.isValid(frame)) return null
        if ((frame[2].toInt() and 0xff) != 0x01 || (frame[3].toInt() and 0xff) != 0x35) return null
        val length = (frame[4].toInt() and 0xff) or ((frame[5].toInt() and 0xff) shl 8)
        if (length < HEADER_LENGTH) return null
        val payload = ByteBuffer.wrap(frame.copyOfRange(6, 6 + length)).order(ByteOrder.LITTLE_ENDIAN)
        val satelliteCount = payload.get(5).toInt() and 0xff
        if (HEADER_LENGTH + satelliteCount * SATELLITE_BLOCK_LENGTH > length) return null
        var used = 0
        val observations = ArrayList<SatelliteSignalObservation>()
        repeat(satelliteCount) { index ->
            val flagsOffset = HEADER_LENGTH + index * SATELLITE_BLOCK_LENGTH + FLAGS_OFFSET_IN_BLOCK
            val flags = payload.getInt(flagsOffset)
            if (flags and SV_USED_FLAG != 0) {
                used += 1
                val blockOffset = HEADER_LENGTH + index * SATELLITE_BLOCK_LENGTH
                val gnssId = payload.get(blockOffset).toInt() and 0xff
                val svid = payload.get(blockOffset + 1).toInt() and 0xff
                val cn0 = payload.get(blockOffset + 2).toInt() and 0xff
                val constellation = constellationFor(gnssId)
                if (constellation != SatelliteConstellation.UNKNOWN && svid > 0) {
                    observations += SatelliteSignalObservation(
                        key = SatelliteSignalKey(
                            constellation = constellation,
                            svid = svid,
                            band = SatelliteSignalKey.BAND_ANY,
                        ),
                        source = SatelliteMonitorSource.SOLUTION,
                        observedAtEpochMillis = nowMillis,
                        cn0DbHz = cn0.toDouble(),
                        used = true,
                    )
                }
            }
        }
        return UbloxNavSatTelemetry(
            updatedAtMillis = nowMillis,
            satellitesInView = satelliteCount,
            satellitesUsed = used,
            satelliteSignalObservations = observations,
        )
    }

    private const val HEADER_LENGTH = 8
    private const val SATELLITE_BLOCK_LENGTH = 12
    private const val FLAGS_OFFSET_IN_BLOCK = 8
    private const val SV_USED_FLAG = 0x08

    private fun constellationFor(gnssId: Int): SatelliteConstellation = UbloxSatelliteMapping.constellationFor(gnssId)
}
