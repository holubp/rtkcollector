package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RecordingServiceStateUsbRecoveryTest {
    @Test fun `reopen without bytes remains degraded and preserves other failure evidence`() {
        val usb = RecordingServiceState(lastError = "USB lost", errorCategory = RecordingErrorCategory.USB,
            errorSeverity = RecordingErrorSeverity.DEGRADED, rawRecordingActive = true)
            .awaitReceiverBytesAfterReconnect()
        assertEquals(false, usb.rawRecordingActive)
        assertEquals(RecordingErrorCategory.USB, usb.errorCategory)
        assertEquals("USB receiver reopened; waiting for receiver bytes.", usb.lastError)
        val correction = usb.copy(lastError = "Corrections missing", errorCategory = RecordingErrorCategory.NTRIP)
        assertEquals("Corrections missing", correction.awaitReceiverBytesAfterReconnect().lastError)
        assertEquals("Protocol missing", usb.copy(lastError = "Protocol missing")
            .awaitReceiverBytesAfterReconnect(protocolMissing = true).lastError)
    }
    @Test
    fun `successful usb io clears recoverable usb error`() {
        val recovered = RecordingServiceState(
            lastError = "USB serial transport is not open.",
            errorCategory = RecordingErrorCategory.USB,
            errorSeverity = RecordingErrorSeverity.DEGRADED,
        ).clearRecoverableUsbError()

        assertNull(recovered.lastError)
        assertEquals(RecordingErrorCategory.NONE, recovered.errorCategory)
        assertEquals(RecordingErrorSeverity.NONE, recovered.errorSeverity)
    }

    @Test
    fun `successful usb io does not clear fatal error`() {
        val failed = RecordingServiceState(
            lastError = "USB serial device could not be opened.",
            errorCategory = RecordingErrorCategory.USB,
            errorSeverity = RecordingErrorSeverity.FATAL,
        ).clearRecoverableUsbError()

        assertEquals("USB serial device could not be opened.", failed.lastError)
        assertEquals(RecordingErrorCategory.USB, failed.errorCategory)
        assertEquals(RecordingErrorSeverity.FATAL, failed.errorSeverity)
    }
}
