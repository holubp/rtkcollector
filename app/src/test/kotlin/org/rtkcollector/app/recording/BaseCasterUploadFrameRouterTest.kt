package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.correction.Rtcm3Frame

class BaseCasterUploadFrameRouterTest {
    @Test
    fun `invalid rtcm frame is audited and evented but never offered for upload`() {
        val frameBytes = byteArrayOf(0xd3.toByte(), 0x00, 0x02, 0x43, 0x20, 0x00, 0x00, 0x00)
        val audited = mutableListOf<ByteArray>()
        val events = mutableListOf<String>()
        var uploadCalled = false

        val offered = routeBaseCasterUploadFrame(
            frame = Rtcm3Frame(frameBytes, payloadLength = 2, messageType = 1074, crcValid = false),
            uploaderActive = true,
            timestampMillis = 1234L,
            appendAudit = audited::add,
            appendDroppedEvent = events::add,
            offerUpload = { _, _ ->
                uploadCalled = true
                true
            },
        )

        assertEquals(false, offered)
        assertTrue(audited.isEmpty())
        assertFalse(uploadCalled)
        assertEquals(1, events.size)
        assertTrue(events.single().contains("\"type\":\"base-caster-upload-frame-dropped\""))
        assertTrue(events.single().contains("\"reason\":\"invalid-rtcm-crc\""))
        assertTrue(events.single().contains("\"messageType\":1074"))
        assertTrue(events.single().contains("\"frameBytes\":8"))
        assertTrue(events.single().contains("\"timestampMillis\":1234"))
        assertTrue(events.single().contains("\"frameBase64\":\"0wACQyAAAAA=\""))
    }

    @Test
    fun `valid rtcm frame is audited before being offered unchanged`() {
        val frameBytes = byteArrayOf(0xd3.toByte(), 0x00, 0x02, 0x43, 0x20, 0x12, 0x34, 0x56)
        val actions = mutableListOf<String>()
        var offeredBytes: ByteArray? = null
        var offeredMessageType: Int? = null

        val offered = routeBaseCasterUploadFrame(
            frame = Rtcm3Frame(frameBytes, payloadLength = 2, messageType = 1074, crcValid = true),
            uploaderActive = true,
            timestampMillis = 5678L,
            appendAudit = {
                actions += "audit"
                offeredBytes = it
            },
            appendDroppedEvent = { actions += "event" },
            offerUpload = { bytes, messageType ->
                actions += "upload"
                offeredBytes = bytes
                offeredMessageType = messageType
                true
            },
        )

        assertEquals(true, offered)
        assertEquals(listOf("audit", "upload"), actions)
        assertArrayEquals(frameBytes, offeredBytes)
        assertEquals(1074, offeredMessageType)
    }

    @Test
    fun `inactive uploader does not create a base upload audit`() {
        val actions = mutableListOf<String>()

        val offered = routeBaseCasterUploadFrame(
            frame = Rtcm3Frame(byteArrayOf(0xd3.toByte()), 0, null, false),
            uploaderActive = false,
            timestampMillis = 1L,
            appendAudit = { actions += "audit" },
            appendDroppedEvent = { actions += "event" },
            offerUpload = { _, _ ->
                actions += "upload"
                true
            },
        )

        assertEquals(null, offered)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `queue drop leaves valid frame in audit without marking its crc invalid`() {
        val frameBytes = byteArrayOf(0xd3.toByte(), 0, 0, 1, 2, 3)
        val audited = mutableListOf<ByteArray>()
        val events = mutableListOf<String>()
        val offered = routeBaseCasterUploadFrame(
            Rtcm3Frame(frameBytes, 0, 1005, true), true, 99L,
            audited::add, events::add,
            { _, _ -> false },
        )
        assertEquals(false, offered)
        assertArrayEquals(frameBytes, audited.single())
        assertTrue(events.isEmpty())
    }
}
