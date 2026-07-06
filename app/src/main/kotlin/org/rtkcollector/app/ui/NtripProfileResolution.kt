package org.rtkcollector.app.ui

import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.effectiveNtripCasterProfileRef
import org.rtkcollector.app.profile.effectiveNtripMountpointProfileRef

internal data class ResolvedNtripProfiles(
    val caster: NtripCasterProfile?,
    val mountpoint: NtripMountpointProfile?,
    val settingsSet: RecordingSettingsSet,
)

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
