package org.rtkcollector.app.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingStartPreflightTest {
    @Test
    fun `plain rover does not require ntrip mountpoint`() {
        val result = RecordingStartPreflight.validate(
            RecordingStartPreflight.Input(
                workflowUsesNtrip = false,
                usbProfileSelected = true,
                usbDeviceConnected = true,
                usbPermissionGranted = true,
                serialDriverAvailable = true,
                serialOpenSucceeded = true,
                storageWritable = true,
                ntripMountpointConfigured = false,
            ),
        )

        assertTrue(result.canStart)
        assertEquals(RecordingErrorCategory.NONE, result.category)
    }

    @Test
    fun `ntrip workflow requires configured mountpoint`() {
        val result = RecordingStartPreflight.validate(
            RecordingStartPreflight.Input(
                workflowUsesNtrip = true,
                usbProfileSelected = true,
                usbDeviceConnected = true,
                usbPermissionGranted = true,
                serialDriverAvailable = true,
                serialOpenSucceeded = true,
                storageWritable = true,
                ntripMountpointConfigured = false,
            ),
        )

        assertFalse(result.canStart)
        assertEquals(RecordingErrorCategory.NTRIP, result.category)
        assertEquals(RecordingErrorSeverity.FATAL, result.severity)
        assertTrue(result.message.contains("NTRIP mountpoint", ignoreCase = true))
    }

    @Test
    fun `missing connected usb device is reported before ntrip`() {
        val result = RecordingStartPreflight.validate(
            RecordingStartPreflight.Input(
                workflowUsesNtrip = true,
                usbProfileSelected = true,
                usbDeviceConnected = false,
                usbPermissionGranted = false,
                serialDriverAvailable = false,
                serialOpenSucceeded = false,
                storageWritable = true,
                ntripMountpointConfigured = false,
            ),
        )

        assertFalse(result.canStart)
        assertEquals(RecordingErrorCategory.USB, result.category)
        assertTrue(result.message.contains("USB receiver is not connected", ignoreCase = true))
    }

    @Test
    fun `serial open failure is visible`() {
        val result = RecordingStartPreflight.validate(
            RecordingStartPreflight.Input(
                workflowUsesNtrip = false,
                usbProfileSelected = true,
                usbDeviceConnected = true,
                usbPermissionGranted = true,
                serialDriverAvailable = true,
                serialOpenSucceeded = false,
                storageWritable = true,
                ntripMountpointConfigured = false,
            ),
        )

        assertFalse(result.canStart)
        assertEquals(RecordingErrorCategory.USB, result.category)
        assertTrue(result.message.contains("open", ignoreCase = true))
    }
}
