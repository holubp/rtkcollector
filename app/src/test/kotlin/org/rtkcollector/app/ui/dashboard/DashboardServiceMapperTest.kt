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
    fun `maps mock provider monitor with last interval and solution age`() {
        val intent = Intent()
            .putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            .putExtra(RecordingForegroundService.EXTRA_STATE_MOCK_LOCATION_STATE, "PUBLISHED")
            .putExtra(RecordingForegroundService.EXTRA_STATE_MOCK_LOCATION_INTERVAL_MS, 250L)
            .putExtra(RecordingForegroundService.EXTRA_STATE_MOCK_LOCATION_SOLUTION_AGE_MS, 80L)
            .putExtra(RecordingForegroundService.EXTRA_STATE_MOCK_LOCATION_RATE_HZ, 5)

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("PUBLISHED · 4.0 Hz · last 250 ms · age 80 ms", state.fix.mockLocation)
        assertEquals(MockGpsDashboardState(enabled = true, rateHz = 5), state.mockGps)
    }

    @Test
    fun `mock provider monitor does not use primary dashboard solution age`() {
        val intent = Intent()
            .putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            .putExtra(RecordingForegroundService.EXTRA_STATE_MOCK_LOCATION_STATE, "PUBLISHED")
            .putExtra(RecordingForegroundService.EXTRA_STATE_MOCK_LOCATION_INTERVAL_MS, 1000L)
            .putExtra(RecordingForegroundService.EXTRA_STATE_MOCK_LOCATION_SOLUTION_AGE_MS, 80L)
            .putExtra("bestSolutionAgeMs", 900L)

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("PUBLISHED · 1.0 Hz · last 1000 ms · age 80 ms", state.fix.mockLocation)
    }

    @Test
    fun `default ublox frequency label does not hide live um980 frequency`() {
        val intent = Intent()
            .putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            .putExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_FAMILY, "um980")
            .putExtra(
                RecordingForegroundService.EXTRA_STATE_UM980_FREQUENCY,
                "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/-/-/-/-/4 Hz",
            )
            .putExtra(
                RecordingForegroundService.EXTRA_STATE_UBLOX_FREQUENCY,
                "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA -/-/-/-/-/-/- Hz",
            )

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals(
            "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/-/-/-/-/4 Hz",
            state.fix.receiverFrequency,
        )
    }

    @Test
    fun `ublox receiver family shows ublox frequency line even if um980 has stale values`() {
        val intent = Intent()
            .putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            .putExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_FAMILY, "ublox")
            .putExtra(
                RecordingForegroundService.EXTRA_STATE_UM980_FREQUENCY,
                "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/-/-/-/-/4 Hz",
            )
            .putExtra(
                RecordingForegroundService.EXTRA_STATE_UBLOX_FREQUENCY,
                "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA 1/1/-/5/-/-/- Hz",
            )

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals(
            "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA 1/1/-/5/-/-/- Hz",
            state.fix.receiverFrequency,
        )
    }

    @Test
    fun `um980 receiver family shows um980 frequency line even if ublox has stale values`() {
        val intent = Intent()
            .putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            .putExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_FAMILY, "um980")
            .putExtra(
                RecordingForegroundService.EXTRA_STATE_UM980_FREQUENCY,
                "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/-/-/-/-/4 Hz",
            )
            .putExtra(
                RecordingForegroundService.EXTRA_STATE_UBLOX_FREQUENCY,
                "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA 1/1/-/5/-/-/- Hz",
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
    fun `service state maps total session bytes separately from raw artifact bytes`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_RX_BYTES, 11_508_415L)
            putExtra(RecordingForegroundService.EXTRA_STATE_TX_BYTES, 4_792_865L)
            putExtra(RecordingForegroundService.EXTRA_STATE_CORRECTION_BYTES, 4_792_235L)
            putExtra(RecordingForegroundService.EXTRA_STATE_NMEA_BYTES, 12_838_811L)
            putExtra(RecordingForegroundService.EXTRA_STATE_SESSION_TOTAL_BYTES, 125_545_871L)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("11.5 MB", state.files.receiverRxBytes)
        assertEquals("4.8 MB", state.files.txToReceiverBytes)
        assertEquals("4.8 MB", state.files.ntripBytes)
        assertEquals("12.8 MB", state.files.nmeaBytes)
        assertEquals("125.5 MB", state.files.sessionTotalBytes)
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
    fun `correction last update ignores elapsed realtime timestamps`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_CORRECTION_LAST_UPDATED_AT, 691_380_000L)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("n/a", state.ntrip.lastUpdated)
    }

    @Test
    fun `correction last update displays wall clock epoch timestamps`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_CORRECTION_LAST_UPDATED_AT, 1_781_699_696_000L)
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("2026-06-17T12:34:56.000Z", state.ntrip.lastUpdated)
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
    fun `ublox best solution fix is shown when gga and bestnav fields are absent`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_FAMILY, "ublox")
            putExtra(RecordingForegroundService.EXTRA_STATE_BEST_SOLUTION_SOURCE, "UBX-NAV-PVT")
            putExtra(RecordingForegroundService.EXTRA_STATE_BEST_SOLUTION_FIX, "DGPS")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("DGPS", state.fix.fixType)
        assertEquals("DGPS from UBX-NAV-PVT", state.fix.bestSolution)
    }

    @Test
    fun `satellite monitor remains unavailable without explicit monitor payload`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_SATELLITES, "8/12")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertFalse(state.satelliteMonitor.hasFrequencyGroups)
        assertEquals("Satellite monitor unavailable", state.satelliteMonitor.message)
    }

    @Test
    fun `satellite monitor maps explicit unavailable payload from service`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_SATELLITE_MONITOR_ENGINE, "RTKLIB")
            putExtra(RecordingForegroundService.EXTRA_STATE_SATELLITE_MONITOR_SOURCES, "R:UNAVAILABLE;B:UNAVAILABLE;S:UNAVAILABLE")
            putExtra(RecordingForegroundService.EXTRA_STATE_SATELLITE_MONITOR_GROUPS, "")
            putExtra(RecordingForegroundService.EXTRA_STATE_SATELLITE_MONITOR_MESSAGE, "Per-frequency monitor profile not active")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertFalse(state.satelliteMonitor.hasFrequencyGroups)
        assertEquals("RTKLIB", state.satelliteMonitor.engineLabel)
        assertEquals(SatelliteMonitorSourceFreshness.UNAVAILABLE, state.satelliteMonitor.sources.rover.freshness)
        assertEquals(SatelliteMonitorSourceFreshness.UNAVAILABLE, state.satelliteMonitor.sources.base.freshness)
        assertEquals(SatelliteMonitorSourceFreshness.UNAVAILABLE, state.satelliteMonitor.sources.solution.freshness)
        assertEquals("Per-frequency monitor profile not active", state.satelliteMonitor.message)
    }

    @Test
    fun `satellite monitor maps explicit constellation frequency payload`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_SATELLITE_MONITOR_ENGINE, "RTKLIB")
            putExtra(RecordingForegroundService.EXTRA_STATE_SATELLITE_MONITOR_SOURCES, "R:FRESH;B:STALE;S:UNAVAILABLE")
            putExtra(
                RecordingForegroundService.EXTRA_STATE_SATELLITE_MONITOR_GROUPS,
                "GPS|L1|8|11|9|12;GPS|L2|6|9|7|10;Galileo|E1|4|6|5|8",
            )
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("RTKLIB", state.satelliteMonitor.engineLabel)
        assertEquals(SatelliteMonitorSourceFreshness.FRESH, state.satelliteMonitor.sources.rover.freshness)
        assertEquals(SatelliteMonitorSourceFreshness.STALE, state.satelliteMonitor.sources.base.freshness)
        assertEquals(SatelliteMonitorSourceFreshness.UNAVAILABLE, state.satelliteMonitor.sources.solution.freshness)
        assertEquals(listOf("GPS", "Galileo"), state.satelliteMonitor.constellations.map { it.label })
        assertEquals(listOf("L1", "L2"), state.satelliteMonitor.constellations.first().frequencies.map { it.bandLabel })
        assertEquals("8/11", state.satelliteMonitor.constellations.first().frequencies.first().rover.displayValue)
        assertEquals("9/12", state.satelliteMonitor.constellations.first().frequencies.first().base.displayValue)
    }

    @Test
    fun `rtklib card maps position height and accuracy`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_STATE, "RUNNING")
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_FIX_CLASS, "RTK_FIXED")
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_SOLUTION_AGE_MS, 120L)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_LAT_LON, "50.087451200, 14.421253400")
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_ELLIPSOIDAL_HEIGHT, "287.423 m")
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_ACCURACY_HV, "8.0 mm / 12.0 mm")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals("RUNNING", state.rtklib?.state)
        assertEquals("RTK_FIXED", state.rtklib?.fixClass)
        assertEquals("120 ms", state.rtklib?.age)
        assertEquals("50.087451200, 14.421253400", state.rtklib?.latLon)
        assertEquals("287.423 m", state.rtklib?.ellipsoidalHeight)
        assertEquals("8.0 mm / 12.0 mm", state.rtklib?.accuracyHv)
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
    fun `rtklib card is hidden when disabled`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_STATE, "Disabled")
        }

        val state = dashboardStateFromRecordingIntent(intent)

        assertEquals(null, state.rtklib)
    }

    @Test
    fun `rtklib card maps worker counters and route details`() {
        val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
            putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_STATE, "LAGGING")
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_ROUTE_PLAN, "rover=input_unicore(UNICORE_OBSVMB)")
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_SNAPSHOT_ID, "rtklib-ex-2.5.0@commit")
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_LAST_ERROR, "queue pressure")
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_FIX_CLASS, "RTK_FLOAT")
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_SOLUTION_AGE_MS, 80L)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_ROVER_QUEUE_BYTES, 1536)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_CORRECTION_QUEUE_BYTES, 512)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_DROPPED_ROVER_BYTES, 64L)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_DROPPED_CORRECTION_BYTES, 32L)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_DECODED_ROVER_EPOCHS, 12L)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_DECODED_CORRECTION_MESSAGES, 34L)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_OUTPUT_NMEA_LINES, 5L)
            putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_OUTPUT_POS_LINES, 6L)
        }

        val rtklib = dashboardStateFromRecordingIntent(intent).rtklib

        assertEquals("LAGGING", rtklib?.state)
        assertEquals("rover=input_unicore(UNICORE_OBSVMB)", rtklib?.routePlan)
        assertEquals("rtklib-ex-2.5.0@commit", rtklib?.snapshotId)
        assertEquals("queue pressure", rtklib?.lastError)
        assertEquals("RTK_FLOAT", rtklib?.fixClass)
        assertEquals("80 ms", rtklib?.age)
        assertEquals("1.5 kB", rtklib?.roverQueue)
        assertEquals("512 B", rtklib?.correctionQueue)
        assertEquals("64 B / 32 B", rtklib?.dropped)
        assertEquals("12 rover / 34 corr", rtklib?.decoded)
        assertEquals("5 NMEA / 6 POS", rtklib?.outputs)
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
