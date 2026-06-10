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
            putExtra(RecordingForegroundService.EXTRA_STATE_LAT_ERROR, "0.010 m")
            putExtra(RecordingForegroundService.EXTRA_STATE_LON_ERROR, "0.011 m")
            putExtra(RecordingForegroundService.EXTRA_STATE_BASELINE, "12.345 m")
            putExtra(RecordingForegroundService.EXTRA_STATE_PDOP, "1.2")
            putExtra(RecordingForegroundService.EXTRA_STATE_HDOP_VDOP, "0.8 / 1.0")
            putExtra(RecordingForegroundService.EXTRA_STATE_LAT_LON, "48.100000000, 17.100000000")
            putExtra(RecordingForegroundService.EXTRA_STATE_UTC_TIME, "12:34:56Z")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertFalse(state.isRecording)
        assertEquals(DashboardActionKind.START, state.primaryAction.kind)
        assertEquals("content://session/current", state.files.sessionLocation)
        assertEquals("123 B", state.files.receiverRxBytes)
        assertEquals("45 B", state.files.txToReceiverBytes)
        assertTrue(state.files.zipShareEnabled)
        assertEquals("0.010 m", state.position.latError)
        assertEquals("0.011 m", state.position.lonError)
        assertEquals("12.345 m", state.fix.baseline)
        assertEquals("1.2", state.fix.pdop)
        assertEquals("0.8 / 1.0", state.fix.hdopVdop)
        assertEquals("48.100000000, 17.100000000", state.position.latLon)
        assertEquals("12:34:56Z", state.position.utcTime)
    }

    @Test
    fun `ntrip placeholder url does not become mountpoint a`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_URL, "n/a")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("n/a", state.status.mountpoint)
    }

    @Test
    fun `bestnav none falls back to gga fix quality`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "NONE")
            putExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, 2)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("DGPS", state.fix.fixType)
    }

    @Test
    fun `ppp status can describe fix when bestnav is none`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "NONE")
            putExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS, "PPP_CONVERGING")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("PPP_CONVERGING", state.fix.fixType)
    }

    @Test
    fun `ppp none does not hide gga fix quality`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "NONE")
            putExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS, "NONE")
            putExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, 5)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("RTK float", state.fix.fixType)
        assertEquals("n/a", state.fix.pppStatus)
    }

    @Test
    fun `bestnav ppp converging type is shown only in ppp field`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "PPP_CONVERGING")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("n/a", state.fix.fixType)
        assertEquals("PPP_CONVERGING", state.fix.pppStatus)
    }

    @Test
    fun `sbas and psrdiff are displayed as dgps fix`() {
        val sbasIntent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "SBAS")
        }
        val psrdiffIntent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "PSRDIFF")
        }

        assertEquals("DGPS", dashboardStateFromRecordingIntent(sbasIntent).fix.fixType)
        assertEquals("DGPS", dashboardStateFromRecordingIntent(psrdiffIntent).fix.fixType)
    }

    @Test
    fun `ppp converging stays in ppp field and does not replace gga fix type`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "PPP_CONVERGING")
            putExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, 2)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("DGPS", state.fix.fixType)
        assertEquals("PPP_CONVERGING", state.fix.pppStatus)
    }

    @Test
    fun `converged ppp is displayed as ppp fix`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "PPP")
            putExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, 2)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("PPP", state.fix.fixType)
        assertEquals("PPP", state.fix.pppStatus)
    }
}
