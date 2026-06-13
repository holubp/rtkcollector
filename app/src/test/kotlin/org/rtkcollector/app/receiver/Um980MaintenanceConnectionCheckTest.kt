package org.rtkcollector.app.receiver

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Um980MaintenanceConnectionCheckTest {
    @Test
    fun `version probe is ascii command with crlf`() {
        assertArrayEquals("VERSION\r\n".toByteArray(Charsets.US_ASCII), um980VersionProbeBytes())
    }

    @Test
    fun `classifies unicore version response as live`() {
        assertTrue(isPlausibleUm980MaintenanceResponse("UM980 firmware 11833\r\n".toByteArray(Charsets.US_ASCII)))
        assertTrue(isPlausibleUm980MaintenanceResponse("Unicore GNSS Receiver\r\n".toByteArray(Charsets.US_ASCII)))
        assertTrue(isPlausibleUm980MaintenanceResponse("#VERSIONA,COM1,0,62.0,FINESTEERING".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `generic nmea streaming does not authorize um980 persistent writes`() {
        assertFalse(
            isPlausibleUm980MaintenanceResponse(
                "\$GNGGA,123519,5000.0,N,01400.0,E,1,12,0.9,287.0,M,0.0,M,,*00\r\n"
                    .toByteArray(Charsets.US_ASCII),
            ),
        )
    }

    @Test
    fun `rejects empty and random bytes`() {
        assertFalse(isPlausibleUm980MaintenanceResponse(ByteArray(0)))
        assertFalse(isPlausibleUm980MaintenanceResponse(byteArrayOf(0x00, 0x01, 0x02, 0x03)))
    }
}
