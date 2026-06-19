package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordingReceiverFamilyTest {
    @Test
    fun `command receiver family wins over stale receiver profile`() {
        assertEquals("ublox", recordingReceiverFamily(receiverProfileId = "um980-n4", commandReceiverFamily = "ublox-m8t"))
    }

    @Test
    fun `receiver profile is used when command receiver family is absent`() {
        assertEquals("um980", recordingReceiverFamily(receiverProfileId = "um980-n4", commandReceiverFamily = null))
    }

    @Test
    fun `empty command receiver family falls back to receiver profile`() {
        assertEquals("um980", recordingReceiverFamily(receiverProfileId = "unicore-n4", commandReceiverFamily = ""))
    }

    @Test
    fun `session receiver driver id uses command family over stale receiver profile`() {
        assertEquals("ublox-m8t", sessionReceiverDriverId(receiverProfileId = "um980-n4", commandReceiverFamily = "ublox-m8t"))
    }

    @Test
    fun `session receiver driver id falls back to receiver profile`() {
        assertEquals("um980-n4", sessionReceiverDriverId(receiverProfileId = "um980-n4", commandReceiverFamily = ""))
    }
}
