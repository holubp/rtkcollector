package org.rtkcollector.app.ui.dashboard

import android.content.Intent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.recording.RecordingForegroundService

class DashboardServiceMapperTest {
    @Test
    fun `stopped service state preserves last session files`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, false)
            putExtra(RecordingForegroundService.EXTRA_STATE_WORKFLOW_LABEL, "Rover + NTRIP")
            putExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_LABEL, "UM980")
            putExtra(RecordingForegroundService.EXTRA_STATE_STORAGE_LABEL, "SAF folder")
            putExtra(RecordingForegroundService.EXTRA_STATE_SESSION_PATH, "content://session/current")
            putExtra(RecordingForegroundService.EXTRA_STATE_RX_BYTES, 123L)
            putExtra(RecordingForegroundService.EXTRA_STATE_TX_BYTES, 45L)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertFalse(state.isRecording)
        assertEquals(DashboardActionKind.START, state.primaryAction.kind)
        assertEquals("content://session/current", state.files.sessionLocation)
        assertEquals("123 B", state.files.receiverRxBytes)
        assertEquals("45 B", state.files.txToReceiverBytes)
        assertTrue(state.files.zipShareEnabled)
    }
}
