package org.rtkcollector.app.recording

internal data class UsbConsentTicket(val generation: Long, val deviceId: Int, val nonce: Long)

/** No Android calls under this lock; callbacks cannot revive stopped or replaced attachments. */
internal class UsbRecoveryConsent {
    private var generation: Long? = null
    private var deviceId: Int? = null
    private var sequence = 0L
    private var requested = false
    private var pending: UsbConsentTicket? = null
    var denied: Boolean = false
        @Synchronized get
        private set

    @Synchronized fun begin(generation: Long) { stop(); this.generation = generation }
    @Synchronized fun stop() { generation = null; deviceId = null; requested = false; pending = null; denied = false }
    @Synchronized fun observe(deviceId: Int?) {
        if (this.deviceId == deviceId) return
        this.deviceId = deviceId; requested = false; pending = null; denied = false
    }
    @Synchronized fun detached(deviceId: Int) { if (this.deviceId == deviceId) observe(null) }
    @Synchronized fun accessAvailable() { pending = null; requested = true; denied = false }
    @Synchronized fun request(): UsbConsentTicket? {
        val generation = generation ?: return null
        val device = deviceId ?: return null
        if (requested) return null
        requested = true
        return UsbConsentTicket(generation, device, ++sequence).also { pending = it }
    }
    @Synchronized fun isCurrent(ticket: UsbConsentTicket): Boolean = pending == ticket && generation == ticket.generation
    @Synchronized fun complete(ticket: UsbConsentTicket, granted: Boolean): Boolean {
        if (!isCurrent(ticket)) return false
        pending = null; denied = !granted
        return true
    }
}
