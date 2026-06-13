package org.rtkcollector.app.profile

import android.content.Context
import org.json.JSONArray
import org.rtkcollector.receiver.ublox.UbloxM8tProfiles

class ProfileStores(context: Context) {
    private val preferences = context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE)

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

    fun saveNtripCasterProfiles(profiles: List<NtripCasterProfile>) =
        writeProfiles("ntripCasterProfiles", profiles.onEach(NtripCasterProfile::validate).map(NtripCasterProfile::toJson))

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

    fun storageProfiles(): List<StorageProfile> =
        readProfiles(
            key = "storageProfiles",
            defaults = ::defaultStorageProfiles,
            decode = StorageProfile::fromJson,
            migrate = ProfileStoreMigrations::storageProfiles,
            encode = StorageProfile::toJson,
        )

    fun saveStorageProfiles(profiles: List<StorageProfile>) =
        writeProfiles("storageProfiles", profiles.onEach(StorageProfile::validate).map(StorageProfile::toJson))

    fun settingsSets(): List<RecordingSettingsSet> =
        readProfiles(
            key = "settingsSets",
            defaults = ::defaultSettingsSets,
            decode = RecordingSettingsSet::fromJson,
            migrate = ProfileStoreMigrations::settingsSets,
            encode = RecordingSettingsSet::toJson,
        )

    fun saveSettingsSets(settingsSets: List<RecordingSettingsSet>) =
        writeProfiles("settingsSets", settingsSets.onEach(RecordingSettingsSet::validate).map(RecordingSettingsSet::toJson))

    fun selectedSettingsSetId(): String =
        preferences.getString("selectedSettingsSetId", null) ?: defaultSettingsSets().first().id

    fun saveSelectedSettingsSetId(id: String) {
        require(id.isNotBlank()) { "Selected settings set id must not be blank." }
        preferences.edit().putString("selectedSettingsSetId", id).apply()
    }

    fun selectedWorkflowId(): String? =
        preferences.getString("selectedWorkflowId", null)?.takeIf(String::isNotBlank)

    fun saveSelectedWorkflowId(id: String?) {
        preferences.edit().apply {
            if (id.isNullOrBlank()) {
                remove("selectedWorkflowId")
            } else {
                putString("selectedWorkflowId", id)
            }
        }.apply()
    }

    fun lastActiveNtripMountpointProfileId(): String? =
        preferences.getString("lastActiveNtripMountpointProfileId", null)
            ?.takeIf { it.isNotBlank() && !it.equals("a", ignoreCase = true) }

    fun saveLastActiveNtripMountpointProfileId(id: String?) {
        preferences.edit().apply {
            if (id.isNullOrBlank()) {
                remove("lastActiveNtripMountpointProfileId")
            } else {
                putString("lastActiveNtripMountpointProfileId", id)
            }
        }.apply()
    }

    fun duplicateId(prefix: String): String =
        "$prefix-${System.currentTimeMillis()}"

    private fun <T> readProfiles(
        key: String,
        defaults: () -> List<T>,
        decode: (org.json.JSONObject) -> T,
        migrate: (List<T>, List<T>) -> List<T> = { profiles, _ -> profiles },
        encode: ((T) -> org.json.JSONObject)? = null,
    ): List<T> {
        val raw = preferences.getString(key, null) ?: return defaults()
        val decoded = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index -> decode(array.getJSONObject(index)) }
        }.getOrElse { defaults() }
        val migrated = migrate(decoded, defaults())
        if (migrated != decoded && encode != null) {
            writeProfiles(key, migrated.map(encode))
        }
        return migrated
    }

    private fun writeProfiles(key: String, jsonObjects: List<org.json.JSONObject>) {
        val array = JSONArray()
        jsonObjects.forEach(array::put)
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun defaultCommandProfiles(): List<CommandProfile> =
        listOf(
            CommandProfile(
                id = "um980-binary-multihz",
                name = "UM980 binary multi-Hz",
                runtimeScript = UM980_BINARY_MULTI_HZ_SCRIPT,
            ),
            CommandProfile(
                id = "um980-ascii-ppp-nmea",
                name = "UM980 ASCII PPP/NMEA",
                runtimeScript = UM980_ASCII_PPP_NMEA_SCRIPT,
            ),
            CommandProfile(
                id = "ublox-m8t-raw-1hz-safe",
                name = "u-blox M8T raw 1 Hz safe",
                receiverFamily = "ublox-m8t",
                runtimeScript = UBLOX_M8T_RAW_1HZ_SCRIPT,
            ),
            CommandProfile(
                id = "ublox-m8t-raw-5hz-rtklib-ex",
                name = "u-blox M8T raw 5 Hz RTKLIB-EX",
                receiverFamily = "ublox-m8t",
                runtimeScript = UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT,
            ),
            CommandProfile(
                id = "ublox-m8t-raw-status-mock",
                name = "u-blox M8T raw + status/mock",
                receiverFamily = "ublox-m8t",
                runtimeScript = UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT,
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

    private fun defaultNtripMountpointProfiles(): List<NtripMountpointProfile> =
        emptyList()

    private fun defaultRecordingPolicyProfiles(): List<RecordingPolicyProfile> =
        listOf(
            RecordingPolicyProfile(
                id = "default-record-everything",
                name = "Default V1 recording outputs",
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
        listOf(RecordingSettingsSet.builtInRoverNtrip())

    companion object {
        const val OLD_UM980_COMMAND_PROFILE_ID = "um980-default-commands"
        const val UM980_BINARY_MULTI_HZ_PROFILE_ID = "um980-binary-multihz"
        const val UM980_ASCII_PPP_NMEA_PROFILE_ID = "um980-ascii-ppp-nmea"
        const val UBLOX_M8T_RAW_1HZ_PROFILE_ID = "ublox-m8t-raw-1hz-safe"
        const val UBLOX_M8T_RAW_5HZ_RTKLIB_EX_PROFILE_ID = "ublox-m8t-raw-5hz-rtklib-ex"
        const val UBLOX_M8T_RAW_STATUS_MOCK_PROFILE_ID = "ublox-m8t-raw-status-mock"
        const val OLD_NTRIP_MOUNTPOINT_PROFILE_ID = "ntrip-mountpoint-default"
        const val DEFAULT_RECORDING_POLICY_ID = "default-record-everything"
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
            OBSVMCMPB COM1 0.25
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

            MODE ROVER

            GNGGA 0.05
            GNRMC 0.05
            GNGST 0.05
            GNGSV 1
            GNGSA 1
            GPGLL 1
            GPGNS 1
            GPGRS 30
            PPPNAVA 10
            ADRNAVA 10

            TROPINFOA ONCHANGED
            GPSIONB ONCHANGED
        """.trimIndent()

        val UBLOX_M8T_RAW_1HZ_SCRIPT: String = UbloxM8tProfiles.raw1HzSafe
        val UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT: String = UbloxM8tProfiles.raw5HzRtklibEx
        val UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT: String = UbloxM8tProfiles.rawStatusMock
    }
}
