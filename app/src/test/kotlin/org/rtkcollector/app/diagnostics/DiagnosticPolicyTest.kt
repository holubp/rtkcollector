package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiagnosticPolicyTest {
    @Test fun `defaults and migration preserve operator opt in`() {
        assertEquals(DiagnosticLevel.MINIMAL, DiagnosticLevel.fromStored(null, false))
        assertEquals(DiagnosticLevel.VERBOSE, DiagnosticLevel.fromStored(null, true))
        assertEquals(DiagnosticLevel.DEBUG, DiagnosticLevel.fromStored("DEBUG", false))
        assertEquals(DiagnosticLevel.MINIMAL, DiagnosticLevel.fromStored("bad", false))
        assertEquals(10_000_000L, DiagnosticsRetention().maxOptionalBytes)
        assertEquals(7, DiagnosticsRetention().maxOptionalAgeDays)
        assertEquals(listOf(5, 10, 25, 50), DiagnosticsRetention.SIZE_CHOICES_MB)
    }

    @Test fun `levels are cumulative and only elevated levels have advisories`() {
        assertTrue(DiagnosticLevel.DEBUG.includes(DiagnosticLevel.VERBOSE))
        assertFalse(DiagnosticLevel.MINIMAL.includes(DiagnosticLevel.VERBOSE))
        assertNull(DiagnosticLevel.MINIMAL.advisory)
        assertEquals("Debug logging enabled", DiagnosticLevel.DEBUG.advisory)
    }
}
