package org.rtkcollector.app.alarms

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 27, 33])
class AlarmSettingsStoreTest {
    @Test fun `old settings retain normal routing and Bluetooth choice survives recreation`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("rtkcollector-alarms", 0).edit().clear()
            .putString("snapshot", "{\"delay\":9000}").commit()
        val store = AlarmSettingsStore(context)
        val legacy = store.read()
        assertFalse(legacy.preferBluetoothAudio)
        assertEquals(9_000L, legacy.positionLossDelayMillis)
        store.write(legacy.copy(preferBluetoothAudio = true))
        assertTrue(AlarmSettingsStore(context).read().preferBluetoothAudio)
        assertEquals(legacy.categories, AlarmSettingsStore(context).read().categories)
    }
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
