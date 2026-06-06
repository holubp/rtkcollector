package org.rtkcollector.core.quality

import org.rtkcollector.receiver.api.QualityEvent
import org.rtkcollector.receiver.api.SolutionEvent

data class LiveQualitySnapshot(
    val latestSolution: SolutionEvent? = null,
    val events: List<QualityEvent> = emptyList(),
)
