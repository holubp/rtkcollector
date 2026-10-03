package org.rtkcollector.app.ui.profiles

fun uploadSelectionLabel(
    applicable: Boolean,
    enabled: Boolean?,
    profileName: String?,
): String = when {
    !applicable -> "Not needed"
    enabled == null -> "Select"
    !enabled -> "Off"
    profileName.isNullOrBlank() -> "Missing profile"
    else -> profileName
}
