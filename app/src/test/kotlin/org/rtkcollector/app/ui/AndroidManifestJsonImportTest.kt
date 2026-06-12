package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AndroidManifestJsonImportTest {
    @Test
    fun `manifest registers json view and send import filters`() {
        val manifest = Files.readString(sourceFile("src/main/AndroidManifest.xml"))

        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android.intent.action.SEND"))
        assertTrue(manifest.contains("android:mimeType=\"application/json\""))
        assertTrue(manifest.contains("android:mimeType=\"text/json\""))
        assertTrue(manifest.contains("android:mimeType=\"text/plain\""))
        assertTrue(manifest.contains("android:scheme=\"content\""))
    }

    private fun sourceFile(relative: String): Path {
        val candidates = listOf(Path.of(relative), Path.of("app").resolve(relative))
        return candidates.firstOrNull(Files::exists)
            ?: error("Cannot locate source file $relative from ${Path.of("").toAbsolutePath()}")
    }
}
