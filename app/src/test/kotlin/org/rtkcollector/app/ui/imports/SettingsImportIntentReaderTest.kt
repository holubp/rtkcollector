package org.rtkcollector.app.ui.imports

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsImportIntentReaderTest {
    @Test
    fun `extracts action view data uri`() {
        val uri = Uri.parse("content://example/settings.json")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/json")

        assertEquals(uri, settingsImportUriFromIntent(intent))
    }

    @Test
    fun `extracts action send stream uri`() {
        val uri = Uri.parse("content://example/settings.json")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)

        assertEquals(uri, settingsImportUriFromIntent(intent))
    }

    @Test
    fun `extracts action send clip data uri`() {
        val uri = Uri.parse("content://example/settings.json")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .apply {
                clipData = ClipData.newRawUri("settings", uri)
            }

        assertEquals(uri, settingsImportUriFromIntent(intent))
    }

    @Test
    fun `rejects unsupported uri schemes`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.test/settings.json"))
            .setType("application/json")

        assertNull(settingsImportUriFromIntent(intent))
    }

    @Test
    fun `rejects file uri imports`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("file:///sdcard/Download/settings.json"))
            .setType("application/json")

        assertNull(settingsImportUriFromIntent(intent))
    }

    @Test
    fun `ignores launcher intent`() {
        assertNull(settingsImportUriFromIntent(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `ignores action send without stream`() {
        assertNull(settingsImportUriFromIntent(Intent(Intent.ACTION_SEND).setType("application/json")))
    }
}
