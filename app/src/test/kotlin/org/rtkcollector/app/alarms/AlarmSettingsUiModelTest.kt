package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rtkcollector.app.ui.alarms.AlarmSettingsUiModel

class AlarmSettingsUiModelTest {
    @Test fun `independent controls preserve other categories and sound`() {
        val defaults = AlarmSettingsSnapshot.defaults()
        val system = AlarmSoundChoice.SystemNotification("content://settings/system/notification_sound")
        val chosen = AlarmSettingsUiModel(defaults).sound(AlarmCategory.POSITION_LOST, system)
        val disabled = AlarmSettingsUiModel(chosen).acoustic(AlarmCategory.POSITION_LOST, false)
        assertEquals(system, disabled.categories.getValue(AlarmCategory.POSITION_LOST).sound)
        assertTrue(disabled.categories.getValue(AlarmCategory.POSITION_LOST).visual)
        assertEquals(defaults.categories.getValue(AlarmCategory.CONNECTION_LOST), disabled.categories.getValue(AlarmCategory.CONNECTION_LOST))
        assertEquals(60_000L, AlarmSettingsUiModel(disabled).delaySeconds(60).positionLossDelayMillis)
        assertThrows(IllegalArgumentException::class.java) { AlarmSettingsUiModel(disabled).delaySeconds(0) }
    }
}
