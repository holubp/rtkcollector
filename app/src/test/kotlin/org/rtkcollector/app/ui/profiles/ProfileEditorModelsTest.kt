package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileEditorModelsTest {
    @Test
    fun `command profile editor exposes persistent write action with warning text`() {
        val action = persistentReceiverWriteAction(onClick = {})

        assertEquals("Write init config persistently to device", action.label)
        assertTrue(action.warningTitle.orEmpty().contains("receiver", ignoreCase = true))
        assertTrue(action.warningBody.orEmpty().contains("non-volatile", ignoreCase = true))
        assertTrue(action.warningBody.orEmpty().contains("other apps", ignoreCase = true))
        assertEquals("Write persistently", action.confirmLabel)
    }

    @Test
    fun `usb baud editor exposes persistent target baud write action with warning text`() {
        val action = persistentBaudWriteAction(
            initialBaud = 230400,
            targetBaud = 460800,
            usbDeviceLabel = "FTDI UM980 0403:6015",
            onClick = {},
        )

        assertEquals("Write target baud persistently to device", action.label)
        assertTrue(action.warningTitle.orEmpty().contains("baud", ignoreCase = true))
        assertTrue(action.warningBody.orEmpty().contains("230400"))
        assertTrue(action.warningBody.orEmpty().contains("460800"))
        assertTrue(action.warningBody.orEmpty().contains("FTDI UM980 0403:6015"))
        assertTrue(action.warningBody.orEmpty().contains("UM980"))
        assertEquals("Write persistently", action.confirmLabel)
    }

    @Test
    fun `command persistent warning mentions active recording connection`() {
        val action = persistentReceiverWriteAction(onClick = {})

        assertTrue(action.warningBody.orEmpty().contains("active recording connection", ignoreCase = true))
    }
}
