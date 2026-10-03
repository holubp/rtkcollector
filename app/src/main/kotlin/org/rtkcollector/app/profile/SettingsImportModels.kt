package org.rtkcollector.app.profile

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

const val MAX_SETTINGS_IMPORT_BYTES: Int = 2 * 1024 * 1024

data class SettingsImportSummary(
    val commandProfileCount: Int,
    val usbBaudProfileCount: Int,
    val ntripCasterProfileCount: Int,
    val ntripCasterUploadProfileCount: Int,
    val ntripMountpointProfileCount: Int,
    val recordingPolicyProfileCount: Int,
    val rtklibProfileCount: Int,
    val solutionPolicyProfileCount: Int,
    val storageProfileCount: Int,
    val settingsSetCount: Int,
    val selectedSettingsSetId: String?,
    val selectedWorkflowId: String?,
    val lastActiveNtripMountpointProfileId: String?,
    val containsPlaintextPasswords: Boolean,
    val safTreeUriReselectionCount: Int = 0,
    val omittedProfileFamilies: List<String> = emptyList(),
)

data class SettingsBackupImportPlan(
    val backup: SettingsBackupFile,
    val safTreeUriReselectionCount: Int,
)

data class RetainedSettingsProfileIds(
    val ntripCasterUploadProfileIds: Set<String> = emptySet(),
    val rtklibProfileIds: Set<String> = emptySet(),
    val solutionPolicyProfileIds: Set<String> = emptySet(),
)

/** Supplies fresh opaque IDs for one settings-import planning pass. */
fun interface SettingsImportIdFactory {
    fun newId(namespace: String): String
}

private val collisionResistantSettingsImportIdFactory = SettingsImportIdFactory { namespace ->
    "$namespace-import-${UUID.randomUUID()}"
}

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

    if (json.optInt("formatVersion", 0) !in 1..SettingsBackupFile.CURRENT_FORMAT_VERSION) {
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
    SettingsBackupProfileFamily.entries
        .filter { family -> family.jsonKey !in requiredArrays }
        .forEach { family ->
            if (json.has(family.jsonKey) && json.optJSONArray(family.jsonKey) == null) {
                return SettingsImportValidationResult.Invalid(
                    "Settings backup contains invalid ${family.jsonKey}.",
                )
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
    if (backup.settingsSets.isEmpty()) {
        return SettingsImportValidationResult.Invalid("Settings backup contains no settings sets.")
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
            rtklibProfileCount = backup.rtklibProfiles.size,
            solutionPolicyProfileCount = backup.solutionPolicyProfiles.size,
            storageProfileCount = backup.storageProfiles.size,
            settingsSetCount = backup.settingsSets.size,
            selectedSettingsSetId = backup.selectedSettingsSetId,
            selectedWorkflowId = backup.selectedWorkflowId,
            lastActiveNtripMountpointProfileId = backup.lastActiveNtripMountpointProfileId,
            containsPlaintextPasswords = backup.plaintextPasswordsBySecretId.isNotEmpty(),
            omittedProfileFamilies = SettingsBackupProfileFamily.entries
                .filterNot(backup.includedProfileFamilies::contains)
                .map(SettingsBackupProfileFamily::jsonKey),
        ),
    )
}

internal fun hasPersistedSafTreeAuthority(readPermission: Boolean, writePermission: Boolean): Boolean =
    readPermission && writePermission

/**
 * Isolates imported NTRIP credentials and removes unavailable SAF authority.
 *
 * Imported NTRIP profiles receive fresh IDs so they cannot resolve secrets from
 * the current installation. Persisted URI grants are also installation-local;
 * affected SAF profiles remain unusable until the user selects a folder again.
 */
fun SettingsBackupFile.withValidatedSafAuthority(
    persistedSafTreeUrisWithWriteAccess: Set<String>,
): SettingsBackupFile {
    val unavailable = storageProfiles.filter {
        it.kind == "SAF_TREE" && (it.requiresTreeReselection || it.treeUri.isNullOrBlank() ||
            it.treeUri !in persistedSafTreeUrisWithWriteAccess)
    }.mapTo(linkedSetOf()) { it.id }
    val updatedRecovery = migrationRecovery.toMutableMap()
    settingsSets.forEach { set ->
        val prior = updatedRecovery[set.id]
        if (prior == null && unavailable.isEmpty()) return@forEach
        val record = prior ?: LegacyMigrationRecovery(set, emptySet())
        val issues = unavailable.map { id -> MigrationReviewIssue(ActiveSetupOptionKey.STORAGE, "treeUri",
            LegacyFieldDisposition.UNCERTAIN, MigrationReviewReason.SAF_RESELECTION, id) }
        val reconciled = (record.issues.map { issue ->
            if (issue.reason != MigrationReviewReason.SAF_RESELECTION || issue.profileId == null) issue
            else if (issue.profileId in unavailable) issue.copy(resolution = null)
            else if (storageProfiles.any { it.id == issue.profileId && it.kind == "SAF_TREE" &&
                    !it.requiresTreeReselection && it.treeUri in persistedSafTreeUrisWithWriteAccess })
                issue.copy(resolution = MigrationIssueResolution.PERSISTED_SAF_AUTHORITY)
            else issue
        } + issues).distinct()
        updatedRecovery[set.id] = record.copy(issues = reconciled,
            reviewReasons = (record.reviewReasons - MigrationReviewReason.SAF_RESELECTION) +
                if (reconciled.any { it.reason == MigrationReviewReason.SAF_RESELECTION && it.resolution == null })
                    setOf(MigrationReviewReason.SAF_RESELECTION) else emptySet())
    }
    return copy(storageProfiles = storageProfiles.map { profile ->
        if (profile.id in unavailable) profile.copy(treeUri = null, requiresTreeReselection = true) else profile
    }, migrationRecovery = updatedRecovery)
}

fun settingsBackupImportPlan(
    backup: SettingsBackupFile,
    persistedSafTreeUrisWithWriteAccess: Set<String>,
    retainedProfileIds: RetainedSettingsProfileIds = RetainedSettingsProfileIds(),
    idFactory: SettingsImportIdFactory = collisionResistantSettingsImportIdFactory,
    retainedUploadProfiles: List<NtripCasterUploadProfile> = emptyList(),
): SettingsBackupImportPlan {
    validateRetainedOptionalProfileReferences(backup, retainedProfileIds)?.let { error ->
        throw IllegalArgumentException(error)
    }
    val uploadIncluded = SettingsBackupProfileFamily.NTRIP_CASTER_UPLOAD in backup.includedProfileFamilies
    val materializationInput = if (uploadIncluded) backup else backup.copy(ntripCasterUploadProfiles = retainedUploadProfiles)
    val complete = planLegacyProfileOwnership(materializationInput)
    val materializedBackup = if (uploadIncluded) complete else complete.copy(
        ntripCasterUploadProfiles = complete.ntripCasterUploadProfiles.filter { candidate ->
            retainedUploadProfiles.none { it.id == candidate.id }
        },
    )
    val remappedBackup = remapImportedNtripGraph(materializedBackup, idFactory,
        allowLegacyAliases = backup.formatVersion == 1)
    var reselectionCount = 0
    val storageProfilesById = remappedBackup.storageProfiles.associateBy(StorageProfile::id)
    val storageProfiles = remappedBackup.storageProfiles.map { profile ->
        if (profile.kind == "SAF_TREE" && profile.treeUri !in persistedSafTreeUrisWithWriteAccess) {
            reselectionCount++
            profile.copy(
                kind = "SAF_TREE",
                treeUri = null,
                requiresTreeReselection = true,
            )
        } else {
            profile
        }
    }
    val settingsSets = remappedBackup.settingsSets.map { settingsSet ->
        val storageOverride = settingsSet.overrides.storage
        val effectiveStorageRef = settingsSet.overrides.storageProfileRef ?: settingsSet.storageProfileRef
        val referencedStorageProfile = storageProfilesById[effectiveStorageRef.id] ?: return@map settingsSet
        val effectiveStorageKind = storageOverride?.kind ?: referencedStorageProfile.kind
        if (
            storageOverride != null &&
            effectiveStorageKind == "SAF_TREE" &&
            !storageOverride.treeUri.isNullOrBlank() &&
            storageOverride.treeUri !in persistedSafTreeUrisWithWriteAccess
        ) {
            reselectionCount++
            settingsSet.copy(
                overrides = settingsSet.overrides.copy(
                    storage = storageOverride.copy(
                        kind = "SAF_TREE",
                        treeUri = null,
                        requiresTreeReselection = true,
                    ),
                ),
            )
        } else {
            settingsSet
        }
    }
    val sanitized = remappedBackup.copy(
            storageProfiles = storageProfiles,
            settingsSets = settingsSets,
        )
    val recovery = sanitized.migrationRecovery.toMutableMap()
    sanitized.settingsSets.forEach { set ->
        val record = recovery[set.id] ?: LegacyMigrationRecovery(set, emptySet())
        val issues = record.issues.toMutableList()
        fun add(key: ActiveSetupOptionKey, field: String, reason: MigrationReviewReason, id: String?) {
            issues += MigrationReviewIssue(key, field, LegacyFieldDisposition.UNCERTAIN, reason, id)
        }
        val availableRtklibIds = if (SettingsBackupProfileFamily.RTKLIB in sanitized.includedProfileFamilies) {
            sanitized.rtklibProfiles.mapTo(linkedSetOf()) { it.id }
        } else retainedProfileIds.rtklibProfileIds
        val rtklibReferences = listOfNotNull(set.rtklibProfileRef?.id) +
            listOfNotNull(sanitized.activeSetupSelections[set.id]?.rememberedChoices?.get(ActiveSetupOptionKey.RTKLIB)?.profileId,
                sanitized.activeSetupSelections[set.id]?.activeChoices?.get(ActiveSetupOptionKey.RTKLIB)?.profileId)
        rtklibReferences.distinct().filterNot(availableRtklibIds::contains).forEach {
            add(ActiveSetupOptionKey.RTKLIB, "selection", MigrationReviewReason.MISSING_PROFILE, it)
        }
        set.basePositionProfileRef?.let { add(ActiveSetupOptionKey.BASE_COORDINATE, "basePositionProfileRef",
            MigrationReviewReason.BASE_COORDINATE, it.id) }
        sanitized.activeSetupSelections[set.id]?.let { state ->
            (state.rememberedChoices + state.activeChoices)[ActiveSetupOptionKey.BASE_COORDINATE]?.profileId?.let {
                add(ActiveSetupOptionKey.BASE_COORDINATE, "baseCoordinateSelection", MigrationReviewReason.BASE_COORDINATE, it)
            }
        }
        sanitized.storageProfiles.filter { it.requiresTreeReselection }.forEach {
            add(ActiveSetupOptionKey.STORAGE, "treeUri", MigrationReviewReason.SAF_RESELECTION, it.id)
        }
        sanitized.ntripMountpointProfiles.forEach { mount ->
            sanitized.ntripCasterProfiles.firstOrNull { it.id == mount.casterProfileId }?.let { caster ->
                if (caster.secretId !in sanitized.plaintextPasswordsBySecretId) add(ActiveSetupOptionKey.NTRIP_MOUNTPOINT,
                    "secretId", MigrationReviewReason.MISSING_CREDENTIAL, mount.id)
            }
        }
        sanitized.ntripCasterUploadProfiles.forEach { upload ->
            if (upload.secretId !in sanitized.plaintextPasswordsBySecretId) add(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD,
                "secretId", MigrationReviewReason.MISSING_CREDENTIAL, upload.id)
        }
        recovery[set.id] = record.copy(issues = issues.distinct(), reviewReasons = record.reviewReasons + issues.mapNotNull { it.reason })
    }
    return SettingsBackupImportPlan(
        backup = sanitized.copy(migrationRecovery = recovery),
        safTreeUriReselectionCount = reselectionCount,
    )
}

fun SettingsImportValidationResult.sanitizedForPersistedSafWriteAccess(
    persistedSafTreeUrisWithWriteAccess: Set<String>,
    retainedProfileIds: RetainedSettingsProfileIds = RetainedSettingsProfileIds(),
    idFactory: SettingsImportIdFactory = collisionResistantSettingsImportIdFactory,
): SettingsImportValidationResult =
    when (this) {
        SettingsImportValidationResult.Loading,
        is SettingsImportValidationResult.Invalid,
        -> this
        is SettingsImportValidationResult.Valid -> {
            val plan = settingsBackupImportPlan(
                backup = backup,
                persistedSafTreeUrisWithWriteAccess = persistedSafTreeUrisWithWriteAccess,
                retainedProfileIds = retainedProfileIds,
                idFactory = idFactory,
            )
            copy(
                backup = plan.backup,
                summary = summary.copy(safTreeUriReselectionCount = plan.safTreeUriReselectionCount),
            )
        }
    }

private fun remapImportedNtripGraph(
    backup: SettingsBackupFile,
    idFactory: SettingsImportIdFactory,
    allowLegacyAliases: Boolean,
): SettingsBackupFile {
    val sourceSecretIds = backup.referencedNtripSecretIds()
    val forbiddenIds = buildSet {
        addAll(sourceSecretIds)
        addAll(backup.ntripCasterProfiles.map(NtripCasterProfile::id))
        addAll(backup.ntripCasterUploadProfiles.map(NtripCasterUploadProfile::id))
        backup.settingsSets.forEach { settingsSet ->
            settingsSet.ntripCasterProfileRef?.id?.let(::add)
            settingsSet.ntripCasterRestrictionRef?.id?.let(::add)
            settingsSet.ntripCasterUploadProfileRef?.id?.let(::add)
            settingsSet.overrides.ntripCasterProfileRef?.id?.let(::add)
            settingsSet.overrides.ntripCasterUploadProfileRef?.id?.let(::add)
        }
    }
    val freshIds = FreshSettingsImportIds(idFactory, forbiddenIds)
    val casterIdMap = backup.ntripCasterProfiles.associate { profile ->
        profile.id to freshIds.next("ntrip-caster")
    }
    val uploadFamilyIncluded = SettingsBackupProfileFamily.NTRIP_CASTER_UPLOAD in backup.includedProfileFamilies
    val uploadIdMap = backup.ntripCasterUploadProfiles.associate { profile ->
        profile.id to freshIds.next("ntrip-caster-upload")
    }
    val remappedPasswords = linkedMapOf<String, String>()
    val remappedOverrideSecretIds = mutableMapOf<Pair<String, String>, String>()

    val casterProfiles = backup.ntripCasterProfiles.map { profile ->
        val newProfileId = casterIdMap.getValue(profile.id)
        val newSecretId = freshIds.next("ntrip-caster-owner-secret")
        profilePassword(
            passwords = backup.plaintextPasswordsBySecretId,
            profileOwnedSecretId = profile.secretId,
            allowLegacyAliases = allowLegacyAliases,
            legacySecretIds = listOf(ntripCasterSecretId(profile.id), legacyNtripCasterSecretId(profile)) +
                backup.legacyNtripMountpointSecretIds(profile),
        )?.let { password -> remappedPasswords[newSecretId] = password }
        profile.copy(
            id = newProfileId,
            secretId = newSecretId,
            unsafeTlsAcknowledged = false,
        )
    }
    val uploadProfiles = backup.ntripCasterUploadProfiles.map { profile ->
            val newProfileId = uploadIdMap.getValue(profile.id)
            val newSecretId = freshIds.next("ntrip-caster-upload-owner-secret")
            profilePassword(
                passwords = backup.plaintextPasswordsBySecretId,
                profileOwnedSecretId = profile.secretId,
                allowLegacyAliases = allowLegacyAliases,
                legacySecretIds = listOf(ntripCasterUploadSecretId(profile.id)),
            )?.let { password -> remappedPasswords[newSecretId] = password }
            profile.copy(
                id = newProfileId,
                secretId = newSecretId,
                unsafeTlsAcknowledged = false,
            )
    }

    fun remapExplicitSecretId(
        sourceSecretId: String?,
        namespace: String,
        requireFreshBinding: Boolean = false,
    ): String? {
        val sourceId = sourceSecretId?.takeIf(String::isNotBlank) ?: return null
        if (!requireFreshBinding && sourceId !in backup.plaintextPasswordsBySecretId) return null
        val newSecretId = remappedOverrideSecretIds.getOrPut(namespace to sourceId) { freshIds.next(namespace) }
        backup.plaintextPasswordsBySecretId[sourceId]?.let { password ->
            remappedPasswords[newSecretId] = password
        }
        return newSecretId
    }

    fun remapUploadOverrideSecretId(override: NtripCasterUploadOverride): String? {
        if (uploadFamilyIncluded) {
            return remapExplicitSecretId(
                override.secretId,
                "ntrip-caster-upload-override-secret",
            )
        }
        if (!override.hasEffectiveEndpointOrCredentialOverride()) return null
        val sourceSecretId = override.secretId?.takeIf(String::isNotBlank)
        if (sourceSecretId == null) {
            return freshIds.next("ntrip-caster-upload-override-secret")
        }
        return remapExplicitSecretId(
            sourceSecretId = sourceSecretId,
            namespace = "ntrip-caster-upload-override-secret",
            requireFreshBinding = true,
        )
    }

    fun remapSettingsSet(settingsSet: RecordingSettingsSet): RecordingSettingsSet =
        settingsSet.copy(
            ntripCasterProfileRef = settingsSet.ntripCasterProfileRef.remapProfileReference(
                casterIdMap,
                "NTRIP caster profile",
            ),
            ntripCasterRestrictionRef = settingsSet.ntripCasterRestrictionRef.remapProfileReference(
                casterIdMap,
                "NTRIP caster restriction",
            ),
            ntripCasterUploadProfileRef = if (uploadFamilyIncluded) {
                settingsSet.ntripCasterUploadProfileRef.remapProfileReference(
                    uploadIdMap,
                    "NTRIP caster upload profile",
                )
            } else {
                settingsSet.ntripCasterUploadProfileRef?.let { ref -> ref.copy(id = uploadIdMap[ref.id] ?: ref.id) }
            },
            overrides = settingsSet.overrides.copy(
                ntripCasterProfileRef = settingsSet.overrides.ntripCasterProfileRef.remapProfileReference(
                    casterIdMap,
                    "NTRIP caster profile",
                ),
                ntripCasterUploadProfileRef = if (uploadFamilyIncluded) {
                    settingsSet.overrides.ntripCasterUploadProfileRef.remapProfileReference(
                        uploadIdMap,
                        "NTRIP caster upload profile",
                    )
                } else {
                    settingsSet.overrides.ntripCasterUploadProfileRef?.let { ref -> ref.copy(id = uploadIdMap[ref.id] ?: ref.id) }
                },
                ntripCaster = settingsSet.overrides.ntripCaster?.let { override ->
                    override.copy(
                        secretId = remapExplicitSecretId(
                            override.secretId,
                            "ntrip-caster-override-secret",
                        ),
                    )
                },
                ntripCasterUpload = settingsSet.overrides.ntripCasterUpload?.let { override ->
                    override.copy(
                        secretId = remapUploadOverrideSecretId(override),
                    )
                },
            ),
        )
    val settingsSets = backup.settingsSets.map(::remapSettingsSet)

    fun remapChoice(key: ActiveSetupOptionKey, choice: SelectionChoice): SelectionChoice {
        if (choice.kind != SelectionChoiceKind.PROFILE) return choice
        val oldId = requireNotNull(choice.profileId)
        val remapped = when (key) {
            ActiveSetupOptionKey.NTRIP_CASTER -> casterIdMap[oldId] ?: oldId
            ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> uploadIdMap[oldId] ?: oldId
            else -> oldId
        }
        return SelectionChoice.profile(remapped)
    }

    val selections = backup.activeSetupSelections.mapValues { (_, state) ->
        state.copy(
            rememberedChoices = state.rememberedChoices.mapValues { (key, choice) -> remapChoice(key, choice) },
            activeChoices = state.activeChoices.mapValues { (key, choice) -> remapChoice(key, choice) },
            transientChoices = emptyMap(),
            uploadSelection = state.uploadSelection?.let { upload ->
                upload.copy(profile = remapChoice(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD, upload.profile))
            },
            transientUploadSelection = null,
        )
    }
    val recovery = backup.migrationRecovery.mapValues { (_, record) ->
        record.copy(legacySettingsSet = remapSettingsSet(record.legacySettingsSet), issues = record.issues.map { issue ->
            issue.copy(profileId = issue.profileId?.let { id -> when (issue.option) {
                ActiveSetupOptionKey.NTRIP_CASTER -> casterIdMap[id] ?: id
                ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> uploadIdMap[id] ?: id
                else -> id
            } })
        })
    }
    val mountpointProfiles = backup.ntripMountpointProfiles.map { profile ->
        profile.copy(
            casterProfileId = requireNotNull(casterIdMap[profile.casterProfileId]) {
                "NTRIP mountpoint '${profile.name}' references missing caster profile '${profile.casterProfileId}'."
            },
        )
    }

    return backup.copy(
        ntripCasterProfiles = casterProfiles,
        ntripCasterUploadProfiles = uploadProfiles,
        ntripMountpointProfiles = mountpointProfiles,
        settingsSets = settingsSets,
        activeSetupSelections = selections,
        migrationRecovery = recovery,
        plaintextPasswordsBySecretId = remappedPasswords.filterKeys { secret ->
            casterProfiles.any { it.secretId == secret } || uploadProfiles.any { it.secretId == secret }
        },
    )
}

private class FreshSettingsImportIds(
    private val factory: SettingsImportIdFactory,
    private val forbiddenIds: Set<String>,
) {
    private val allocatedIds = mutableSetOf<String>()

    fun next(namespace: String): String {
        val id = factory.newId(namespace)
        require(id.isNotBlank()) { "Settings import ID factory returned a blank id." }
        require(id !in forbiddenIds) { "Settings import ID factory reused an imported id." }
        require(allocatedIds.add(id)) { "Settings import ID factory returned a duplicate id." }
        return id
    }

    fun reserveDerived(id: String) {
        require(id !in forbiddenIds) { "Settings import ID factory reused an imported secret id." }
        require(allocatedIds.add(id)) { "Settings import ID factory produced conflicting ids." }
    }
}

private fun ProfileReference?.remapProfileReference(
    profileIdMap: Map<String, String>,
    label: String,
): ProfileReference? {
    val reference = this ?: return null
    val remappedId = requireNotNull(profileIdMap[reference.id]) {
        "Settings set references missing $label '${reference.id}'."
    }
    return reference.copy(id = remappedId)
}

private fun profilePassword(
    passwords: Map<String, String>,
    profileOwnedSecretId: String,
    legacySecretIds: List<String>,
    allowLegacyAliases: Boolean,
): String? {
    if (profileOwnedSecretId in passwords) return passwords.getValue(profileOwnedSecretId)
    if (profileOwnedSecretId.isNotBlank() && !allowLegacyAliases) return null
    return legacySecretIds
        .firstOrNull { secretId -> secretId.isNotBlank() && secretId in passwords }
        ?.let(passwords::getValue)
}

private fun NtripCasterUploadOverride.hasEffectiveEndpointOrCredentialOverride(): Boolean =
    host != null ||
        port != null ||
        mountpoint != null ||
        username != null ||
        secretId != null

private fun validateBackupReferences(backup: SettingsBackupFile): String? {
    duplicateId("command profile", backup.commandProfiles.map { it.id })?.let { return it }
    duplicateId("USB/baud profile", backup.usbBaudProfiles.map { it.id })?.let { return it }
    duplicateId("NTRIP caster profile", backup.ntripCasterProfiles.map { it.id })?.let { return it }
    duplicateId("NTRIP caster upload profile", backup.ntripCasterUploadProfiles.map { it.id })?.let { return it }
    duplicateId("NTRIP mountpoint profile", backup.ntripMountpointProfiles.map { it.id })?.let { return it }
    duplicateId("recording output profile", backup.recordingPolicyProfiles.map { it.id })?.let { return it }
    duplicateId("RTKLIB profile", backup.rtklibProfiles.map { it.id })?.let { return it }
    duplicateId("solution policy profile", backup.solutionPolicyProfiles.map { it.id })?.let { return it }
    duplicateId("storage profile", backup.storageProfiles.map { it.id })?.let { return it }
    duplicateId("settings set", backup.settingsSets.map { it.id })?.let { return it }

    val commandIds = backup.commandProfiles.mapTo(mutableSetOf()) { it.id }
    val usbBaudIds = backup.usbBaudProfiles.mapTo(mutableSetOf()) { it.id }
    val casterIds = backup.ntripCasterProfiles.mapTo(mutableSetOf()) { it.id }
    val casterUploadIds = backup.ntripCasterUploadProfiles.mapTo(mutableSetOf()) { it.id }
    val mountpointIds = backup.ntripMountpointProfiles.mapTo(mutableSetOf()) { it.id }
    val recordingIds = backup.recordingPolicyProfiles.mapTo(mutableSetOf()) { it.id }
    val rtklibIds = backup.rtklibProfiles.mapTo(mutableSetOf()) { it.id }
    val solutionPolicyIds = backup.solutionPolicyProfiles.mapTo(mutableSetOf()) { it.id }
    val storageIds = backup.storageProfiles.mapTo(mutableSetOf()) { it.id }
    val settingsSetIds = backup.settingsSets.mapTo(mutableSetOf()) { it.id }

    backup.activeSetupSelections.keys.firstOrNull { it !in settingsSetIds }?.let { id ->
        return "Active selection state references missing settings set '$id'."
    }
    backup.migrationRecovery.keys.firstOrNull { it !in settingsSetIds }?.let { id ->
        return "Migration recovery references missing settings set '$id'."
    }

    backup.ntripMountpointProfiles.firstOrNull { it.casterProfileId !in casterIds }?.let {
        return "NTRIP mountpoint '${it.name}' references missing caster profile '${it.casterProfileId}'."
    }
    backup.settingsSets.forEach { settingsSet ->
        missingReference(settingsSet.commandProfileRef.id, commandIds, settingsSet.name, "command profile")?.let { return it }
        missingReference(settingsSet.usbBaudProfileRef.id, usbBaudIds, settingsSet.name, "USB/baud profile")?.let { return it }
        settingsSet.ntripCasterProfileRef?.id
            ?.let { missingReference(it, casterIds, settingsSet.name, "NTRIP caster profile") }
            ?.let { return it }
        settingsSet.overrides.ntripCasterProfileRef?.id
            ?.let { missingReference(it, casterIds, settingsSet.name, "NTRIP caster profile") }
            ?.let { return it }
        settingsSet.ntripMountpointProfileRef?.id
            ?.let { missingReference(it, mountpointIds, settingsSet.name, "NTRIP mountpoint profile") }
            ?.let { return it }
        if (SettingsBackupProfileFamily.NTRIP_CASTER_UPLOAD in backup.includedProfileFamilies) {
            settingsSet.ntripCasterUploadProfileRef?.id
                ?.let { missingReference(it, casterUploadIds, settingsSet.name, "NTRIP caster upload profile") }
                ?.let { return it }
            settingsSet.overrides.ntripCasterUploadProfileRef?.id
                ?.let { missingReference(it, casterUploadIds, settingsSet.name, "NTRIP caster upload profile") }
                ?.let { return it }
        }
        if (SettingsBackupProfileFamily.RTKLIB in backup.includedProfileFamilies &&
            ActiveSetupResolver.resolve(settingsSet, backup.activeSetupSelections[settingsSet.id] ?:
                ActiveSetupSelections(settingsSet.id)).option(ActiveSetupOptionKey.RTKLIB).dependencyActive) {
            settingsSet.rtklibProfileRef?.id
                ?.let { missingReference(it, rtklibIds, settingsSet.name, "RTKLIB profile") }
                ?.let { return it }
        }
        if (SettingsBackupProfileFamily.SOLUTION_POLICY in backup.includedProfileFamilies) {
            settingsSet.solutionPolicyProfileRef?.id
                ?.let { missingReference(it, solutionPolicyIds, settingsSet.name, "solution policy profile") }
                ?.let { return it }
        }
        missingReference(
            settingsSet.recordingOutputProfileRef.id,
            recordingIds,
            settingsSet.name,
            "recording output profile",
        )?.let { return it }
        missingReference(settingsSet.storageProfileRef.id, storageIds, settingsSet.name, "storage profile")?.let { return it }
        settingsSet.overrides.storageProfileRef?.id
            ?.let { missingReference(it, storageIds, settingsSet.name, "storage profile") }
            ?.let { return it }
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

    val knownSecretIds = backup.referencedNtripSecretIds()
    backup.plaintextPasswordsBySecretId.keys.firstOrNull { it !in knownSecretIds }?.let { secretId ->
        return "Plaintext NTRIP password references unknown secret '$secretId'."
    }

    return null
}

private fun validateRetainedOptionalProfileReferences(
    backup: SettingsBackupFile,
    retainedProfileIds: RetainedSettingsProfileIds,
): String? {
    backup.settingsSets.forEach { settingsSet ->
        if (SettingsBackupProfileFamily.NTRIP_CASTER_UPLOAD !in backup.includedProfileFamilies) {
            listOfNotNull(
                settingsSet.ntripCasterUploadProfileRef?.id,
                settingsSet.overrides.ntripCasterUploadProfileRef?.id,
            ).forEach { profileId ->
                missingReference(
                    profileId,
                    retainedProfileIds.ntripCasterUploadProfileIds,
                    settingsSet.name,
                    "retained NTRIP caster upload profile",
                )?.let { return it }
            }
        }
        if (SettingsBackupProfileFamily.RTKLIB !in backup.includedProfileFamilies &&
            ActiveSetupResolver.resolve(settingsSet, backup.activeSetupSelections[settingsSet.id] ?:
                ActiveSetupSelections(settingsSet.id)).option(ActiveSetupOptionKey.RTKLIB).dependencyActive) {
            settingsSet.rtklibProfileRef?.id
                ?.let {
                    missingReference(
                        it,
                        retainedProfileIds.rtklibProfileIds,
                        settingsSet.name,
                        "retained RTKLIB profile",
                    )
                }
                ?.let { return it }
        }
        if (SettingsBackupProfileFamily.SOLUTION_POLICY !in backup.includedProfileFamilies) {
            settingsSet.solutionPolicyProfileRef?.id
                ?.let {
                    missingReference(
                        it,
                        retainedProfileIds.solutionPolicyProfileIds,
                        settingsSet.name,
                        "retained solution policy profile",
                    )
                }
                ?.let { return it }
        }
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
