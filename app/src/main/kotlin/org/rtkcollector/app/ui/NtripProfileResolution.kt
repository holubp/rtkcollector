package org.rtkcollector.app.ui

import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet

internal data class ResolvedNtripProfiles(
    val caster: NtripCasterProfile?,
    val mountpoint: NtripMountpointProfile?,
    val settingsSet: RecordingSettingsSet,
)

internal fun RecordingSettingsSet.resolveNtripProfiles(
    casterProfiles: List<NtripCasterProfile>,
    mountpointProfiles: List<NtripMountpointProfile>,
): ResolvedNtripProfiles {
    val mountpoint = ntripMountpointProfileRef?.id
        ?.let { id -> mountpointProfiles.firstOrNull { it.id == id } }
    val casterFromMountpoint = mountpoint?.casterProfileId
        ?.let { casterId -> casterProfiles.firstOrNull { it.id == casterId } }
    if (mountpoint != null) {
        val syncedSettingsSet = if (
            casterFromMountpoint != null &&
            ntripCasterProfileRef?.id != casterFromMountpoint.id
        ) {
            copy(ntripCasterProfileRef = ProfileReference(casterFromMountpoint.id, casterFromMountpoint.name))
        } else {
            this
        }
        return ResolvedNtripProfiles(
            caster = casterFromMountpoint,
            mountpoint = mountpoint,
            settingsSet = syncedSettingsSet,
        )
    }

    val caster = ntripCasterProfileRef?.id
        ?.let { id -> casterProfiles.firstOrNull { it.id == id } }
    return ResolvedNtripProfiles(caster = caster, mountpoint = null, settingsSet = this)
}
