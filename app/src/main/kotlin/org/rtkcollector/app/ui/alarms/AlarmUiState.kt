package org.rtkcollector.app.ui.alarms

/** One broadcast snapshot, independent of high-rate recording/dashboard state. */
data class AlarmUiState(
    val generation: Long = -1,
    val message: String? = null,
    val terminalPending: Boolean = false,
    val tokens: List<String> = emptyList(),
    val delivery: String? = null,
) {
    val hasContent: Boolean get() = message != null || terminalPending || delivery != null || tokens.isNotEmpty()
    val hasActions: Boolean get() = tokens.isNotEmpty() || terminalPending
    fun accept(next: AlarmUiState): AlarmUiState = if (next.generation >= generation) next else this
}
