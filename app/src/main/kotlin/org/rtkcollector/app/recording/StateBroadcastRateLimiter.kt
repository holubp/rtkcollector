package org.rtkcollector.app.recording

internal class StateBroadcastRateLimiter(
    private val minIntervalMillis: Long,
) {
    private var lastBroadcastAtMillis: Long? = null

    fun shouldBroadcast(nowMillis: Long): Boolean {
        val last = lastBroadcastAtMillis
        if (last != null && nowMillis - last < minIntervalMillis) {
            return false
        }
        lastBroadcastAtMillis = nowMillis
        return true
    }

    fun reset() {
        lastBroadcastAtMillis = null
    }
}
