package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteMonitorSource
import org.rtkcollector.core.quality.SatelliteSignalKey

class UbloxRawxParserTest {
    @Test
    fun `parses gps rawx observations for l1 and l2`() {
        val payload = UbloxRawxTestPayloadBuilder()
            .measurement(gnssId = 0, svId = 7, signalId = 0, cno = 44, pseudorangeStd = 100)
            .measurement(gnssId = 0, svId = 9, signalId = 3, cno = 42, carrierPhaseStd = 120)
            .build()

        val frame = UbloxFrame.build(0x02, 0x15, payload)
        val observations = UbloxRawxParser.parse(frame, observedAtMillis = 12_000L)

        assertEquals(2, observations.size)
        assertEquals(
            listOf(
                SatelliteSignalKey(SatelliteConstellation.GPS, 7, "L1", "L1"),
                SatelliteSignalKey(SatelliteConstellation.GPS, 9, "L2", "L2"),
            ),
            observations.map { it.key },
        )
        assertEquals(listOf(SatelliteMonitorSource.ROVER, SatelliteMonitorSource.ROVER), observations.map { it.source })
        assertEquals(listOf(false, false), observations.map { it.used })
        assertEquals(44.0, observations[0].cn0DbHz)
        assertEquals(42.0, observations[1].cn0DbHz)
    }

    @Test
    fun `maps galileo rawx observations to e1 and e5a`() {
        val payload = UbloxRawxTestPayloadBuilder()
            .measurement(gnssId = 2, svId = 11, signalId = 0, cno = 38, pseudorangeStd = 100)
            .measurement(gnssId = 2, svId = 12, signalId = 5, cno = 39, pseudorangeStd = 100)
            .build()

        val frame = UbloxFrame.build(0x02, 0x15, payload)
        val observations = UbloxRawxParser.parse(frame, observedAtMillis = 12_000L)

        assertEquals(2, observations.size)
        assertEquals("E1", observations[0].key.signalCode)
        assertEquals("E5a", observations[1].key.signalCode)
    }

    @Test
    fun `uses tracking status as visibility fallback when std is zero`() {
        val payload = UbloxRawxTestPayloadBuilder()
            .measurement(
                gnssId = 0,
                svId = 3,
                signalId = 0,
                cno = 39,
                trackingStatus = 0x01,
            )
            .build()

        val observations = UbloxRawxParser.parse(UbloxFrame.build(0x02, 0x15, payload), observedAtMillis = 12_000L)

        assertEquals(1, observations.size)
    }

    @Test
    fun `malformed rawx payload returns no observations and does not throw`() {
        val frame = UbloxFrame.build(
            0x02,
            0x15,
            byteArrayOf(1, 2, 3),
        )
        val observations = UbloxRawxParser.parse(frame, observedAtMillis = 0L)
        assertEquals(0, observations.size)
    }

    @Test
    fun `malformed rawx frame returns no observations and does not throw`() {
        val observations = UbloxRawxParser.parse(byteArrayOf(0xB5.toByte(), 0x62.toByte(), 0x02.toByte()), observedAtMillis = 0L)
        assertEquals(0, observations.size)
    }

    private class UbloxRawxTestPayloadBuilder {
        private data class RawxMeasurement(
            val gnssId: Int,
            val svId: Int,
            val signalId: Int,
            val freqId: Int = 0,
            val cno: Int,
            val pseudorangeStd: Int = 0,
            val carrierPhaseStd: Int = 0,
            val trackingStatus: Int = 0x00,
        )

        private val measurements = mutableListOf<RawxMeasurement>()

        fun measurement(
            gnssId: Int,
            svId: Int,
            signalId: Int,
            freqId: Int = 0,
            cno: Int,
            pseudorangeStd: Int = 0,
            carrierPhaseStd: Int = 0,
            trackingStatus: Int = 0x00,
        ): UbloxRawxTestPayloadBuilder {
            measurements += RawxMeasurement(
                gnssId = gnssId,
                svId = svId,
                signalId = signalId,
                freqId = freqId,
                cno = cno,
                pseudorangeStd = pseudorangeStd,
                carrierPhaseStd = carrierPhaseStd,
                trackingStatus = trackingStatus,
            )
            return this
        }

        fun build(): ByteArray {
            val payload = ByteArray(16 + measurements.size * 32)
            payload[11] = measurements.size.toByte()
            measurements.forEachIndexed { index, entry ->
                val offset = 16 + (index * 32)
                payload[offset + 20] = entry.gnssId.toByte()
                payload[offset + 21] = entry.svId.toByte()
                payload[offset + 22] = entry.signalId.toByte()
                payload[offset + 23] = entry.freqId.toByte()
                payload[offset + 24] = 0
                payload[offset + 25] = 0
                payload[offset + 26] = entry.cno.toByte()
                payload[offset + 27] = entry.pseudorangeStd.toByte()
                payload[offset + 28] = entry.carrierPhaseStd.toByte()
                payload[offset + 29] = 0
                payload[offset + 30] = entry.trackingStatus.toByte()
            }
            return payload
        }
    }
}
