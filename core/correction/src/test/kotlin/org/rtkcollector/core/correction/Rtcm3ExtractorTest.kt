package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Rtcm3ExtractorTest {
    @Test
    fun `extracts valid zero length frame and ignores leading noise`() {
        val extractor = Rtcm3Extractor(validateCrc = false)
        val frames = extractor.accept(byteArrayOf(0x55, 0xd3.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00))

        assertEquals(1, frames.size)
        assertEquals(0, frames.single().payloadLength)
        assertEquals(byteArrayOf(0xd3.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00).toList(), frames.single().bytes.toList())
    }

    @Test
    fun `buffers partial frame across calls`() {
        val extractor = Rtcm3Extractor(validateCrc = false)

        assertTrue(extractor.accept(byteArrayOf(0xd3.toByte(), 0x00)).isEmpty())
        val frames = extractor.accept(byteArrayOf(0x00, 0x00, 0x00, 0x00))

        assertEquals(1, frames.size)
    }

    @Test
    fun `reports crc status when validation is enabled`() {
        val extractor = Rtcm3Extractor(validateCrc = true)

        val frames = extractor.accept(byteArrayOf(0xd3.toByte(), 0x00, 0x00, 0x47, 0xea.toByte(), 0x4b))

        assertEquals(1, frames.size)
        assertEquals(true, frames.single().crcValid)
    }
}
