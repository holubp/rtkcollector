package org.rtkcollector.app.profile

internal object ProfileStoreMigrations {
    fun commandProfiles(
        profiles: List<CommandProfile>,
        defaults: List<CommandProfile>,
    ): List<CommandProfile> {
        val rewritten = profiles.map { profile ->
            when (profile.id) {
                ProfileStores.OLD_UM980_COMMAND_PROFILE_ID -> defaults.required(ProfileStores.UM980_BINARY_MULTI_HZ_PROFILE_ID)
                ProfileStores.UM980_BINARY_MULTI_HZ_PROFILE_ID -> profile.copy(
                    name = profile.name.ifBlank { "UM980 binary multi-Hz" },
                    runtimeScript = profile.runtimeScript
                        .ifBlank { ProfileStores.UM980_BINARY_MULTI_HZ_SCRIPT }
                        .migrateUm980BinaryPppStatusOutput(),
                    isProtected = false,
                )
                ProfileStores.UM980_ASCII_PPP_NMEA_PROFILE_ID -> profile.copy(
                    runtimeScript = profile.runtimeScript.ifBlank { ProfileStores.UM980_ASCII_PPP_NMEA_SCRIPT },
                    isProtected = false,
                )
                else -> profile
            }
        }.distinctBy(CommandProfile::id)

        return rewritten.withMissingDefaults(defaults, CommandProfile::id)
    }

    fun usbBaudProfiles(
        profiles: List<UsbBaudProfile>,
        defaults: List<UsbBaudProfile>,
    ): List<UsbBaudProfile> =
        profiles.map { profile ->
            if (profile.id == ProfileStores.DEFAULT_USB_BAUD_PROFILE_ID) {
                profile.copy(isProtected = false)
            } else {
                profile
            }
        }.withMissingDefaults(defaults, UsbBaudProfile::id)

    fun ntripCasterProfiles(
        profiles: List<NtripCasterProfile>,
        defaults: List<NtripCasterProfile>,
    ): List<NtripCasterProfile> =
        profiles.map { profile ->
            if (profile.id == ProfileStores.DEFAULT_NTRIP_CASTER_PROFILE_ID) {
                profile.copy(isProtected = false)
            } else {
                profile
            }
        }.withMissingDefaults(defaults, NtripCasterProfile::id)

    fun ntripMountpointProfiles(
        profiles: List<NtripMountpointProfile>,
        defaults: List<NtripMountpointProfile>,
    ): List<NtripMountpointProfile> =
        profiles
            .filterNot { it.id == ProfileStores.OLD_NTRIP_MOUNTPOINT_PROFILE_ID && it.mountpoint.isBlank() }
            .map { profile ->
                if (profile.id == ProfileStores.OLD_NTRIP_MOUNTPOINT_PROFILE_ID) {
                    profile.copy(isProtected = false, ggaUploadPolicy = profile.ggaUploadPolicy.blankOldNone())
                } else {
                    profile.copy(ggaUploadPolicy = profile.ggaUploadPolicy.blankOldNone())
                }
            }
            .withMissingDefaults(defaults, NtripMountpointProfile::id)

    fun recordingPolicyProfiles(
        profiles: List<RecordingPolicyProfile>,
        defaults: List<RecordingPolicyProfile>,
    ): List<RecordingPolicyProfile> =
        profiles.map { profile ->
            if (profile.id == ProfileStores.DEFAULT_RECORDING_POLICY_ID) {
                profile.copy(name = "Default V1 recording outputs", isProtected = false)
            } else {
                profile
            }
        }.withMissingDefaults(defaults, RecordingPolicyProfile::id)

    fun storageProfiles(
        profiles: List<StorageProfile>,
        defaults: List<StorageProfile>,
    ): List<StorageProfile> =
        profiles.map { profile ->
            if (profile.id == ProfileStores.DEFAULT_STORAGE_PROFILE_ID) {
                profile.copy(isProtected = false)
            } else {
                profile
            }
        }.withMissingDefaults(defaults, StorageProfile::id)

    fun settingsSets(
        settingsSets: List<RecordingSettingsSet>,
        defaults: List<RecordingSettingsSet>,
    ): List<RecordingSettingsSet> =
        settingsSets.map { settingsSet ->
            settingsSet.copy(
                commandProfileRef = if (settingsSet.commandProfileRef.id == ProfileStores.OLD_UM980_COMMAND_PROFILE_ID) {
                    ProfileReference(ProfileStores.UM980_BINARY_MULTI_HZ_PROFILE_ID, "UM980 binary multi-Hz")
                } else {
                    settingsSet.commandProfileRef
                },
                ntripMountpointProfileRef = settingsSet.ntripMountpointProfileRef
                    ?.takeUnless { it.id == ProfileStores.OLD_NTRIP_MOUNTPOINT_PROFILE_ID },
                recordingOutputProfileRef = if (settingsSet.recordingOutputProfileRef.id == ProfileStores.DEFAULT_RECORDING_POLICY_ID) {
                    settingsSet.recordingOutputProfileRef.copy(name = "Default V1 recording outputs")
                } else {
                    settingsSet.recordingOutputProfileRef
                },
            )
        }.withMissingDefaults(defaults, RecordingSettingsSet::id)
}

private fun String.blankOldNone(): String =
    if (equals("NONE", ignoreCase = true)) "" else this

private fun String.migrateUm980BinaryPppStatusOutput(): String {
    if (isOldUm980BinaryProfile()) return ProfileStores.UM980_BINARY_MULTI_HZ_SCRIPT
    if (!contains("CONFIG PPP ENABLE", ignoreCase = true)) return this
    if (lineStartsWith("PPPNAVB")) return this
    return trimEnd() + "\nPPPNAVB COM1 1"
}

private fun String.isOldUm980BinaryProfile(): Boolean =
    contains("BESTNAVB COM1 0.1", ignoreCase = true) &&
        contains("OBSVMCMPB COM1 0.25", ignoreCase = true) &&
        contains("STADOPB COM1 1", ignoreCase = true) &&
        !contains("CONFIG PPP ENABLE", ignoreCase = true)

private fun String.lineStartsWith(prefix: String): Boolean =
    lineSequence().any { it.trimStart().startsWith(prefix, ignoreCase = true) }

private fun List<CommandProfile>.required(id: String): CommandProfile =
    firstOrNull { it.id == id } ?: error("Missing default command profile '$id'.")

private inline fun <T> List<T>.withMissingDefaults(
    defaults: List<T>,
    idOf: (T) -> String,
): List<T> {
    val existingIds = map(idOf).toSet()
    return this + defaults.filterNot { idOf(it) in existingIds }
}
