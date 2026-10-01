package org.rtkcollector.app.ui

import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.effectiveNtripCasterProfileRef
import org.rtkcollector.app.profile.effectiveNtripMountpointProfileRef
import org.rtkcollector.app.profile.effectiveForActiveSetup
import org.rtkcollector.app.profile.isOptionLocked

internal data class ResolvedNtripProfiles(
    val caster: NtripCasterProfile?,
    val mountpoint: NtripMountpointProfile?,
    val settingsSet: RecordingSettingsSet,
    val problem: String? = null,
)

internal fun RecordingSettingsSet.selectableNtripMountpoints(
    mountpointProfiles: List<NtripMountpointProfile>,
): List<NtripMountpointProfile> {
    if (!isOptionLocked(ActiveSetupOptionKey.NTRIP_CASTER)) return mountpointProfiles
    val fixedCasterId = effectiveForActiveSetup().effectiveNtripCasterProfileRef()?.id
    return mountpointProfiles.filter { it.casterProfileId == fixedCasterId }
}

internal fun RecordingSettingsSet.withSelectedNtripMountpoint(
    mountpoint: NtripMountpointProfile,
    casterProfiles: List<NtripCasterProfile>,
): RecordingSettingsSet {
    require(!isOptionLocked(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)) {
        "NTRIP mountpoint is fixed by the settings set."
    }
    require(!isOptionLocked(ActiveSetupOptionKey.NTRIP_CASTER) ||
        mountpoint.casterProfileId == effectiveForActiveSetup().effectiveNtripCasterProfileRef()?.id) {
        "Selected mountpoint belongs to another caster; unlock the caster or choose a compatible mountpoint."
    }
    val casterRef = casterProfiles.firstOrNull { it.id == mountpoint.casterProfileId }?.let {
        ProfileReference(it.id, it.name)
    }
    return copy(overrides = overrides.copy(
        ntripCasterProfileRef = if (isOptionLocked(ActiveSetupOptionKey.NTRIP_CASTER)) {
            overrides.ntripCasterProfileRef
        } else {
            casterRef
        },
        ntripMountpointProfileRef = ProfileReference(mountpoint.id, mountpoint.name),
        ntripMountpoint = null,
    ))
}

internal fun RecordingSettingsSet.resolveNtripProfiles(
    casterProfiles: List<NtripCasterProfile>,
    mountpointProfiles: List<NtripMountpointProfile>,
): ResolvedNtripProfiles {
    val mountpoint = effectiveNtripMountpointProfileRef()?.id
        ?.let { id -> mountpointProfiles.firstOrNull { it.id == id } }
    val settingsCaster = effectiveNtripCasterProfileRef()?.id
        ?.let { id -> casterProfiles.firstOrNull { it.id == id } }
    val casterFromMountpoint = mountpoint?.casterProfileId
        ?.let { casterId -> casterProfiles.firstOrNull { it.id == casterId } }
    if (mountpoint != null && isOptionLocked(ActiveSetupOptionKey.NTRIP_CASTER)) {
        return ResolvedNtripProfiles(
            caster = settingsCaster,
            mountpoint = mountpoint,
            settingsSet = this,
            problem = "Locked NTRIP caster does not match the selected mountpoint."
                .takeIf { settingsCaster != null && mountpoint.casterProfileId != settingsCaster.id },
        )
    }
    if (mountpoint != null) {
        val configuredCasters = casterProfiles.filter(NtripCasterProfile::isConfiguredForCorrectionStart)
        val casterMatchingMountpoint = configuredCasters.firstOrNull { caster ->
            mountpoint.mountpoint.isNotBlank() && mountpoint.mountpoint in caster.sourcetableMountpoints
        }
        val singleConfiguredCaster = configuredCasters.singleOrNull()
        val resolvedCaster = casterFromMountpoint
            ?.takeIf(NtripCasterProfile::isConfiguredForCorrectionStart)
            ?: settingsCaster?.takeIf(NtripCasterProfile::isConfiguredForCorrectionStart)
            ?: casterMatchingMountpoint
            ?: singleConfiguredCaster
            ?: settingsCaster
            ?: casterFromMountpoint
        val syncedSettingsSet = if (
            resolvedCaster != null &&
            effectiveNtripCasterProfileRef()?.id != resolvedCaster.id
        ) {
            val casterRef = ProfileReference(resolvedCaster.id, resolvedCaster.name)
            if (overrides.ntripMountpointProfileRef != null || overrides.ntripCasterProfileRef != null) {
                copy(overrides = overrides.copy(ntripCasterProfileRef = casterRef))
            } else {
                copy(ntripCasterProfileRef = casterRef)
            }
        } else {
            this
        }
        return ResolvedNtripProfiles(
            caster = resolvedCaster,
            mountpoint = mountpoint,
            settingsSet = syncedSettingsSet,
        )
    }

    return ResolvedNtripProfiles(caster = settingsCaster, mountpoint = null, settingsSet = this)
}

private fun NtripCasterProfile.isConfiguredForCorrectionStart(): Boolean =
    host.isNotBlank() && port in 1..65535
