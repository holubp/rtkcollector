package org.rtkcollector.app.alarms

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler

/** All Android queries and callbacks belong to the alarm worker, never capture. */
internal class AlarmAudioDevices(private val manager: AudioManager, handler: Handler,
    private val query: () -> List<AudioDeviceInfo> = { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }) : AutoCloseable {
    private var revision = 0L
    private var closed = false
    private var outputs = emptyList<AudioDeviceInfo>()
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { refreshSafely() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { refreshSafely() }
    }
    init { refresh(); manager.registerAudioDeviceCallback(callback, handler) }
    val version: Long get() = revision
    val devices: List<AlarmRouteDevice> get() = outputs.map {
        val bluetooth = it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= 28 && it.type == AudioDeviceInfo.TYPE_HEARING_AID) ||
            (Build.VERSION.SDK_INT >= 31 && (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
            (Build.VERSION.SDK_INT >= 33 && it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
        AlarmRouteDevice(it.id, when {
            bluetooth -> AlarmRouteKind.BLUETOOTH
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AlarmRouteKind.SPEAKER
            else -> AlarmRouteKind.OTHER
        }, if (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) 1 else 0)
    }
    fun find(id: Int): AudioDeviceInfo? = outputs.firstOrNull { it.id == id }
    private fun refreshSafely() {
        if (closed) return
        runCatching { refresh() }.onFailure {
            outputs = runCatching { outputs.filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } }.getOrDefault(emptyList())
            revision++
        }
    }
    private fun refresh() {
        val next = query()
        if (next.map { it.id to it.type } != outputs.map { it.id to it.type }) { outputs = next; revision++ }
    }
    override fun close() { closed = true; manager.unregisterAudioDeviceCallback(callback) }
}
