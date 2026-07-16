package org.rtkcollector.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AndroidStateReceiverRegistrationTest {
    @Test
    fun `legacy activity keeps both private receivers non-exported on every supported api`() {
        val source = Files.readString(sourceFile("src/main/kotlin/org/rtkcollector/app/MainActivity.kt"))
        val registration = source.substringAfter("private fun registerReceivers()")
            .substringBefore("private fun rebuildListener()")

        assertFalse(registration.contains("Build.VERSION.SDK_INT"))
        assertEquals(2, Regex("ContextCompat\\.registerReceiver\\(").findAll(registration).count())
        assertEquals(2, Regex("ContextCompat\\.RECEIVER_NOT_EXPORTED").findAll(registration).count())
        assertTrue(registration.contains("usbPermissionReceiver"))
        assertTrue(registration.contains("serviceStateReceiver"))
    }

    private fun sourceFile(relative: String): Path {
        val candidates = listOf(Path.of(relative), Path.of("app").resolve(relative))
        return candidates.firstOrNull(Files::exists)
            ?: error("Cannot locate source file $relative from ${Path.of(\"\").toAbsolutePath()}")
    }
}
