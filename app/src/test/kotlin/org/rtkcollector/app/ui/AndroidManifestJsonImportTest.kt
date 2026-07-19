package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.testing.TestFiles

class AndroidManifestJsonImportTest {
    @Test
    fun `manifest registers json view and send import filters`() {
        val manifest = TestFiles.readString(TestFiles.locateProjectPath("src/main/AndroidManifest.xml"))
        val importFilter = settingsImportViewIntentFilter(manifest)

        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android.intent.action.SEND"))
        assertTrue(importFilter.contains("android:mimeType=\"application/json\""))
        assertTrue(importFilter.contains("android:mimeType=\"text/json\""))
        assertTrue(manifest.contains("android:mimeType=\"text/plain\""))
        assertTrue(importFilter.contains("android:scheme=\"content\""))
    }

    @Test
    fun `settings import view intent does not use browsable or file scheme`() {
        val manifest = TestFiles.readString(TestFiles.locateProjectPath("src/main/AndroidManifest.xml"))
        val importFilter = settingsImportViewIntentFilter(manifest)

        assertFalse(importFilter.contains("android.intent.category.BROWSABLE"))
        assertFalse(importFilter.contains("android:scheme=\"file\""))
        assertTrue(importFilter.contains("android.intent.category.DEFAULT"))
        assertTrue(importFilter.contains("android:scheme=\"content\""))
        assertTrue(importFilter.contains("android:mimeType=\"application/json\""))
        assertTrue(importFilter.contains("android:mimeType=\"text/json\""))
    }

    private fun settingsImportViewIntentFilter(manifest: String): String =
        manifest.substringAfter("<action android:name=\"android.intent.action.VIEW\" />")
            .substringBefore("</intent-filter>")
}
