package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DiagnosticsRedactionTest {
    @Test fun `credential containers and ambiguous encoded payloads are not exposed`() {
        val inputs = listOf(
            """{"plaintextPasswords":{"ntrip:example:user":"container-secret-marker"}}""",
            "Invalid JSON: {\\\"password\\\":\\\"encoded-secret-marker\\\"}",
        )
        inputs.forEach {
            val safe = redactDiagnosticText(it)
            assertFalse(safe.contains("container-secret-marker"))
            assertFalse(safe.contains("encoded-secret-marker"))
        }
    }
    @Test fun `nested serialized json secrets are sanitized and source prose stays readable`() {
        val nested = org.json.JSONObject().put("body", org.json.JSONObject().put("password", "nested-secret-marker").toString()).toString()
        assertFalse(redactDiagnosticText(nested).contains("nested-secret-marker"))
        assertEquals("Caster rejected source upload request", redactDiagnosticText("Caster rejected source upload request"))
    }
    @Test fun `structured and quoted secret values cannot leak through messages or attributes`() {
        val secret = "marker with spaces"
        val inputs = listOf("""{"password":"$secret","token":"$secret"}""", "password='$secret'", "token=\"$secret\"")
        inputs.forEach { input ->
            assertFalse(redactDiagnosticText(input).contains("marker"))
            assertFalse(redactDiagnosticText(input).contains("with spaces"))
            assertFalse(RuntimeDiagnosticRecord(1, DiagnosticCategory.APP, "ERROR", input,
                mapOf("exception" to input)).toJsonLine().contains("marker"))
        }
    }
    @Test
    fun `redacts source passwords and secure url credentials`() {
        assertFalse(redactDiagnosticText("SOURCE top-secret /MOUNT").contains("top-secret"))
        assertFalse(redactDiagnosticText("https://user:pass@example.org/MOUNT").contains("user:pass"))
        val json = RuntimeDiagnosticRecord(1, DiagnosticCategory.APP, "ERROR", "test",
            mapOf("top-secret-key" to "hidden", "password" to "hidden")).toJsonLine()
        assertFalse(json.contains("top-secret-key"))
        assertFalse(json.contains("hidden"))
    }
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
