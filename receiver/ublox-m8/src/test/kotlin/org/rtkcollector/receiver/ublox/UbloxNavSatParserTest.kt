package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rtkcollector.core.quality.SatelliteMonitorSource
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteSignalKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UbloxNavSatParserTest {
    @Test
    fun `parses used nav sat observations and filters unknown svid constellations`() {
        val frame = UbloxNavSatPayloadBuilder()
            .satellite(gnssId = 0, svid = 7, cn0 = 44, used = true)
            .satellite(gnssId = 2, svid = 13, cn0 = 39, used = true)
            .satellite(gnssId = 0, svid = 0, cn0 = 30, used = true)
            .satellite(gnssId = 3, svid = 20, cn0 = 31, used = false)
            .satellite(gnssId = 99, svid = 5, cn0 = 55, used = true)
            .buildFrame()

        val telemetry = UbloxNavSatParser.parse(frame, nowMillis = 4_000L)

        assertEquals(5, telemetry?.satellitesInView)
        assertEquals(4, telemetry?.satellitesUsed)
        assertEquals(4_000L, telemetry?.updatedAtMillis)
        assertEquals(2, telemetry?.satelliteSignalObservations?.size)
        assertEquals(
            listOf(
                SatelliteSignalKey(
                    constellation = SatelliteConstellation.GPS,
                    svid = 7,
                    band = "*",
                ),
                SatelliteSignalKey(
                    constellation = SatelliteConstellation.GALILEO,
                    svid = 13,
                    band = "*",
                ),
            ),
            telemetry?.satelliteSignalObservations?.map { it.key },
        )
        assertEquals(
            listOf(SatelliteMonitorSource.SOLUTION, SatelliteMonitorSource.SOLUTION),
            telemetry?.satelliteSignalObservations?.map { it.source },
        )
        assertEquals(listOf(true, true), telemetry?.satelliteSignalObservations?.map { it.used })
        assertEquals(listOf(44.0, 39.0), telemetry?.satelliteSignalObservations?.map { it.cn0DbHz })
    }

    @Test
    fun `rejects truncated nav sat payload`() {
        val payload = ByteArray(8)
        payload[5] = 2
        val frame = UbloxFrame.build(0x01, 0x35, payload)

        assertNull(UbloxNavSatParser.parse(frame, nowMillis = 0L))
    }

    @Test
    fun `ignores malformed nav sat payload without throwing`() {
        val payload = ByteArray(9) { 0x01 }
        val frame = UbloxFrame.build(0x01, 0x35, payload)

        assertNull(UbloxNavSatParser.parse(frame, nowMillis = 0L))
    }

    private class UbloxNavSatPayloadBuilder {
        private data class Satellite(
            val gnssId: Int,
            val svid: Int,
            val cn0: Int,
            val used: Boolean,
        )

        private val satellites = mutableListOf<Satellite>()

        fun satellite(
            gnssId: Int,
            svid: Int,
            cn0: Int,
            used: Boolean,
        ): UbloxNavSatPayloadBuilder {
            satellites += Satellite(gnssId, svid, cn0, used)
            return this
        }

        fun buildFrame(): ByteArray {
            val payload = ByteArray(8 + satellites.size * 12)
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(0, 123_000)
            buffer.put(4, 1)
            buffer.put(5, satellites.size.toByte())
            satellites.forEachIndexed { index, satellite ->
                val offset = 8 + (index * 12)
                payload[offset] = satellite.gnssId.toByte()
                payload[offset + 1] = satellite.svid.toByte()
                payload[offset + 2] = satellite.cn0.toByte()
                if (satellite.used) {
                    buffer.putInt(offset + 8, UBX_NAV_SAT_USED_FLAG)
                }
            }
            return UbloxFrame.build(0x01, 0x35, payload)
        }
    }

    private companion object {
        const val UBX_NAV_SAT_USED_FLAG = 0x08
    }
}
