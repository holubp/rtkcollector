package org.rtkcollector.app.ui.profiles

data class UnsavedEditorState(
    val savedFingerprint: String,
    val currentFingerprint: String,
) {
    val hasUnsavedChanges: Boolean
        get() = savedFingerprint != currentFingerprint

    val canLeaveWithoutPrompt: Boolean
        get() = !hasUnsavedChanges
}

fun profileEditorFingerprint(values: Map<String, String>): String =
    values.toSortedMap().entries.joinToString(separator = "") { (key, value) ->
        "${key.length}:$key=${value.length}:$value;"
    }
