package org.rtkcollector.app.ui.profiles

import org.rtkcollector.app.profile.RecordingSettingsSet

data class ProfileListRow(
    val id: String,
    val name: String,
    val isProtected: Boolean,
    val hasLocalOverrides: Boolean,
    val isSelected: Boolean = false,
) {
    val displayName: String = if (hasLocalOverrides) "$name + local changes" else name
    val canEdit: Boolean = !isProtected
    val canRename: Boolean = !isProtected
    val canCopy: Boolean = true
    val canDelete: Boolean = !isProtected || hasLocalOverrides
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
                    )
                },
            )
    }
}

data class EditableProfileField(
    val key: String,
    val label: String,
    val value: String,
    val multiline: Boolean = false,
    val secret: Boolean = false,
    val boolean: Boolean = false,
    val options: List<String> = emptyList(),
    val optionItems: List<EditableProfileOption> = options.map { EditableProfileOption(it, it) },
    val readOnly: Boolean = false,
    val readOnlyList: List<String> = emptyList(),
)

data class ProfileEditorData(
    val title: String,
    val fields: List<EditableProfileField>,
)

data class ProfileEditorAction(
    val label: String,
    val onClick: () -> Unit,
)

data class EditableProfileOption(
    val value: String,
    val label: String,
)
