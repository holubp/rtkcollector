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
}
