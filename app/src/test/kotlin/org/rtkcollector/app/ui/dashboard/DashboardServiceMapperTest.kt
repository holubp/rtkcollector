package org.rtkcollector.app.ui.dashboard

import android.content.Intent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.recording.RecordingErrorCategory
import org.rtkcollector.app.recording.RecordingErrorSeverity
import org.rtkcollector.app.recording.RecordingForegroundService

class DashboardServiceMapperTest {
    @Test
    fun `failed service state exposes last error on planned dashboard`() {
        val intent = Intent()
            .putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, false)
            .putExtra(RecordingForegroundService.EXTRA_STATE_ERROR, "USB serial device could not be opened.")
            .putExtra(RecordingForegroundService.EXTRA_STATE_ERROR_CATEGORY, RecordingErrorCategory.USB.name)
            .putExtra(RecordingForegroundService.EXTRA_STATE_ERROR_SEVERITY, RecordingErrorSeverity.FATAL.name)

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("USB serial device could not be opened.", state.lastError)
        assertEquals("USB", state.errorCategory)
        assertEquals("FATAL", state.errorSeverity)
    }

    @Test
    fun `maps receiver diagnostics frequency and mode`() {
        val intent = Intent()
            .putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            .putExtra(
                RecordingForegroundService.EXTRA_STATE_UM980_FREQUENCY,
                "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/1/1/1/1/4 Hz",
            )
            .putExtra(RecordingForegroundService.EXTRA_STATE_UM980_MODE, "Commanded ROVER SURVEY")

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals(
            "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/1/1/1/1/4 Hz",
            state.fix.receiverFrequency,
        )
        assertEquals("Commanded ROVER SURVEY", state.fix.receiverMode)
    }

    @Test
    fun `default ublox frequency label does not hide live um980 frequency`() {
        val intent = Intent()
            .putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            .putExtra(
                RecordingForegroundService.EXTRA_STATE_UM980_FREQUENCY,
                "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/-/-/-/-/4 Hz",
            )
            .putExtra(
                RecordingForegroundService.EXTRA_STATE_UBLOX_FREQUENCY,
                "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA -/-/-/-/- Hz",
            )

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals(
            "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/-/-/-/-/4 Hz",
            state.fix.receiverFrequency,
        )
    }

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
    fun `ppp status does not describe main fix when bestnav is none`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "NONE")
            putExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS, "PPP_CONVERGING")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("n/a", state.fix.fixType)
        assertEquals("PPP converging", state.fix.pppStatus)
    }

    @Test
    fun `converged ppp status alone does not override gga fix quality`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "NONE")
            putExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS, "PPP")
            putExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, 2)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("DGPS", state.fix.fixType)
        assertEquals("PPP converged", state.fix.pppStatus)
    }

    @Test
    fun `explicit ppp none is shown as not started and does not hide gga fix quality`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "NONE")
            putExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS, "NONE")
            putExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, 5)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("RTK float", state.fix.fixType)
        assertEquals("PPP not started", state.fix.pppStatus)
    }

    @Test
    fun `bestnav ppp converging type is shown as receiver solution state`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "PPP_CONVERGING")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("PPP converging", state.fix.fixType)
        assertEquals("n/a", state.fix.pppStatus)
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
    fun `bestnav solution type has priority over unknown gga quality`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "PSRDIFF")
            putExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, 7)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("DGPS", state.fix.fixType)
    }

    @Test
    fun `base manual gga quality is shown explicitly`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, 7)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("Base/manual", state.fix.fixType)
    }

    @Test
    fun `unknown gga quality is shown as unknown instead of settled fix`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, 17)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("Unknown GGA 17", state.fix.fixType)
    }

    @Test
    fun `ppp converging stays in ppp field and does not replace gga fix type`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "PPP_CONVERGING")
            putExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS, "PPP_CONVERGING")
            putExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, 2)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("DGPS", state.fix.fixType)
        assertEquals("PPP converging", state.fix.pppStatus)
    }

    @Test
    fun `explicit service rtk status is shown separately from ppp and rtklib`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "NARROW_FLOAT")
            putExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS, "PPP_CONVERGING")
            putExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_RTK_STATUS, "RTK float")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("RTK float", state.fix.fixType)
        assertEquals("PPP converging", state.fix.pppStatus)
        assertEquals("RTK float", state.fix.rtkStatus)
        assertEquals("Not configured", state.fix.rtklibStatus)
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
        assertEquals("n/a", state.fix.pppStatus)
    }
}
