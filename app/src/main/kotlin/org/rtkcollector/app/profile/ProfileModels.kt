package org.rtkcollector.app.profile

import org.json.JSONObject

data class CommandProfile(
    val id: String,
    val name: String,
    val receiverFamily: String = "um980-n4",
    val initScript: String = "# Optional user init commands",
    val shutdownScript: String = "",
) {
    fun validate() {
        require(id.isNotBlank()) { "Command profile id must not be blank." }
        require(name.isNotBlank()) { "Command profile name must not be blank." }
        require(receiverFamily.isNotBlank()) { "Command profile receiver family must not be blank." }
    }

    fun copyProfile(id: String, name: String): CommandProfile =
        copy(id = id, name = name).also(CommandProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("receiverFamily", receiverFamily)
        .put("initScript", initScript)
        .put("shutdownScript", shutdownScript)

    companion object {
        fun fromJson(json: JSONObject): CommandProfile = CommandProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            receiverFamily = json.optString("receiverFamily", "um980-n4"),
            initScript = json.optString("initScript", "# Optional user init commands"),
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
) {
    fun validate() {
        require(id.isNotBlank()) { "USB/baud profile id must not be blank." }
        require(name.isNotBlank()) { "USB/baud profile name must not be blank." }
        require(profileBaud in BAUD_RANGE) { "Profile baud must be 9600..921600." }
        require(serialBaud in BAUD_RANGE) { "Serial baud must be 9600..921600." }
    }

    fun copyProfile(id: String, name: String): UsbBaudProfile =
        copy(id = id, name = name).also(UsbBaudProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
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
) {
    fun validate() {
        require(id.isNotBlank()) { "NTRIP caster profile id must not be blank." }
        require(name.isNotBlank()) { "NTRIP caster profile name must not be blank." }
        require(port in 1..65535) { "NTRIP port must be 1..65535." }
    }

    fun copyProfile(id: String, name: String): NtripCasterProfile =
        copy(id = id, name = name).also(NtripCasterProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
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
    val ggaUploadPolicy: String = "NONE",
    val expectedFormat: String = "RTCM3",
    val remoteBaseRawAvailable: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "NTRIP mountpoint profile id must not be blank." }
        require(name.isNotBlank()) { "NTRIP mountpoint profile name must not be blank." }
        require(casterProfileId.isNotBlank()) { "NTRIP mountpoint profile must reference a caster profile." }
    }

    fun copyProfile(id: String, name: String): NtripMountpointProfile =
        copy(id = id, name = name).also(NtripMountpointProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("casterProfileId", casterProfileId)
        .put("mountpoint", mountpoint)
        .put("ggaUploadPolicy", ggaUploadPolicy)
        .put("expectedFormat", expectedFormat)
        .put("remoteBaseRawAvailable", remoteBaseRawAvailable)

    companion object {
        fun fromJson(json: JSONObject): NtripMountpointProfile = NtripMountpointProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            casterProfileId = json.getString("casterProfileId"),
            mountpoint = json.optString("mountpoint", ""),
            ggaUploadPolicy = json.optString("ggaUploadPolicy", "NONE"),
            expectedFormat = json.optString("expectedFormat", "RTCM3"),
            remoteBaseRawAvailable = json.optBoolean("remoteBaseRawAvailable", false),
        ).also(NtripMountpointProfile::validate)
    }
}

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
) {
    fun validate() {
        require(id.isNotBlank()) { "Recording policy id must not be blank." }
        require(name.isNotBlank()) { "Recording policy name must not be blank." }
        require(recordReceiverRx) { "Device receiver RX recording is required." }
    }

    fun copyProfile(id: String, name: String): RecordingPolicyProfile =
        copy(id = id, name = name).also(RecordingPolicyProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("recordReceiverRx", recordReceiverRx)
        .put("recordTxToReceiver", recordTxToReceiver)
        .put("recordNtripCorrectionInput", recordNtripCorrectionInput)
        .put("exportNmea", exportNmea)
        .put("exportJsonSolution", exportJsonSolution)
        .put("exportGpx", exportGpx)
        .put("recordRemoteBaseRaw", recordRemoteBaseRaw)

    companion object {
        fun fromJson(json: JSONObject): RecordingPolicyProfile = RecordingPolicyProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            recordReceiverRx = json.optBoolean("recordReceiverRx", true),
            recordTxToReceiver = json.optBoolean("recordTxToReceiver", true),
            recordNtripCorrectionInput = json.optBoolean("recordNtripCorrectionInput", true),
            exportNmea = json.optBoolean("exportNmea", true),
            exportJsonSolution = json.optBoolean("exportJsonSolution", true),
            exportGpx = json.optBoolean("exportGpx", false),
            recordRemoteBaseRaw = json.optBoolean("recordRemoteBaseRaw", false),
        ).also(RecordingPolicyProfile::validate)
    }
}

data class StorageProfile(
    val id: String,
    val name: String,
    val kind: String = "APP_PRIVATE",
    val treeUri: String? = null,
) {
    fun validate() {
        require(id.isNotBlank()) { "Storage profile id must not be blank." }
        require(name.isNotBlank()) { "Storage profile name must not be blank." }
        require(kind == "APP_PRIVATE" || kind == "SAF_TREE") { "Storage profile kind must be APP_PRIVATE or SAF_TREE." }
        require(kind != "SAF_TREE" || !treeUri.isNullOrBlank()) { "SAF storage profile requires a tree URI." }
    }

    fun copyProfile(id: String, name: String): StorageProfile =
        copy(id = id, name = name).also(StorageProfile::validate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("kind", kind)
        .putNullable("treeUri", treeUri)

    companion object {
        fun fromJson(json: JSONObject): StorageProfile = StorageProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            kind = json.optString("kind", "APP_PRIVATE"),
            treeUri = json.optNullableString("treeUri"),
        ).also(StorageProfile::validate)
    }
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    if (value == null) put(name, JSONObject.NULL) else put(name, value)

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
