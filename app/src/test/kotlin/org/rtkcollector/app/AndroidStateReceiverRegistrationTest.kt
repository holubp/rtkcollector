package org.rtkcollector.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rtkcollector.app.testing.TestFiles
import java.nio.file.Files
import java.nio.file.Path

class AndroidStateReceiverRegistrationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `legacy activity keeps both private receivers non-exported on every supported api`() {
        val source = TestFiles.readString(
            TestFiles.locateProjectPath("src/main/kotlin/org/rtkcollector/app/MainActivity.kt"),
        )
        val registration = source.substringAfter("private fun registerReceivers()")
            .substringBefore("private fun rebuildListener()")

        assertFalse(registration.contains("Build.VERSION.SDK_INT"))
        assertEquals(2, Regex("ContextCompat\\.registerReceiver\\(").findAll(registration).count())
        assertEquals(2, Regex("ContextCompat\\.RECEIVER_NOT_EXPORTED").findAll(registration).count())
        assertTrue(registration.contains("usbPermissionReceiver"))
        assertTrue(registration.contains("serviceStateReceiver"))
    }

    @Test
    fun `source lookup cannot escape into a parent checkout`() {
        val nestedCheckout = tempDir.resolve("nested")
        Files.createDirectories(nestedCheckout.resolve("app"))
        TestFiles.writeString(nestedCheckout.resolve("settings.gradle.kts"), "rootProject.name = \"nested\"")
        TestFiles.writeString(nestedCheckout.resolve("app/build.gradle.kts"), "plugins {}")

        val parentOnlySource = tempDir.resolve("app/src/main/ParentOnly.kt")
        Files.createDirectories(parentOnlySource.parent)
        TestFiles.writeString(parentOnlySource, "class ParentOnly")

        assertThrows(IllegalStateException::class.java) {
            TestFiles.locateProjectPathFrom(
                workingDirectory = nestedCheckout.resolve("app"),
                relative = "app/src/main/ParentOnly.kt",
            )
        }

        val nestedSource = nestedCheckout.resolve("app/src/main/Nested.kt")
        Files.createDirectories(nestedSource.parent)
        TestFiles.writeString(nestedSource, "class Nested")
        assertEquals(
            nestedSource,
            TestFiles.locateProjectPathFrom(
                workingDirectory = nestedCheckout.resolve("app"),
                relative = "app/src/main/Nested.kt",
            ),
        )
    }
}
