package org.rtkcollector.app.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.rtkcollector.receiver.ublox.UbloxM8tProfiles
import org.rtkcollector.app.secrets.NtripSecretStore
import org.rtkcollector.app.secrets.StoredNtripPassword
import org.rtkcollector.app.sessions.ActiveRecordingSessionRegistry
import org.rtkcollector.app.base.BasePositionJsonCodec
import org.rtkcollector.app.base.FixedBaseHandoffPlan
import org.rtkcollector.app.base.AcceptedBaseCoordinate
import org.rtkcollector.app.base.FixedBaseCommandValidator
import java.util.IdentityHashMap
import java.util.UUID

sealed interface ConfigurationReadiness {
    data object Ready : ConfigurationReadiness
    data class ReviewRequired(val settingsSetIds: Set<String>) : ConfigurationReadiness
    data class Blocked(val reason: Reason, val message: String) : ConfigurationReadiness {
        enum class Reason { RECOVERY, UNREADABLE_GRAPH, RECORDING_ACTIVE }
    }
}

class SelectionRevisionConflictException : IllegalStateException(
    "The next-start selection changed. Review the current selection before applying this acknowledgement.",
)

class ProfileStores(context: Context) {
    private val applicationContext = context.applicationContext
    private val ownerSecrets by lazy { NtripSecretStore(applicationContext) }
    private val preferences = context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE)
    private val coordinatePreferences = context.getSharedPreferences("accepted-base-coordinates", Context.MODE_PRIVATE)
    private val journal = ProfileGraphJournal(
        NamespacedPreferenceCommitTarget(
            SharedPreferencesCommitTarget(preferences),
            SharedPreferencesCommitTarget(coordinatePreferences),
        ), preferences,
    )

    fun requireConfigurationRecovered() = synchronized(CONFIGURATION_LOCK) {
        if (journal.requireRecovered()) transientSelections.invalidate(preferences)
    }

    /** Call once while idle before normal startup reads or recording Start. */
    fun prepareIdleConfiguration(): ConfigurationReadiness = synchronized(CONFIGURATION_LOCK) {
        try {
            if (journal.requireRecovered()) transientSelections.invalidate(preferences)
        } catch (_: GraphRecoveryException) {
            transientSelections.invalidate(preferences)
            return@synchronized ConfigurationReadiness.Blocked(
                ConfigurationReadiness.Blocked.Reason.RECOVERY,
                "Settings recovery could not complete. Check device storage and retry.",
            )
        }
        if (ActiveRecordingSessionRegistry.isAnyActive()) {
            return@synchronized ConfigurationReadiness.Blocked(
                ConfigurationReadiness.Blocked.Reason.RECORDING_ACTIVE,
                "Stop recording before migrating stored profiles.",
            )
        }
        try {
            validatePriorProfileGraph()
            ConfigurationReadiness.Ready
        } catch (_: Exception) {
            ConfigurationReadiness.Blocked(
                ConfigurationReadiness.Blocked.Reason.UNREADABLE_GRAPH,
                "Stored profiles cannot be read. Restore settings from a backup before recording.",
            )
        }
    }

    fun <T> withRecoveredConfiguration(block: () -> T): T = synchronized(CONFIGURATION_LOCK) {
        try {
            if (journal.requireRecovered()) transientSelections.invalidate(preferences)
            block()
        } catch (failure: Exception) {
            if (failure !is SelectionRevisionConflictException) transientSelections.invalidate(preferences)
            throw failure
        }
    }

    fun activeSelections(settingsSet: RecordingSettingsSet): ActiveSetupSelections = withRecoveredConfiguration {
        val durable = readActiveSelections()[settingsSet.id] ?: ActiveSetupSelections(settingsSet.id)
        transientSelections.cached(preferences, durable) ?: durable
    }

    fun selectionRevision(setId: String): Long = withRecoveredConfiguration {
        require(setId.isNotBlank()) { "Settings set ID must not be blank." }
        readSelectionRevisions()[setId] ?: 0L
    }

    fun captureStartSelections(set: RecordingSettingsSet, currentWorkflowId: String?): StartSelectionLease = withRecoveredConfiguration {
        val state = activeSelections(set)
        val applicable = ActiveSetupResolver.resolve(set, state, currentWorkflowId).options.values
            .filter { it.applicable }.mapTo(linkedSetOf()) { it.key }
        transientSelections.captureStart(preferences, state, selectionRevision(set.id), applicable)
    }

    /** External owners (for example accepted coordinates) invalidate next Start, never the running snapshot. */
    fun invalidateNextStartConfiguration(setIds: Set<String>? = null): Map<String, Long> = withRecoveredConfiguration {
        validatePriorProfileGraph()
        val revisions = readSelectionRevisions().toMutableMap()
        val targets = setIds ?: (revisions.keys + storedSettingsSets().map { it.id } + readActiveSelections().keys)
        require(targets.none(String::isBlank)) { "Settings set identities must not be blank." }
        if (targets.isEmpty()) return@withRecoveredConfiguration emptyMap()
        targets.forEach { revisions[it] = Math.addExact(revisions[it] ?: 0L, 1L) }
        journal.publish(mapOf("activeSetupSelectionRevisions" to selectionRevisionsJson(revisions))) {}
        transientSelections.invalidate(preferences, targets)
        revisions.filterKeys(targets::contains)
    }

    fun saveActiveSelections(state: ActiveSetupSelections, expectedRevision: Long? = null): Long = withRecoveredConfiguration {
        val next = persistActiveSelections(state, expectedRevision)
        transientSelections.remember(preferences, state)
        next
    }

    fun savePublishedLiveSelections(candidate: ActiveSetupSelections, option: ActiveSetupOptionKey,
        expectedRevision: Long): StartSelectionLease = withRecoveredConfiguration {
        val set = requireNotNull(storedSettingsSets().singleOrNull { it.id == candidate.settingsSetId })
        val merged = activeSelections(set).withPublishedChoice(candidate, option)
        val next = persistActiveSelections(merged, expectedRevision)
        transientSelections.publishChoice(preferences, merged, next, option)
    }

    private fun persistActiveSelections(state: ActiveSetupSelections, expectedRevision: Long?): Long {
        validatePriorProfileGraph()
        val revisions = readSelectionRevisions()
        val current = revisions[state.settingsSetId] ?: 0L
        if (expectedRevision != null && expectedRevision != current) throw SelectionRevisionConflictException()
        val next = Math.addExact(current, 1L)
        journal.publish(mapOf(
            "activeSetupSelections" to activeSelectionsJson(readActiveSelections() + (state.settingsSetId to state)),
            "activeSetupSelectionRevisions" to selectionRevisionsJson(revisions + (state.settingsSetId to next)),
        )) {}
        return next
    }

    fun resetActiveSelections(
        settingsSet: RecordingSettingsSet,
        currentWorkflowId: String?,
    ): ActiveSetupSelections = withRecoveredConfiguration {
        activeSelections(settingsSet).reapply(settingsSet, currentWorkflowId).also { saveActiveSelections(it) }
    }

    fun afterStopOrFailedStart(settingsSet: RecordingSettingsSet): ActiveSetupSelections = withRecoveredConfiguration {
        activeSelections(settingsSet).afterStopOrFailedStart().also { saveActiveSelections(it) }
    }

    fun migrationRecovery(): Map<String, LegacyMigrationRecovery> = withRecoveredConfiguration {
        readMigrationRecovery()
    }

    fun migrationReviewIssues(set: RecordingSettingsSet, secretStore: NtripSecretStore = ownerSecrets): List<MigrationReviewIssue> = withRecoveredConfiguration {
        refreshCredentialRecovery(secretStore)
        readMigrationRecovery()[set.id]?.blockingIssues(set, activeSelections(set)).orEmpty()
    }

    /** Reconcile named current owner bindings after an editor repairs a password; no arbitrary acknowledgement. */
    fun refreshCredentialRecovery(secretStore: NtripSecretStore = ownerSecrets) = withRecoveredConfiguration {
        val records = readMigrationRecovery()
        if (records.values.none { record -> record.issues.any {
            it.reason == MigrationReviewReason.MISSING_CREDENTIAL || it.reason == MigrationReviewReason.UNREADABLE_CREDENTIAL
        } }) return@withRecoveredConfiguration
        val casters = ntripCasterProfiles().associateBy { it.id }
        val sources = ntripMountpointProfiles().associateBy { it.id }
        val uploads = ntripCasterUploadProfiles().associateBy { it.id }
        val credentials = mutableMapOf<String, LegacyCredential>()
        val updated = records.mapValues { (_, record) ->
            reconcileCredentialReview(record) { key, id ->
                val binding = when (key) {
                    ActiveSetupOptionKey.NTRIP_MOUNTPOINT -> sources[id]?.let { casters[it.casterProfileId]?.secretId }
                    ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> uploads[id]?.secretId
                    else -> null
                } ?: return@reconcileCredentialReview null
                if (binding.isBlank()) LegacyCredential.Missing else credentials.getOrPut(binding) {
                    when (val value = secretStore.readPassword(binding)) {
                        StoredNtripPassword.Missing -> LegacyCredential.Missing
                        StoredNtripPassword.Unreadable -> LegacyCredential.Unreadable
                        is StoredNtripPassword.Available -> LegacyCredential.Available(value.value)
                    }
                }
            }
        }
        val changedSets = updated.keys.filter { updated[it] != records[it] }
        if (changedSets.isNotEmpty()) {
            validatePriorProfileGraph()
            val revisions = readSelectionRevisions().toMutableMap()
            changedSets.forEach { revisions[it] = Math.addExact(revisions[it] ?: 0L, 1L) }
            journal.publish(mapOf("migrationRecovery" to migrationRecoveryJson(updated),
                "activeSetupSelectionRevisions" to selectionRevisionsJson(revisions))) {}
        }
    }

    fun planImportedSettings(
        backup: SettingsBackupFile,
        persistedSafTreeUrisWithWriteAccess: Set<String>,
    ): SettingsBackupImportPlan = withRecoveredConfiguration {
        settingsBackupImportPlan(backup, persistedSafTreeUrisWithWriteAccess,
            retainedProfileIds = RetainedSettingsProfileIds(
                ntripCasterUploadProfiles().mapTo(linkedSetOf()) { it.id },
                rtklibProfiles().mapTo(linkedSetOf()) { it.id },
                solutionPolicyProfiles().mapTo(linkedSetOf()) { it.id },
            ), retainedUploadProfiles = ntripCasterUploadProfiles())
    }

    fun committedNtripSecretIds(): Set<String> = withRecoveredConfiguration {
        (ntripCasterProfiles().map { it.secretId } + ntripCasterUploadProfiles().map { it.secretId })
            .filterTo(linkedSetOf(), String::isNotBlank)
    }

    /** Explicit idle initialization only; never invoke from a capture or dashboard refresh loop. */
    fun initializeOwnershipWhileIdle(
        secretStore: NtripSecretStore,
        provenance: LegacyChoiceProvenance = LegacyChoiceProvenance(),
    ): ConfigurationReadiness = synchronized(CONFIGURATION_LOCK) {
        val readiness = prepareIdleConfiguration()
        if (readiness is ConfigurationReadiness.Blocked) return@synchronized readiness
        try {
            val safGrants = applicationContext.contentResolver.persistedUriPermissions
                .filter { hasPersistedSafTreeAuthority(it.isReadPermission, it.isWritePermission) }.mapTo(linkedSetOf()) { it.uri.toString() }
            if (preferences.getString("profileOwnershipMigrationVersion", null) != "1") {
                val source = storedProfileGraphSnapshot(retainOriginal = true)
                val plan = planLegacyOwnershipMigration(source, { id ->
                    when (val credential = secretStore.readPassword(id)) {
                        StoredNtripPassword.Missing -> LegacyCredential.Missing
                        StoredNtripPassword.Unreadable -> LegacyCredential.Unreadable
                        is StoredNtripPassword.Available -> LegacyCredential.Available(credential.value)
                    }
                }, provenance = provenance.copy(
                    unscopedWorkflowId = provenance.unscopedWorkflowId ?: source.selectedWorkflowId,
                ))
                val privateRef = "profile-recovery-${UUID.randomUUID()}"
                val privateInput = requireNotNull(source.privateRecoveryInput)
                val safeBackup = plan.backup.withValidatedSafAuthority(safGrants)
                publishImportedSettings(safeBackup, safeBackup.settingsSets,
                    safeBackup.selectedSettingsSetId ?: safeBackup.settingsSets.first().id,
                    safeBackup.selectedWorkflowId, safeBackup.lastActiveNtripMountpointProfileId,
                    stageSecrets = {
                        secretStore.requireUnallocated(plan.newSecretBindings + privateRef)
                        secretStore.putNewPasswords(plan.stagedPasswords +
                            (privateRef to privateInput.contentForPrivateStorage()))
                    },
                    privateRecoveryRef = privateRef,
                )
            } else {
                val source = storedProfileGraphSnapshot(retainOriginal = true)
                val safeBackup = source.withValidatedSafAuthority(safGrants)
                if (safeBackup.storageProfiles != source.storageProfiles || safeBackup.migrationRecovery != source.migrationRecovery) {
                    val privateRef = "profile-recovery-${UUID.randomUUID()}"
                    val privateInput = requireNotNull(source.privateRecoveryInput)
                    publishImportedSettings(safeBackup, safeBackup.settingsSets,
                        safeBackup.selectedSettingsSetId ?: safeBackup.settingsSets.first().id,
                        safeBackup.selectedWorkflowId, safeBackup.lastActiveNtripMountpointProfileId,
                        stageSecrets = { secretStore.putNewPasswords(mapOf(privateRef to privateInput.contentForPrivateStorage())) },
                        privateRecoveryRef = privateRef)
                }
            }
            refreshCredentialRecovery(secretStore)
            val pending = readMigrationRecovery().filterValues { it.reviewReasons.isNotEmpty() }.keys
            if (pending.isEmpty()) ConfigurationReadiness.Ready else ConfigurationReadiness.ReviewRequired(pending)
        } catch (_: GraphRecoveryException) {
            ConfigurationReadiness.Blocked(ConfigurationReadiness.Blocked.Reason.RECOVERY,
                "Settings recovery could not complete. Check device storage and retry.")
        } catch (_: Exception) {
            ConfigurationReadiness.Blocked(ConfigurationReadiness.Blocked.Reason.UNREADABLE_GRAPH,
                "Settings migration could not complete. Keep the recovery data and retry after checking storage and credentials.")
        }
    }

    fun commandProfiles(): List<CommandProfile> =
        readProfiles(
            key = "commandProfiles",
            defaults = ::defaultCommandProfiles,
            decode = CommandProfile::fromJson,
            migrate = ProfileStoreMigrations::commandProfiles,
            encode = CommandProfile::toJson,
        )

    fun saveCommandProfiles(profiles: List<CommandProfile>) =
        writeProfiles("commandProfiles", profiles.onEach(CommandProfile::validate).map(CommandProfile::toJson))

    fun usbBaudProfiles(): List<UsbBaudProfile> =
        readProfiles(
            key = "usbBaudProfiles",
            defaults = ::defaultUsbBaudProfiles,
            decode = UsbBaudProfile::fromJson,
            migrate = ProfileStoreMigrations::usbBaudProfiles,
            encode = UsbBaudProfile::toJson,
        )

    fun saveUsbBaudProfiles(profiles: List<UsbBaudProfile>) =
        writeProfiles("usbBaudProfiles", profiles.onEach(UsbBaudProfile::validate).map(UsbBaudProfile::toJson))

    fun ntripCasterProfiles(): List<NtripCasterProfile> =
        readProfiles(
            key = "ntripCasterProfiles",
            defaults = ::defaultNtripCasterProfiles,
            decode = NtripCasterProfile::fromJson,
            migrate = ProfileStoreMigrations::ntripCasterProfiles,
            encode = NtripCasterProfile::toJson,
        )

    fun saveNtripCasterProfiles(
        profiles: List<NtripCasterProfile>,
        freshlyAcknowledgedEditorProfileId: String? = null,
    ) = withRecoveredConfiguration {
        writeProfiles(
            "ntripCasterProfiles",
            clearChangedNtripCasterAcknowledgements(profiles, freshlyAcknowledgedEditorProfileId)
                .onEach(NtripCasterProfile::validate)
                .map(NtripCasterProfile::toJson),
        )
    }

    fun ntripCasterUploadProfiles(): List<NtripCasterUploadProfile> =
        readProfiles(
            key = "ntripCasterUploadProfiles",
            defaults = ::defaultNtripCasterUploadProfiles,
            decode = NtripCasterUploadProfile::fromJson,
            migrate = ProfileStoreMigrations::ntripCasterUploadProfiles,
            encode = NtripCasterUploadProfile::toJson,
        )

    fun saveNtripCasterUploadProfiles(
        profiles: List<NtripCasterUploadProfile>,
        freshlyAcknowledgedEditorProfileId: String? = null,
    ) = withRecoveredConfiguration {
        writeProfiles(
            "ntripCasterUploadProfiles",
            clearChangedNtripCasterUploadAcknowledgements(profiles, freshlyAcknowledgedEditorProfileId)
                .onEach(NtripCasterUploadProfile::validate)
                .map(NtripCasterUploadProfile::toJson),
        )
    }

    fun ntripMountpointProfiles(): List<NtripMountpointProfile> =
        readProfiles(
            key = "ntripMountpointProfiles",
            defaults = ::defaultNtripMountpointProfiles,
            decode = NtripMountpointProfile::fromJson,
            migrate = ProfileStoreMigrations::ntripMountpointProfiles,
            encode = NtripMountpointProfile::toJson,
        )

    fun saveNtripMountpointProfiles(profiles: List<NtripMountpointProfile>) =
        writeProfiles(
            "ntripMountpointProfiles",
            profiles.onEach(NtripMountpointProfile::validate).map(NtripMountpointProfile::toJson),
        )

    fun recordingPolicyProfiles(): List<RecordingPolicyProfile> =
        readProfiles(
            key = "recordingPolicyProfiles",
            defaults = ::defaultRecordingPolicyProfiles,
            decode = RecordingPolicyProfile::fromJson,
            migrate = ProfileStoreMigrations::recordingPolicyProfiles,
            encode = RecordingPolicyProfile::toJson,
        )

    fun saveRecordingPolicyProfiles(profiles: List<RecordingPolicyProfile>) =
        writeProfiles(
            "recordingPolicyProfiles",
            profiles.onEach(RecordingPolicyProfile::validate).map(RecordingPolicyProfile::toJson),
        )

    fun rtklibProfiles(): List<RtklibProfile> =
        readProfiles(
            key = "rtklibProfiles",
            defaults = ::defaultRtklibProfiles,
            decode = RtklibProfile::fromJson,
            encode = RtklibProfile::toJson,
        )

    fun saveRtklibProfiles(profiles: List<RtklibProfile>) =
        writeProfiles(
            "rtklibProfiles",
            profiles.onEach(RtklibProfile::validate).map(RtklibProfile::toJson),
        )

    fun solutionPolicyProfiles(): List<SolutionPolicyProfile> =
        readProfiles(
            key = "solutionPolicyProfiles",
            defaults = ::defaultSolutionPolicyProfiles,
            decode = SolutionPolicyProfile::fromJson,
            encode = SolutionPolicyProfile::toJson,
        )

    fun saveSolutionPolicyProfiles(profiles: List<SolutionPolicyProfile>) =
        writeProfiles(
            "solutionPolicyProfiles",
            profiles.onEach(SolutionPolicyProfile::validate).map(SolutionPolicyProfile::toJson),
        )

    fun storageProfiles(): List<StorageProfile> =
        readProfiles(
            key = "storageProfiles",
            defaults = ::defaultStorageProfiles,
            decode = StorageProfile::fromJson,
            migrate = ProfileStoreMigrations::storageProfiles,
            encode = StorageProfile::toJson,
        )

    fun saveStorageProfiles(profiles: List<StorageProfile>) = withRecoveredConfiguration {
        ActiveRecordingSessionRegistry.requireNoActiveRecording("save storage profiles")
        profiles.forEach(StorageProfile::validate)
        validatePriorProfileGraph()
        val prior = storedProfileGraphSnapshot()
        val grants = applicationContext.contentResolver.persistedUriPermissions
            .filter { hasPersistedSafTreeAuthority(it.isReadPermission, it.isWritePermission) }.mapTo(linkedSetOf()) { it.uri.toString() }
        val updated = prior.copy(storageProfiles = profiles).withValidatedSafAuthority(grants)
        val changes = linkedMapOf<String, String?>(
            "storageProfiles" to profileJson(updated.storageProfiles, StorageProfile::toJson),
            "activeSetupSelectionRevisions" to selectionRevisionsJson(revisionsAfterGraphChange()),
        )
        if (updated.migrationRecovery != prior.migrationRecovery) {
            changes["migrationRecovery"] = migrationRecoveryJson(updated.migrationRecovery)
        }
        journal.publish(changes) {}
        transientSelections.invalidate(preferences)
    }

    fun settingsSets(): List<RecordingSettingsSet> =
        readProfiles(
            key = "settingsSets",
            defaults = ::defaultSettingsSets,
            decode = RecordingSettingsSet::fromJson,
            migrate = ProfileStoreMigrations::settingsSets,
            encode = RecordingSettingsSet::toJson,
        )

    fun saveSettingsSets(settingsSets: List<RecordingSettingsSet>) = withRecoveredConfiguration {
        publishSettingsSets(settingsSets)
    }

    fun saveSettingsSetsWithValidatedRepair(settingsSets: List<RecordingSettingsSet>, repairSetId: String,
        baseCoordinates: List<AcceptedBaseCoordinate>): List<RecordingSettingsSet> = withRecoveredConfiguration {
        ActiveRecordingSessionRegistry.requireNoActiveRecording("apply migration repairs")
        val prior = storedSettingsSets().firstOrNull { it.id == repairSetId }
        require(prior != null && !prior.isProtected) { "Copy protected settings before repairing them." }
        val recovery = readMigrationRecovery()
        val record = requireNotNull(recovery[repairSetId]) { "No migration repair is pending for this settings set." }
        val edited = requireNotNull(settingsSets.firstOrNull { it.id == repairSetId })
        val graph = ActiveSetupProfileGraph(commandProfiles(), usbBaudProfiles(), ntripCasterProfiles(),
            ntripMountpointProfiles(), ntripCasterUploadProfiles(), rtklibProfiles(), solutionPolicyProfiles(),
            recordingPolicyProfiles(), storageProfiles(), baseCoordinates)
        val grants = applicationContext.contentResolver.persistedUriPermissions
            .filter { hasPersistedSafTreeAuthority(it.isReadPermission, it.isWritePermission) }
            .mapTo(linkedSetOf()) { it.uri.toString() }
        val plan = planLegacyMigrationRepair(edited, record, graph, ActiveSetupOptionKey.entries.toSet(), grants) { binding ->
            when (val value = ownerSecrets.readPassword(binding)) {
                StoredNtripPassword.Missing -> LegacyCredential.Missing
                StoredNtripPassword.Unreadable -> LegacyCredential.Unreadable
                is StoredNtripPassword.Available -> LegacyCredential.Available(value.value)
            }
        }
        val repaired = settingsSets.map { if (it.id == repairSetId) plan.settingsSet else it }
        publishSettingsSets(repaired, recovery + (repairSetId to plan.recovery))
        repaired
    }

    private fun publishSettingsSets(settingsSets: List<RecordingSettingsSet>,
        recovery: Map<String, LegacyMigrationRecovery>? = null) {
        validatePriorProfileGraph()
        val previous = storedSettingsSets().associateBy { it.id }
        val durable = readActiveSelections()
        val updated = settingsSets.onEach(RecordingSettingsSet::validate).associate { set ->
            val state = durable[set.id] ?: ActiveSetupSelections(set.id)
            set.id to (previous[set.id]?.let { state.afterPolicyChange(it, set) } ?: ActiveSetupSelections(set.id))
        }
        require(settingsSets.map { it.id }.distinct().size == settingsSets.size) { "Settings set identities must be unique." }
        val changes = mutableMapOf(
            "settingsSets" to profileJson(settingsSets, RecordingSettingsSet::toJson),
            "activeSetupSelections" to activeSelectionsJson(updated),
            "activeSetupSelectionRevisions" to selectionRevisionsJson(revisionsAfterGraphChange(updated.keys)),
        )
        recovery?.let { changes["migrationRecovery"] = migrationRecoveryJson(it) }
        journal.publish(changes) {}
        transientSelections.invalidate(preferences)
    }

    fun publishFixedBaseHandoff(plan: FixedBaseHandoffPlan, filter: ProfileDeviceFilter) = withRecoveredConfiguration {
        ActiveRecordingSessionRegistry.requireNoActiveRecording("publish fixed-base handoff")
        validatePriorProfileGraph()
        require(commandProfiles() == plan.priorCommands && settingsSets() == plan.priorSettingsSets) {
            "Settings changed while stopping recording; review the fixed-base handoff again."
        }
        require(plan.commands.map { it.id }.distinct().size == plan.commands.size)
        require(plan.settingsSets.map { it.id }.distinct().size == plan.settingsSets.size)
        plan.commands.forEach(CommandProfile::validate)
        plan.settingsSets.forEach(RecordingSettingsSet::validate)
        require(plan.targetSet in plan.settingsSets)
        require(plan.selections.settingsSetId == plan.targetSet.id)
        require(plan.targetSet.workflowId == "fixed-base" && plan.targetSet.workflowActivationPolicyProblem() == null) {
            "Fixed-base workflow policy must be reviewed before handoff."
        }
        val selectedCommand = requireNotNull(plan.commands.firstOrNull { it.id == plan.targetSet.commandProfileRef.id })
        FixedBaseCommandValidator.validateSelectedCoordinateMatchesProfile(selectedCommand, plan.coordinate)
        plan.overwritesCommandId?.let { overwrittenId ->
            val affectedNow = settingsSets().asSequence().filter { it.id != plan.targetSet.id }
                .filter { set ->
                    val selections = activeSelections(set)
                    overwrittenId == set.commandProfileRef.id ||
                        listOf(selections.activeChoices, selections.rememberedChoices, selections.transientChoices)
                            .any { it[ActiveSetupOptionKey.RECEIVER_COMMAND]?.profileId == overwrittenId }
                }.map { it.id }.toSet()
            require(affectedNow == plan.confirmedAffectedSettingsSetIds) {
                "Shared command usage changed; confirm affected settings sets again."
            }
        }
        val existingCoordinates = coordinatePreferences.getString("acceptedBaseCoordinates", null)
            ?.let { raw ->
                val array = JSONArray(raw)
                (0 until array.length()).map { index ->
                    BasePositionJsonCodec.decode(array.getJSONObject(index).toString(),
                        "base-coordinate-$index", "Base coordinate ${index + 1}")
                }
            }.orEmpty()
        val updatedCoordinates = if (existingCoordinates.any { it.id == plan.coordinate.id }) {
            existingCoordinates.map { if (it.id == plan.coordinate.id) plan.coordinate else it }
        } else listOf(plan.coordinate) + existingCoordinates
        val coordinateJson = profileJson(updatedCoordinates) {
            JSONObject(BasePositionJsonCodec.encode(it))
        }
        val durable = readActiveSelections()
        val revisions = revisionsAfterGraphChange(plan.settingsSets.mapTo(linkedSetOf()) { it.id })
        journal.publish(mapOf(
            "commandProfiles" to profileJson(plan.commands, CommandProfile::toJson),
            "settingsSets" to profileJson(plan.settingsSets, RecordingSettingsSet::toJson),
            "activeSetupSelections" to activeSelectionsJson(durable + (plan.targetSet.id to plan.selections)),
            "activeSetupSelectionRevisions" to selectionRevisionsJson(revisions),
            "selectedSettingsSetId" to plan.targetSet.id,
            "selectedWorkflowId" to "fixed-base",
            "lastFixedBaseSettingsSetId.${filter.storageValue}" to plan.targetSet.id,
            "coordinate.acceptedBaseCoordinates" to coordinateJson,
            "coordinate.selectedAcceptedBaseCoordinateId" to plan.coordinate.id,
        )) {}
        transientSelections.invalidate(preferences)
        transientSelections.remember(preferences, plan.selections)
    }

    /** Replaces one validated settings snapshot without exposing a partially imported profile graph. */
    fun replaceImportedSettings(
        backup: SettingsBackupFile,
        settingsSets: List<RecordingSettingsSet>,
        selectedSettingsSetId: String,
        selectedWorkflowId: String?,
        lastActiveNtripMountpointProfileId: String?,
    ) {
        require(backup.plaintextPasswordsBySecretId.isEmpty()) {
            "Credential-bearing imports require staged secret publication."
        }
        require(backup.privateRecoveryInput == null) { "Original input requires private staged recovery publication." }
        publishImportedSettings(
            backup, settingsSets, selectedSettingsSetId, selectedWorkflowId,
            lastActiveNtripMountpointProfileId, stageSecrets = {},
        )
    }

    fun publishImportedSettings(
        backup: SettingsBackupFile,
        settingsSets: List<RecordingSettingsSet>,
        selectedSettingsSetId: String,
        selectedWorkflowId: String?,
        lastActiveNtripMountpointProfileId: String?,
        secretStore: NtripSecretStore,
    ) = withRecoveredConfiguration {
        ActiveRecordingSessionRegistry.requireNoActiveRecording("import settings")
        val bindings = backup.committedNtripSecretIds()
        val owners = backup.ntripCasterProfiles.size + backup.ntripCasterUploadProfiles.size
        require(bindings.size == owners) { "Imported NTRIP owner bindings must be unique and nonblank." }
        require(backup.plaintextPasswordsBySecretId.keys.all(bindings::contains)) {
            "Imported NTRIP passwords must belong to committed profiles."
        }
        val prior = storedProfileGraphSnapshot()
        val liveBindings = prior.ntripCasterProfiles.map { it.secretId } + prior.ntripCasterUploadProfiles.map { it.secretId }
        require(bindings.none { it in liveBindings }) { "Imported secret bindings must not overwrite live owner bindings." }
        val privateRef = backup.privateRecoveryInput?.let { "profile-recovery-${UUID.randomUUID()}" }
        publishImportedSettings(
            backup, settingsSets, selectedSettingsSetId, selectedWorkflowId,
            lastActiveNtripMountpointProfileId,
            stageSecrets = {
                secretStore.requireUnallocated(bindings + listOfNotNull(privateRef))
                val original = backup.privateRecoveryInput
                val privatePayload = if (original != null && privateRef != null) {
                    mapOf(privateRef to original.contentForPrivateStorage())
                } else emptyMap()
                secretStore.putNewPasswords(backup.plaintextPasswordsBySecretId + privatePayload)
            },
            privateRecoveryRef = privateRef,
        )
    }

    fun buildCommittedBackup(
        options: SettingsSetExportOptions,
        secretStore: NtripSecretStore,
        exportedAtEpochMillis: Long = System.currentTimeMillis(),
    ): SettingsBackupFile = buildCommittedBackup(options, exportedAtEpochMillis, secretStore::getPassword)

    fun buildCommittedBackup(
        options: SettingsSetExportOptions,
        exportedAtEpochMillis: Long = System.currentTimeMillis(),
        passwordExport: (String) -> String? = { null },
    ): SettingsBackupFile = withRecoveredConfiguration {
        validatePriorProfileGraph()
        val casters = ntripCasterProfiles()
        val uploads = ntripCasterUploadProfiles()
        SettingsBackupFile.fromProfiles(
            commandProfiles = commandProfiles(),
            usbBaudProfiles = usbBaudProfiles(),
            ntripCasterProfiles = casters,
            ntripCasterUploadProfiles = uploads,
            ntripMountpointProfiles = ntripMountpointProfiles(),
            recordingPolicyProfiles = recordingPolicyProfiles(),
            rtklibProfiles = rtklibProfiles(),
            solutionPolicyProfiles = solutionPolicyProfiles(),
            storageProfiles = storageProfiles(),
            settingsSets = settingsSets(),
            selectedSettingsSetId = selectedSettingsSetId(),
            selectedWorkflowId = selectedWorkflowId(),
            lastActiveNtripMountpointProfileId = lastActiveNtripMountpointProfileId(),
            passwordsBySecretId = emptyMap(),
            options = SettingsSetExportOptions(),
            exportedAtEpochMillis = exportedAtEpochMillis,
            activeSetupSelections = readActiveSelections(),
            migrationRecovery = readMigrationRecovery(),
        ).withPasswordExport(options, passwordExport)
    }

    private fun publishImportedSettings(
        backup: SettingsBackupFile,
        settingsSets: List<RecordingSettingsSet>,
        selectedSettingsSetId: String,
        selectedWorkflowId: String?,
        lastActiveNtripMountpointProfileId: String?,
        stageSecrets: () -> Unit,
        privateRecoveryRef: String? = null,
    ) = withRecoveredConfiguration {
        ActiveRecordingSessionRegistry.requireNoActiveRecording("publish settings")
        validatePriorProfileGraph()
        require(settingsSets.any { it.id == selectedSettingsSetId }) {
            "Imported selected settings set is missing."
        }
        val values = linkedMapOf(
            "commandProfiles" to profileJson(backup.commandProfiles.onEach(CommandProfile::validate), CommandProfile::toJson),
            "usbBaudProfiles" to profileJson(backup.usbBaudProfiles.onEach(UsbBaudProfile::validate), UsbBaudProfile::toJson),
            "ntripCasterProfiles" to profileJson(
                backup.ntripCasterProfiles.onEach(NtripCasterProfile::validate),
                NtripCasterProfile::toJson,
            ),
            "ntripMountpointProfiles" to profileJson(
                backup.ntripMountpointProfiles.onEach(NtripMountpointProfile::validate),
                NtripMountpointProfile::toJson,
            ),
            "recordingPolicyProfiles" to profileJson(
                backup.recordingPolicyProfiles.onEach(RecordingPolicyProfile::validate),
                RecordingPolicyProfile::toJson,
            ),
            "storageProfiles" to profileJson(backup.storageProfiles.onEach(StorageProfile::validate), StorageProfile::toJson),
            "settingsSets" to profileJson(settingsSets.onEach(RecordingSettingsSet::validate), RecordingSettingsSet::toJson),
        )
        if (SettingsBackupProfileFamily.NTRIP_CASTER_UPLOAD in backup.includedProfileFamilies) {
            values["ntripCasterUploadProfiles"] = profileJson(
                backup.ntripCasterUploadProfiles.onEach(NtripCasterUploadProfile::validate),
                NtripCasterUploadProfile::toJson,
            )
        } else if (backup.ntripCasterUploadProfiles.isNotEmpty()) {
            val retained = storedProfileGraphSnapshot().ntripCasterUploadProfiles
            require(backup.ntripCasterUploadProfiles.none { candidate -> retained.any { it.id == candidate.id } }) {
                "Imported derived uploads must not replace an omitted profile family."
            }
            values["ntripCasterUploadProfiles"] = profileJson(
                (retained + backup.ntripCasterUploadProfiles).onEach(NtripCasterUploadProfile::validate),
                NtripCasterUploadProfile::toJson,
            )
        }
        if (SettingsBackupProfileFamily.RTKLIB in backup.includedProfileFamilies) {
            values["rtklibProfiles"] = profileJson(backup.rtklibProfiles.onEach(RtklibProfile::validate), RtklibProfile::toJson)
        }
        if (SettingsBackupProfileFamily.SOLUTION_POLICY in backup.includedProfileFamilies) {
            values["solutionPolicyProfiles"] = profileJson(
                backup.solutionPolicyProfiles.onEach(SolutionPolicyProfile::validate),
                SolutionPolicyProfile::toJson,
            )
        }

        val changes = linkedMapOf<String, String?>().apply {
            putAll(values)
            put("activeSetupSelections", activeSelectionsJson(backup.activeSetupSelections))
            put("migrationRecovery", migrationRecoveryJson(backup.migrationRecovery))
            put("profileOwnershipMigrationVersion", "1")
            if (privateRecoveryRef != null) put("privateOwnershipRecoveryRef", privateRecoveryRef)
            val revisions = readSelectionRevisions()
            val changedIds = revisions.keys + readActiveSelections().keys + storedSettingsSets().map { it.id } + settingsSets.map { it.id }
            put("activeSetupSelectionRevisions", selectionRevisionsJson(changedIds.associateWith {
                Math.addExact(revisions[it] ?: 0L, 1L)
            }))
            put("selectedSettingsSetId", selectedSettingsSetId)
            put("selectedWorkflowId", selectedWorkflowId?.takeIf(String::isNotBlank))
            put(
                "lastActiveNtripMountpointProfileId",
                lastActiveNtripMountpointProfileId?.takeIf(String::isNotBlank),
            )
        }
        journal.publish(changes, stageSecrets)
        transientSelections.invalidate(preferences)
    }

    private fun readSelectionRevisions(): Map<String, Long> {
        val json = JSONObject(preferences.getString("activeSetupSelectionRevisions", null) ?: "{}")
        return json.keys().asSequence().associateWith { key ->
            val value = json.get(key)
            require(value is Number && value.toLong() >= 0 && value.toString() == value.toLong().toString()) {
                "Stored selection revisions are unreadable."
            }
            value.toLong()
        }
    }

    private fun selectionRevisionsJson(revisions: Map<String, Long>): String = JSONObject(revisions).toString()

    fun selectedSettingsSetId(): String = withRecoveredConfiguration {
        preferences.getString("selectedSettingsSetId", null) ?: defaultSettingsSets().first().id
    }

    fun saveSelectedSettingsSetId(id: String) = withRecoveredConfiguration {
        require(id.isNotBlank()) { "Selected settings set id must not be blank." }
        writePreference("selectedSettingsSetId", id)
    }

    fun selectedDeviceFilter(): ProfileDeviceFilter = withRecoveredConfiguration {
        ProfileDeviceFilter.fromStorageValue(preferences.getString("selectedDeviceFilter", null))
    }

    fun saveSelectedDeviceFilter(filter: ProfileDeviceFilter) = withRecoveredConfiguration {
        writePreference("selectedDeviceFilter", filter.storageValue)
    }

    fun lastFixedBaseSettingsSetId(filter: ProfileDeviceFilter): String? = withRecoveredConfiguration {
        preferences.getString("lastFixedBaseSettingsSetId.${filter.storageValue}", null)
            ?.takeIf(String::isNotBlank)
    }

    fun saveLastFixedBaseSettingsSetId(filter: ProfileDeviceFilter, id: String?) = withRecoveredConfiguration {
        writePreference("lastFixedBaseSettingsSetId.${filter.storageValue}", id?.takeIf(String::isNotBlank))
    }

    fun selectedWorkflowId(): String? = withRecoveredConfiguration {
        preferences.getString("selectedWorkflowId", null)?.takeIf(String::isNotBlank)
    }

    fun saveSelectedWorkflowId(id: String?) = withRecoveredConfiguration {
        writePreference("selectedWorkflowId", id?.takeIf(String::isNotBlank))
    }

    fun lastActiveNtripMountpointProfileId(): String? = withRecoveredConfiguration {
        preferences.getString("lastActiveNtripMountpointProfileId", null)
            ?.takeIf { it.isNotBlank() && !it.equals("a", ignoreCase = true) }
    }

    fun saveLastActiveNtripMountpointProfileId(id: String?) = withRecoveredConfiguration {
        writePreference("lastActiveNtripMountpointProfileId", id?.takeIf(String::isNotBlank))
    }

    fun duplicateId(prefix: String): String =
        "$prefix-${System.currentTimeMillis()}"

    private fun <T> readProfiles(
        key: String,
        defaults: () -> List<T>,
        decode: (org.json.JSONObject) -> T,
        migrate: (List<T>, List<T>) -> List<T> = { profiles, _ -> profiles },
        encode: ((T) -> org.json.JSONObject)? = null,
    ): List<T> = withRecoveredConfiguration {
        val raw = preferences.getString(key, null) ?: return@withRecoveredConfiguration defaults()
        val decoded = try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index -> decode(array.getJSONObject(index)) }
        } catch (failure: Exception) {
            throw IllegalStateException("Stored $key is unreadable; defaults were not substituted.", failure)
        }
        val migrated = migrate(decoded, defaults())
        if (migrated != decoded && encode != null) {
            ActiveRecordingSessionRegistry.requireNoActiveRecording("migrate stored profiles")
            writeProfiles(key, migrated.map(encode))
        }
        migrated
    }

    private fun writeProfiles(key: String, jsonObjects: List<org.json.JSONObject>) = withRecoveredConfiguration {
        writePreference(key, profileJson(jsonObjects) { it })
    }

    private fun writePreference(key: String, value: String?) {
        validatePriorProfileGraph()
        val changes = linkedMapOf(key to value)
        val graphChanged = key.endsWith("Profiles") || key == "settingsSets"
        if (graphChanged) changes["activeSetupSelectionRevisions"] = selectionRevisionsJson(revisionsAfterGraphChange())
        journal.publish(changes, stageSecrets = {})
        if (graphChanged) transientSelections.invalidate(preferences)
    }

    private fun storedSettingsSets(): List<RecordingSettingsSet> {
        val raw = preferences.getString("settingsSets", null) ?: return defaultSettingsSets()
        val array = JSONArray(raw)
        return (0 until array.length()).map { RecordingSettingsSet.fromJson(array.getJSONObject(it)) }
    }

    private fun storedProfileGraphSnapshot(retainOriginal: Boolean = false): SettingsBackupFile {
        fun <T> profiles(key: String, defaults: () -> List<T>, decode: (JSONObject) -> T): List<T> {
            val raw = preferences.getString(key, null) ?: return defaults()
            val array = JSONArray(raw)
            return (0 until array.length()).map { decode(array.getJSONObject(it)) }
        }
        val backup = SettingsBackupFile.fromProfiles(
            profiles("commandProfiles", ::defaultCommandProfiles, CommandProfile::fromJson),
            profiles("usbBaudProfiles", ::defaultUsbBaudProfiles, UsbBaudProfile::fromJson),
            profiles("ntripCasterProfiles", ::defaultNtripCasterProfiles, NtripCasterProfile::fromJson),
            profiles("ntripCasterUploadProfiles", ::defaultNtripCasterUploadProfiles, NtripCasterUploadProfile::fromJson),
            profiles("ntripMountpointProfiles", ::defaultNtripMountpointProfiles, NtripMountpointProfile::fromJson),
            profiles("recordingPolicyProfiles", ::defaultRecordingPolicyProfiles, RecordingPolicyProfile::fromJson),
            profiles("rtklibProfiles", ::defaultRtklibProfiles, RtklibProfile::fromJson),
            profiles("solutionPolicyProfiles", ::defaultSolutionPolicyProfiles, SolutionPolicyProfile::fromJson),
            profiles("storageProfiles", ::defaultStorageProfiles, StorageProfile::fromJson),
            storedSettingsSets(), preferences.getString("selectedSettingsSetId", null),
            preferences.getString("selectedWorkflowId", null), preferences.getString("lastActiveNtripMountpointProfileId", null),
            emptyMap(), SettingsSetExportOptions(), activeSetupSelections = readActiveSelections(), migrationRecovery = readMigrationRecovery(),
        )
        if (!retainOriginal) return backup
        val original = backup.toJson()
        SettingsBackupProfileFamily.entries.forEach { family ->
            preferences.getString(family.jsonKey, null)?.let { original.put(family.jsonKey, JSONArray(it)) }
        }
        return retainPrivateLegacyInput(backup, original).copy(
            privateRecoveryInput = PrivateRecoveryInput(JSONObject(preferences.all).toString()),
        )
    }

    private fun revisionsAfterGraphChange(additionalSetIds: Set<String> = emptySet()): Map<String, Long> {
        val revisions = readSelectionRevisions()
        val ids = revisions.keys + readActiveSelections().keys + storedSettingsSets().map { it.id } + additionalSetIds
        return ids.associateWith { Math.addExact(revisions[it] ?: 0L, 1L) }
    }

    private fun readActiveSelections(): Map<String, ActiveSetupSelections> {
        val raw = preferences.getString("activeSetupSelections", null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { setId ->
                ActiveSetupSelections.fromJson(json.getJSONObject(setId)).also { state ->
                    require(state.settingsSetId == setId) { "Stored selection key does not match its set." }
                }
            }
        } catch (failure: Exception) {
            throw IllegalStateException("Stored active selections are unreadable.", failure)
        }
    }

    private fun readMigrationRecovery(): Map<String, LegacyMigrationRecovery> {
        val raw = preferences.getString("migrationRecovery", null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { setId ->
                LegacyMigrationRecovery.fromJson(json.getJSONObject(setId)).also { record ->
                    require(record.legacySettingsSet.id == setId) { "Stored recovery key does not match its set." }
                }
            }
        } catch (failure: Exception) {
            throw IllegalStateException("Stored migration recovery data are unreadable.", failure)
        }
    }

    private fun activeSelectionsJson(states: Map<String, ActiveSetupSelections>): String =
        JSONObject().also { json ->
            states.forEach { (setId, state) ->
                require(setId == state.settingsSetId) { "Selection state key must match its settings set." }
                json.put(setId, state.toJson())
            }
        }.toString()

    private fun migrationRecoveryJson(records: Map<String, LegacyMigrationRecovery>): String =
        JSONObject().also { json ->
            records.forEach { (setId, record) ->
                require(setId == record.legacySettingsSet.id) { "Recovery key must match its settings set." }
                json.put(setId, record.toJson())
            }
        }.toString()

    private fun validatePriorProfileGraph() {
        fun check(key: String, decode: (JSONObject) -> Any) {
            val raw = preferences.getString(key, null) ?: return
            val array = JSONArray(raw)
            val ids = mutableSetOf<String>()
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                decode(json)
                require(ids.add(json.getString("id"))) { "Stored profile graph contains duplicate identities." }
            }
        }
        check("commandProfiles", CommandProfile::fromJson)
        check("usbBaudProfiles", UsbBaudProfile::fromJson)
        check("ntripCasterProfiles", NtripCasterProfile::fromJson)
        check("ntripCasterUploadProfiles", NtripCasterUploadProfile::fromJson)
        check("ntripMountpointProfiles", NtripMountpointProfile::fromJson)
        check("recordingPolicyProfiles", RecordingPolicyProfile::fromJson)
        check("rtklibProfiles", RtklibProfile::fromJson)
        check("solutionPolicyProfiles", SolutionPolicyProfile::fromJson)
        check("storageProfiles", StorageProfile::fromJson)
        check("settingsSets", RecordingSettingsSet::fromJson)
        readActiveSelections()
        readMigrationRecovery()
        readSelectionRevisions()
    }

    private fun clearChangedNtripCasterAcknowledgements(
        profiles: List<NtripCasterProfile>,
        freshlyAcknowledgedEditorProfileId: String?,
    ): List<NtripCasterProfile> {
        val persisted = ntripCasterProfiles().associateBy(NtripCasterProfile::id)
        return profiles.map { candidate ->
            candidate.clearUnsafeAcknowledgementUnlessUnchanged(
                persisted[candidate.id], candidate.id == freshlyAcknowledgedEditorProfileId,
            )
        }
    }

    private fun clearChangedNtripCasterUploadAcknowledgements(
        profiles: List<NtripCasterUploadProfile>,
        freshlyAcknowledgedEditorProfileId: String?,
    ): List<NtripCasterUploadProfile> {
        val persisted = ntripCasterUploadProfiles().associateBy(NtripCasterUploadProfile::id)
        return profiles.map { candidate ->
            candidate.clearUnsafeAcknowledgementUnlessUnchanged(
                persisted[candidate.id], candidate.id == freshlyAcknowledgedEditorProfileId,
            )
        }
    }

    private fun <T> profileJson(profiles: List<T>, encode: (T) -> org.json.JSONObject): String =
        JSONArray().also { array -> profiles.forEach { array.put(encode(it)) } }.toString()

    private fun defaultCommandProfiles(): List<CommandProfile> =
        defaultCommandProfilesForTests()

    companion object {
        private val CONFIGURATION_LOCK = Any()
        private val transientSelections = ActiveSelectionsMemory()
        internal fun defaultCommandProfilesForTests(): List<CommandProfile> = listOf(
            CommandProfile(
                id = "um980-binary-multihz",
                name = "UM980 multi-Hz binary RTK+PPP",
                receiverFamily = "um980-n4",
                runtimeScript = UM980_BINARY_MULTI_HZ_SCRIPT,
                satelliteTelemetry = SatelliteTelemetryCapability.UM980_BINARY,
                isProtected = true,
            ),
            CommandProfile(
                id = "um980-binary-multihz-rtklib-obsvmb",
                name = "UM980 multi-Hz binary RTKLIB OBSVMB",
                receiverFamily = "um980-n4",
                runtimeScript = UM980_BINARY_MULTI_HZ_RTKLIB_OBSVMB_SCRIPT,
                satelliteTelemetry = SatelliteTelemetryCapability.UM980_BINARY,
                isProtected = true,
            ),
            CommandProfile(
                id = "um980-ascii-ppp-nmea",
                name = "UM980 multi-Hz ASCII RTK+PPP",
                receiverFamily = "um980-n4",
                runtimeScript = UM980_ASCII_PPP_NMEA_SCRIPT,
                satelliteTelemetry = SatelliteTelemetryCapability.UM980_ASCII_NMEA,
                isProtected = true,
            ),
            CommandProfile(
                id = "um980-ascii-1hz-rtk-ppp",
                name = "UM980 1 Hz ASCII RTK+PPP",
                receiverFamily = "um980-n4",
                runtimeScript = UM980_ASCII_1HZ_RTK_PPP_SCRIPT,
                satelliteTelemetry = SatelliteTelemetryCapability.UM980_ASCII_NMEA,
                isProtected = true,
            ),
            CommandProfile(
                id = "um980-base-config",
                name = "UM980 base config",
                receiverFamily = "um980-n4",
                runtimeScript = UM980_BASE_CONFIG_SCRIPT,
                satelliteTelemetry = SatelliteTelemetryCapability.UM980_BINARY,
                isProtected = true,
            ),
            CommandProfile(
                id = "ublox-m8t-raw-1hz-safe",
                name = "u-blox M8T raw 1 Hz safe",
                receiverFamily = "ublox-m8t",
                runtimeScript = UBLOX_M8T_RAW_1HZ_SCRIPT,
                satelliteTelemetry = SatelliteTelemetryCapability.UBLOX_NAV_SAT,
                isProtected = true,
            ),
            CommandProfile(
                id = "ublox-m8t-raw-5hz-rtklib-ex",
                name = "u-blox M8T raw 5 Hz RTKLIB-EX",
                receiverFamily = "ublox-m8t",
                runtimeScript = UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT,
                satelliteTelemetry = SatelliteTelemetryCapability.UBLOX_NAV_SAT,
                isProtected = true,
            ),
            CommandProfile(
                id = "ublox-m8t-raw-status-mock",
                name = "u-blox M8T raw + status/mock",
                receiverFamily = "ublox-m8t",
                runtimeScript = UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT,
                satelliteTelemetry = SatelliteTelemetryCapability.UBLOX_NAV_SAT,
                isProtected = true,
            ),
        )

    private fun defaultUsbBaudProfiles(): List<UsbBaudProfile> =
        listOf(
            UsbBaudProfile(
                id = "um980-230400",
                name = "UM980 230400",
                profileBaud = 230400,
                serialBaud = 230400,
            ),
        )

    private fun defaultNtripCasterProfiles(): List<NtripCasterProfile> =
        listOf(
            NtripCasterProfile(
                id = "ntrip-caster-default",
                name = "NTRIP caster",
            ),
        )

    private fun defaultNtripCasterUploadProfiles(): List<NtripCasterUploadProfile> =
        emptyList()

    private fun defaultNtripMountpointProfiles(): List<NtripMountpointProfile> =
        emptyList()

    private fun defaultRecordingPolicyProfiles(): List<RecordingPolicyProfile> =
        listOf(
            RecordingPolicyProfile(
                id = "default-record-everything",
                name = "Default V1 recording outputs",
            ),
        )

    private fun defaultRtklibProfiles(): List<RtklibProfile> =
        listOf(
            RtklibProfile(
                id = RTKLIB_DISABLED_PROFILE_ID,
                name = "RTKLIB disabled",
                enabled = false,
                isProtected = true,
            ),
            RtklibProfile(
                id = RTKLIB_ROVER_KINEMATIC_PROFILE_ID,
                name = "RTKLIB rover kinematic RTK",
                enabled = true,
                preset = RtklibProfile.PRESET_ROVER_KINEMATIC_RTK,
                frequencyCount = 1,
                serverCycleMillis = 50,
                serverBufferBytes = 65_536,
                solutionBufferBytes = 65_536,
                isProtected = true,
            ),
            RtklibProfile(
                id = RTKLIB_TEMPORARY_BASE_STATIC_PROFILE_ID,
                name = "RTKLIB temporary-base static RTK",
                enabled = true,
                preset = RtklibProfile.PRESET_TEMPORARY_BASE_STATIC_RTK,
                isProtected = true,
            ),
        )

    private fun defaultSolutionPolicyProfiles(): List<SolutionPolicyProfile> =
        listOf(
            SolutionPolicyProfile(
                id = "solution-auto-best",
                name = "Automatic best solution",
                isProtected = true,
            ),
            SolutionPolicyProfile(
                id = "solution-device-internal",
                name = "Device internal solution only",
                screenPolicy = org.rtkcollector.core.solution.SolutionSourcePolicy.DEVICE_INTERNAL_ONLY,
                mockPolicy = org.rtkcollector.core.solution.SolutionSourcePolicy.DEVICE_INTERNAL_ONLY,
                isProtected = true,
            ),
            SolutionPolicyProfile(
                id = "solution-rtklib",
                name = "RTKLIB solution only",
                screenPolicy = org.rtkcollector.core.solution.SolutionSourcePolicy.RTKLIB_ONLY,
                mockPolicy = org.rtkcollector.core.solution.SolutionSourcePolicy.RTKLIB_ONLY,
                isProtected = true,
            ),
        )

    private fun defaultStorageProfiles(): List<StorageProfile> =
        listOf(
            StorageProfile(
                id = "app-private",
                name = "App-private external storage",
                kind = "APP_PRIVATE",
            ),
        )

    private fun defaultSettingsSets(): List<RecordingSettingsSet> =
        RecordingSettingsSet.builtInDefaults()

        const val OLD_UM980_COMMAND_PROFILE_ID = "um980-default-commands"
        const val UM980_BINARY_MULTI_HZ_PROFILE_ID = "um980-binary-multihz"
        const val UM980_BINARY_MULTI_HZ_RTKLIB_OBSVMB_PROFILE_ID = "um980-binary-multihz-rtklib-obsvmb"
        const val UM980_ASCII_PPP_NMEA_PROFILE_ID = "um980-ascii-ppp-nmea"
        const val UM980_ASCII_1HZ_RTK_PPP_PROFILE_ID = "um980-ascii-1hz-rtk-ppp"
        const val UM980_BASE_CONFIG_PROFILE_ID = "um980-base-config"
        const val UBLOX_M8T_RAW_1HZ_PROFILE_ID = "ublox-m8t-raw-1hz-safe"
        const val UBLOX_M8T_RAW_5HZ_RTKLIB_EX_PROFILE_ID = "ublox-m8t-raw-5hz-rtklib-ex"
        const val UBLOX_M8T_RAW_STATUS_MOCK_PROFILE_ID = "ublox-m8t-raw-status-mock"
        const val OLD_NTRIP_MOUNTPOINT_PROFILE_ID = "ntrip-mountpoint-default"
        const val DEFAULT_RECORDING_POLICY_ID = "default-record-everything"
        const val RTKLIB_DISABLED_PROFILE_ID = "rtklib-disabled"
        const val RTKLIB_ROVER_KINEMATIC_PROFILE_ID = "rtklib-rover-kinematic"
        const val RTKLIB_TEMPORARY_BASE_STATIC_PROFILE_ID = "rtklib-temporary-base-static"
        const val DEFAULT_STORAGE_PROFILE_ID = "app-private"
        const val DEFAULT_USB_BAUD_PROFILE_ID = "um980-230400"
        const val DEFAULT_NTRIP_CASTER_PROFILE_ID = "ntrip-caster-default"

        val UM980_BINARY_MULTI_HZ_SCRIPT: String = """
            UNLOG COM1
            MODE ROVER SURVEY
            CONFIG MMP ENABLE
            CONFIG RTK TIMEOUT 120
            CONFIG RTK RELIABILITY 3 1
            CONFIG PPP ENABLE E6-HAS
            CONFIG PPP DATUM WGS84
            CONFIG PPP TIMEOUT 120
            CONFIG PPP CONVERGE 15 30
            VERSIONB
            BESTNAVB COM1 0.05
            ADRNAVB COM1 1
            PPPNAVB COM1 1
            RTKSTATUSB COM1 1
            RTCMSTATUSB COM1 ONCHANGED
            OBSVMCMPB COM1 0.5
            BESTSATB COM1 1
            STADOPB COM1 1
            GPSEPHB COM1 300
            GLOEPHB COM1 300
            GALEPHB COM1 300
            BDSEPHB COM1 300
            BD3EPHB COM1 300
            QZSSEPHB COM1 300
            GPSIONB ONCHANGED
            BDSIONB ONCHANGED
            BD3IONB ONCHANGED
            GALIONB ONCHANGED
            GPSUTCB ONCHANGED
            BDSUTCB ONCHANGED
            BD3UTCB ONCHANGED
            GALUTCB ONCHANGED
        """.trimIndent()

        val UM980_BINARY_MULTI_HZ_RTKLIB_OBSVMB_SCRIPT: String = """
            UNLOG COM1
            MODE ROVER SURVEY
            CONFIG MMP ENABLE
            CONFIG RTK TIMEOUT 120
            CONFIG RTK RELIABILITY 3 1
            CONFIG PPP ENABLE E6-HAS
            CONFIG PPP DATUM WGS84
            CONFIG PPP TIMEOUT 120
            CONFIG PPP CONVERGE 15 30
            VERSIONB
            BESTNAVB COM1 0.05
            ADRNAVB COM1 1
            PPPNAVB COM1 1
            RTKSTATUSB COM1 1
            RTCMSTATUSB COM1 ONCHANGED
            OBSVMB COM1 0.2
            BESTSATB COM1 1
            STADOPB COM1 1
            GPSEPHB COM1 300
            GLOEPHB COM1 300
            GALEPHB COM1 300
            BDSEPHB COM1 300
            BD3EPHB COM1 300
            QZSSEPHB COM1 300
            GPSIONB ONCHANGED
            BDSIONB ONCHANGED
            BD3IONB ONCHANGED
            GALIONB ONCHANGED
            GPSUTCB ONCHANGED
            BDSUTCB ONCHANGED
            BD3UTCB ONCHANGED
            GALUTCB ONCHANGED
        """.trimIndent()

        val UM980_ASCII_PPP_NMEA_SCRIPT: String = """
            CONFIG PPP ENABLE E6-HAS
            CONFIG PPP DATUM WGS84
            CONFIG PPP TIMEOUT 120
            CONFIG PPP CONVERGE 15 30

            MODE ROVER SURVEY
            CONFIG RTK TIMEOUT 120
            CONFIG RTK RELIABILITY 3 1

            GNGGA 0.05
            GNRMC 0.05
            GNGST 0.05
            GNGSV 1
            GNGSA 1
            GPGLL 1
            GPGNS 1
            GPGRS 30
            PPPNAVA 1
            ADRNAVA 1
            RTKSTATUSA 1
            RTCMSTATUSA ONCHANGED

            TROPINFOA ONCHANGED
            GPSIONB ONCHANGED
        """.trimIndent()

        val UM980_ASCII_1HZ_RTK_PPP_SCRIPT: String = """
            UNLOG COM1
            MODE ROVER SURVEY
            CONFIG MMP ENABLE
            CONFIG RTK TIMEOUT 120
            CONFIG RTK RELIABILITY 3 1
            CONFIG PPP ENABLE E6-HAS
            CONFIG PPP DATUM WGS84
            CONFIG PPP TIMEOUT 120
            CONFIG PPP CONVERGE 15 30

            GNGGA 1
            GNRMC 1
            GNGST 1
            GNGSV 1
            GNGSA 1
            GPGLL 1
            GPGNS 1
            GPGRS 30
            PPPNAVA 1
            ADRNAVA 1
            RTKSTATUSA 1
            RTCMSTATUSA ONCHANGED

            TROPINFOA ONCHANGED
            GPSIONB ONCHANGED
        """.trimIndent()

        val UM980_BASE_CONFIG_SCRIPT: String = """
            UNLOG COM1
            MODE BASE TIME 120 2.5
            GNGGA 1
            GNRMC 1
            GNGST 1
            GNGSV 1
            GNGSA 1
            BESTNAVB COM1 1
            STADOPB COM1 1
            RTCM1006 COM1 10
            RTCM1033 COM1 10
            RTCM1074 COM1 1
            RTCM1084 COM1 1
            RTCM1094 COM1 1
            RTCM1114 COM1 1
            RTCM1124 COM1 1
            RTCM1230 COM1 10
            OBSVMCMPB COM1 0.5
        """.trimIndent()

        val UBLOX_M8T_RAW_1HZ_SCRIPT: String = UbloxM8tProfiles.raw1HzSafe
        val UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT: String = UbloxM8tProfiles.raw5HzRtklibEx
        val UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT: String = UbloxM8tProfiles.rawStatusMock
    }
}

internal class ActiveSelectionsMemory {
    private data class Entry(val durableState: ActiveSetupSelections, val state: ActiveSetupSelections)

    private val byOwner = IdentityHashMap<Any, MutableMap<String, Entry>>()

    @Synchronized
    fun publishChoice(owner: Any, candidate: ActiveSetupSelections, selectionRevision: Long,
        option: ActiveSetupOptionKey): StartSelectionLease {
        val current = byOwner[owner]?.get(candidate.settingsSetId)?.state
            ?: ActiveSetupSelections.fromJson(candidate.toJson())
        // Merge only the accepted option: terminal cleanup may already have removed other answers.
        val merged = current.withPublishedChoice(candidate, option)
        return captureStart(owner, merged, selectionRevision, setOf(option))
    }

    @Synchronized
    fun remember(owner: Any, state: ActiveSetupSelections) {
        val normalized = state.copy(
            transientAnswerGenerations = state.transientChoices.keys.associateWith {
                state.transientAnswerGenerations[it] ?: ActiveSetupSelections.nextAnswerGeneration()
            },
            transientUploadGeneration = if (state.transientUploadSelection == null) null else
                state.transientUploadGeneration ?: ActiveSetupSelections.nextAnswerGeneration(),
        )
        byOwner.getOrPut(owner) { mutableMapOf() }[state.settingsSetId] = Entry(ActiveSetupSelections.fromJson(state.toJson()), normalized)
    }

    @Synchronized
    fun captureStart(owner: Any, state: ActiveSetupSelections, selectionRevision: Long,
        applicableOptions: Set<ActiveSetupOptionKey>): StartSelectionLease {
        remember(owner, state)
        val consumed = byOwner.getValue(owner).getValue(state.settingsSetId).state
        val generations = consumed.transientAnswerGenerations.filterKeys { it in applicableOptions }
        val uploadGeneration = consumed.transientUploadGeneration.takeIf {
            ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD in applicableOptions
        }
        return StartSelectionLease(consumed, selectionRevision) {
            completeStart(owner, state.settingsSetId, generations, uploadGeneration)
        }
    }

    @Synchronized
    private fun completeStart(owner: Any, setId: String, generations: Map<ActiveSetupOptionKey, Long>, uploadGeneration: Long?) {
        val entries = byOwner[owner] ?: return
        val entry = entries[setId] ?: return
        val current = entry.state
        val consumedKeys = generations.keys.filterTo(linkedSetOf()) {
            current.transientAnswerGenerations[it] == generations[it]
        }
        val consumedUpload = uploadGeneration != null && current.transientUploadGeneration == uploadGeneration
        entries[setId] = entry.copy(state = current.copy(
            transientChoices = current.transientChoices - consumedKeys,
            transientAnswerGenerations = current.transientAnswerGenerations - consumedKeys,
            transientUploadSelection = current.transientUploadSelection.takeUnless { consumedUpload },
            transientUploadGeneration = current.transientUploadGeneration.takeUnless { consumedUpload },
        ))
    }

    @Synchronized
    fun cached(owner: Any, durable: ActiveSetupSelections): ActiveSetupSelections? {
        val entries = byOwner[owner] ?: return null
        val entry = entries[durable.settingsSetId] ?: return null
        if (entry.durableState == durable) return entry.state
        entries.remove(durable.settingsSetId)
        if (entries.isEmpty()) byOwner.remove(owner)
        return null
    }

    @Synchronized
    fun invalidate(owner: Any) {
        byOwner.remove(owner)
    }

    @Synchronized
    fun invalidate(owner: Any, setIds: Set<String>) {
        val entries = byOwner[owner] ?: return
        setIds.forEach(entries::remove)
        if (entries.isEmpty()) byOwner.remove(owner)
    }
}
