package org.rtkcollector.app.profile

/** Owns transient answers consumed by one recording, including published live choices; never serialized. */
class StartSelectionLease internal constructor(
    val selections: ActiveSetupSelections,
    val selectionRevision: Long,
    terminal: () -> Unit,
) {
    private var finished = false
    private val completions = mutableListOf(terminal)

    internal fun adopt(consumed: StartSelectionLease) {
        val completeNow = synchronized(this) {
            if (finished) true else {
                completions += consumed::finish
                false
            }
        }
        if (completeNow) consumed.finish()
    }

    fun finish() {
        val pending = synchronized(this) {
            if (finished) return
            finished = true
            completions.toList().also { completions.clear() }
        }
        pending.forEach { it() }
    }
}
