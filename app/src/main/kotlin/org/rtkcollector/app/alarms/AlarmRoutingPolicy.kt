package org.rtkcollector.app.alarms

enum class AlarmRouteKind { BLUETOOTH, SPEAKER, OTHER }
data class AlarmRouteDevice(val id: Int, val kind: AlarmRouteKind, val priority: Int = 0)
data class AlarmRouteDecision(val deviceId: Int?, val confirmed: Boolean, val fallback: Boolean,
    val failed: Boolean, val label: String, val warning: String? = null)

/** A requested route is not evidence of playback. All timing uses monotonic receipt time. */
class AlarmRoutingPolicy {
    private var availability = Long.MIN_VALUE
    private val rejected = linkedSetOf<Int>()
    private var bluetoothFailed = false
    private var speakerFailed = false
    private var target: AlarmRouteDevice? = null
    private var checkingSince: Long? = null

    fun reset() {
        availability = Long.MIN_VALUE; rejected.clear(); bluetoothFailed = false
        speakerFailed = false; target = null; checkingSince = null
    }
    fun reject(deviceId: Int, kind: AlarmRouteKind? = null) {
        if (rejected.size < 16) rejected.add(deviceId)
        when (kind ?: target?.takeIf { it.id == deviceId }?.kind) {
            AlarmRouteKind.BLUETOOTH -> bluetoothFailed = true
            AlarmRouteKind.SPEAKER -> speakerFailed = true
            else -> Unit
        }
        target = null; checkingSince = null
    }
    fun decide(enabled: Boolean, devices: List<AlarmRouteDevice>, actualDeviceId: Int?, now: Long,
        availabilityVersion: Long, playing: Boolean = true): AlarmRouteDecision {
        if (!enabled) {
            reset()
            return AlarmRouteDecision(null, true, false, false, "System audio")
        }
        if (availability != availabilityVersion) {
            availability = availabilityVersion; rejected.clear(); bluetoothFailed = false
            speakerFailed = false; checkingSince = null
        }
        val available = devices.distinctBy { it.id }.filter { it.id !in rejected }
        val bluetooth = if (bluetoothFailed) emptyList() else available.filter { it.kind == AlarmRouteKind.BLUETOOTH }
        val chosen = bluetooth.firstOrNull { it.id == target?.id }
            ?: bluetooth.firstOrNull { it.id == actualDeviceId }
            ?: bluetooth.minWithOrNull(compareBy<AlarmRouteDevice> { it.priority }.thenBy { it.id })
            ?: available.filter { !speakerFailed && it.kind == AlarmRouteKind.SPEAKER }.minByOrNull { it.id }
            ?: return AlarmRouteDecision(null, false, true, true, "Alarm audio unavailable",
                "Phone speaker unavailable for alarm playback.")
        if (chosen != target) { target = chosen; checkingSince = null }
        val confirmed = playing && chosen.id == actualDeviceId
        if (confirmed || !playing) checkingSince = null
        else {
            val since = checkingSince ?: now.also { checkingSince = it }
            if (now - since >= 500) {
                reject(chosen.id)
                return decide(enabled, devices, actualDeviceId, now, availabilityVersion, playing)
            }
        }
        val fallback = chosen.kind == AlarmRouteKind.SPEAKER
        val label = if (fallback) "Phone speaker" else "Bluetooth audio"
        return AlarmRouteDecision(chosen.id, confirmed, fallback, false,
            if (confirmed) label else if (fallback) "Switching to phone speaker" else "Switching to Bluetooth audio",
            if (fallback && bluetoothFailed) "Bluetooth audio unavailable; using phone speaker." else null)
    }
}
