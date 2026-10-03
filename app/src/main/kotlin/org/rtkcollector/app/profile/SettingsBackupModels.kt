package org.rtkcollector.app.profile

import org.json.JSONArray
import org.json.JSONObject
import org.rtkcollector.app.profile.ActiveSetupResolver.defaultOptionValues

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

enum class MigrationReviewReason(val blocksApplicableRoute: Boolean) {
    CASTER_LINEAGE(true),
    POLICY_CONFLICT(true),
    MISSING_PROFILE(true),
    MISSING_CREDENTIAL(true),
    UNREADABLE_CREDENTIAL(true),
    BASE_COORDINATE(true),
    SAF_RESELECTION(true),
    DORMANT_OVERRIDE(false),
    UNCLASSIFIED_INPUT(false),
}

enum class LegacyFieldDisposition { EFFECTIVE, DORMANT, UNCERTAIN }

enum class MigrationIssueResolution { VALIDATED_OPERATOR_REPAIR, EXACT_BOUND_CREDENTIAL, PERSISTED_SAF_AUTHORITY }

data class MigrationReviewIssue(
    val option: ActiveSetupOptionKey,
    val field: String,
    val disposition: LegacyFieldDisposition,
    val reason: MigrationReviewReason? = null,
    /** Null means the decision concerns the option rather than a particular profile. */
    val profileId: String? = null,
    val resolution: MigrationIssueResolution? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("option", option.name)
        .put("field", field.takeIf(::isAllowedRecoveryField) ?: "unclassifiedInput")
        .put("disposition", disposition.name).putNullable("reason", reason?.name)
        .putNullable("profileId", profileId)
        .putNullable("resolution", resolution?.name)

    companion object {
        fun fromJson(json: JSONObject) = MigrationReviewIssue(
            ActiveSetupOptionKey.valueOf(json.getString("option")),
            json.getString("field").takeIf(::isAllowedRecoveryField) ?: "unclassifiedInput",
            LegacyFieldDisposition.valueOf(json.getString("disposition")),
            json.optNullableString("reason")?.let(MigrationReviewReason::valueOf),
            json.optNullableString("profileId"),
            json.optNullableString("resolution")?.let(MigrationIssueResolution::valueOf),
        )
    }
}

data class LegacyMigrationRecovery(
    val legacySettingsSet: RecordingSettingsSet,
    val reviewReasons: Set<MigrationReviewReason>,
    val issues: List<MigrationReviewIssue> = emptyList(),
    /** Global choices without proven set ownership remain evidence, never active choices. */
    val choiceProvenance: Map<String, String> = emptyMap(),
    /** Named recovery inputs only; neither passwords nor permission to resolve runtime aliases. */
    val ownerSecretInputs: Map<String, List<String>> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("legacySettingsSet", legacySettingsSet.toJson())
        .put("reviewReasons", JSONArray().also { array -> reviewReasons.forEach { array.put(it.name) } })
        .put("issues", issues.toJsonArray(MigrationReviewIssue::toJson))
        .put("choiceProvenance", JSONObject(choiceProvenance.filterKeys { it in RECOVERY_CHOICE_KEYS }))
        .put("ownerSecretInputs", JSONObject().also { bindings ->
            ownerSecretInputs.filterKeys(::isAllowedRecoveryOwner).forEach { (owner, inputs) -> bindings.put(owner, JSONArray(inputs)) }
        })

    fun blockingIssues(set: RecordingSettingsSet, state: ActiveSetupSelections): List<MigrationReviewIssue> {
        val active = ActiveSetupResolver.resolve(set, state)
        val originalDefaults = legacySettingsSet.defaultOptionValues()
        return issues.filter { issue ->
            issue.resolution == null && issue.disposition != LegacyFieldDisposition.DORMANT && issue.reason?.blocksApplicableRoute == true &&
                active.options[issue.option]?.let { option ->
                    val requiresDependency = issue.reason in setOf(MigrationReviewReason.MISSING_PROFILE,
                        MigrationReviewReason.MISSING_CREDENTIAL, MigrationReviewReason.UNREADABLE_CREDENTIAL,
                        MigrationReviewReason.CASTER_LINEAGE)
                    option.applicable && (!requiresDependency || option.dependencyActive) &&
                        (issue.reason !in setOf(MigrationReviewReason.SAF_RESELECTION,
                            MigrationReviewReason.MISSING_CREDENTIAL, MigrationReviewReason.UNREADABLE_CREDENTIAL) ||
                            issue.profileId == null || issue.profileId == option.effectiveValueId ||
                            issue.profileId == originalDefaults[issue.option])
                } == true
        }
    }

    companion object {
        fun fromJson(json: JSONObject): LegacyMigrationRecovery = LegacyMigrationRecovery(
            legacySettingsSet = RecordingSettingsSet.fromJson(json.getJSONObject("legacySettingsSet")),
            reviewReasons = json.getJSONArray("reviewReasons").let { array ->
                (0 until array.length()).mapTo(linkedSetOf()) { index ->
                    MigrationReviewReason.valueOf(array.getString(index))
                }
            },
            issues = json.optJSONArray("issues")?.mapObjects(MigrationReviewIssue::fromJson).orEmpty(),
            choiceProvenance = json.optJSONObject("choiceProvenance")?.let { values ->
                values.keys().asSequence().filter { it in RECOVERY_CHOICE_KEYS }.associateWith(values::getString)
            }.orEmpty(),
            ownerSecretInputs = json.optJSONObject("ownerSecretInputs")?.let { values ->
                values.keys().asSequence().filter(::isAllowedRecoveryOwner).associateWith { owner ->
                    val inputs = values.getJSONArray(owner)
                    (0 until inputs.length()).map(inputs::getString)
                }
            }.orEmpty(),
        )
    }
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
    val activeSetupSelections: Map<String, ActiveSetupSelections> = emptyMap(),
    val migrationRecovery: Map<String, LegacyMigrationRecovery> = emptyMap(),
    /** Families physically present in the imported JSON; all families are present in new exports. */
    val includedProfileFamilies: Set<SettingsBackupProfileFamily> = SettingsBackupProfileFamily.entries.toSet(),
    /** Never serialized or printed. Publication must retain this in private encrypted storage first. */
    val privateRecoveryInput: PrivateRecoveryInput? = null,
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
            if (formatVersion >= 2) {
                json.put("includedProfileFamilies", JSONArray(includedProfileFamilies.map { it.name }))
                json.put("activeSetupSelections", JSONObject().also { selections ->
                    activeSetupSelections.forEach { (setId, state) ->
                        require(setId == state.settingsSetId) { "Selection state key must match its settings set." }
                        selections.put(setId, state.toJson())
                    }
                })
                json.put("migrationRecovery", JSONObject().also { recovery ->
                    migrationRecovery.forEach { (setId, record) ->
                        require(setId == record.legacySettingsSet.id) { "Recovery key must match its settings set." }
                        recovery.put(setId, record.toJson())
                    }
                })
            }
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
        const val CURRENT_FORMAT_VERSION = 2

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
            activeSetupSelections: Map<String, ActiveSetupSelections> = emptyMap(),
            migrationRecovery: Map<String, LegacyMigrationRecovery> = emptyMap(),
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
                activeSetupSelections = activeSetupSelections,
                migrationRecovery = migrationRecovery,
            ).let { backup ->
                backup.copy(
                    plaintextPasswordsBySecretId = if (options.includePlaintextPasswords) {
                        backup.committedNtripSecretIds().mapNotNull { id -> passwordsBySecretId[id]?.let { id to it } }.toMap()
                    } else {
                        emptyMap()
                    },
                )
            }

        fun fromJson(json: JSONObject): SettingsBackupFile {
            val formatVersion = json.optInt("formatVersion", 0)
            require(formatVersion in 1..CURRENT_FORMAT_VERSION) {
                "Unsupported settings backup format version."
            }
            val selections = if (formatVersion >= 2) {
                requireNotNull(json.optJSONObject("activeSetupSelections")) {
                    "Settings backup is missing active setup selections."
                }
            } else null
            val recovery = if (formatVersion >= 2) {
                requireNotNull(json.optJSONObject("migrationRecovery")) {
                    "Settings backup is missing migration recovery data."
                }
            } else null
            val passwords = json.optJSONObject("plaintextPasswords")
            return SettingsBackupFile(
                formatVersion = formatVersion,
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
                activeSetupSelections = selections?.let { objectJson ->
                    objectJson.keys().asSequence().associateWith { setId ->
                        ActiveSetupSelections.fromJson(objectJson.getJSONObject(setId)).also { state ->
                            require(state.settingsSetId == setId) { "Selection state key does not match its settings set." }
                        }
                    }
                }.orEmpty(),
                migrationRecovery = recovery?.let { objectJson ->
                    objectJson.keys().asSequence().associateWith { setId ->
                        LegacyMigrationRecovery.fromJson(objectJson.getJSONObject(setId)).also { record ->
                            require(record.legacySettingsSet.id == setId) { "Recovery key does not match its settings set." }
                        }
                    }
                }.orEmpty(),
                includedProfileFamilies = SettingsBackupProfileFamily.entries.filterTo(linkedSetOf()) { family ->
                    json.optJSONArray(family.jsonKey) != null && (formatVersion < 2 ||
                        json.optJSONArray("includedProfileFamilies")?.let { included ->
                            (0 until included.length()).any { included.getString(it) == family.name }
                        } != false)
                },
            ).let { retainPrivateLegacyInput(it, json) }
        }
    }
}

private val RECOVERY_CHOICE_KEYS = setOf("workflow", "baseCoordinate", "unscopedWorkflow", "unscopedBaseCoordinate")

private fun isAllowedRecoveryOwner(owner: String): Boolean =
    listOf(ActiveSetupOptionKey.NTRIP_CASTER, ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD).any {
        owner.startsWith("${it.name}:") && owner.length > it.name.length + 1
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

/** Only these committed owner bindings may be consulted for a consented export. */
fun SettingsBackupFile.committedNtripSecretIds(): Set<String> = buildSet {
    ntripCasterProfiles.map(NtripCasterProfile::secretId).filterTo(this, String::isNotBlank)
    ntripCasterUploadProfiles.map(NtripCasterUploadProfile::secretId).filterTo(this, String::isNotBlank)
}

/** Consent gates the callback itself, not merely inclusion of its result in JSON. */
fun SettingsBackupFile.withPasswordExport(
    options: SettingsSetExportOptions,
    passwordLookup: (String) -> String?,
): SettingsBackupFile = copy(plaintextPasswordsBySecretId = if (options.includePlaintextPasswords) {
    committedNtripSecretIds().mapNotNull { id -> passwordLookup(id)?.let { id to it } }.toMap()
} else emptyMap())
