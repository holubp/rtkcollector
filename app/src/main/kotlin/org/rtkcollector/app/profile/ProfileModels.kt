package org.rtkcollector.app.profile

import org.json.JSONObject

data class CommandProfile(
    val id: String,
    val name: String,
    val receiverFamily: String = "um980-n4",
    val initScript: String = "",
    val runtimeScript: String = "",
    val shutdownScript: String = "",
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

    companion object {
        fun fromJson(json: JSONObject): CommandProfile = CommandProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isProtected = json.optProtectedFlag(),
            receiverFamily = json.optString("receiverFamily", "um980-n4"),
            initScript = json.optString("initScript", ""),
            runtimeScript = json.optString("runtimeScript", ""),
            shutdownScript = json.optString("shutdownScript", ""),
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
        copy(id = id, name = name, isProtected = false).also(NtripCasterProfile::validate)

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
    val exportJsonSolution: Boolean = true,
    val exportGpx: Boolean = false,
    val recordRemoteBaseRaw: Boolean = false,
    val enableMockLocation: Boolean = false,
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "Recording policy id must not be blank." }
        require(name.isNotBlank()) { "Recording policy name must not be blank." }
        require(recordReceiverRx) { "Device receiver RX recording is required." }
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
        .put("exportJsonSolution", exportJsonSolution)
        .put("exportGpx", exportGpx)
        .put("recordRemoteBaseRaw", recordRemoteBaseRaw)
        .put("enableMockLocation", enableMockLocation)

    companion object {
        fun fromJson(json: JSONObject): RecordingPolicyProfile = RecordingPolicyProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isProtected = json.optProtectedFlag(),
            recordReceiverRx = json.optBoolean("recordReceiverRx", true),
            recordTxToReceiver = json.optBoolean("recordTxToReceiver", true),
            recordNtripCorrectionInput = json.optBoolean("recordNtripCorrectionInput", true),
            exportNmea = json.optBoolean("exportNmea", true),
            exportJsonSolution = json.optBoolean("exportJsonSolution", true),
            exportGpx = json.optBoolean("exportGpx", false),
            recordRemoteBaseRaw = json.optBoolean("recordRemoteBaseRaw", false),
            enableMockLocation = json.optBoolean("enableMockLocation", false),
        ).also(RecordingPolicyProfile::validate)
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
