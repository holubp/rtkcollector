package org.rtkcollector.app.ui.profiles

import org.rtkcollector.app.profile.RecordingSettingsSet

data class ProfileListRow(
    val id: String,
    val name: String,
    val isProtected: Boolean,
    val hasLocalOverrides: Boolean,
    val isSelected: Boolean = false,
    val summary: String = "",
    val warningText: String? = null,
) {
    val displayName: String = if (hasLocalOverrides) "$name + local changes" else name
    val canEdit: Boolean = !isProtected
    val canRename: Boolean = !isProtected
    val canCopy: Boolean = true
    val canDelete: Boolean = !isProtected || hasLocalOverrides
    val hasWarning: Boolean get() = !warningText.isNullOrBlank()
}

data class SettingsSetListState(
    val rows: List<ProfileListRow>,
) {
    companion object {
        fun from(settingsSets: List<RecordingSettingsSet>, selectedId: String): SettingsSetListState =
            SettingsSetListState(
                rows = settingsSets.map { set ->
                    ProfileListRow(
                        id = set.id,
                        name = set.name,
                        isProtected = set.isProtected,
                        hasLocalOverrides = set.hasLocalOverrides,
                        isSelected = set.id == selectedId,
                        summary = settingsSetSummary(set),
                    )
                },
            )
    }
}

private fun settingsSetSummary(set: RecordingSettingsSet): String =
    listOf(
        set.workflowId,
        set.commandProfileRef.name,
        set.ntripMountpointProfileRef?.name ?: "No NTRIP mountpoint",
        set.storageProfileRef.name,
    ).joinToString(" · ")

data class EditableProfileField(
    val key: String,
    val label: String,
    val value: String,
    val multiline: Boolean = false,
    val secret: Boolean = false,
    val boolean: Boolean = false,
    val options: List<String> = emptyList(),
    val optionItems: List<EditableProfileOption> = options.map { EditableProfileOption(it, it) },
    val optionGroups: Map<String, List<EditableProfileOption>> = emptyMap(),
    val readOnly: Boolean = false,
    val readOnlyList: List<String> = emptyList(),
    val errorText: String? = null,
) {
    val hasError: Boolean get() = !errorText.isNullOrBlank()
}

fun EditableProfileField.withRuntimeProfileValidation(values: Map<String, String>): EditableProfileField {
    if (key != "mountpoint") return this
    val selectedCasterId = values["casterProfileId"].orEmpty()
    val currentMountpoint = values[key].orEmpty()
    val runtimeOptions = optionGroups[selectedCasterId] ?: optionItems
    val knownMountpoints = runtimeOptions.map { it.value }.filter(String::isNotBlank)
    val runtimeError = currentMountpoint
        .takeIf(String::isNotBlank)
        ?.takeIf { knownMountpoints.isNotEmpty() && it !in knownMountpoints }
        ?.let { "Mountpoint is not in the selected caster sourcetable." }
    return copy(optionItems = runtimeOptions, errorText = runtimeError)
}

fun canSaveProfileEditor(fields: List<EditableProfileField>): Boolean =
    fields.none { it.hasError }

data class ProfileEditorData(
    val title: String,
    val fields: List<EditableProfileField>,
)

data class ProfileEditorAction(
    val label: String,
    val onClick: () -> Unit,
    val onClickWithValues: ((Map<String, String>) -> Unit)? = null,
    val destructive: Boolean = false,
    val warningTitle: String? = null,
    val warningBody: String? = null,
    val confirmLabel: String = label,
)

data class EditableProfileOption(
    val value: String,
    val label: String,
)
