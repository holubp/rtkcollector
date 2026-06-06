package org.rtkcollector.receiver.generic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenericNmeaRtcmDriverTest {
    @Test
    fun `line splitter returns complete NMEA lines without trailing newline`() {
        val lines = NmeaLineSplitter.completeLines("\$GPGGA,1*00\r\n\$GPRMC,2*00\npartial".encodeToByteArray())

        assertEquals(listOf("\$GPGGA,1*00", "\$GPRMC,2*00"), lines)
    }

    @Test
    fun `GGA parser returns fix quality`() {
        val event = BasicGgaParser.parseFixQuality(
            "\$GPGGA,123519,4807.038,N,01131.000,E,4,12,0.8,545.4,M,46.9,M,,*47"
        )

        assertEquals(4, event?.fixQuality)
        assertEquals("rtk-fixed", event?.fixDescription)
    }

    @Test
    fun `generic driver exposes advisory GGA solution events`() {
        val driver = GenericNmeaRtcmDriver()
        val events = driver.parseSolution(
            "\$GPGGA,123519,4807.038,N,01131.000,E,5,12,0.8,545.4,M,46.9,M,,*47\r\n".encodeToByteArray()
        )

        assertEquals(1, events.size)
        assertEquals("rtk-float", events.single().fixType)
        assertTrue(driver.capabilities.supportsRtcmInput)
    }
}
