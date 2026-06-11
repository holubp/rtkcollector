package org.rtkcollector.app.profile

import org.json.JSONArray
import org.json.JSONObject

data class SettingsBackupFile(
    val formatVersion: Int,
    val exportedAtEpochMillis: Long,
    val commandProfiles: List<CommandProfile>,
    val usbBaudProfiles: List<UsbBaudProfile>,
    val ntripCasterProfiles: List<NtripCasterProfile>,
    val ntripMountpointProfiles: List<NtripMountpointProfile>,
    val recordingPolicyProfiles: List<RecordingPolicyProfile>,
    val storageProfiles: List<StorageProfile>,
    val settingsSets: List<RecordingSettingsSet>,
    val selectedSettingsSetId: String?,
    val selectedWorkflowId: String?,
    val lastActiveNtripMountpointProfileId: String?,
    val plaintextPasswordsBySecretId: Map<String, String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("formatVersion", formatVersion)
        .put("exportedAtEpochMillis", exportedAtEpochMillis)
        .put("commandProfiles", commandProfiles.toJsonArray { it.toJson() })
        .put("usbBaudProfiles", usbBaudProfiles.toJsonArray { it.toJson() })
        .put("ntripCasterProfiles", ntripCasterProfiles.toJsonArray { it.toJson() })
        .put("ntripMountpointProfiles", ntripMountpointProfiles.toJsonArray { it.toJson() })
        .put("recordingPolicyProfiles", recordingPolicyProfiles.toJsonArray { it.toJson() })
        .put("storageProfiles", storageProfiles.toJsonArray { it.toJson() })
        .put("settingsSets", settingsSets.toJsonArray { it.toJson() })
        .putNullable("selectedSettingsSetId", selectedSettingsSetId)
        .putNullable("selectedWorkflowId", selectedWorkflowId)
        .putNullable("lastActiveNtripMountpointProfileId", lastActiveNtripMountpointProfileId)
        .also { json ->
            if (plaintextPasswordsBySecretId.isNotEmpty()) {
                json.put(
                    "plaintextPasswords",
                    JSONObject().also { passwords ->
                        plaintextPasswordsBySecretId.forEach { (secretId, password) ->
                            passwords.put(secretId, password)
                        }
                    },
                )
            }
        }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1

        fun fromProfiles(
            commandProfiles: List<CommandProfile>,
            usbBaudProfiles: List<UsbBaudProfile>,
            ntripCasterProfiles: List<NtripCasterProfile>,
            ntripMountpointProfiles: List<NtripMountpointProfile>,
            recordingPolicyProfiles: List<RecordingPolicyProfile>,
            storageProfiles: List<StorageProfile>,
            settingsSets: List<RecordingSettingsSet>,
            selectedSettingsSetId: String?,
            selectedWorkflowId: String?,
            lastActiveNtripMountpointProfileId: String?,
            passwordsBySecretId: Map<String, String>,
            options: SettingsSetExportOptions,
            exportedAtEpochMillis: Long = System.currentTimeMillis(),
        ): SettingsBackupFile =
            SettingsBackupFile(
                formatVersion = CURRENT_FORMAT_VERSION,
                exportedAtEpochMillis = exportedAtEpochMillis,
                commandProfiles = commandProfiles,
                usbBaudProfiles = usbBaudProfiles,
                ntripCasterProfiles = ntripCasterProfiles,
                ntripMountpointProfiles = ntripMountpointProfiles,
                recordingPolicyProfiles = recordingPolicyProfiles,
                storageProfiles = storageProfiles,
                settingsSets = settingsSets,
                selectedSettingsSetId = selectedSettingsSetId,
                selectedWorkflowId = selectedWorkflowId,
                lastActiveNtripMountpointProfileId = lastActiveNtripMountpointProfileId,
                plaintextPasswordsBySecretId = if (options.includePlaintextPasswords) {
                    passwordsBySecretId
                } else {
                    emptyMap()
                },
            )

        fun fromJson(json: JSONObject): SettingsBackupFile {
            require(json.optInt("formatVersion", 0) == CURRENT_FORMAT_VERSION) {
                "Unsupported settings backup format version."
            }
            val passwords = json.optJSONObject("plaintextPasswords")
            return SettingsBackupFile(
                formatVersion = json.getInt("formatVersion"),
                exportedAtEpochMillis = json.optLong("exportedAtEpochMillis", 0L),
                commandProfiles = json.getJSONArray("commandProfiles").mapObjects(CommandProfile::fromJson),
                usbBaudProfiles = json.getJSONArray("usbBaudProfiles").mapObjects(UsbBaudProfile::fromJson),
                ntripCasterProfiles = json.getJSONArray("ntripCasterProfiles").mapObjects(NtripCasterProfile::fromJson),
                ntripMountpointProfiles = json.getJSONArray("ntripMountpointProfiles").mapObjects(
                    NtripMountpointProfile::fromJson,
                ),
                recordingPolicyProfiles = json.getJSONArray("recordingPolicyProfiles").mapObjects(
                    RecordingPolicyProfile::fromJson,
                ),
                storageProfiles = json.getJSONArray("storageProfiles").mapObjects(StorageProfile::fromJson),
                settingsSets = json.getJSONArray("settingsSets").mapObjects(RecordingSettingsSet::fromJson),
                selectedSettingsSetId = json.optNullableString("selectedSettingsSetId"),
                selectedWorkflowId = json.optNullableString("selectedWorkflowId"),
                lastActiveNtripMountpointProfileId = json.optNullableString("lastActiveNtripMountpointProfileId"),
                plaintextPasswordsBySecretId = passwords?.keys()?.asSequence()
                    ?.associateWith { secretId -> passwords.getString(secretId) }
                    .orEmpty(),
            )
        }
    }
}

private fun <T> List<T>.toJsonArray(encode: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(encode(it)) } }

private fun <T> JSONArray.mapObjects(decode: (JSONObject) -> T): List<T> =
    (0 until length()).map { index -> decode(getJSONObject(index)) }

private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
    if (value == null) put(key, JSONObject.NULL) else put(key, value)

private fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf(String::isNotBlank) else null
