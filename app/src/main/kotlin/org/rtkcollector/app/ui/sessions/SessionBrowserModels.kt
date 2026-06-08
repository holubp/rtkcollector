package org.rtkcollector.app.ui.sessions

data class SessionListItem(
    val sessionId: String,
    val title: String,
    val subtitle: String,
    val isActive: Boolean,
)

data class SessionFileItem(
    val name: String,
    val location: String,
    val sizeText: String,
    val shareable: Boolean,
)

data class SessionDetailState(
    val sessionId: String,
    val location: String,
    val files: List<SessionFileItem>,
    val canShareZip: Boolean,
)
