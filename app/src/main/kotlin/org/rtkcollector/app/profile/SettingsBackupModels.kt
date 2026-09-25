package org.rtkcollector.app.profile

import org.json.JSONArray
import org.json.JSONObject

enum class SettingsBackupProfileFamily(val jsonKey: String) {
    COMMAND("commandProfiles"),
    USB_BAUD("usbBaudProfiles"),
    NTRIP_CASTER("ntripCasterProfiles"),
    NTRIP_CASTER_UPLOAD("ntripCasterUploadProfiles"),
    NTRIP_MOUNTPOINT("ntripMountpointProfiles"),
    RECORDING_POLICY("recordingPolicyProfiles"),
    RTKLIB("rtklibProfiles"),
    SOLUTION_POLICY("solutionPolicyProfiles"),
    STORAGE("storageProfiles"),
    SETTINGS_SET("settingsSets"),
}

data class SettingsBackupFile(
    val formatVersion: Int,
    val exportedAtEpochMillis: Long,
    val commandProfiles: List<CommandProfile>,
    val usbBaudProfiles: List<UsbBaudProfile>,
    val ntripCasterProfiles: List<NtripCasterProfile>,
    val ntripCasterUploadProfiles: List<NtripCasterUploadProfile>,
    val ntripMountpointProfiles: List<NtripMountpointProfile>,
    val recordingPolicyProfiles: List<RecordingPolicyProfile>,
    val rtklibProfiles: List<RtklibProfile>,
    val solutionPolicyProfiles: List<SolutionPolicyProfile>,
    val storageProfiles: List<StorageProfile>,
    val settingsSets: List<RecordingSettingsSet>,
    val selectedSettingsSetId: String?,
    val selectedWorkflowId: String?,
    val lastActiveNtripMountpointProfileId: String?,
    val plaintextPasswordsBySecretId: Map<String, String>,
    /** Families physically present in the imported JSON; all families are present in new exports. */
    val includedProfileFamilies: Set<SettingsBackupProfileFamily> = SettingsBackupProfileFamily.entries.toSet(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("formatVersion", formatVersion)
        .put("exportedAtEpochMillis", exportedAtEpochMillis)
        .put("commandProfiles", commandProfiles.toJsonArray { it.toJson() })
        .put("usbBaudProfiles", usbBaudProfiles.toJsonArray { it.toJson() })
        .put("ntripCasterProfiles", ntripCasterProfiles.toJsonArray { it.toJson() })
        .put("ntripCasterUploadProfiles", ntripCasterUploadProfiles.toJsonArray { it.toJson() })
        .put("ntripMountpointProfiles", ntripMountpointProfiles.toJsonArray { it.toJson() })
        .put("recordingPolicyProfiles", recordingPolicyProfiles.toJsonArray { it.toJson() })
        .put("rtklibProfiles", rtklibProfiles.toJsonArray { it.toJson() })
        .put("solutionPolicyProfiles", solutionPolicyProfiles.toJsonArray { it.toJson() })
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
            ntripCasterUploadProfiles: List<NtripCasterUploadProfile>,
            ntripMountpointProfiles: List<NtripMountpointProfile>,
            recordingPolicyProfiles: List<RecordingPolicyProfile>,
            rtklibProfiles: List<RtklibProfile> = emptyList(),
            solutionPolicyProfiles: List<SolutionPolicyProfile> = emptyList(),
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
                ntripCasterUploadProfiles = ntripCasterUploadProfiles,
                ntripMountpointProfiles = ntripMountpointProfiles,
                recordingPolicyProfiles = recordingPolicyProfiles,
                rtklibProfiles = rtklibProfiles,
                solutionPolicyProfiles = solutionPolicyProfiles,
                storageProfiles = storageProfiles,
                settingsSets = settingsSets,
                selectedSettingsSetId = selectedSettingsSetId,
                selectedWorkflowId = selectedWorkflowId,
                lastActiveNtripMountpointProfileId = lastActiveNtripMountpointProfileId,
                plaintextPasswordsBySecretId = emptyMap(),
            ).let { backup ->
                backup.copy(
                    plaintextPasswordsBySecretId = if (options.includePlaintextPasswords) {
                        passwordsBySecretId.filterKeys(backup.referencedNtripSecretIds()::contains)
                    } else {
                        emptyMap()
                    },
                )
            }

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
                ntripCasterUploadProfiles = json.optJSONArray("ntripCasterUploadProfiles")?.mapObjects(
                    NtripCasterUploadProfile::fromJson,
                ).orEmpty(),
                ntripMountpointProfiles = json.getJSONArray("ntripMountpointProfiles").mapObjects(
                    NtripMountpointProfile::fromJson,
                ),
                recordingPolicyProfiles = json.getJSONArray("recordingPolicyProfiles").mapObjects(
                    RecordingPolicyProfile::fromJson,
                ),
                rtklibProfiles = json.optJSONArray("rtklibProfiles")?.mapObjects(RtklibProfile::fromJson).orEmpty(),
                solutionPolicyProfiles = json.optJSONArray("solutionPolicyProfiles")?.mapObjects(
                    SolutionPolicyProfile::fromJson,
                ).orEmpty(),
                storageProfiles = json.getJSONArray("storageProfiles").mapObjects(StorageProfile::fromJson),
                settingsSets = json.getJSONArray("settingsSets").mapObjects(RecordingSettingsSet::fromJson),
                selectedSettingsSetId = json.optNullableString("selectedSettingsSetId"),
                selectedWorkflowId = json.optNullableString("selectedWorkflowId"),
                lastActiveNtripMountpointProfileId = json.optNullableString("lastActiveNtripMountpointProfileId"),
                plaintextPasswordsBySecretId = passwords?.keys()?.asSequence()
                    ?.associateWith { secretId -> passwords.getString(secretId) }
                    .orEmpty(),
                includedProfileFamilies = SettingsBackupProfileFamily.entries
                    .filterTo(linkedSetOf()) { family -> json.optJSONArray(family.jsonKey) != null },
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

/**
 * RC2 stored caster passwords using this endpoint-derived key before the
 * profile-owned secret-id migration. It is accepted only when it exactly
 * corresponds to an imported caster profile and is re-keyed during import.
 */
internal fun legacyNtripCasterSecretId(profile: NtripCasterProfile): String =
    "ntrip:${profile.host}:${profile.id}:${profile.username}"

/**
 * Earlier RC2 backups keyed a caster password by its selected mountpoint.
 * Accept this only when the mountpoint remains explicitly bound to the caster.
 */
internal fun legacyNtripMountpointSecretId(
    host: String,
    username: String,
    mountpoint: NtripMountpointProfile,
): String = "ntrip:$host:${mountpoint.mountpoint}:$username"

private fun legacyNtripCasterHosts(profile: NtripCasterProfile): List<String> = buildList {
    profile.host.takeIf(String::isNotBlank)?.let(::add)
    legacyNtripSecretHost(profile.secretId, profile.username)?.let(::add)
}.distinct()

private fun legacyNtripSecretHost(secretId: String, username: String): String? {
    val parts = secretId.split(':')
    return parts.getOrNull(1)?.takeIf { host ->
        parts.size == 4 &&
            parts[0] == "ntrip" &&
            host.isNotBlank() &&
            parts[2].isNotBlank() &&
            parts[3] == username
    }
}

internal fun SettingsBackupFile.legacyNtripMountpointSecretIds(
    profile: NtripCasterProfile,
): List<String> = legacyNtripCasterHosts(profile)
    .flatMap { host ->
        ntripMountpointProfiles
            .asSequence()
            .filter { it.casterProfileId == profile.id }
            .map { mountpoint -> legacyNtripMountpointSecretId(host, profile.username, mountpoint) }
            .toList()
    }
    .distinct()

internal fun SettingsBackupFile.referencedNtripSecretIds(): Set<String> = buildSet {
    ntripCasterProfiles.forEach { profile ->
        add(ntripCasterSecretId(profile.id))
        add(legacyNtripCasterSecretId(profile))
        addAll(legacyNtripMountpointSecretIds(profile))
        profile.secretId.takeIf(String::isNotBlank)?.let(::add)
    }
    if (SettingsBackupProfileFamily.NTRIP_CASTER_UPLOAD in includedProfileFamilies) {
        ntripCasterUploadProfiles.forEach { profile ->
            add(ntripCasterUploadSecretId(profile.id))
            profile.secretId.takeIf(String::isNotBlank)?.let(::add)
        }
    }
    settingsSets.forEach { settingsSet ->
        settingsSet.overrides.ntripCaster?.secretId?.takeIf(String::isNotBlank)?.let(::add)
        settingsSet.overrides.ntripCasterUpload?.secretId?.takeIf(String::isNotBlank)?.let(::add)
    }
}
