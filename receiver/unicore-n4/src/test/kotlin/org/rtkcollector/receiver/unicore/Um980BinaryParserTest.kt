package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class Um980BinaryParserTest {
    @Test
    fun `parses documented BESTNAVB solution fields`() {
        val frame = bestnavbFrame()

        val telemetry = Um980BinaryParser.parseBestnavb(frame)

        requireNotNull(telemetry)
        assertEquals("BESTNAVB", telemetry.source)
        assertEquals("SOL_COMPUTED", telemetry.solutionStatus)
        assertEquals("NARROW_INT", telemetry.positionType)
        assertEquals(50.087451234, telemetry.latDeg)
        assertEquals(14.421253456, telemetry.lonDeg)
        assertEquals(243.812, telemetry.altitudeM)
        assertEquals(287.423, telemetry.ellipsoidalHeightM!!, 0.0001)
        assertEquals(0.008, telemetry.latErrorM!!, 0.0001)
        assertEquals(0.007, telemetry.lonErrorM!!, 0.0001)
        assertEquals(18, telemetry.satellitesUsed)
        assertEquals(31, telemetry.satellitesInView)
        assertEquals(0.8, telemetry.differentialAgeS!!, 0.0001)
        assertEquals("1234", telemetry.stationId)
    }

    @Test
    fun `returns null for non BESTNAVB message id`() {
        val frame = bestnavbFrame(messageId = 999)

        assertNull(Um980BinaryParser.parseBestnavb(frame))
    }

    @Test
    fun `frame extractor accepts only valid crc`() {
        val frame = bestnavbFrame()
        val invalid = frame.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }

        assertEquals(frame.toList(), Um980BinaryParser.extractFrames(frame).single().toList())
        assertTrue(Um980BinaryParser.isValidFrame(frame))
        assertFalse(Um980BinaryParser.isValidFrame(invalid))
        assertEquals(emptyList<ByteArray>(), Um980BinaryParser.extractFrames(invalid))
    }

    @Test
    fun `frame extractor skips noise and incomplete frames`() {
        val frame = bestnavbFrame()
        val mixed = byteArrayOf(1, 2, 3) + frame + frame.copyOfRange(0, 10)

        assertEquals(frame.toList(), Um980BinaryParser.extractFrames(mixed).single().toList())
    }

    companion object {
        fun bestnavbFrame(messageId: Int = 2118): ByteArray {
            val payloadLength = 120
            val frame = ByteArray(24 + payloadLength + 4)
            frame[0] = 0xAA.toByte()
            frame[1] = 0x44
            frame[2] = 0xB5.toByte()
            frame[3] = 24
            putU16(frame, 4, messageId)
            putU16(frame, 6, payloadLength)
            putU16(frame, 10, 2419)
            putU32(frame, 12, 132_572_000)
            val payloadBytes = ByteArray(payloadLength)
            val payload = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
            payload.putInt(0, 0)
            payload.putInt(4, 50)
            payload.putDouble(8, 50.087451234)
            payload.putDouble(16, 14.421253456)
            payload.putDouble(24, 243.812)
            payload.putFloat(32, 43.611f)
            payload.putFloat(40, 0.008f)
            payload.putFloat(44, 0.007f)
            payload.putFloat(48, 0.06f)
            "1234".encodeToByteArray().copyInto(payloadBytes, destinationOffset = 52)
            payload.putFloat(56, 0.8f)
            payload.put(64, 31)
            payload.put(65, 18)
            payload.putInt(72, 0)
            payload.putInt(76, 50)
            payloadBytes.copyInto(frame, destinationOffset = 24)
            putU32(frame, 24 + payloadLength, crc32(frame, 0, 24 + payloadLength).toInt())
            return frame
        }

        private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value and 0xff).toByte()
            bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
        }

        private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value and 0xff).toByte()
            bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
            bytes[offset + 2] = ((value ushr 16) and 0xff).toByte()
            bytes[offset + 3] = ((value ushr 24) and 0xff).toByte()
        }

        private fun crc32(bytes: ByteArray, offset: Int, length: Int): Long {
            val crc = CRC32()
            crc.update(bytes, offset, length)
            return crc.value
        }
    }
}
