package org.rtkcollector.app.ui.profiles

import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.effectiveCommandProfileRef
import org.rtkcollector.app.profile.effectiveNtripMountpointProfileRef
import org.rtkcollector.app.profile.effectiveStorageProfileRef

enum class ProfileRowTone {
    DEFAULT,
    APPLIED,
    MODIFIED,
    WARNING,
}

data class ProfileListRow(
    val id: String,
    val name: String,
    val isProtected: Boolean,
    val hasLocalOverrides: Boolean,
    val isSelected: Boolean = false,
    val summary: String = "",
    val warningText: String? = null,
    val outsideFilter: Boolean = false,
) {
    val displayName: String = if (hasLocalOverrides) "$name +" else name
    val tone: ProfileRowTone
        get() = when {
            outsideFilter -> ProfileRowTone.WARNING
            hasLocalOverrides -> ProfileRowTone.MODIFIED
            isSelected -> ProfileRowTone.APPLIED
            else -> ProfileRowTone.DEFAULT
        }
    val displaySummary: String = if (outsideFilter) {
        listOf("Outside filter", summary).filter(String::isNotBlank).joinToString(" · ")
    } else {
        summary
    }
    val canEdit: Boolean = !isProtected
    val canViewDetails: Boolean = true
    val editActionLabel: String = if (isProtected) "View" else "Edit"
    val canRename: Boolean = !isProtected
    val canCopy: Boolean = true
    val canDelete: Boolean = !isProtected || hasLocalOverrides
    val hasWarning: Boolean get() = outsideFilter || !warningText.isNullOrBlank()
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
        set.effectiveCommandProfileRef().name,
        set.effectiveNtripMountpointProfileRef()?.name ?: "No NTRIP mountpoint",
        set.effectiveStorageProfileRef().name,
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
    val helperText: String? = null,
    val sourceUploadUsername: Boolean = false,
    val casterUploadSafety: Boolean = false,
) {
    val hasError: Boolean get() = !errorText.isNullOrBlank()
    val hasHelper: Boolean get() = !helperText.isNullOrBlank()
}

fun EditableProfileField.withRuntimeProfileValidation(values: Map<String, String>): EditableProfileField {
    val currentValue = values[key] ?: value
    return when (key) {
        "mountpoint" -> withRuntimeMountpointValidation(values, currentValue)
        "username" -> if (sourceUploadUsername) {
            withRuntimeSourceUploadUsernameState(values, currentValue)
        } else {
            copy(value = currentValue)
        }
        "safetyRulesEnabled" -> if (casterUploadSafety) {
            withRuntimeCasterUploadSafetyState(values, currentValue)
        } else {
            copy(value = currentValue)
        }
        else -> copy(value = currentValue)
    }
}

fun canSaveProfileEditor(fields: List<EditableProfileField>): Boolean =
    fields.none { it.hasError }

private fun EditableProfileField.withRuntimeMountpointValidation(
    values: Map<String, String>,
    currentValue: String,
): EditableProfileField {
    val selectedCasterId = values["casterProfileId"].orEmpty()
    val runtimeOptions = optionGroups[selectedCasterId] ?: optionItems
    val knownMountpoints = runtimeOptions.map { it.value }.filter(String::isNotBlank)
    val runtimeError = currentValue
        .takeIf(String::isNotBlank)
        ?.takeIf { knownMountpoints.isNotEmpty() && it !in knownMountpoints }
        ?.let { "Mountpoint is not in the selected caster sourcetable." }
    return copy(value = currentValue, optionItems = runtimeOptions, errorText = runtimeError)
}

private fun EditableProfileField.withRuntimeSourceUploadUsernameState(
    values: Map<String, String>,
    currentValue: String,
): EditableProfileField {
    val protocolPolicy = values["protocolPolicy"].orEmpty()
    return if (protocolPolicy == "NTRIP_V1_ONLY") {
        copy(
            label = "Username (not used for NTRIP v1 source upload)",
            value = currentValue,
            readOnly = true,
            helperText = "Kept for switching back to v2; not sent in the v1 SOURCE request.",
        )
    } else {
        copy(
            label = "Username",
            value = currentValue,
            readOnly = false,
            helperText = null,
        )
    }
}

private fun EditableProfileField.withRuntimeCasterUploadSafetyState(
    values: Map<String, String>,
    currentValue: String,
): EditableProfileField {
    val host = values["host"].orEmpty()
    return if (host.isRtk2goUploadHost()) {
        copy(
            label = "RTK2go safety rules (required for RTK2go)",
            value = "true",
            readOnly = true,
            helperText = "Safety rules are enforced for RTK2go hosts.",
        )
    } else {
        copy(
            label = "RTK2go safety rules",
            value = currentValue,
            readOnly = false,
            helperText = null,
        )
    }
}

private fun String.isRtk2goUploadHost(): Boolean =
    trim().lowercase() in setOf("rtk2go.com", "www.rtk2go.com")

data class ProfileEditorData(
    val title: String,
    val fields: List<EditableProfileField>,
    val readOnly: Boolean = false,
    val warningText: String? = null,
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
