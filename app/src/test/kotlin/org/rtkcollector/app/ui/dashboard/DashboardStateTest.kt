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
        assertEquals(listOf(DashboardAction("Menu", DashboardActionKind.MENU)), state.secondaryActions)
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
    }

    @Test
    fun `running session keeps stop visible`() {
        val state = DashboardState.running(
            status = DashboardStatus("Rover + NTRIP", "TUBO00CZE0", "UM980", "SAF folder"),
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
                DashboardAction("Mark", DashboardActionKind.MARK),
            ),
            state.secondaryActions,
        )
    }

    @Test
    fun `card states have dashboard friendly defaults`() {
        assertEquals("n/a", PositionCardState().latLon)
        assertEquals("Not configured", FixCardState().rtklibStatus)
        assertEquals("n/a", NtripCardState().status)
        assertEquals("0 B", FilesCardState().receiverRxBytes)
        assertFalse(FilesCardState().zipShareEnabled)
    }
}
