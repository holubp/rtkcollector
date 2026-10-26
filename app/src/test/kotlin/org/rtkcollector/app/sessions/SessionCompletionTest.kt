package org.rtkcollector.app.sessions

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SessionCompletionTest {
    @Test fun `active sessions are not interrupted and missing evidence is unknown`() {
        assertEquals("Recording", sessionCompletionLabel(JSONObject(), true))
        assertEquals("Incomplete; cause unknown", sessionCompletionLabel(JSONObject(), false))
        assertEquals("Stopped (legacy evidence)", sessionCompletionLabel(JSONObject("""{"stoppedAt":"now"}"""), false))
        assertEquals("Stopped: USER", sessionCompletionLabel(JSONObject("""{"stoppedAt":"now","stopReason":"USER","stopOutcome":"FINALIZED"}"""), false))
        assertEquals("Incomplete; cause unknown", sessionCompletionLabel(JSONObject("""{"stoppedAt":"now","stopOutcome":"EVIDENCE_INCOMPLETE"}"""), false))
    }
}
