package org.rtkcollector.app.ui

import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.ActiveSetup
import org.rtkcollector.app.profile.ActiveSetupResolver
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.effectiveNtripMountpointProfileRef
import org.rtkcollector.app.profile.isOptionLocked
import org.rtkcollector.app.profile.correctionCasterRestrictionRef
import org.rtkcollector.app.profile.correctionCasterPolicyProblem

internal data class ResolvedNtripProfiles(
    val caster: NtripCasterProfile?,
    val mountpoint: NtripMountpointProfile?,
    val settingsSet: RecordingSettingsSet,
    val problem: String? = null,
    val requiresCasterPolicyReview: Boolean = false,
)

private fun RecordingSettingsSet.casterRestrictionId(): String? =
    correctionCasterRestrictionRef()?.id

internal fun RecordingSettingsSet.selectableNtripMountpoints(
    mountpointProfiles: List<NtripMountpointProfile>,
): List<NtripMountpointProfile> {
    val restrictionId = casterRestrictionId() ?: return mountpointProfiles
    return mountpointProfiles.filter { it.casterProfileId == restrictionId }
}

internal fun RecordingSettingsSet.withSelectedNtripMountpoint(
    mountpoint: NtripMountpointProfile,
    casterProfiles: List<NtripCasterProfile>,
): RecordingSettingsSet {
    require(!isOptionLocked(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)) {
        "NTRIP mountpoint is fixed by the settings set."
    }
    require(casterRestrictionId()?.let { it == mountpoint.casterProfileId } != false) {
        "Selected mountpoint does not satisfy the caster restriction."
    }
    val casterRef = casterProfiles.filter { it.id == mountpoint.casterProfileId }.singleOrNull()?.let {
        ProfileReference(it.id, it.name)
    }
    require(casterRef != null) { "Selected mountpoint's caster profile is missing." }
    return copy(overrides = overrides.copy(
        ntripMountpointProfileRef = ProfileReference(mountpoint.id, mountpoint.name),
        ntripMountpoint = null,
    ))
}

internal fun RecordingSettingsSet.resolveNtripProfiles(
    casterProfiles: List<NtripCasterProfile>,
    mountpointProfiles: List<NtripMountpointProfile>,
): ResolvedNtripProfiles = resolveNtripProfiles(
    ActiveSetupResolver.resolve(this,
        rememberedOverrides = effectiveNtripMountpointProfileRef()?.id?.let {
            mapOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT to it)
        } ?: emptyMap(), transientChoices = emptyMap(), compatibility = emptyMap()),
    casterProfiles, mountpointProfiles,
)

internal fun RecordingSettingsSet.resolveNtripProfiles(
    setup: ActiveSetup,
    casterProfiles: List<NtripCasterProfile>,
    mountpointProfiles: List<NtripMountpointProfile>,
): ResolvedNtripProfiles {
    require(setup.settingsSetId == id) { "Active setup belongs to another settings set." }
    if (!setup.option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).applicable) {
        return ResolvedNtripProfiles(null, null, this)
    }
    val sourceId = setup.option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).effectiveValueId
    val mountpoint = sourceId?.let { selected -> mountpointProfiles.filter { it.id == selected }.singleOrNull() }
    val caster = mountpoint?.casterProfileId?.let { selected -> casterProfiles.filter { it.id == selected }.singleOrNull() }
    val restrictionId = casterRestrictionId()
    val policyProblem = correctionCasterPolicyProblem()
    val needsReview = policyProblem != null
    val problem = when {
        policyProblem != null -> policyProblem
        sourceId == null -> "NTRIP source must be selected."
        mountpoint == null -> "Selected NTRIP source profile $sourceId is missing."
        caster == null -> "NTRIP caster profile ${mountpoint.casterProfileId} is missing."
        caster.host.isBlank() || caster.port !in 1..65535 ->
            "NTRIP caster profile ${caster.id} selected by source is not configured."
        restrictionId != null && caster.id != restrictionId ->
            "Selected NTRIP source does not satisfy caster restriction $restrictionId."
        else -> null
    }
    return ResolvedNtripProfiles(caster, mountpoint, this, problem, needsReview)
}
