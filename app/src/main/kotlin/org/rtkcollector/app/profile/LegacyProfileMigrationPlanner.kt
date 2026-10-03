package org.rtkcollector.app.profile

import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

sealed interface LegacyCredential {
    data object Missing : LegacyCredential
    data object Unreadable : LegacyCredential
    class Available(val value: String) : LegacyCredential {
        override fun toString(): String = "Available(redacted)"
    }
}

data class LegacyChoiceProvenance(
    val workflowsBySet: Map<String, String> = emptyMap(),
    val baseCoordinatesBySet: Map<String, ProfileReference> = emptyMap(),
    val unscopedWorkflowId: String? = null,
    val unscopedBaseCoordinateId: String? = null,
)

class LegacyOwnershipMigrationPlan(
    val backup: SettingsBackupFile,
    val newSecretBindings: Set<String>,
    val stagedPasswords: Map<String, String>,
)

/** A readable password at the current explicit binding is the only credential-repair evidence. */
fun reconcileCredentialReview(
    recovery: LegacyMigrationRecovery,
    credentialForProfile: (ActiveSetupOptionKey, String) -> LegacyCredential?,
): LegacyMigrationRecovery {
    val credentialReasons = setOf(MigrationReviewReason.MISSING_CREDENTIAL, MigrationReviewReason.UNREADABLE_CREDENTIAL)
    val issues = recovery.issues.map { issue ->
        if (issue.reason !in credentialReasons || issue.profileId == null) return@map issue
        when (credentialForProfile(issue.option, issue.profileId)) {
            is LegacyCredential.Available -> issue.copy(resolution = MigrationIssueResolution.EXACT_BOUND_CREDENTIAL)
            LegacyCredential.Missing, LegacyCredential.Unreadable -> issue.copy(resolution = null)
            null -> issue
        }
    }
    return recovery.copy(issues = issues, reviewReasons = (recovery.reviewReasons - credentialReasons) +
        issues.filter { it.resolution == null }.mapNotNull { it.reason })
}

/** Reads only named legacy inputs; newly bound passwords are staged before this graph is published. */
fun planLegacyOwnershipMigration(
    source: SettingsBackupFile,
    readCredential: (String) -> LegacyCredential,
    newBinding: () -> String = { "ntrip-owner-${UUID.randomUUID()}" },
    provenance: LegacyChoiceProvenance = LegacyChoiceProvenance(),
): LegacyOwnershipMigrationPlan {
    val materialized = planLegacyProfileOwnership(source)
    val allocated = linkedSetOf<String>()
    val passwords = linkedMapOf<String, String>()
    val availability = mutableMapOf<Pair<ActiveSetupOptionKey, String>, LegacyCredential>()
    val ownerInputs = linkedMapOf<String, List<String>>()
    val forbidden = source.referencedNtripSecretIds() + materialized.committedNtripSecretIds()

    fun bind(key: ActiveSetupOptionKey, profileId: String, explicit: String, aliases: List<String>): String {
        val id = newBinding()
        require(id.isNotBlank() && id !in forbidden && allocated.add(id)) {
            "Migration secret binding is not fresh and unique."
        }
        val inputs = if (explicit.isNotBlank()) listOf(explicit) else aliases.distinct()
        ownerInputs["${key.name}:$profileId"] = inputs
        fun read(id: String): LegacyCredential = try { readCredential(id) } catch (_: Exception) { LegacyCredential.Unreadable }
        val credential = if (explicit.isNotBlank()) read(explicit) else {
            // Multiple available legacy aliases are ambiguous, even when their passwords happen to match.
            val candidates = aliases.distinct().map(::read).filter { it != LegacyCredential.Missing }
            when (candidates.size) {
                0 -> LegacyCredential.Missing
                1 -> candidates.single()
                else -> LegacyCredential.Unreadable
            }
        }
        availability[key to profileId] = credential
        if (credential is LegacyCredential.Available) passwords[id] = credential.value
        return id
    }
    val casters = materialized.ntripCasterProfiles.map { profile ->
        profile.copy(secretId = bind(ActiveSetupOptionKey.NTRIP_CASTER, profile.id, profile.secretId, listOf(ntripCasterSecretId(profile.id),
            legacyNtripCasterSecretId(profile)) + materialized.legacyNtripMountpointSecretIds(profile)))
    }
    val uploads = materialized.ntripCasterUploadProfiles.map { profile ->
        profile.copy(secretId = bind(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD, profile.id, profile.secretId, listOf(ntripCasterUploadSecretId(profile.id))))
    }
    val selections = materialized.activeSetupSelections.toMutableMap()
    val recovery = materialized.migrationRecovery.toMutableMap()
    materialized.settingsSets.forEach { set ->
        var state = selections[set.id] ?: ActiveSetupSelections(set.id)
        val prior = reconcileCredentialReview(recovery[set.id] ?:
            LegacyMigrationRecovery(source.settingsSets.single { it.id == set.id }, emptySet())) { key, id ->
            when (key) {
                ActiveSetupOptionKey.NTRIP_MOUNTPOINT -> materialized.ntripMountpointProfiles.firstOrNull { it.id == id }
                    ?.let { availability[ActiveSetupOptionKey.NTRIP_CASTER to it.casterProfileId] }
                ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> availability[key to id]
                else -> null
            }
        }
        val issues = prior.issues.toMutableList()
        val evidence = prior.choiceProvenance.toMutableMap()
        fun credentialIssue(key: ActiveSetupOptionKey, profileId: String, credential: LegacyCredential?) {
            val reason = when (credential) {
                LegacyCredential.Missing -> MigrationReviewReason.MISSING_CREDENTIAL
                LegacyCredential.Unreadable -> MigrationReviewReason.UNREADABLE_CREDENTIAL
                else -> return
            }
            issues += MigrationReviewIssue(key, "secretId", LegacyFieldDisposition.UNCERTAIN, reason, profileId)
        }
        // Profile-scoped records also cover a later switch to a currently dormant source or upload.
        materialized.ntripMountpointProfiles.forEach { mount ->
            credentialIssue(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, mount.id, availability[ActiveSetupOptionKey.NTRIP_CASTER to mount.casterProfileId])
        }
        uploads.forEach { upload -> credentialIssue(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD, upload.id, availability[ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD to upload.id]) }
        provenance.workflowsBySet[set.id]?.let { workflow ->
            evidence["workflow"] = workflow
            if (set.workflowApplicationPolicy == WorkflowApplicationPolicy.LEAVE_INTACT) state = state.copy(workflowBaselineId = workflow)
            else if (!set.isOptionLocked(ActiveSetupOptionKey.WORKFLOW) &&
                set.optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW) != SettingsSetOptionPolicy.ASK_EVERY_TIME) {
                state = state.choose(set, ActiveSetupOptionKey.WORKFLOW, SelectionChoice.profile(workflow))
            }
        }
        provenance.baseCoordinatesBySet[set.id]?.let { coordinate ->
            evidence["baseCoordinate"] = coordinate.id
            if (!set.isOptionLocked(ActiveSetupOptionKey.BASE_COORDINATE) &&
                set.optionPolicies.policyFor(ActiveSetupOptionKey.BASE_COORDINATE) != SettingsSetOptionPolicy.ASK_EVERY_TIME) {
                state = state.choose(set, ActiveSetupOptionKey.BASE_COORDINATE, SelectionChoice.profile(coordinate.id))
            }
        }
        provenance.unscopedWorkflowId?.let { evidence["unscopedWorkflow"] = it }
        provenance.unscopedBaseCoordinateId?.let { evidence["unscopedBaseCoordinate"] = it }
        recovery[set.id] = prior.copy(issues = issues.distinct(), reviewReasons = prior.reviewReasons + issues.mapNotNull { it.reason },
            choiceProvenance = evidence, ownerSecretInputs = prior.ownerSecretInputs + ownerInputs)
        selections[set.id] = state
    }
    return LegacyOwnershipMigrationPlan(materialized.copy(ntripCasterProfiles = casters,
        ntripCasterUploadProfiles = uploads, activeSetupSelections = selections, migrationRecovery = recovery,
        plaintextPasswordsBySecretId = emptyMap()), allocated, passwords)
}

/** Pure conversion of legacy set-local fields into complete, selectable profiles. */
fun planLegacyProfileOwnership(source: SettingsBackupFile): SettingsBackupFile {
    val commands = source.commandProfiles.toMutableList()
    val baudProfiles = source.usbBaudProfiles.toMutableList()
    val outputs = source.recordingPolicyProfiles.toMutableList()
    val storage = source.storageProfiles.toMutableList()
    val casters = source.ntripCasterProfiles.toMutableList()
    val mountpoints = source.ntripMountpointProfiles.toMutableList()
    val uploads = source.ntripCasterUploadProfiles.toMutableList()
    val selections = source.activeSetupSelections.toMutableMap()
    val recovery = source.migrationRecovery.toMutableMap()

    fun <T> derive(
        profiles: MutableList<T>,
        candidate: T,
        family: String,
        isProtected: (T) -> Boolean,
        toJson: (T) -> JSONObject,
        withIdentity: (T, String) -> T,
    ): T {
        val content = profileContent(toJson(candidate))
        profiles.firstOrNull { !isProtected(it) && profileContent(toJson(it)) == content }?.let { return it }
        val id = "migrated-$family-${digest(content)}"
        require(profiles.none { toJson(it).optString("id") == id }) {
            "A migrated profile ID collides with an unrelated profile."
        }
        return withIdentity(candidate, id).also(profiles::add)
    }

    val migratedSets = source.settingsSets.map { set ->
        val legacy = set.overrides
        if (!legacy.hasChanges && recovery.containsKey(set.id)) return@map set
        var state = selections[set.id] ?: ActiveSetupSelections(set.id)
        val reasons = recovery[set.id]?.reviewReasons.orEmpty().toMutableSet()
        val issues = recovery[set.id]?.issues.orEmpty().toMutableList()
        var migratedSet = set

        fun record(key: ActiveSetupOptionKey, field: String, reason: MigrationReviewReason? = null,
            disposition: LegacyFieldDisposition = if (set.isOptionLocked(key)) LegacyFieldDisposition.DORMANT
                else if (reason == null) LegacyFieldDisposition.EFFECTIVE else LegacyFieldDisposition.UNCERTAIN,
            profileId: String? = null,
        ) {
            issues += MigrationReviewIssue(key, field, disposition, reason, profileId)
            if (reason != null) reasons += reason
        }

        fun choose(key: ActiveSetupOptionKey, id: String?) {
            when (set.optionPolicies.policyFor(key)) {
                SettingsSetOptionPolicy.LOCKED -> record(key, "selection", MigrationReviewReason.DORMANT_OVERRIDE)
                SettingsSetOptionPolicy.ASK_EVERY_TIME -> record(key, "selection", MigrationReviewReason.POLICY_CONFLICT)
                else -> if (id == null) {
                    record(key, "selection", MigrationReviewReason.MISSING_PROFILE)
                } else {
                    state = state.choose(set, key, SelectionChoice.profile(id))
                }
            }
        }

        fun <T> referenced(ref: ProfileReference?, profiles: List<T>, idOf: (T) -> String): T? =
            ref?.let { reference -> profiles.firstOrNull { idOf(it) == reference.id } }

        val workflowPolicy = set.optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW)
        val workflowConsistent = when (set.workflowApplicationPolicy) {
            WorkflowApplicationPolicy.SET_SPECIFIC -> workflowPolicy in setOf(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE, SettingsSetOptionPolicy.LOCKED)
            WorkflowApplicationPolicy.LET_USER_SELECT -> workflowPolicy in setOf(SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER, SettingsSetOptionPolicy.ASK_EVERY_TIME)
            WorkflowApplicationPolicy.LEAVE_INTACT -> workflowPolicy == SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE
            else -> false
        }
        if (!workflowConsistent) record(ActiveSetupOptionKey.WORKFLOW, "workflowApplicationPolicy",
            MigrationReviewReason.POLICY_CONFLICT, LegacyFieldDisposition.UNCERTAIN)
        when (set.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_CASTER)) {
            SettingsSetOptionPolicy.LOCKED -> {
                if (set.ntripCasterRestrictionRef == null && set.ntripCasterProfileRef != null) {
                    migratedSet = migratedSet.copy(ntripCasterRestrictionRef = set.ntripCasterProfileRef)
                }
                if (migratedSet.ntripCasterRestrictionRef == null) record(ActiveSetupOptionKey.NTRIP_MOUNTPOINT,
                    "ntripCasterPolicy", MigrationReviewReason.POLICY_CONFLICT, LegacyFieldDisposition.UNCERTAIN)
            }
            SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER, SettingsSetOptionPolicy.ASK_EVERY_TIME ->
                record(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, "ntripCasterPolicy", MigrationReviewReason.POLICY_CONFLICT,
                    LegacyFieldDisposition.UNCERTAIN)
            else -> Unit
        }

        val commandRef = legacy.commandProfileRef ?: set.commandProfileRef
        val command = referenced(commandRef, commands, CommandProfile::id)
        if (legacy.command?.hasChanges() == true) {
            if (set.isOptionLocked(ActiveSetupOptionKey.RECEIVER_COMMAND)) {
                record(ActiveSetupOptionKey.RECEIVER_COMMAND, "command", MigrationReviewReason.DORMANT_OVERRIDE)
            } else if (command == null) {
                record(ActiveSetupOptionKey.RECEIVER_COMMAND, "command", MigrationReviewReason.MISSING_PROFILE)
            } else {
                val effective = command.copy(
                    initScript = legacy.command.initScript ?: command.initScript,
                    shutdownScript = legacy.command.shutdownScript ?: command.shutdownScript,
                )
                val derived = derive(commands, effective, "command", CommandProfile::isProtected,
                    CommandProfile::toJson) { candidate, id ->
                    candidate.copy(id = id, name = "Migrated commands", isProtected = false)
                }
                choose(ActiveSetupOptionKey.RECEIVER_COMMAND, derived.id)
            }
        } else if (legacy.commandProfileRef != null) {
            choose(ActiveSetupOptionKey.RECEIVER_COMMAND, command?.id)
        }

        val baudRef = legacy.usbBaudProfileRef ?: set.usbBaudProfileRef
        val baud = referenced(baudRef, baudProfiles, UsbBaudProfile::id)
        if (legacy.usbBaud?.let { it.profileBaud != null || it.serialBaud != null } == true) {
            if (set.isOptionLocked(ActiveSetupOptionKey.USB_BAUD)) {
                record(ActiveSetupOptionKey.USB_BAUD, "usbBaud", MigrationReviewReason.DORMANT_OVERRIDE)
            } else if (baud == null) {
                record(ActiveSetupOptionKey.USB_BAUD, "usbBaud", MigrationReviewReason.MISSING_PROFILE)
            } else {
                val override = legacy.usbBaud
                val effective = baud.copy(
                    profileBaud = override.profileBaud ?: baud.profileBaud,
                    serialBaud = override.serialBaud ?: baud.serialBaud,
                ).also(UsbBaudProfile::validate)
                val derived = derive(baudProfiles, effective, "usb-baud", UsbBaudProfile::isProtected,
                    UsbBaudProfile::toJson) { candidate, id ->
                    candidate.copy(id = id, name = "Migrated USB/baud", isProtected = false)
                }
                choose(ActiveSetupOptionKey.USB_BAUD, derived.id)
            }
        } else if (legacy.usbBaudProfileRef != null) {
            choose(ActiveSetupOptionKey.USB_BAUD, baud?.id)
        }
        legacy.usbBaud?.let { override ->
            listOf("usbVid" to override.usbVid, "usbPid" to override.usbPid,
                "usbDeviceName" to override.usbDeviceName).filter { it.second != null }.forEach { (field, _) ->
                record(ActiveSetupOptionKey.USB_BAUD, "usbBaud.$field", MigrationReviewReason.DORMANT_OVERRIDE,
                    LegacyFieldDisposition.DORMANT)
            }
        }

        val outputRef = legacy.recordingOutputProfileRef ?: set.recordingOutputProfileRef
        val output = referenced(outputRef, outputs, RecordingPolicyProfile::id)
        if (legacy.recordingOutput != null) {
            if (set.isOptionLocked(ActiveSetupOptionKey.RECORDING_OUTPUT)) {
                record(ActiveSetupOptionKey.RECORDING_OUTPUT, "recordingOutput", MigrationReviewReason.DORMANT_OVERRIDE)
            } else if (output == null) {
                record(ActiveSetupOptionKey.RECORDING_OUTPUT, "recordingOutput", MigrationReviewReason.MISSING_PROFILE)
            } else {
                val override = legacy.recordingOutput
                val effective = output.copy(
                    recordTxToReceiver = override.recordTxToReceiver ?: output.recordTxToReceiver,
                    recordNtripCorrectionInput = override.recordNtripCorrectionInput ?: output.recordNtripCorrectionInput,
                    exportNmea = override.exportNmea ?: output.exportNmea,
                    pppNmeaGgaQuality = override.pppNmeaGgaQuality ?: output.pppNmeaGgaQuality,
                    exportJsonSolution = override.exportJsonSolution ?: output.exportJsonSolution,
                    exportGpx = override.exportGpx ?: output.exportGpx,
                    recordRemoteBaseRaw = override.recordRemoteBaseRaw ?: output.recordRemoteBaseRaw,
                    enableMockLocation = override.enableMockLocation ?: output.enableMockLocation,
                    mockLocationRateHz = override.mockLocationRateHz ?: output.mockLocationRateHz,
                ).also(RecordingPolicyProfile::validate)
                val derived = derive(outputs, effective, "output", RecordingPolicyProfile::isProtected,
                    RecordingPolicyProfile::toJson) { candidate, id ->
                    candidate.copy(id = id, name = "Migrated outputs", isProtected = false)
                }
                choose(ActiveSetupOptionKey.RECORDING_OUTPUT, derived.id)
            }
        } else if (legacy.recordingOutputProfileRef != null) {
            choose(ActiveSetupOptionKey.RECORDING_OUTPUT, output?.id)
        }

        val storageRef = legacy.storageProfileRef ?: set.storageProfileRef
        val storageProfile = referenced(storageRef, storage, StorageProfile::id)
        if (legacy.storage != null) {
            if (set.isOptionLocked(ActiveSetupOptionKey.STORAGE)) {
                record(ActiveSetupOptionKey.STORAGE, "storage", MigrationReviewReason.DORMANT_OVERRIDE)
            } else if (storageProfile == null) {
                record(ActiveSetupOptionKey.STORAGE, "storage", MigrationReviewReason.MISSING_PROFILE)
            } else {
                val override = legacy.storage
                val kind = override.kind ?: storageProfile.kind
                val effective = storageProfile.copy(
                    kind = kind,
                    treeUri = if (kind == "SAF_TREE") override.treeUri ?: storageProfile.treeUri else null,
                    requiresTreeReselection = kind == "SAF_TREE" && (override.requiresTreeReselection ||
                        (override.treeUri == null && storageProfile.requiresTreeReselection)),
                ).also(StorageProfile::validate)
                val derived = derive(storage, effective, "storage", StorageProfile::isProtected,
                    StorageProfile::toJson) { candidate, id ->
                    candidate.copy(id = id, name = "Migrated storage", isProtected = false)
                }
                if (derived.requiresTreeReselection) record(ActiveSetupOptionKey.STORAGE, "storage.treeUri",
                    MigrationReviewReason.SAF_RESELECTION, profileId = derived.id)
                choose(ActiveSetupOptionKey.STORAGE, derived.id)
            }
        } else if (legacy.storageProfileRef != null) {
            choose(ActiveSetupOptionKey.STORAGE, storageProfile?.id)
        }

        val sourceKey = ActiveSetupOptionKey.NTRIP_MOUNTPOINT
        if (legacy.ntripCaster != null || legacy.ntripMountpoint != null ||
            legacy.ntripCasterProfileRef != null || legacy.ntripMountpointProfileRef != null
        ) {
            val mount = referenced(legacy.ntripMountpointProfileRef ?: set.ntripMountpointProfileRef,
                mountpoints, NtripMountpointProfile::id)
            val casterRef = legacy.ntripCasterProfileRef ?: set.ntripCasterProfileRef
            val caster = if (casterRef != null) referenced(casterRef, casters, NtripCasterProfile::id)
                else mount?.let { item -> casters.firstOrNull { it.id == item.casterProfileId } }
            if (set.isOptionLocked(sourceKey)) {
                record(sourceKey, "ntrip", MigrationReviewReason.DORMANT_OVERRIDE)
            } else if (mount == null || caster == null || mount.casterProfileId != caster.id) {
                record(sourceKey, "ntrip", MigrationReviewReason.CASTER_LINEAGE)
            } else {
                val override = legacy.ntripCaster
                val credentialsChanged = override?.let {
                    (it.host != null && it.host != caster.host) || (it.port != null && it.port != caster.port) ||
                        (it.username != null && it.username != caster.username)
                } == true
                val candidate = caster.copy(
                    host = override?.host ?: caster.host, port = override?.port ?: caster.port,
                    username = override?.username ?: caster.username,
                    secretId = override?.secretId ?: if (credentialsChanged) "" else caster.secretId,
                    unsafeTlsAcknowledged = if (credentialsChanged) false else caster.unsafeTlsAcknowledged,
                )
                val derivedCaster = if (override == null) caster else derive(casters, candidate, "caster",
                    NtripCasterProfile::isProtected, NtripCasterProfile::toJson) { value, id ->
                    value.copy(id = id, name = "Migrated caster", isProtected = false)
                }
                val sameSourceIdentity = (override?.host == null || override.host == caster.host) &&
                    (override?.port == null || override.port == caster.port) &&
                    (legacy.ntripMountpoint?.mountpoint == null || legacy.ntripMountpoint.mountpoint == mount.mountpoint)
                val derivedMount = derive(mountpoints, mount.copy(casterProfileId = derivedCaster.id,
                    mountpoint = legacy.ntripMountpoint?.mountpoint ?: mount.mountpoint,
                    stationId = if (sameSourceIdentity) legacy.ntripMountpoint?.stationId ?: mount.stationId else null,
                    baseLatDeg = if (sameSourceIdentity) legacy.ntripMountpoint?.baseLatDeg ?: mount.baseLatDeg else null,
                    baseLonDeg = if (sameSourceIdentity) legacy.ntripMountpoint?.baseLonDeg ?: mount.baseLonDeg else null), "mountpoint",
                    NtripMountpointProfile::isProtected, NtripMountpointProfile::toJson) { value, id ->
                    value.copy(id = id, name = "Migrated correction source", isProtected = false)
                }
                choose(sourceKey, derivedMount.id)
                if (credentialsChanged && override?.secretId.isNullOrBlank()) {
                    record(sourceKey, "ntripCaster.secretId", MigrationReviewReason.MISSING_CREDENTIAL,
                        profileId = derivedMount.id)
                }
                // These source fields remain descriptive metadata; no height or GGA is synthesized.
                legacy.ntripMountpoint?.let { metadata ->
                    listOf("stationId" to metadata.stationId, "baseLatDeg" to metadata.baseLatDeg,
                        "baseLonDeg" to metadata.baseLonDeg).filter { it.second != null }.forEach { (field, _) ->
                        record(sourceKey, "ntripMountpoint.$field",
                            if (sameSourceIdentity) null else MigrationReviewReason.CASTER_LINEAGE,
                            if (sameSourceIdentity) LegacyFieldDisposition.EFFECTIVE else LegacyFieldDisposition.UNCERTAIN,
                            derivedMount.id)
                    }
                }
            }
        }

        val uploadKey = ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD
        if (legacy.ntripCasterUpload != null || legacy.ntripCasterUploadProfileRef != null ||
            legacy.baseCasterUploadEnabled != null
        ) {
            val upload = referenced(legacy.ntripCasterUploadProfileRef ?: set.ntripCasterUploadProfileRef,
                uploads, NtripCasterUploadProfile::id)
            val enabled = legacy.baseCasterUploadEnabled ?: set.baseCasterUploadEnabled
            if (set.isOptionLocked(uploadKey)) {
                record(uploadKey, "upload", MigrationReviewReason.DORMANT_OVERRIDE)
            } else if (set.optionPolicies.policyFor(uploadKey) == SettingsSetOptionPolicy.ASK_EVERY_TIME) {
                record(uploadKey, "upload", MigrationReviewReason.POLICY_CONFLICT)
            } else if (upload == null && (enabled || legacy.ntripCasterUpload != null || legacy.ntripCasterUploadProfileRef != null)) {
                record(uploadKey, "upload", MigrationReviewReason.MISSING_PROFILE)
            } else {
                val override = legacy.ntripCasterUpload
                val changedIdentity = override?.let {
                    (it.host != null && it.host != upload?.host) || (it.port != null && it.port != upload?.port) ||
                        (it.username != null && it.username != upload?.username)
                } == true
                val derived = upload?.let { original ->
                    if (override == null) original else derive(uploads, original.copy(
                        host = override.host ?: original.host, port = override.port ?: original.port,
                        mountpoint = override.mountpoint ?: original.mountpoint,
                        username = override.username ?: original.username,
                        secretId = override.secretId ?: if (changedIdentity) "" else original.secretId,
                        unsafeTlsAcknowledged = if (changedIdentity) false else original.unsafeTlsAcknowledged,
                    ), "upload", NtripCasterUploadProfile::isProtected, NtripCasterUploadProfile::toJson) { value, id ->
                        value.copy(id = id, name = "Migrated caster upload", isProtected = false)
                    }
                }
                state = state.chooseUpload(set, UploadSelection(enabled,
                    derived?.let { SelectionChoice.profile(it.id) } ?: SelectionChoice.none()))
                if (changedIdentity && override?.secretId.isNullOrBlank()) record(uploadKey, "upload.secretId",
                    MigrationReviewReason.MISSING_CREDENTIAL, profileId = derived?.id)
            }
        }

        val legacyJson = set.toJson().optJSONObject("overrides")
        legacyJson?.keys()?.asSequence()?.forEach { field ->
            val key = when {
                field.startsWith("command") -> ActiveSetupOptionKey.RECEIVER_COMMAND
                field.startsWith("usb") -> ActiveSetupOptionKey.USB_BAUD
                field.startsWith("ntripCasterUpload") || field == "baseCasterUploadEnabled" -> uploadKey
                field.startsWith("ntrip") -> sourceKey
                field.startsWith("recording") -> ActiveSetupOptionKey.RECORDING_OUTPUT
                else -> ActiveSetupOptionKey.STORAGE
            }
            val value = legacyJson.opt(field)
            val unresolved = issues.firstOrNull { it.option == key && it.disposition == LegacyFieldDisposition.UNCERTAIN }
            if (value is JSONObject) value.keys().asSequence().forEach { nested ->
                if (!value.isNull(nested) && issues.none { it.field == "$field.$nested" }) {
                    record(key, "$field.$nested", unresolved?.reason,
                        unresolved?.disposition ?: if (set.isOptionLocked(key)) LegacyFieldDisposition.DORMANT else LegacyFieldDisposition.EFFECTIVE)
                }
            } else if (value != null && value != JSONObject.NULL && issues.none { it.field == field }) {
                record(key, field, unresolved?.reason,
                    unresolved?.disposition ?: if (set.isOptionLocked(key)) LegacyFieldDisposition.DORMANT else LegacyFieldDisposition.EFFECTIVE)
            }
        }

        selections[set.id] = state
        recovery[set.id] = (recovery[set.id] ?: LegacyMigrationRecovery(set, emptySet())).copy(
            reviewReasons = reasons, issues = issues.distinct())
        migratedSet.copy(overrides = SettingsSetOverrides())
    }

    return source.copy(
        formatVersion = SettingsBackupFile.CURRENT_FORMAT_VERSION,
        commandProfiles = commands,
        usbBaudProfiles = baudProfiles,
        recordingPolicyProfiles = outputs,
        storageProfiles = storage,
        ntripCasterProfiles = casters,
        ntripMountpointProfiles = mountpoints,
        ntripCasterUploadProfiles = uploads,
        settingsSets = migratedSets,
        activeSetupSelections = selections,
        migrationRecovery = recovery,
    )
}

private fun profileContent(json: JSONObject): String = canonicalJson(JSONObject(json.toString()).apply {
    remove("id")
    remove("name")
    remove("isProtected")
})

private fun canonicalJson(value: Any?): String = when (value) {
    is JSONObject -> value.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}") {
        JSONObject.quote(it) + ":" + canonicalJson(value.get(it))
    }
    is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonicalJson(value.get(it)) }
    else -> JSONArray().put(value).toString().let { it.substring(1, it.length - 1) }
}

private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .take(12)
    .joinToString("") { byte -> "%02x".format(byte) }
