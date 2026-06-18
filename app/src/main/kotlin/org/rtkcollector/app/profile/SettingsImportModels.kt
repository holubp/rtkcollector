package org.rtkcollector.app.profile

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONException
import org.json.JSONObject

const val MAX_SETTINGS_IMPORT_BYTES: Int = 2 * 1024 * 1024

data class SettingsImportSummary(
    val commandProfileCount: Int,
    val usbBaudProfileCount: Int,
    val ntripCasterProfileCount: Int,
    val ntripCasterUploadProfileCount: Int,
    val ntripMountpointProfileCount: Int,
    val recordingPolicyProfileCount: Int,
    val storageProfileCount: Int,
    val settingsSetCount: Int,
    val selectedSettingsSetId: String?,
    val selectedWorkflowId: String?,
    val lastActiveNtripMountpointProfileId: String?,
    val containsPlaintextPasswords: Boolean,
)

sealed class SettingsImportValidationResult {
    data object Loading : SettingsImportValidationResult()

    data class Valid(
        val backup: SettingsBackupFile,
        val summary: SettingsImportSummary,
    ) : SettingsImportValidationResult()

    data class Invalid(val message: String) : SettingsImportValidationResult()
}

fun validateSettingsImportJson(text: String): SettingsImportValidationResult {
    if (text.toByteArray(Charsets.UTF_8).size > MAX_SETTINGS_IMPORT_BYTES) {
        return SettingsImportValidationResult.Invalid("Settings backup is too large.")
    }

    val json = try {
        JSONObject(text)
    } catch (_: JSONException) {
        return SettingsImportValidationResult.Invalid("This JSON file is not a RtkCollector settings backup.")
    }

    if (json.optInt("formatVersion", 0) != SettingsBackupFile.CURRENT_FORMAT_VERSION) {
        return SettingsImportValidationResult.Invalid("Unsupported settings backup format version.")
    }

    val requiredArrays = listOf(
        "commandProfiles",
        "usbBaudProfiles",
        "ntripCasterProfiles",
        "ntripMountpointProfiles",
        "recordingPolicyProfiles",
        "storageProfiles",
        "settingsSets",
    )
    requiredArrays.forEach { key ->
        if (!json.has(key) || json.optJSONArray(key) == null) {
            return SettingsImportValidationResult.Invalid("Settings backup is missing $key.")
        }
    }

    if (json.has("plaintextPasswords") && !json.isNull("plaintextPasswords") && json.optJSONObject("plaintextPasswords") == null) {
        return SettingsImportValidationResult.Invalid("Settings backup contains invalid NTRIP password data.")
    }
    json.optJSONObject("plaintextPasswords")?.let { passwords ->
        val keys = passwords.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (passwords.opt(key) !is String) {
                return SettingsImportValidationResult.Invalid("Settings backup contains invalid NTRIP password data.")
            }
        }
    }

    val backup = runCatching { SettingsBackupFile.fromJson(json) }
        .getOrElse {
            return SettingsImportValidationResult.Invalid(
                it.message ?: "This JSON file is not a RtkCollector settings backup.",
            )
        }
    validateBackupReferences(backup)?.let { error ->
        return SettingsImportValidationResult.Invalid(error)
    }

    return SettingsImportValidationResult.Valid(
        backup = backup,
        summary = SettingsImportSummary(
            commandProfileCount = backup.commandProfiles.size,
            usbBaudProfileCount = backup.usbBaudProfiles.size,
            ntripCasterProfileCount = backup.ntripCasterProfiles.size,
            ntripCasterUploadProfileCount = backup.ntripCasterUploadProfiles.size,
            ntripMountpointProfileCount = backup.ntripMountpointProfiles.size,
            recordingPolicyProfileCount = backup.recordingPolicyProfiles.size,
            storageProfileCount = backup.storageProfiles.size,
            settingsSetCount = backup.settingsSets.size,
            selectedSettingsSetId = backup.selectedSettingsSetId,
            selectedWorkflowId = backup.selectedWorkflowId,
            lastActiveNtripMountpointProfileId = backup.lastActiveNtripMountpointProfileId,
            containsPlaintextPasswords = backup.plaintextPasswordsBySecretId.isNotEmpty(),
        ),
    )
}

private fun validateBackupReferences(backup: SettingsBackupFile): String? {
    duplicateId("command profile", backup.commandProfiles.map { it.id })?.let { return it }
    duplicateId("USB/baud profile", backup.usbBaudProfiles.map { it.id })?.let { return it }
    duplicateId("NTRIP caster profile", backup.ntripCasterProfiles.map { it.id })?.let { return it }
    duplicateId("NTRIP caster upload profile", backup.ntripCasterUploadProfiles.map { it.id })?.let { return it }
    duplicateId("NTRIP mountpoint profile", backup.ntripMountpointProfiles.map { it.id })?.let { return it }
    duplicateId("recording output profile", backup.recordingPolicyProfiles.map { it.id })?.let { return it }
    duplicateId("storage profile", backup.storageProfiles.map { it.id })?.let { return it }
    duplicateId("settings set", backup.settingsSets.map { it.id })?.let { return it }

    val commandIds = backup.commandProfiles.mapTo(mutableSetOf()) { it.id }
    val usbBaudIds = backup.usbBaudProfiles.mapTo(mutableSetOf()) { it.id }
    val casterIds = backup.ntripCasterProfiles.mapTo(mutableSetOf()) { it.id }
    val casterUploadIds = backup.ntripCasterUploadProfiles.mapTo(mutableSetOf()) { it.id }
    val mountpointIds = backup.ntripMountpointProfiles.mapTo(mutableSetOf()) { it.id }
    val recordingIds = backup.recordingPolicyProfiles.mapTo(mutableSetOf()) { it.id }
    val storageIds = backup.storageProfiles.mapTo(mutableSetOf()) { it.id }
    val settingsSetIds = backup.settingsSets.mapTo(mutableSetOf()) { it.id }

    backup.ntripMountpointProfiles.firstOrNull { it.casterProfileId !in casterIds }?.let {
        return "NTRIP mountpoint '${it.name}' references missing caster profile '${it.casterProfileId}'."
    }
    backup.settingsSets.forEach { settingsSet ->
        missingReference(settingsSet.commandProfileRef.id, commandIds, settingsSet.name, "command profile")?.let { return it }
        missingReference(settingsSet.usbBaudProfileRef.id, usbBaudIds, settingsSet.name, "USB/baud profile")?.let { return it }
        settingsSet.ntripCasterProfileRef?.id
            ?.let { missingReference(it, casterIds, settingsSet.name, "NTRIP caster profile") }
            ?.let { return it }
        settingsSet.ntripMountpointProfileRef?.id
            ?.let { missingReference(it, mountpointIds, settingsSet.name, "NTRIP mountpoint profile") }
            ?.let { return it }
        settingsSet.ntripCasterUploadProfileRef?.id
            ?.let { missingReference(it, casterUploadIds, settingsSet.name, "NTRIP caster upload profile") }
            ?.let { return it }
        missingReference(
            settingsSet.recordingOutputProfileRef.id,
            recordingIds,
            settingsSet.name,
            "recording output profile",
        )?.let { return it }
        missingReference(settingsSet.storageProfileRef.id, storageIds, settingsSet.name, "storage profile")?.let { return it }
    }
    backup.selectedSettingsSetId?.let { selectedId ->
        if (selectedId !in settingsSetIds) {
            return "Selected settings set '$selectedId' is not present in this backup."
        }
    }
    backup.lastActiveNtripMountpointProfileId?.let { selectedId ->
        if (selectedId !in mountpointIds) {
            return "Last active NTRIP mountpoint '$selectedId' is not present in this backup."
        }
    }

    val knownSecretIds = backup.ntripCasterProfiles.mapNotNullTo(mutableSetOf()) { it.secretId.takeIf(String::isNotBlank) }
    backup.ntripCasterUploadProfiles.mapNotNullTo(knownSecretIds) { it.secretId.takeIf(String::isNotBlank) }
    backup.settingsSets.mapNotNullTo(knownSecretIds) { it.overrides.ntripCaster?.secretId?.takeIf(String::isNotBlank) }
    backup.settingsSets.mapNotNullTo(knownSecretIds) { it.overrides.ntripCasterUpload?.secretId?.takeIf(String::isNotBlank) }
    backup.plaintextPasswordsBySecretId.keys.firstOrNull { it !in knownSecretIds }?.let { secretId ->
        return "Plaintext NTRIP password references unknown secret '$secretId'."
    }

    return null
}

private fun duplicateId(label: String, ids: List<String>): String? {
    val seen = mutableSetOf<String>()
    ids.forEach { id ->
        if (!seen.add(id)) return "Duplicate $label id '$id' in settings backup."
    }
    return null
}

private fun missingReference(id: String, knownIds: Set<String>, settingsSetName: String, label: String): String? =
    if (id in knownIds) {
        null
    } else {
        "Settings set '$settingsSetName' references missing $label '$id'."
    }

fun readSettingsImportText(
    resolver: ContentResolver,
    uri: Uri,
    maxBytes: Int = MAX_SETTINGS_IMPORT_BYTES,
): String {
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > maxBytes) error("Settings backup is too large.")
        }
        output.toByteArray()
    } ?: error("Settings backup could not be read.")
    return bytes.toString(Charsets.UTF_8)
}
