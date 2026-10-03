package org.rtkcollector.app.profile

import org.rtkcollector.app.profile.ActiveSetupResolver.defaultOptionValues

data class LegacyMigrationRepairPlan(val settingsSet: RecordingSettingsSet, val recovery: LegacyMigrationRecovery)

/** Explicit editor decisions validate saved defaults without publishing ASK or Choose-once answers. */
fun planLegacyMigrationRepair(
    edited: RecordingSettingsSet,
    recovery: LegacyMigrationRecovery,
    graph: ActiveSetupProfileGraph,
    confirmedOptions: Set<ActiveSetupOptionKey>,
    persistedSafTreeUrisWithWriteAccess: Set<String> = emptySet(),
    credentialLookup: (String) -> LegacyCredential = { LegacyCredential.Missing },
): LegacyMigrationRepairPlan {
    require(edited.id == recovery.legacySettingsSet.id && !edited.isProtected) { "Copy protected settings before repairing them." }
    val repairsCasterPolicy = ActiveSetupOptionKey.NTRIP_MOUNTPOINT in confirmedOptions &&
        (edited.ntripCasterPolicyNeedsReview || edited.correctionCasterPolicyProblem() != null)
    val set = if (repairsCasterPolicy) edited.copy(ntripCasterProfileRef = null,
        ntripCasterRestrictionRef = edited.correctionCasterRestrictionRef(), ntripCasterPolicyNeedsReview = false,
        optionPolicies = edited.optionPolicies.withPolicy(ActiveSetupOptionKey.NTRIP_CASTER, SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE)) else edited
    set.validate()
    require(!set.hasLocalOverrides) { "Migrate legacy profile fields before repairing settings." }
    var validationSelections = ActiveSetupSelections(set.id, workflowBaselineId = set.workflowId)
    set.defaultOptionValues().forEach { (key, id) ->
        if (key !in setOf(ActiveSetupOptionKey.NTRIP_CASTER, ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD) &&
            !set.isOptionLocked(key)) {
            validationSelections = validationSelections.choose(set, key,
                id?.let(SelectionChoice::profile) ?: SelectionChoice.none())
        }
    }
    if (!set.isOptionLocked(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD)) validationSelections = validationSelections.chooseUpload(
        set, UploadSelection(set.baseCasterUploadEnabled,
            set.ntripCasterUploadProfileRef?.id?.let(SelectionChoice::profile) ?: SelectionChoice.none()))
    val setup = ActiveSetupResolver.resolve(set, validationSelections, profileGraph = graph)
    require(setup.canStart) { setup.messages.joinToString(" ") { it.message } }
    val relevant = recovery.blockingIssues(set, validationSelections).toSet()
    val issues = recovery.issues.map { issue ->
        if (issue !in relevant || issue.option !in confirmedOptions) return@map issue
        val owners = requireNotNull(setup.resolvedProfiles)
        when (issue.reason) {
            MigrationReviewReason.MISSING_CREDENTIAL, MigrationReviewReason.UNREADABLE_CREDENTIAL -> {
                val binding = when (issue.option) {
                    ActiveSetupOptionKey.NTRIP_MOUNTPOINT -> owners.caster?.secretId
                    ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> owners.upload?.secretId
                    else -> null
                }
                require(!binding.isNullOrBlank() && credentialLookup(binding) is LegacyCredential.Available) {
                    "Repair the selected profile's exact bound credential before applying repairs."
                }
            }
            MigrationReviewReason.SAF_RESELECTION -> {
                val storage = requireNotNull(owners.storage)
                require(storage.kind == "SAF_TREE" && !storage.requiresTreeReselection &&
                    storage.treeUri in persistedSafTreeUrisWithWriteAccess) {
                    "Select the Android folder with persisted write permission before applying repairs."
                }
            }
            MigrationReviewReason.CASTER_LINEAGE -> require(owners.source != null && owners.caster != null &&
                owners.source.casterProfileId == owners.caster.id &&
                (set.correctionCasterRestrictionRef() == null || set.correctionCasterRestrictionRef()?.id == owners.caster.id)) {
                "Select an exact source caster and compatible restriction before applying repairs."
            }
            MigrationReviewReason.BASE_COORDINATE -> require(owners.baseCoordinate != null) { "Select a valid accepted base coordinate." }
            MigrationReviewReason.POLICY_CONFLICT, MigrationReviewReason.MISSING_PROFILE -> Unit
            else -> return@map issue
        }
        issue.copy(resolution = MigrationIssueResolution.VALIDATED_OPERATOR_REPAIR)
    }
    return LegacyMigrationRepairPlan(set, recovery.copy(issues = issues,
        reviewReasons = (recovery.reviewReasons - recovery.issues.mapNotNull { it.reason }.toSet()) +
            issues.filter { it.resolution == null }.mapNotNull { it.reason }))
}
