package org.rtkcollector.app.storage

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingStorageTest {
    @Test
    fun `supports app private and saf tree only`() {
        assertTrue(RecordingStorage.isSupportedStorageKind("APP_PRIVATE"))
        assertTrue(RecordingStorage.isSupportedStorageKind("SAF_TREE"))
        assertFalse(RecordingStorage.isSupportedStorageKind("DOWNLOADS"))
    }

    @Test
    fun `saf storage requires tree uri`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordingStorage.validateStorageSelection("SAF_TREE", null)
        }
        RecordingStorage.validateStorageSelection("SAF_TREE", "content://authority/tree/primary%3ARtkCollector")
    }

    @Test
    fun `detects likely saf tree uri`() {
        assertTrue(RecordingStorage.isLikelySafTreeUri("content://authority/tree/primary%3ARtkCollector"))
        assertFalse(RecordingStorage.isLikelySafTreeUri("content://authority/document/primary%3Afile"))
    }
}
