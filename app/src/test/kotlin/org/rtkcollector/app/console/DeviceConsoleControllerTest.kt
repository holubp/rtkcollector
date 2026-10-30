package org.rtkcollector.app.console

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.transport.SerialTransport
import java.util.concurrent.CopyOnWriteArrayList

class DeviceConsoleControllerTest {
    @Test
    fun `console cannot open while stopped UI still has a service-owned session`() {
        val location = "console-stopping-session"
        val transport = FakeTransport()
        val controller = DeviceConsoleController({ false }, { transport }, {})
        org.rtkcollector.app.sessions.ActiveRecordingSessionRegistry.activate(location)
        try {
            assertFalse(controller.connect().isSuccess)
            assertFalse(transport.opened)
        } finally {
            controller.disconnect()
            org.rtkcollector.app.sessions.ActiveRecordingSessionRegistry.deactivate(location)
        }
    }

    @Test
    fun `send rechecks recording ownership rather than using connect-time state`() {
        var recording = false
        val transport = FakeTransport()
        val controller = DeviceConsoleController({ recording }, { transport }, {})
        try {
            assertTrue(controller.connect().isSuccess)
            recording = true
            assertFalse(controller.send("MODE BASE", DeviceConsoleLineEnding.CRLF).isSuccess)
            assertTrue(transport.written.isEmpty())
        } finally { controller.disconnect() }
    }
    @Test
    fun `connect is rejected while recording is active`() {
        val transport = FakeTransport()
        val states = CopyOnWriteArrayList<DeviceConsoleState>()
        val controller = DeviceConsoleController(
            recordingActive = { true },
            transportFactory = { transport },
            stateListener = states::add,
        )

        val result = controller.connect()

        assertFalse(result.isSuccess)
        assertFalse(transport.opened)
        assertEquals("Stop recording before opening the device console.", controller.state.lastError)
    }

    @Test
    fun `send writes input plus selected line ending`() {
        val transport = FakeTransport()
        val controller = DeviceConsoleController(
            recordingActive = { false },
            transportFactory = { transport },
            stateListener = {},
        )

        assertTrue(controller.connect().isSuccess)
        assertTrue(controller.send("VERSION", DeviceConsoleLineEnding.CRLF).isSuccess)

        assertEquals("VERSION\r\n", transport.written.decodeToString())
    }

    @Test
    fun `disconnect closes transport`() {
        val transport = FakeTransport()
        val controller = DeviceConsoleController(
            recordingActive = { false },
            transportFactory = { transport },
            stateListener = {},
        )

        controller.connect()
        controller.disconnect()

        assertFalse(transport.isOpen)
    }

    private class FakeTransport : SerialTransport {
        var opened = false
        var written = ByteArray(0)
        override val isOpen: Boolean
            get() = opened

        override fun open() {
            opened = true
        }

        override fun close() {
            opened = false
        }

        override fun readAvailable(maxBytes: Int): ByteArray = byteArrayOf()

        override fun write(bytes: ByteArray) {
            written += bytes
        }
    }
}
