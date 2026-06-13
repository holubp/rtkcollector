package org.rtkcollector.app.profile

import org.json.JSONObject

private val SETTINGS_SET_PROFILES_SET = setOf("APP_PRIVATE", "SAF_TREE")
private val WORKFLOW_APPLICATION_POLICIES = setOf(
    WorkflowApplicationPolicy.SET_SPECIFIC,
    WorkflowApplicationPolicy.LET_USER_SELECT,
    WorkflowApplicationPolicy.LEAVE_INTACT,
)

object WorkflowApplicationPolicy {
    const val SET_SPECIFIC = "SET_SPECIFIC"
    const val LET_USER_SELECT = "LET_USER_SELECT"
    const val LEAVE_INTACT = "LEAVE_INTACT"
}

data class ProfileReference(
    val id: String,
    val name: String,
) {
    fun validate() {
        require(id.isNotBlank()) { "Profile reference id must not be blank." }
        require(name.isNotBlank()) { "Profile reference name must not be blank." }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)

    companion object {
        fun fromJson(json: JSONObject): ProfileReference = ProfileReference(
            id = json.getString("id"),
            name = json.getString("name"),
        ).also(ProfileReference::validate)
    }
}

data class CommandProfileOverride(
    val initScript: String? = null,
    val shutdownScript: String? = null,
) {
    fun hasChanges(): Boolean = initScript != null || shutdownScript != null
}

data class UsbBaudProfileOverride(
    val profileBaud: Int? = null,
    val serialBaud: Int? = null,
    val usbVid: Int? = null,
    val usbPid: Int? = null,
    val usbDeviceName: String? = null,
) {
    fun validate() {
        profileBaud?.let { require(it in 9600..921600) { "Profile baud override must be 9600..921600." } }
        serialBaud?.let { require(it in 9600..921600) { "Serial baud override must be 9600..921600." } }
    }
}

data class NtripCasterOverride(
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val secretId: String? = null,
    val password: String? = null,
) {
    fun validate() {
        port?.let { require(it in 1..65535) { "NTRIP port override must be 1..65535." } }
        require(password == null) { "Settings sets must not contain plaintext NTRIP passwords." }
    }
}

data class NtripMountpointOverride(
    val mountpoint: String? = null,
    val stationId: String? = null,
    val baseLatDeg: Double? = null,
    val baseLonDeg: Double? = null,
)

data class RecordingOutputOverride(
    val recordTxToReceiver: Boolean? = null,
    val recordNtripCorrectionInput: Boolean? = null,
    val exportNmea: Boolean? = null,
    val exportJsonSolution: Boolean? = null,
    val exportGpx: Boolean? = null,
    val recordRemoteBaseRaw: Boolean? = null,
    val enableMockLocation: Boolean? = null,
)

data class StorageProfileOverride(
    val kind: String? = null,
    val treeUri: String? = null,
) {
    fun validate() {
        kind?.let {
            require(it in SETTINGS_SET_PROFILES_SET) {
                "Storage kind override must be APP_PRIVATE or SAF_TREE."
            }
            if (it == "SAF_TREE") {
                require(!treeUri.isNullOrBlank()) { "Storage tree URI is required for SAF_TREE override." }
            }
        }
    }
}

data class SettingsSetOverrides(
    val command: CommandProfileOverride? = null,
    val usbBaud: UsbBaudProfileOverride? = null,
    val ntripCaster: NtripCasterOverride? = null,
    val ntripMountpoint: NtripMountpointOverride? = null,
    val recordingOutput: RecordingOutputOverride? = null,
    val storage: StorageProfileOverride? = null,
) {
    fun validate() {
        usbBaud?.validate()
        ntripCaster?.validate()
        storage?.validate()
    }

    val hasChanges: Boolean
        get() = command?.hasChanges() == true ||
            usbBaud != null ||
            ntripCaster != null ||
            ntripMountpoint != null ||
            recordingOutput != null ||
            storage != null
}

data class RecordingSettingsSet(
    val id: String,
    val name: String,
    val workflowId: String,
    val workflowApplicationPolicy: String = WorkflowApplicationPolicy.SET_SPECIFIC,
    val receiverProfileId: String,
    val commandProfileRef: ProfileReference,
    val usbBaudProfileRef: ProfileReference,
    val ntripCasterProfileRef: ProfileReference? = null,
    val ntripMountpointProfileRef: ProfileReference? = null,
    val recordingOutputProfileRef: ProfileReference,
    val storageProfileRef: ProfileReference,
    val basePositionProfileRef: ProfileReference? = null,
    val overrides: SettingsSetOverrides = SettingsSetOverrides(),
    val isProtected: Boolean = false,
) {
    val hasLocalOverrides: Boolean get() = overrides.hasChanges

    fun validate() {
        require(id.isNotBlank()) { "Settings set id must not be blank." }
        require(name.isNotBlank()) { "Settings set name must not be blank." }
        require(workflowId.isNotBlank()) { "Settings set workflow id must not be blank." }
        require(workflowApplicationPolicy in WORKFLOW_APPLICATION_POLICIES) {
            "Settings set workflow application policy is invalid."
        }
        require(receiverProfileId.isNotBlank()) { "Settings set receiver profile id must not be blank." }
        commandProfileRef.validate()
        usbBaudProfileRef.validate()
        ntripCasterProfileRef?.validate()
        ntripMountpointProfileRef?.validate()
        recordingOutputProfileRef.validate()
        storageProfileRef.validate()
        basePositionProfileRef?.validate()
        overrides.validate()
    }

    fun displayNameWithOverrides(): String =
        if (hasLocalOverrides) "$name + local changes" else name

    fun copySet(id: String, name: String): RecordingSettingsSet =
        copy(id = id, name = name, isProtected = false).also(RecordingSettingsSet::validate)

    fun toJson(): JSONObject {
        validate()
        return SettingsSetJson.toJson(this)
    }

    companion object {
        fun fromJson(json: JSONObject): RecordingSettingsSet =
            SettingsSetJson.fromJson(json).also(RecordingSettingsSet::validate)

        fun builtInRoverNtrip(): RecordingSettingsSet =
            RecordingSettingsSet(
                id = "um980-rover-ntrip",
                name = "UM980 rover + NTRIP",
                workflowId = "rover-ntrip",
                receiverProfileId = "um980-n4",
                commandProfileRef = ProfileReference("um980-binary-multihz", "UM980 binary multi-Hz"),
                usbBaudProfileRef = ProfileReference("um980-230400", "UM980 230400"),
                ntripCasterProfileRef = ProfileReference("ntrip-caster-default", "NTRIP caster"),
                ntripMountpointProfileRef = null,
                recordingOutputProfileRef = ProfileReference(
                    "default-record-everything",
                    "Default V1 recording outputs",
                ),
                storageProfileRef = ProfileReference("app-private", "App-private external storage"),
                isProtected = true,
            )
    }
}

object SettingsSetJson {
    private const val KEY_COMMAND = "commandProfile"
    private const val KEY_USB_BAUD = "usbBaudProfile"
    private const val KEY_NTRIP_CASTER = "ntripCasterProfile"
    private const val KEY_NTRIP_MOUNTPOINT = "ntripMountpointProfile"
    private const val KEY_RECORDING_OUTPUT = "recordingOutputProfile"
    private const val KEY_STORAGE = "storageProfile"
    private const val KEY_BASE_POSITION = "basePositionProfile"

    fun toJson(settingsSet: RecordingSettingsSet): JSONObject = JSONObject()
        .put("id", settingsSet.id)
        .put("name", settingsSet.name)
        .put("workflowId", settingsSet.workflowId)
        .put("workflowApplicationPolicy", settingsSet.workflowApplicationPolicy)
        .put("receiverProfileId", settingsSet.receiverProfileId)
        .put("commandProfile", settingsSet.commandProfileRef.toJson())
        .put("usbBaudProfile", settingsSet.usbBaudProfileRef.toJson())
        .putNullable("ntripCasterProfile", settingsSet.ntripCasterProfileRef?.toJson())
        .putNullable("ntripMountpointProfile", settingsSet.ntripMountpointProfileRef?.toJson())
        .put("recordingOutputProfile", settingsSet.recordingOutputProfileRef.toJson())
        .put("storageProfile", settingsSet.storageProfileRef.toJson())
        .putNullable("basePositionProfile", settingsSet.basePositionProfileRef?.toJson())
        .put("isProtected", settingsSet.isProtected)
        .put("overrides", settingsSet.overrides.toJson())

    fun fromJson(json: JSONObject): RecordingSettingsSet = RecordingSettingsSet(
        id = json.getString("id"),
        name = json.getString("name"),
        workflowId = json.getString("workflowId"),
        workflowApplicationPolicy = json.optString(
            "workflowApplicationPolicy",
            WorkflowApplicationPolicy.SET_SPECIFIC,
        ),
        receiverProfileId = json.getString("receiverProfileId"),
        commandProfileRef = ProfileReference.fromJson(json.getJSONObject(KEY_COMMAND)),
        usbBaudProfileRef = ProfileReference.fromJson(json.getJSONObject(KEY_USB_BAUD)),
        ntripCasterProfileRef = json.optJSONObject(KEY_NTRIP_CASTER)?.let(ProfileReference::fromJson),
        ntripMountpointProfileRef = json.optJSONObject(KEY_NTRIP_MOUNTPOINT)?.let(ProfileReference::fromJson),
        recordingOutputProfileRef = ProfileReference.fromJson(json.getJSONObject(KEY_RECORDING_OUTPUT)),
        storageProfileRef = ProfileReference.fromJson(json.getJSONObject(KEY_STORAGE)),
        basePositionProfileRef = json.optJSONObject(KEY_BASE_POSITION)?.let(ProfileReference::fromJson),
        overrides = SettingsSetOverridesJson.fromJson(json.optJSONObject("overrides") ?: JSONObject()),
        isProtected = json.optBoolean("isProtected", false),
    )
}

private fun SettingsSetOverrides.toJson(): JSONObject {
    val json = JSONObject()
    command?.let {
        json.put(
            "command",
            JSONObject()
                .putNullable("initScript", it.initScript)
                .putNullable("shutdownScript", it.shutdownScript),
        )
    }
    usbBaud?.let {
        json.put(
            "usbBaud",
            JSONObject()
                .putNullable("profileBaud", it.profileBaud)
                .putNullable("serialBaud", it.serialBaud)
                .putNullable("usbVid", it.usbVid)
                .putNullable("usbPid", it.usbPid)
                .putNullable("usbDeviceName", it.usbDeviceName),
        )
    }
    ntripCaster?.let {
        json.put(
            "ntripCaster",
            JSONObject()
                .putNullable("host", it.host)
                .putNullable("port", it.port)
                .putNullable("username", it.username)
                .putNullable("secretId", it.secretId),
        )
    }
    ntripMountpoint?.let {
        json.put(
            "ntripMountpoint",
            JSONObject()
                .putNullable("mountpoint", it.mountpoint)
                .putNullable("stationId", it.stationId)
                .putNullable("baseLatDeg", it.baseLatDeg)
                .putNullable("baseLonDeg", it.baseLonDeg),
        )
    }
    recordingOutput?.let {
        json.put(
            "recordingOutput",
            JSONObject()
                .putNullable("recordTxToReceiver", it.recordTxToReceiver)
                .putNullable("recordNtripCorrectionInput", it.recordNtripCorrectionInput)
                .putNullable("exportNmea", it.exportNmea)
                .putNullable("exportJsonSolution", it.exportJsonSolution)
                .putNullable("exportGpx", it.exportGpx)
                .putNullable("recordRemoteBaseRaw", it.recordRemoteBaseRaw)
                .putNullable("enableMockLocation", it.enableMockLocation),
        )
    }
    storage?.let {
        json.put(
            "storage",
            JSONObject()
                .putNullable("kind", it.kind)
                .putNullable("treeUri", it.treeUri),
        )
    }
    return json
}

private object SettingsSetOverridesJson {
    fun fromJson(json: JSONObject): SettingsSetOverrides = SettingsSetOverrides(
        command = if (!json.has("command") && !json.has("commandProfile") && !json.has("commandOverrides")) {
            null
        } else {
            json.optJSONObject("command")?.let { JSONObject(it.toString()) }?.let {
                CommandProfileOverride(
                    initScript = it.optNullableString("initScript"),
                    shutdownScript = it.optNullableString("shutdownScript"),
                )
            }
        },
        usbBaud = if (!json.has("usbBaud") && !json.has("usbBaudProfile") && !json.has("usbBaudOverrides")) {
            null
        } else {
            json.optJSONObject("usbBaud")?.let {
                UsbBaudProfileOverride(
                    profileBaud = it.optIntOrNull("profileBaud"),
                    serialBaud = it.optIntOrNull("serialBaud"),
                    usbVid = it.optIntOrNull("usbVid"),
                    usbPid = it.optIntOrNull("usbPid"),
                    usbDeviceName = it.optNullableString("usbDeviceName"),
                )
            }
        },
        ntripCaster = if (!json.has("ntripCaster") && !json.has("ntripCasterProfile") && !json.has("ntripCasterOverrides")) {
            null
        } else {
            json.optJSONObject("ntripCaster")?.let {
                require(!(it.has("password") && !it.isNull("password"))) {
                    "Settings sets must not contain plaintext NTRIP passwords."
                }
                NtripCasterOverride(
                    host = it.optNullableString("host"),
                    port = it.optIntOrNull("port"),
                    username = it.optNullableString("username"),
                    secretId = it.optNullableString("secretId"),
                    password = it.optNullableString("password"),
                )
            }
        },
        ntripMountpoint = if (!json.has("ntripMountpoint") && !json.has("ntripMountpointProfile")) {
            null
        } else {
            json.optJSONObject("ntripMountpoint")?.let {
                NtripMountpointOverride(
                    mountpoint = it.optNullableString("mountpoint"),
                    stationId = it.optNullableString("stationId"),
                    baseLatDeg = it.optDoubleOrNull("baseLatDeg"),
                    baseLonDeg = it.optDoubleOrNull("baseLonDeg"),
                )
            }
        },
        recordingOutput = if (!json.has("recordingOutput") && !json.has("recordingOutputProfile")) {
            null
        } else {
            json.optJSONObject("recordingOutput")?.let {
                RecordingOutputOverride(
                    recordTxToReceiver = it.optBooleanOrNull("recordTxToReceiver"),
                    recordNtripCorrectionInput = it.optBooleanOrNull("recordNtripCorrectionInput"),
                    exportNmea = it.optBooleanOrNull("exportNmea"),
                    exportJsonSolution = it.optBooleanOrNull("exportJsonSolution"),
                    exportGpx = it.optBooleanOrNull("exportGpx"),
                    recordRemoteBaseRaw = it.optBooleanOrNull("recordRemoteBaseRaw"),
                    enableMockLocation = it.optBooleanOrNull("enableMockLocation"),
                )
            }
        },
        storage = if (!json.has("storage") && !json.has("storageProfile")) {
            null
        } else {
            json.optJSONObject("storage")?.let {
                StorageProfileOverride(
                    kind = it.optNullableString("kind"),
                    treeUri = it.optNullableString("treeUri"),
                )
            }
        },
    ).also(SettingsSetOverrides::validate)
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    if (value == null) this else put(name, value)

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name) else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null
