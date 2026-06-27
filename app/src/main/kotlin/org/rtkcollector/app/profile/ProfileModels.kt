package org.rtkcollector.app.profile

import org.json.JSONObject
import org.rtkcollector.core.solution.SolutionSourcePolicy

enum class SatelliteTelemetryCapability(
    val storageId: String,
    val displayName: String,
) {
    NONE("none", "No satellite telemetry"),
    UM980_BINARY("um980-binary", "UM980 binary satellite telemetry"),
    UM980_ASCII_NMEA("um980-ascii-nmea", "UM980 NMEA satellite telemetry"),
    UBLOX_NAV_SAT("ublox-nav-sat", "u-blox NAV-SAT telemetry"),
    ;

    val isSupported: Boolean get() = this != NONE

    companion object {
        fun fromStorageId(value: String?): SatelliteTelemetryCapability =
            entries.firstOrNull { it.storageId == value } ?: NONE
    }
}

enum class NtripCasterUploadRetryMode {
    ADAPTIVE,
    FIXED,
    ;

    companion object {
        fun fromStorageValue(value: String?): NtripCasterUploadRetryMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ADAPTIVE
    }
}

private val RTK2GO_HOSTS = setOf("rtk2go.com", "www.rtk2go.com")

data class CommandProfile(
    val id: String,
    val name: String,
    val receiverFamily: String = "um980-n4",
    val initScript: String = "",
    val runtimeScript: String = "",
    val shutdownScript: String = "",
    val satelliteTelemetry: SatelliteTelemetryCapability = SatelliteTelemetryCapability.NONE,
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "Command profile id must not be blank." }
        require(name.isNotBlank()) { "Command profile name must not be blank." }
        require(receiverFamily.isNotBlank()) { "Command profile receiver family must not be blank." }
    }

    fun copyProfile(id: String, name: String): CommandProfile =
        copy(id = id, name = name, isProtected = false).also(CommandProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("isProtected", isProtected)
        .put("receiverFamily", receiverFamily)
        .put("initScript", initScript)
        .put("runtimeScript", runtimeScript)
        .put("shutdownScript", shutdownScript)
        .put("satelliteTelemetry", satelliteTelemetry.storageId)

    companion object {
        fun fromJson(json: JSONObject): CommandProfile = CommandProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isProtected = json.optProtectedFlag(),
            receiverFamily = json.optString("receiverFamily", "um980-n4"),
            initScript = json.optString("initScript", ""),
            runtimeScript = json.optString("runtimeScript", ""),
            shutdownScript = json.optString("shutdownScript", ""),
            satelliteTelemetry = SatelliteTelemetryCapability.fromStorageId(json.optString("satelliteTelemetry", "")),
        ).also(CommandProfile::validate)
    }
}

data class UsbBaudProfile(
    val id: String,
    val name: String,
    val profileBaud: Int = 230400,
    val serialBaud: Int = 230400,
    val usbVid: Int? = null,
    val usbPid: Int? = null,
    val usbDeviceName: String? = null,
    val usbProductName: String? = null,
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "USB/baud profile id must not be blank." }
        require(name.isNotBlank()) { "USB/baud profile name must not be blank." }
        require(profileBaud in BAUD_RANGE) { "Initial receiver baud must be 9600..921600." }
        require(serialBaud in BAUD_RANGE) { "Target receiver and host baud must be 9600..921600." }
    }

    fun copyProfile(id: String, name: String): UsbBaudProfile =
        copy(id = id, name = name, isProtected = false).also(UsbBaudProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("isProtected", isProtected)
        .put("profileBaud", profileBaud)
        .put("serialBaud", serialBaud)
        .putNullable("usbVid", usbVid)
        .putNullable("usbPid", usbPid)
        .putNullable("usbDeviceName", usbDeviceName)
        .putNullable("usbProductName", usbProductName)

    companion object {
        private val BAUD_RANGE = 9600..921600

        fun fromJson(json: JSONObject): UsbBaudProfile = UsbBaudProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isProtected = json.optProtectedFlag(),
            profileBaud = json.optInt("profileBaud", 230400),
            serialBaud = json.optInt("serialBaud", 230400),
            usbVid = json.optNullableInt("usbVid"),
            usbPid = json.optNullableInt("usbPid"),
            usbDeviceName = json.optNullableString("usbDeviceName"),
            usbProductName = json.optNullableString("usbProductName"),
        ).also(UsbBaudProfile::validate)
    }
}

data class NtripCasterProfile(
    val id: String,
    val name: String,
    val host: String = "",
    val port: Int = 2101,
    val username: String = "",
    val secretId: String = "",
    val protocolPolicy: String = "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY",
    val sourcetableMountpoints: List<String> = emptyList(),
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "NTRIP caster profile id must not be blank." }
        require(name.isNotBlank()) { "NTRIP caster profile name must not be blank." }
        require(port in 1..65535) { "NTRIP port must be 1..65535." }
    }

    fun copyProfile(id: String, name: String): NtripCasterProfile =
        copy(id = id, name = name, secretId = ntripCasterSecretId(id), isProtected = false)
            .also(NtripCasterProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("isProtected", isProtected)
        .put("host", host)
        .put("port", port)
        .put("username", username)
        .put("secretId", secretId)
        .put("protocolPolicy", protocolPolicy)
        .putStringList("sourcetableMountpoints", sourcetableMountpoints)

    companion object {
        fun fromJson(json: JSONObject): NtripCasterProfile = NtripCasterProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isProtected = json.optProtectedFlag(),
            host = json.optString("host", ""),
            port = json.optInt("port", 2101),
            username = json.optString("username", ""),
            secretId = json.optString("secretId", ""),
            protocolPolicy = json.optString("protocolPolicy", "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY"),
            sourcetableMountpoints = json.optStringList("sourcetableMountpoints"),
        ).also(NtripCasterProfile::validate)
    }
}

fun ntripCasterSecretId(profileId: String): String {
    require(profileId.isNotBlank()) { "NTRIP caster profile id must not be blank." }
    return "ntrip-caster-profile:$profileId"
}

data class NtripCasterUploadProfile(
    val id: String,
    val name: String,
    val host: String = "",
    val port: Int = 2101,
    val mountpoint: String = "",
    val username: String = "",
    val secretId: String = "",
    val protocolPolicy: String = "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY",
    val retryMode: NtripCasterUploadRetryMode = NtripCasterUploadRetryMode.ADAPTIVE,
    val fixedReconnectDelaySeconds: Int = 10,
    val adaptiveInitialDelaySeconds: Int = 10,
    val adaptiveMaxDelaySeconds: Int = 300,
    val stopAfterFailuresEnabled: Boolean = true,
    val stopAfterConsecutiveFailures: Int = 5,
    val safetyRulesEnabled: Boolean = false,
    val safetyMaxBitrateKbps: Int = 35,
    val safetyBitrateWindowSeconds: Int = 60,
    val safetyMaxSessionUploadMb: Int = 500,
    val enabledByDefault: Boolean = false,
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "NTRIP caster upload profile id must not be blank." }
        require(name.isNotBlank()) { "NTRIP caster upload profile name must not be blank." }
        require(port in 1..65535) { "NTRIP caster upload port must be 1..65535." }
        require(protocolPolicy in PROTOCOL_POLICIES) { "NTRIP caster upload protocol policy is invalid." }
        if (retryMode == NtripCasterUploadRetryMode.FIXED) {
            require(fixedReconnectDelaySeconds >= 10) {
                "Fixed reconnect delay must be at least 10 seconds."
            }
        }
        require(adaptiveInitialDelaySeconds >= 10) {
            "Adaptive initial reconnect delay must be at least 10 seconds."
        }
        require(adaptiveMaxDelaySeconds >= adaptiveInitialDelaySeconds) {
            "Adaptive maximum reconnect delay must be greater than or equal to the initial delay."
        }
        require(stopAfterConsecutiveFailures >= 1) {
            "Stop-after-failures count must be at least 1."
        }
        require(safetyMaxBitrateKbps >= 1) { "Safety bitrate threshold must be positive." }
        require(safetyBitrateWindowSeconds >= 1) { "Safety bitrate window must be positive." }
        require(safetyMaxSessionUploadMb >= 1) { "Safety session upload limit must be positive." }
    }

    fun validateForStart() {
        validate()
        require(host.isNotBlank()) { "NTRIP caster upload host is required." }
        require(mountpoint.isNotBlank()) { "NTRIP caster upload mountpoint is required." }
    }

    fun copyProfile(id: String, name: String): NtripCasterUploadProfile =
        copy(id = id, name = name, secretId = ntripCasterUploadSecretId(id), isProtected = false)
            .also(NtripCasterUploadProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("isProtected", isProtected)
        .put("host", host)
        .put("port", port)
        .put("mountpoint", mountpoint)
        .put("username", username)
        .put("secretId", secretId)
        .put("protocolPolicy", protocolPolicy)
        .put("retryMode", retryMode.name)
        .put("fixedReconnectDelaySeconds", fixedReconnectDelaySeconds)
        .put("adaptiveInitialDelaySeconds", adaptiveInitialDelaySeconds)
        .put("adaptiveMaxDelaySeconds", adaptiveMaxDelaySeconds)
        .put("stopAfterFailuresEnabled", stopAfterFailuresEnabled)
        .put("stopAfterConsecutiveFailures", stopAfterConsecutiveFailures)
        .put("safetyRulesEnabled", safetyRulesEnabled)
        .put("safetyMaxBitrateKbps", safetyMaxBitrateKbps)
        .put("safetyBitrateWindowSeconds", safetyBitrateWindowSeconds)
        .put("safetyMaxSessionUploadMb", safetyMaxSessionUploadMb)
        .put("enabledByDefault", enabledByDefault)

    val isRtk2goHost: Boolean
        get() = host.trim().lowercase() in RTK2GO_HOSTS

    val effectiveSafetyRulesEnabled: Boolean
        get() = safetyRulesEnabled || isRtk2goHost

    companion object {
        val PROTOCOL_POLICIES = setOf(
            "NTRIP_V2_ONLY",
            "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY",
            "NTRIP_V1_ONLY",
        )

        fun fromJson(json: JSONObject): NtripCasterUploadProfile = NtripCasterUploadProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isProtected = json.optProtectedFlag(),
            host = json.optString("host", ""),
            port = json.optInt("port", 2101),
            mountpoint = json.optString("mountpoint", ""),
            username = json.optString("username", ""),
            secretId = json.optString("secretId", ""),
            protocolPolicy = json.optString("protocolPolicy", "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY"),
            retryMode = NtripCasterUploadRetryMode.fromStorageValue(json.optString("retryMode", "")),
            fixedReconnectDelaySeconds = json.optInt("fixedReconnectDelaySeconds", 10),
            adaptiveInitialDelaySeconds = json.optInt("adaptiveInitialDelaySeconds", 10),
            adaptiveMaxDelaySeconds = json.optInt("adaptiveMaxDelaySeconds", 300),
            stopAfterFailuresEnabled = json.optBoolean("stopAfterFailuresEnabled", true),
            stopAfterConsecutiveFailures = json.optInt("stopAfterConsecutiveFailures", 5),
            safetyRulesEnabled = json.optBoolean("safetyRulesEnabled", false),
            safetyMaxBitrateKbps = json.optInt("safetyMaxBitrateKbps", 35),
            safetyBitrateWindowSeconds = json.optInt("safetyBitrateWindowSeconds", 60),
            safetyMaxSessionUploadMb = json.optInt("safetyMaxSessionUploadMb", 500),
            enabledByDefault = json.optBoolean("enabledByDefault", false),
        ).also(NtripCasterUploadProfile::validate)
    }
}

fun ntripCasterUploadSecretId(profileId: String): String {
    require(profileId.isNotBlank()) { "NTRIP caster upload profile id must not be blank." }
    return "ntrip-caster-upload-profile:$profileId"
}

data class NtripMountpointProfile(
    val id: String,
    val name: String,
    val casterProfileId: String,
    val mountpoint: String = "",
    val ggaUploadPolicy: String = "",
    val expectedFormat: String = "RTCM3",
    val remoteBaseRawAvailable: Boolean = false,
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "NTRIP mountpoint profile id must not be blank." }
        require(name.isNotBlank()) { "NTRIP mountpoint profile name must not be blank." }
        require(casterProfileId.isNotBlank()) { "NTRIP mountpoint profile must reference a caster profile." }
    }

    fun copyProfile(id: String, name: String): NtripMountpointProfile =
        copy(id = id, name = name, isProtected = false).also(NtripMountpointProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("casterProfileId", casterProfileId)
        .put("isProtected", isProtected)
        .put("mountpoint", mountpoint)
        .put("ggaUploadPolicy", ggaUploadPolicy)
        .put("expectedFormat", expectedFormat)
        .put("remoteBaseRawAvailable", remoteBaseRawAvailable)

    companion object {
        fun fromJson(json: JSONObject): NtripMountpointProfile = NtripMountpointProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            casterProfileId = json.getString("casterProfileId"),
            isProtected = json.optProtectedFlag(),
            mountpoint = json.optString("mountpoint", ""),
            ggaUploadPolicy = json.optString("ggaUploadPolicy", ""),
            expectedFormat = json.optString("expectedFormat", "RTCM3"),
            remoteBaseRawAvailable = json.optBoolean("remoteBaseRawAvailable", false),
        ).also(NtripMountpointProfile::validate)
    }
}

fun NtripMountpointProfile.displayMountpoint(): String =
    mountpoint.takeIf { it.isNotBlank() } ?: "n/a"

data class RecordingPolicyProfile(
    val id: String,
    val name: String,
    val recordReceiverRx: Boolean = true,
    val recordTxToReceiver: Boolean = true,
    val recordNtripCorrectionInput: Boolean = true,
    val exportNmea: Boolean = true,
    val pppNmeaGgaQuality: Int = DEFAULT_PPP_NMEA_GGA_QUALITY,
    val exportJsonSolution: Boolean = true,
    val exportGpx: Boolean = false,
    val recordRemoteBaseRaw: Boolean = false,
    val enableMockLocation: Boolean = false,
    val mockLocationRateHz: Int = DEFAULT_MOCK_LOCATION_RATE_HZ,
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "Recording policy id must not be blank." }
        require(name.isNotBlank()) { "Recording policy name must not be blank." }
        require(recordReceiverRx) { "Device receiver RX recording is required." }
        require(pppNmeaGgaQuality in ALLOWED_PPP_NMEA_GGA_QUALITIES) {
            "PPP NMEA GGA quality must be one of ${ALLOWED_PPP_NMEA_GGA_QUALITIES.joinToString()}."
        }
        require(mockLocationRateHz in ALLOWED_MOCK_LOCATION_RATES_HZ) {
            "Mock location rate must be one of ${ALLOWED_MOCK_LOCATION_RATES_HZ.joinToString()} Hz."
        }
    }

    fun copyProfile(id: String, name: String): RecordingPolicyProfile =
        copy(id = id, name = name, isProtected = false).also(RecordingPolicyProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("isProtected", isProtected)
        .put("recordReceiverRx", recordReceiverRx)
        .put("recordTxToReceiver", recordTxToReceiver)
        .put("recordNtripCorrectionInput", recordNtripCorrectionInput)
        .put("exportNmea", exportNmea)
        .put("pppNmeaGgaQuality", pppNmeaGgaQuality)
        .put("exportJsonSolution", exportJsonSolution)
        .put("exportGpx", exportGpx)
        .put("recordRemoteBaseRaw", recordRemoteBaseRaw)
        .put("enableMockLocation", enableMockLocation)
        .put("mockLocationRateHz", mockLocationRateHz)

    companion object {
        const val DEFAULT_PPP_NMEA_GGA_QUALITY = 2
        const val DEFAULT_MOCK_LOCATION_RATE_HZ = 1
        val ALLOWED_PPP_NMEA_GGA_QUALITIES: Set<Int> = setOf(2, 5, 9)
        val ALLOWED_MOCK_LOCATION_RATES_HZ: Set<Int> = setOf(1, 2, 5, 10)

        fun fromJson(json: JSONObject): RecordingPolicyProfile = RecordingPolicyProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isProtected = json.optProtectedFlag(),
            recordReceiverRx = json.optBoolean("recordReceiverRx", true),
            recordTxToReceiver = json.optBoolean("recordTxToReceiver", true),
            recordNtripCorrectionInput = json.optBoolean("recordNtripCorrectionInput", true),
            exportNmea = json.optBoolean("exportNmea", true),
            pppNmeaGgaQuality = json.optInt("pppNmeaGgaQuality", DEFAULT_PPP_NMEA_GGA_QUALITY),
            exportJsonSolution = json.optBoolean("exportJsonSolution", true),
            exportGpx = json.optBoolean("exportGpx", false),
            recordRemoteBaseRaw = json.optBoolean("recordRemoteBaseRaw", false),
            enableMockLocation = json.optBoolean("enableMockLocation", false),
            mockLocationRateHz = json.optInt("mockLocationRateHz", DEFAULT_MOCK_LOCATION_RATE_HZ),
        ).also(RecordingPolicyProfile::validate)
    }
}

data class RtklibProfile(
    val id: String,
    val name: String,
    val enabled: Boolean = false,
    val preset: String = PRESET_ROVER_KINEMATIC_RTK,
    val outputNmea: Boolean = true,
    val outputPos: Boolean = true,
    val maxRoverQueueBytes: Int = DEFAULT_MAX_ROVER_QUEUE_BYTES,
    val maxCorrectionQueueBytes: Int = DEFAULT_MAX_CORRECTION_QUEUE_BYTES,
    val frequencyCount: Int = DEFAULT_FREQUENCY_COUNT,
    val serverCycleMillis: Int = DEFAULT_SERVER_CYCLE_MILLIS,
    val serverBufferBytes: Int = DEFAULT_SERVER_BUFFER_BYTES,
    val solutionBufferBytes: Int = DEFAULT_SOLUTION_BUFFER_BYTES,
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "RTKLIB profile id must not be blank." }
        require(name.isNotBlank()) { "RTKLIB profile name must not be blank." }
        require(preset in PRESETS) { "RTKLIB preset is invalid." }
        require(maxRoverQueueBytes > 0) { "RTKLIB rover queue limit must be positive." }
        require(maxCorrectionQueueBytes > 0) { "RTKLIB correction queue limit must be positive." }
        require(frequencyCount in 1..3) { "RTKLIB frequency count must be 1, 2 or 3." }
        require(serverCycleMillis > 0) { "RTKLIB server cycle must be positive." }
        require(serverBufferBytes > 0) { "RTKLIB server buffer size must be positive." }
        require(solutionBufferBytes > 0) { "RTKLIB solution buffer size must be positive." }
        require(!enabled || outputNmea || outputPos) { "Enabled RTKLIB profile must write NMEA or POS output." }
    }

    fun copyProfile(id: String, name: String): RtklibProfile =
        copy(id = id, name = name, isProtected = false).also(RtklibProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("isProtected", isProtected)
        .put("enabled", enabled)
        .put("preset", preset)
        .put("outputNmea", outputNmea)
        .put("outputPos", outputPos)
        .put("maxRoverQueueBytes", maxRoverQueueBytes)
        .put("maxCorrectionQueueBytes", maxCorrectionQueueBytes)
        .put("frequencyCount", frequencyCount)
        .put("serverCycleMillis", serverCycleMillis)
        .put("serverBufferBytes", serverBufferBytes)
        .put("solutionBufferBytes", solutionBufferBytes)

    companion object {
        const val PRESET_ROVER_KINEMATIC_RTK = "ROVER_KINEMATIC_RTK"
        const val PRESET_TEMPORARY_BASE_STATIC_RTK = "TEMPORARY_BASE_STATIC_RTK"
        const val DEFAULT_MAX_ROVER_QUEUE_BYTES = 1_048_576
        const val DEFAULT_MAX_CORRECTION_QUEUE_BYTES = 262_144
        const val DEFAULT_FREQUENCY_COUNT = 1
        const val DEFAULT_SERVER_CYCLE_MILLIS = 50
        const val DEFAULT_SERVER_BUFFER_BYTES = 65_536
        const val DEFAULT_SOLUTION_BUFFER_BYTES = 65_536
        val PRESETS = setOf(PRESET_ROVER_KINEMATIC_RTK, PRESET_TEMPORARY_BASE_STATIC_RTK)

        fun fromJson(json: JSONObject): RtklibProfile = RtklibProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isProtected = json.optProtectedFlag(),
            enabled = json.optBoolean("enabled", false),
            preset = json.optString("preset", PRESET_ROVER_KINEMATIC_RTK),
            outputNmea = json.optBoolean("outputNmea", true),
            outputPos = json.optBoolean("outputPos", true),
            maxRoverQueueBytes = json.optInt("maxRoverQueueBytes", DEFAULT_MAX_ROVER_QUEUE_BYTES),
            maxCorrectionQueueBytes = json.optInt("maxCorrectionQueueBytes", DEFAULT_MAX_CORRECTION_QUEUE_BYTES),
            frequencyCount = json.optInt("frequencyCount", DEFAULT_FREQUENCY_COUNT),
            serverCycleMillis = json.optInt("serverCycleMillis", DEFAULT_SERVER_CYCLE_MILLIS),
            serverBufferBytes = json.optInt("serverBufferBytes", DEFAULT_SERVER_BUFFER_BYTES),
            solutionBufferBytes = json.optInt("solutionBufferBytes", DEFAULT_SOLUTION_BUFFER_BYTES),
        ).also(RtklibProfile::validate)
    }
}

data class SolutionPolicyProfile(
    val id: String,
    val name: String,
    val screenPolicy: SolutionSourcePolicy = SolutionSourcePolicy.AUTO_BEST,
    val mockPolicy: SolutionSourcePolicy = SolutionSourcePolicy.AUTO_BEST,
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "Solution policy profile id must not be blank." }
        require(name.isNotBlank()) { "Solution policy profile name must not be blank." }
    }

    fun copyProfile(id: String, name: String): SolutionPolicyProfile =
        copy(id = id, name = name, isProtected = false).also(SolutionPolicyProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("isProtected", isProtected)
        .put("screenPolicy", screenPolicy.name)
        .put("mockPolicy", mockPolicy.name)

    companion object {
        fun fromJson(json: JSONObject): SolutionPolicyProfile = SolutionPolicyProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isProtected = json.optProtectedFlag(),
            screenPolicy = json.optSolutionSourcePolicy("screenPolicy"),
            mockPolicy = json.optSolutionSourcePolicy("mockPolicy"),
        ).also(SolutionPolicyProfile::validate)
    }
}

data class StorageProfile(
    val id: String,
    val name: String,
    val kind: String = "APP_PRIVATE",
    val treeUri: String? = null,
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "Storage profile id must not be blank." }
        require(name.isNotBlank()) { "Storage profile name must not be blank." }
        require(kind == "APP_PRIVATE" || kind == "SAF_TREE") { "Storage profile kind must be APP_PRIVATE or SAF_TREE." }
        require(kind != "SAF_TREE" || !treeUri.isNullOrBlank()) { "SAF storage profile requires a tree URI." }
    }

    fun copyProfile(id: String, name: String): StorageProfile =
        copy(id = id, name = name, isProtected = false).also(StorageProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("isProtected", isProtected)
        .put("kind", kind)
        .putNullable("treeUri", treeUri)

    companion object {
        fun fromJson(json: JSONObject): StorageProfile = StorageProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isProtected = json.optProtectedFlag(),
            kind = json.optString("kind", "APP_PRIVATE"),
            treeUri = json.optNullableString("treeUri"),
        ).also(StorageProfile::validate)
    }
}

data class WorkflowProfileDefaults(
    val workflowId: String,
    val receiverProfileId: String,
    val commandProfileId: String,
    val usbBaudProfileId: String,
    val ntripCasterProfileId: String? = null,
    val ntripMountpointProfileId: String? = null,
    val recordingOutputProfileId: String,
    val storageProfileId: String,
) {
    fun validate() {
        require(workflowId.isNotBlank()) { "Workflow profile defaults workflow id must not be blank." }
        require(receiverProfileId.isNotBlank()) { "Workflow profile defaults receiver profile id must not be blank." }
        require(commandProfileId.isNotBlank()) { "Workflow profile defaults command profile id must not be blank." }
        require(usbBaudProfileId.isNotBlank()) { "Workflow profile defaults USB/baud profile id must not be blank." }
        require(recordingOutputProfileId.isNotBlank()) {
            "Workflow profile defaults recording output profile id must not be blank."
        }
        require(storageProfileId.isNotBlank()) { "Workflow profile defaults storage profile id must not be blank." }
        require(ntripCasterProfileId == null || ntripCasterProfileId.isNotBlank()) {
            "Workflow profile defaults NTRIP caster profile id must not be blank."
        }
        require(ntripMountpointProfileId == null || ntripMountpointProfileId.isNotBlank()) {
            "Workflow profile defaults NTRIP mountpoint profile id must not be blank."
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("workflowId", workflowId)
        .put("receiverProfileId", receiverProfileId)
        .put("commandProfileId", commandProfileId)
        .put("usbBaudProfileId", usbBaudProfileId)
        .putNullable("ntripCasterProfileId", ntripCasterProfileId)
        .putNullable("ntripMountpointProfileId", ntripMountpointProfileId)
        .put("recordingOutputProfileId", recordingOutputProfileId)
        .put("storageProfileId", storageProfileId)

    companion object {
        fun fromJson(json: JSONObject): WorkflowProfileDefaults = WorkflowProfileDefaults(
            workflowId = json.getString("workflowId"),
            receiverProfileId = json.getString("receiverProfileId"),
            commandProfileId = json.getString("commandProfileId"),
            usbBaudProfileId = json.getString("usbBaudProfileId"),
            ntripCasterProfileId = json.optNullableString("ntripCasterProfileId"),
            ntripMountpointProfileId = json.optNullableString("ntripMountpointProfileId"),
            recordingOutputProfileId = json.getString("recordingOutputProfileId"),
            storageProfileId = json.getString("storageProfileId"),
        ).also(WorkflowProfileDefaults::validate)
    }
}

fun <T> renameProfile(
    profiles: List<T>,
    profileId: String,
    newName: String,
    idOf: (T) -> String,
    isProtectedOf: (T) -> Boolean,
    rename: (T, String) -> T,
): List<T> {
    require(profileId.isNotBlank()) { "Profile id must not be blank." }
    require(newName.isNotBlank()) { "Profile name must not be blank." }

    var found = false
    val renamed = profiles.map { profile ->
        if (idOf(profile) != profileId) {
            profile
        } else {
            found = true
            require(!isProtectedOf(profile)) { "Protected profiles cannot be renamed." }
            rename(profile, newName)
        }
    }
    require(found) { "Profile '$profileId' was not found." }
    return renamed
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    if (value == null) put(name, JSONObject.NULL) else put(name, value)

private fun JSONObject.optProtectedFlag(): Boolean =
    optBoolean("isProtected", optBoolean("protected", false))

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).takeIf(String::isNotBlank) else null

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.putStringList(name: String, values: List<String>): JSONObject {
    val array = org.json.JSONArray()
    values.forEach(array::put)
    return put(name, array)
}

private fun JSONObject.optStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
}

private fun JSONObject.optSolutionSourcePolicy(name: String): SolutionSourcePolicy =
    runCatching {
        SolutionSourcePolicy.valueOf(optString(name, SolutionSourcePolicy.AUTO_BEST.name))
    }.getOrDefault(SolutionSourcePolicy.AUTO_BEST)
