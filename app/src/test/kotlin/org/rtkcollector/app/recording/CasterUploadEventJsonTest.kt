package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.correction.NtripCasterUploadEvent

class CasterUploadEventJsonTest {
    @Test
    fun `caster upload event json redacts credential-like message content`() {
        val json = casterUploadEventJson(
            NtripCasterUploadEvent(
                kind = "connect",
                message = listOf(
                    "retry after",
                    "Authorization: Basic abc123",
                    "password=secret",
                    "token=abc",
                    "ntrip://user:pass@example.org:2101/MOUNT",
                ).joinToString(" "),
                timestampMillis = 1234L,
            ),
        )

        assertTrue(json.contains("\"type\":\"base-caster-upload\""))
        assertTrue(json.contains("\"kind\":\"connect\""))
        assertTrue(json.contains("\"timestampMillis\":1234"))
        assertTrue(json.contains("retry after"))
        assertTrue(json.contains("<redacted>"))
        assertFalse(json.contains("abc123"))
        assertFalse(json.contains("secret"))
        assertFalse(json.contains("token=abc"))
        assertFalse(json.contains("user:pass"))
    }
}
