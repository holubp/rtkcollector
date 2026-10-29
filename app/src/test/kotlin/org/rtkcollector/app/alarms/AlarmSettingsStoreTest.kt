package org.rtkcollector.app.alarms

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmSettingsStoreTest {
    @Test fun `settings survive recreation with independent outputs and selected sound`() {
        val context = RuntimeEnvironment.getApplication()
        val store = AlarmSettingsStore(context)
        val defaults = store.read()
        assertTrue(defaults.categories.values.all { it.visual && it.acoustic })
        val position = defaults.categories.getValue(AlarmCategory.POSITION_LOST)
        val changed = defaults.copy(positionLossDelayMillis = 9_000,
            categories = defaults.categories + (AlarmCategory.POSITION_LOST to position.copy(
                visual = false, sound = AlarmSoundChoice.SystemNotification("content://settings/system/notification_sound"))))
        store.write(changed)
        assertEquals(changed, AlarmSettingsStore(context).read())
        var calls = 0
        val subscription = store.observeChanges { calls++ }
        store.write(defaults)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(calls > 0)
        subscription.close()
    }
}
