package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DiagnosticsRedactionTest {
    @Test
    fun `redacts authorization headers and password fields`() {
        val input = "Authorization: Basic abc123 password=secret token=abc"
        val redacted = redactDiagnosticText(input)

        assertFalse(redacted.contains("abc123"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("token=abc"))
        assertEquals("Authorization: <redacted> password=<redacted> token=<redacted>", redacted)
    }

    @Test
    fun `redacts ntrip url credentials`() {
        val input = "ntrip://user:pass@example.org:2101/MOUNT"
        val redacted = redactDiagnosticText(input)

        assertEquals("ntrip://<redacted>@example.org:2101/MOUNT", redacted)
    }

    @Test
    fun `redacts colon separated secret fields`() {
        val input = "password: hidden token: abc"
        val redacted = redactDiagnosticText(input)

        assertFalse(redacted.contains("hidden"))
        assertFalse(redacted.contains("abc"))
        assertEquals("password=<redacted> token=<redacted>", redacted)
    }
}
