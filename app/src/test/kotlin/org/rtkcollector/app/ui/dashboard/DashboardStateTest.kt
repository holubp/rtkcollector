package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardStateTest {
    @Test
    fun `planned session shows start as primary action`() {
        val state = DashboardState.planned(
            workflow = "Rover + NTRIP",
            mountpoint = "TUBO00CZE0",
            receiver = "UM980",
            storage = "SAF folder",
        )

        assertFalse(state.isRecording)
        assertEquals("Start", state.primaryAction.label)
        assertEquals(DashboardActionKind.START, state.primaryAction.kind)
        assertEquals(listOf(DashboardAction("USB access", DashboardActionKind.USB_PERMISSION)), state.secondaryActions)
        assertEquals("Rover + NTRIP", state.status.workflow)
        assertEquals("TUBO00CZE0", state.status.mountpoint)
    }

    @Test
    fun `planned session can preserve last known files after stop`() {
        val state = DashboardState.planned(
            workflow = "Rover + NTRIP",
            mountpoint = "TUBO00CZE0",
            receiver = "UM980",
            storage = "SAF folder",
            files = FilesCardState(
                sessionLocation = "content://session/current",
                receiverRxBytes = "123 B",
                zipShareEnabled = true,
            ),
        )

        assertFalse(state.isRecording)
        assertEquals(DashboardActionKind.START, state.primaryAction.kind)
        assertEquals("content://session/current", state.files.sessionLocation)
        assertEquals("123 B", state.files.receiverRxBytes)
        assertTrue(state.files.zipShareEnabled)
        assertEquals("Available", state.files.zipShareLabel)
    }

    @Test
    fun `running session keeps stop visible`() {
        val state = DashboardState.running(
            status = DashboardStatus(
                workflow = "Rover + NTRIP",
                mountpoint = "TUBO00CZE0",
                receiver = "UM980",
                storage = "SAF folder",
            ),
            position = PositionCardState(latLon = "50.087451234, 14.421253456"),
            fix = FixCardState(fixType = "RTK float"),
            ntrip = NtripCardState(status = "Streaming"),
            files = FilesCardState(sessionLocation = ".../session"),
        )

        assertTrue(state.isRecording)
        assertEquals("Stop", state.primaryAction.label)
        assertEquals(DashboardActionKind.STOP, state.primaryAction.kind)
        assertEquals(
            listOf(
                DashboardAction("NTRIP", DashboardActionKind.NTRIP),
            ),
            state.secondaryActions,
        )
    }

    @Test
    fun `mock gps dashboard label shows off and fixed rate states`() {
        assertEquals("Mock GPS off", MockGpsDashboardState().label)
        assertEquals("Mock GPS 5 Hz", MockGpsDashboardState(enabled = true, rateHz = 5).label)
    }

    @Test
    fun `stopped service state keeps planned configuration chips`() {
        val serviceState = DashboardState.planned(
            workflow = "n/a",
            mountpoint = "a",
            receiver = "n/a",
            storage = "n/a",
            files = FilesCardState(sessionLocation = "content://session/current", receiverRxBytes = "123 B"),
        )
        val planned = DashboardState.planned(
            workflow = "Rover + NTRIP",
            mountpoint = "n/a",
            receiver = "UM980",
            storage = "App-private external storage",
            profiles = ProfilesCardState(settingsSet = "UM980 rover + NTRIP"),
        )

        val merged = serviceState.withPlannedConfiguration(planned)

        assertEquals("Rover + NTRIP", merged.status.workflow)
        assertEquals("n/a", merged.status.mountpoint)
        assertEquals("UM980", merged.status.receiver)
        assertEquals("App-private external storage", merged.status.storage)
        assertEquals(planned.mockGps, merged.mockGps)
        assertEquals("content://session/current", merged.files.sessionLocation)
        assertEquals("123 B", merged.files.receiverRxBytes)
    }

    @Test
    fun `stopped service state does not preserve stale mountpoint when planned mountpoint is missing`() {
        val serviceState = DashboardState.planned(
            workflow = "Rover + NTRIP",
            mountpoint = "TUBO00CZE0",
            receiver = "UM980",
            storage = "SAF folder",
            files = FilesCardState(sessionLocation = "content://session/current", receiverRxBytes = "123 B"),
        )
        val planned = DashboardState.planned(
            workflow = "Rover + NTRIP",
            mountpoint = "n/a",
            receiver = "UM980",
            storage = "SAF folder",
        )

        val merged = serviceState.withPlannedConfiguration(planned)

        assertEquals("n/a", merged.status.mountpoint)
    }

    @Test
    fun `stopped service state updates planned receiver frequency without clearing telemetry`() {
        val serviceState = DashboardState.planned(
            workflow = "Rover + NTRIP",
            mountpoint = "TUBO00CZE0",
            receiver = "UM980",
            storage = "SAF folder",
            position = PositionCardState(latLon = "50.087451234, 14.421253456"),
            fix = FixCardState(
                fixType = "DGPS",
                receiverFrequency = DefaultUm980ReceiverFrequency,
            ),
        )
        val planned = DashboardState.planned(
            workflow = "Rover + NTRIP",
            mountpoint = "TUBO00CZE0",
            receiver = "u-blox M8T",
            storage = "SAF folder",
            fix = FixCardState(receiverFrequency = DefaultUbloxReceiverFrequency),
        )

        val merged = serviceState.withPlannedConfiguration(planned)

        assertEquals("50.087451234, 14.421253456", merged.position.latLon)
        assertEquals("DGPS", merged.fix.fixType)
        assertEquals(DefaultUbloxReceiverFrequency, merged.fix.receiverFrequency)
    }

    @Test
    fun `running service state ignores planned configuration during live broadcasts`() {
        val serviceState = DashboardState.running(
            status = DashboardStatus(
                workflow = "Recording workflow",
                mountpoint = "TUBO00CZE0",
                receiver = "UM980 runtime",
                storage = "Session folder",
            ),
            position = PositionCardState(latLon = "50.087451234, 14.421253456"),
            fix = FixCardState(fixType = "RTK fixed", receiverFrequency = "live 20 Hz"),
            ntrip = NtripCardState(status = "STREAMING"),
            files = FilesCardState(receiverRxBytes = "128 KiB"),
        )
        val planned = DashboardState.planned(
            workflow = "Planned workflow",
            mountpoint = "n/a",
            receiver = "Planned receiver",
            storage = "Planned storage",
            fix = FixCardState(receiverFrequency = DefaultUbloxReceiverFrequency),
        )

        val merged = serviceState.withPlannedConfiguration(planned)

        assertEquals("Recording workflow", merged.status.workflow)
        assertEquals("TUBO00CZE0", merged.status.mountpoint)
        assertEquals("UM980 runtime", merged.status.receiver)
        assertEquals("50.087451234, 14.421253456", merged.position.latLon)
        assertEquals("RTK fixed", merged.fix.fixType)
        assertEquals("live 20 Hz", merged.fix.receiverFrequency)
        assertEquals("128 KiB", merged.files.receiverRxBytes)
    }

    @Test
    fun `card states have dashboard friendly defaults`() {
        assertEquals("n/a", PositionCardState().latLon)
        assertEquals("Not configured", FixCardState().rtklibStatus)
        assertEquals("n/a", NtripCardState().status)
        assertEquals("0 B", FilesCardState().receiverRxBytes)
        assertFalse(FilesCardState().zipShareEnabled)
        assertEquals("No recording available yet", FilesCardState(zipShareEnabled = true).zipShareLabel)
        assertEquals("After stop", FilesCardState(sessionLocation = ".../session").zipShareLabel)
    }

    @Test
    fun `position display splits lat and lon for narrow layout`() {
        val lines = PositionCardState(latLon = "50.087451234, 14.421253456").latLonLinesForNarrowLayout()

        assertEquals(listOf("Lat 50.087451234", "Lon 14.421253456"), lines)
    }

    @Test
    fun `position display leaves missing value single line`() {
        assertEquals(listOf("n/a"), PositionCardState().latLonLinesForNarrowLayout())
    }

    @Test
    fun `position coordinate pair parses lat lon for actions`() {
        val coordinates = PositionCardState(latLon = "50.087451234, 14.421253456").coordinatePairOrNull()

        assertEquals(CoordinatePair("50.087451234", "14.421253456"), coordinates)
    }

    @Test
    fun `position coordinate pair ignores missing value`() {
        assertEquals(null, PositionCardState().coordinatePairOrNull())
    }

    @Test
    fun `coordinate copy formats are stable`() {
        val coordinates = CoordinatePair("50.087451234", "14.421253456")

        assertEquals("geo:50.087451234,14.421253456", CoordinateCopyFormat.GEO_URI.format(coordinates))
        assertEquals("50.087451234,14.421253456", CoordinateCopyFormat.LAT_LON.format(coordinates))
        assertEquals("50.087451234", CoordinateCopyFormat.LAT.format(coordinates))
        assertEquals("14.421253456", CoordinateCopyFormat.LON.format(coordinates))
    }

    @Test
    fun `base candidate includes dashboard height and source`() {
        val candidate = PositionCardState(
            latLon = "50.087451234, 14.421253456",
            ellipsoidalHeight = "321.456 m",
        ).baseCoordinateCandidateOrNull(source = "AVERAGE", sampleCount = 12)

        assertEquals("50.087451234, 14.421253456, h 321.456 m", candidate?.displayLabel())
        assertEquals(
            "MODE BASE 50.0874512340 14.4212534560 321.4560",
            candidate?.toUm980FixedBaseModeCommandOrNull(),
        )
        assertEquals(
            "{" +
                "\"latDeg\":50.087451234," +
                "\"lonDeg\":14.421253456," +
                "\"heightM\":321.456," +
                "\"frame\":\"UNKNOWN\"," +
                "\"method\":\"MANUAL_KNOWN_POINT\"," +
                "\"source\":\"AVERAGE\"," +
                "\"sampleCount\":12" +
                "}",
            candidate?.toManualBasePositionJsonOrNull(),
        )
    }

    @Test
    fun `base candidate requires ellipsoidal height and does not fall back to altitude`() {
        val candidate = PositionCardState(
            latLon = "50.087451234, 14.421253456",
            ellipsoidalHeight = "n/a",
            altitude = "287.000 m",
        ).baseCoordinateCandidateOrNull()

        assertEquals(null, candidate)
    }

    @Test
    fun `coordinate averaging starts and accumulates mean`() {
        val started = startCoordinateAveraging(
            sessionLocation = "/sessions/current",
            fixType = "RTK fixed",
            coordinates = CoordinatePair("50.0", "14.0"),
            ellipsoidalHeightM = 300.0,
        )
        val updated = started.addSample(
            sessionLocation = "/sessions/current",
            fixType = "RTK fixed",
            coordinates = CoordinatePair("52.0", "16.0"),
            ellipsoidalHeightM = 304.0,
        )

        assertTrue(updated.active)
        assertEquals(2, updated.sampleCount)
        assertEquals(51.0, updated.meanLat)
        assertEquals(15.0, updated.meanLon)
        assertEquals(302.0, updated.meanEllipsoidalHeightM)
        assertEquals("Avg 2x 51.0000000000, 15.0000000000, h 302.000 m", updated.statusLabel)
    }

    @Test
    fun `coordinate averaging stops when fix type changes`() {
        val started = startCoordinateAveraging(
            sessionLocation = "/sessions/current",
            fixType = "RTK fixed",
            coordinates = CoordinatePair("50.0", "14.0"),
            ellipsoidalHeightM = 300.0,
        )
        val stopped = started.addSample(
            sessionLocation = "/sessions/current",
            fixType = "DGPS",
            coordinates = CoordinatePair("50.1", "14.1"),
            ellipsoidalHeightM = 301.0,
        )

        assertFalse(stopped.active)
        assertEquals("Fix changed", stopped.stoppedReason)
        assertEquals("Fix changed · Avg 1x 50.0000000000, 14.0000000000, h 300.000 m", stopped.statusLabel)
    }

    @Test
    fun `coordinate averaging does not start without known fix`() {
        val started = startCoordinateAveraging(
            sessionLocation = "/sessions/current",
            fixType = "n/a",
            coordinates = CoordinatePair("50.0", "14.0"),
            ellipsoidalHeightM = 300.0,
        )

        assertFalse(started.active)
        assertEquals("No fix", started.stoppedReason)
    }

    @Test
    fun `coordinate averaging does not start without ellipsoidal height`() {
        val started = startCoordinateAveraging(
            sessionLocation = "/sessions/current",
            fixType = "RTK fixed",
            coordinates = CoordinatePair("50.0", "14.0"),
            ellipsoidalHeightM = null,
        )

        assertFalse(started.active)
        assertEquals("No ellipsoidal height", started.stoppedReason)
    }

    @Test
    fun `coordinate averaging does not start without active session`() {
        val started = startCoordinateAveraging(
            sessionLocation = "n/a",
            fixType = "RTK fixed",
            coordinates = CoordinatePair("50.0", "14.0"),
            ellipsoidalHeightM = 300.0,
        )

        assertFalse(started.active)
        assertEquals("No active session", started.stoppedReason)
    }

    @Test
    fun `coordinate averaging stops when session changes`() {
        val started = startCoordinateAveraging(
            sessionLocation = "/sessions/old",
            fixType = "RTK fixed",
            coordinates = CoordinatePair("50.0", "14.0"),
            ellipsoidalHeightM = 300.0,
        )
        val stopped = started.addSample(
            sessionLocation = "/sessions/new",
            fixType = "RTK fixed",
            coordinates = CoordinatePair("52.0", "16.0"),
            ellipsoidalHeightM = 304.0,
        )

        assertFalse(stopped.active)
        assertEquals("Session changed", stopped.stoppedReason)
        assertEquals(1, stopped.sampleCount)
    }

    @Test
    fun `error clipboard text includes category and full message`() {
        val state = DashboardState.planned(
            workflow = "Rover + NTRIP",
            mountpoint = "TUBO00CZE0",
            receiver = "UM980",
            storage = "App-private",
            lastError = "No static method writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;)",
            errorCategory = "SERVICE_LIFECYCLE",
        )

        assertEquals(
            "SERVICE_LIFECYCLE: No static method writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;)",
            state.errorClipboardText(),
        )
    }

    @Test
    fun `error clipboard text is null without an error`() {
        val state = DashboardState.planned(
            workflow = "Plain rover",
            mountpoint = "n/a",
            receiver = "UM980",
            storage = "App-private",
        )

        assertEquals(null, state.errorClipboardText())
    }
}
