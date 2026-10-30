package org.rtkcollector.app.storage

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingStorageTest {
    @Test
    fun `recording tree requires both read and write grants ignoring unrelated flags`() {
        val read = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persistable = android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        for (flags in listOf(0, read, write, persistable, read or persistable, write or persistable)) {
            assertThrows(IllegalArgumentException::class.java) {
                RecordingStorage.validateTreeGrantFlags(flags)
            }
        }
        RecordingStorage.validateTreeGrantFlags(read or write)
        RecordingStorage.validateTreeGrantFlags(read or write or persistable)
    }

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
