package org.rtkcollector.app.diagnostics

import org.json.JSONObject
import org.rtkcollector.app.sessions.assessSessionCompletion
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecoveryDiagnosticsTest {
    @Test fun `recovery assessment is durable minimal evidence without mutating the session`() {
        val metadata = JSONObject("""{"sessionUuid":"f420dd47-1f0f-4e98-84f6-650ba60715c2"}""")
        val before = metadata.toString()
        val records = mutableListOf<RuntimeDiagnosticRecord>()
        val reporter = RecoveryDiagnostics { records.add(it) }
        reporter.record(listOf(assessSessionCompletion(metadata, active = true)))
        assertTrue(records.isEmpty())
        val assessment = assessSessionCompletion(metadata, active = false)
        reporter.record(listOf(assessment, assessment))
        assertEquals(1, records.size)
        val json = JSONObject(records.single().toJsonLine())
        assertEquals("session-recovery-assessed", json.getString("type"))
        assertEquals("INCOMPLETE", json.getJSONObject("attributes").getString("reason"))
        assertEquals(before, metadata.toString())
        assertFalse(json.toString().contains("stoppedAt"))
    }
}
