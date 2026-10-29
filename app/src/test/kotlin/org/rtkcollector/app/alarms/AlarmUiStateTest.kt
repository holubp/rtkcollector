package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rtkcollector.app.ui.alarms.AlarmUiState

class AlarmUiStateTest {
    @Test fun `acoustic-only nonterminal alarm exposes scoped Home controls`() {
        val state = AlarmUiState(generation = 7, tokens = listOf("episode"))
        assertTrue(state.hasContent)
        assertTrue(state.hasActions)
    }
    @Test fun `delivery failure remains visible without an active visual alarm`() {
        val state = AlarmUiState(generation = 7, delivery = "Alarm audio delivery unavailable.")
        assertTrue(state.hasContent)
        assertFalse(state.hasActions)
    }
    @Test fun `recovery clears delivery feedback and stale broadcasts cannot revive it`() {
        val failed = AlarmUiState(7, "Position lost", false, listOf("episode"), "Audio unavailable")
        assertTrue(failed.hasActions)
        val recovered = failed.accept(AlarmUiState(generation = 7))
        assertFalse(recovered.hasContent)
        assertNull(recovered.delivery)
        assertEquals(recovered, recovered.accept(failed.copy(generation = 6)))
    }
}
