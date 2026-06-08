package org.rtkcollector.app.profile

import android.content.Context
import org.json.JSONArray

class ProfileStores(context: Context) {
    private val preferences = context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE)

    fun commandProfiles(): List<CommandProfile> =
        readProfiles("commandProfiles", ::defaultCommandProfiles, CommandProfile::fromJson)

    fun saveCommandProfiles(profiles: List<CommandProfile>) =
        writeProfiles("commandProfiles", profiles.onEach(CommandProfile::validate).map(CommandProfile::toJson))

    fun usbBaudProfiles(): List<UsbBaudProfile> =
        readProfiles("usbBaudProfiles", ::defaultUsbBaudProfiles, UsbBaudProfile::fromJson)

    fun saveUsbBaudProfiles(profiles: List<UsbBaudProfile>) =
        writeProfiles("usbBaudProfiles", profiles.onEach(UsbBaudProfile::validate).map(UsbBaudProfile::toJson))

    fun ntripCasterProfiles(): List<NtripCasterProfile> =
        readProfiles("ntripCasterProfiles", ::defaultNtripCasterProfiles, NtripCasterProfile::fromJson)

    fun saveNtripCasterProfiles(profiles: List<NtripCasterProfile>) =
        writeProfiles("ntripCasterProfiles", profiles.onEach(NtripCasterProfile::validate).map(NtripCasterProfile::toJson))

    fun ntripMountpointProfiles(): List<NtripMountpointProfile> =
        readProfiles("ntripMountpointProfiles", ::defaultNtripMountpointProfiles, NtripMountpointProfile::fromJson)

    fun saveNtripMountpointProfiles(profiles: List<NtripMountpointProfile>) =
        writeProfiles(
            "ntripMountpointProfiles",
            profiles.onEach(NtripMountpointProfile::validate).map(NtripMountpointProfile::toJson),
        )

    fun recordingPolicyProfiles(): List<RecordingPolicyProfile> =
        readProfiles("recordingPolicyProfiles", ::defaultRecordingPolicyProfiles, RecordingPolicyProfile::fromJson)

    fun saveRecordingPolicyProfiles(profiles: List<RecordingPolicyProfile>) =
        writeProfiles(
            "recordingPolicyProfiles",
            profiles.onEach(RecordingPolicyProfile::validate).map(RecordingPolicyProfile::toJson),
        )

    fun storageProfiles(): List<StorageProfile> =
        readProfiles("storageProfiles", ::defaultStorageProfiles, StorageProfile::fromJson)

    fun saveStorageProfiles(profiles: List<StorageProfile>) =
        writeProfiles("storageProfiles", profiles.onEach(StorageProfile::validate).map(StorageProfile::toJson))

    fun settingsSets(): List<RecordingSettingsSet> =
        readProfiles("settingsSets", ::defaultSettingsSets, RecordingSettingsSet::fromJson)

    fun saveSettingsSets(settingsSets: List<RecordingSettingsSet>) =
        writeProfiles("settingsSets", settingsSets.onEach(RecordingSettingsSet::validate).map(RecordingSettingsSet::toJson))

    fun selectedSettingsSetId(): String =
        preferences.getString("selectedSettingsSetId", null) ?: defaultSettingsSets().first().id

    fun saveSelectedSettingsSetId(id: String) {
        require(id.isNotBlank()) { "Selected settings set id must not be blank." }
        preferences.edit().putString("selectedSettingsSetId", id).apply()
    }

    fun duplicateId(prefix: String): String =
        "$prefix-${System.currentTimeMillis()}"

    private fun <T> readProfiles(
        key: String,
        defaults: () -> List<T>,
        decode: (org.json.JSONObject) -> T,
    ): List<T> {
        val raw = preferences.getString(key, null) ?: return defaults()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index -> decode(array.getJSONObject(index)) }
        }.getOrElse { defaults() }
    }

    private fun writeProfiles(key: String, jsonObjects: List<org.json.JSONObject>) {
        val array = JSONArray()
        jsonObjects.forEach(array::put)
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun defaultCommandProfiles(): List<CommandProfile> =
        listOf(
            CommandProfile(
                id = "um980-default-commands",
                name = "UM980 default commands",
                isProtected = true,
            ),
        )

    private fun defaultUsbBaudProfiles(): List<UsbBaudProfile> =
        listOf(
            UsbBaudProfile(
                id = "um980-230400",
                name = "UM980 230400",
                isProtected = true,
                profileBaud = 230400,
                serialBaud = 230400,
            ),
        )

    private fun defaultNtripCasterProfiles(): List<NtripCasterProfile> =
        listOf(
            NtripCasterProfile(
                id = "ntrip-caster-default",
                name = "NTRIP caster",
                isProtected = true,
            ),
        )

    private fun defaultNtripMountpointProfiles(): List<NtripMountpointProfile> =
        listOf(
            NtripMountpointProfile(
                id = "ntrip-mountpoint-default",
                name = "NTRIP mountpoint",
                casterProfileId = "ntrip-caster-default",
                isProtected = true,
            ),
        )

    private fun defaultRecordingPolicyProfiles(): List<RecordingPolicyProfile> =
        listOf(
            RecordingPolicyProfile(
                id = "default-record-everything",
                name = "Default V1 recording policy",
                isProtected = true,
            ),
        )

    private fun defaultStorageProfiles(): List<StorageProfile> =
        listOf(
            StorageProfile(
                id = "app-private",
                name = "App-private external storage",
                isProtected = true,
                kind = "APP_PRIVATE",
            ),
        )

    private fun defaultSettingsSets(): List<RecordingSettingsSet> =
        listOf(RecordingSettingsSet.builtInRoverNtrip())
}
