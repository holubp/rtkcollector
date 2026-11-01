package org.rtkcollector.app.recording

import java.util.UUID

data class StopConfirmationTiming(val delaySeconds: Int = 2, val timeoutSeconds: Int = 10) {
    init { require(delaySeconds in 1..60); require(timeoutSeconds in 1..120) }
}

enum class StopConfirmationPhase { WAITING, ACTIVE, EXPIRED }

data class StopConfirmationWindow(val openedAtMillis: Long, val timing: StopConfirmationTiming) {
    private val activeAt = openedAtMillis + timing.delaySeconds * 1000L
    private val expiresAt = activeAt + timing.timeoutSeconds * 1000L
    fun phase(nowMillis: Long): StopConfirmationPhase = when {
        nowMillis < activeAt -> StopConfirmationPhase.WAITING
        nowMillis < expiresAt -> StopConfirmationPhase.ACTIVE
        else -> StopConfirmationPhase.EXPIRED
    }
    fun remainingSeconds(nowMillis: Long): Int = when (phase(nowMillis)) {
        StopConfirmationPhase.WAITING -> ((activeAt - nowMillis + 999) / 1000).toInt()
        StopConfirmationPhase.ACTIVE -> ((expiresAt - nowMillis + 999) / 1000).toInt()
        StopConfirmationPhase.EXPIRED -> 0
    }
}

data class StopConfirmationRequest(val requestId: String, val ownerId: String,
    val controlId: String, val window: StopConfirmationWindow)

/** Service-owned identity; UI can request a dialog, but cannot choose its owner. */
open class StopConfirmationAuthority {
    private var owner: String? = null
    private var request: StopConfirmationRequest? = null
    private var event: (String, StopConfirmationRequest) -> Unit = { _, _ -> }

    @Synchronized fun activate(ownerId: String, onEvent: (String, StopConfirmationRequest) -> Unit = { _, _ -> }) {
        require(ownerId.isNotBlank())
        request?.let { emit("owner-changed", it) }
        request = null; owner = ownerId; event = onEvent
    }
    @Synchronized fun retire(ownerId: String) {
        if (owner != ownerId) return
        request?.let { emit("owner-retired", it) }
        request = null; owner = null; event = { _, _ -> }
    }
    @Synchronized fun pending(): StopConfirmationRequest? = request
    @Synchronized fun open(controlId: String, nowMillis: Long, timing: StopConfirmationTiming): StopConfirmationRequest? {
        val current = owner ?: return null
        request?.let { return it }
        return StopConfirmationRequest(UUID.randomUUID().toString(), current, controlId.take(40),
            StopConfirmationWindow(nowMillis, timing)).also { request = it; emit("opened", it) }
    }
    @Synchronized fun cancel(requestId: String, outcome: String = "cancelled") {
        val current = request?.takeIf { it.requestId == requestId } ?: return
        request = null; emit(outcome, current)
    }
    @Synchronized fun consume(requestId: String?, nowMillis: Long, ownerId: String?): Boolean {
        val current = request ?: return false
        if (current.requestId != requestId || current.ownerId != ownerId || owner != ownerId) return false
        if (current.window.phase(nowMillis) != StopConfirmationPhase.ACTIVE) return false
        request = null; emit("confirmed", current)
        return true
    }
    private fun emit(outcome: String, value: StopConfirmationRequest) {
        runCatching { event(outcome, value) }
    }
}

object RecordingStopConfirmation : StopConfirmationAuthority()
