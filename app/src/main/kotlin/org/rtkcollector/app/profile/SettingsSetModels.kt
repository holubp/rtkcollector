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

data class NtripCasterUploadOverride(
    val host: String? = null,
    val port: Int? = null,
    val mountpoint: String? = null,
    val username: String? = null,
    val secretId: String? = null,
    val password: String? = null,
) {
    fun validate() {
        port?.let { require(it in 1..65535) { "NTRIP caster upload port override must be 1..65535." } }
        require(password == null) { "Settings sets must not contain plaintext NTRIP caster upload passwords." }
    }
}

data class RecordingOutputOverride(
    val recordTxToReceiver: Boolean? = null,
    val recordNtripCorrectionInput: Boolean? = null,
    val exportNmea: Boolean? = null,
    val pppNmeaGgaQuality: Int? = null,
    val exportJsonSolution: Boolean? = null,
    val exportGpx: Boolean? = null,
    val recordRemoteBaseRaw: Boolean? = null,
    val enableMockLocation: Boolean? = null,
    val mockLocationRateHz: Int? = null,
) {
    fun validate() {
        pppNmeaGgaQuality?.let {
            require(it in RecordingPolicyProfile.ALLOWED_PPP_NMEA_GGA_QUALITIES) {
                "PPP NMEA GGA quality override must be one of ${RecordingPolicyProfile.ALLOWED_PPP_NMEA_GGA_QUALITIES.joinToString()}."
            }
        }
        mockLocationRateHz?.let {
            require(it in RecordingPolicyProfile.ALLOWED_MOCK_LOCATION_RATES_HZ) {
                "Mock location rate override must be one of ${RecordingPolicyProfile.ALLOWED_MOCK_LOCATION_RATES_HZ.joinToString()} Hz."
            }
        }
    }
}

data class StorageProfileOverride(
    val kind: String? = null,
    val treeUri: String? = null,
    val requiresTreeReselection: Boolean = false,
) {
    fun validate() {
        kind?.let {
            require(it in SETTINGS_SET_PROFILES_SET) {
                "Storage kind override must be APP_PRIVATE or SAF_TREE."
            }
            if (it == "SAF_TREE") {
                require(requiresTreeReselection || !treeUri.isNullOrBlank()) {
                    "Storage tree URI is required for SAF_TREE override."
                }
            }
        }
        require(!requiresTreeReselection || kind == "SAF_TREE") {
            "Only a SAF storage override can require folder reselection."
        }
        require(!requiresTreeReselection || treeUri.isNullOrBlank()) {
            "A SAF override awaiting folder reselection must not retain an ungranted URI."
        }
    }
}

data class SettingsSetOverrides(
    val commandProfileRef: ProfileReference? = null,
    val usbBaudProfileRef: ProfileReference? = null,
    val ntripCasterProfileRef: ProfileReference? = null,
    val ntripMountpointProfileRef: ProfileReference? = null,
    val ntripCasterUploadProfileRef: ProfileReference? = null,
    val recordingOutputProfileRef: ProfileReference? = null,
    val storageProfileRef: ProfileReference? = null,
    val baseCasterUploadEnabled: Boolean? = null,
    val command: CommandProfileOverride? = null,
    val usbBaud: UsbBaudProfileOverride? = null,
    val ntripCaster: NtripCasterOverride? = null,
    val ntripMountpoint: NtripMountpointOverride? = null,
    val ntripCasterUpload: NtripCasterUploadOverride? = null,
    val recordingOutput: RecordingOutputOverride? = null,
    val storage: StorageProfileOverride? = null,
) {
    fun validate() {
        commandProfileRef?.validate()
        usbBaudProfileRef?.validate()
        ntripCasterProfileRef?.validate()
        ntripMountpointProfileRef?.validate()
        ntripCasterUploadProfileRef?.validate()
        recordingOutputProfileRef?.validate()
        storageProfileRef?.validate()
        usbBaud?.validate()
        ntripCaster?.validate()
        ntripCasterUpload?.validate()
        recordingOutput?.validate()
        storage?.validate()
    }

    val hasChanges: Boolean
        get() = commandProfileRef != null ||
            usbBaudProfileRef != null ||
            ntripCasterProfileRef != null ||
            ntripMountpointProfileRef != null ||
            ntripCasterUploadProfileRef != null ||
            recordingOutputProfileRef != null ||
            storageProfileRef != null ||
            baseCasterUploadEnabled != null ||
            command?.hasChanges() == true ||
            usbBaud != null ||
            ntripCaster != null ||
            ntripMountpoint != null ||
            ntripCasterUpload != null ||
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
    val ntripCasterUploadProfileRef: ProfileReference? = null,
    val baseCasterUploadEnabled: Boolean = false,
    val rtklibProfileRef: ProfileReference? = null,
    val solutionPolicyProfileRef: ProfileReference? = null,
    val recordingOutputProfileRef: ProfileReference,
    val storageProfileRef: ProfileReference,
    val basePositionProfileRef: ProfileReference? = null,
    val optionPolicies: SettingsSetOptionPolicies = SettingsSetOptionPolicies.defaults(),
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
        ntripCasterUploadProfileRef?.validate()
        rtklibProfileRef?.validate()
        solutionPolicyProfileRef?.validate()
        recordingOutputProfileRef.validate()
        storageProfileRef.validate()
        basePositionProfileRef?.validate()
        overrides.validate()
    }

    fun displayNameWithOverrides(): String =
        if (hasLocalOverrides) "$name +" else name

    fun copySet(id: String, name: String): RecordingSettingsSet =
        copy(id = id, name = name, isProtected = false).also(RecordingSettingsSet::validate)

    fun toJson(): JSONObject {
        validate()
        return SettingsSetJson.toJson(this)
    }

    companion object {
        fun builtInDefaults(): List<RecordingSettingsSet> =
            listOf(
                builtInPlainRover(),
                builtInRoverNtrip(),
                builtInTemporaryBase(),
                builtInFixedBase(),
            )

        fun builtInPlainRover(): RecordingSettingsSet =
            builtInUm980SettingsSet(
                id = "um980-plain-rover",
                name = "UM980 plain rover",
                workflowId = "plain-rover",
                ntripCasterProfileRef = null,
            )

        fun fromJson(json: JSONObject): RecordingSettingsSet =
            SettingsSetJson.fromJson(json).also(RecordingSettingsSet::validate)

        fun builtInRoverNtrip(): RecordingSettingsSet =
            builtInUm980SettingsSet(
                id = "um980-rover-ntrip",
                name = "UM980 rover + NTRIP",
                workflowId = "rover-ntrip",
                ntripCasterProfileRef = ProfileReference("ntrip-caster-default", "NTRIP caster"),
            )

        fun builtInTemporaryBase(): RecordingSettingsSet =
            builtInUm980SettingsSet(
                id = "um980-temporary-base",
                name = "UM980 temporary base",
                workflowId = "base-calibration",
                ntripCasterProfileRef = ProfileReference("ntrip-caster-default", "NTRIP caster"),
            )

        fun builtInFixedBase(): RecordingSettingsSet =
            builtInUm980SettingsSet(
                id = "um980-fixed-base",
                name = "UM980 fixed base",
                workflowId = "fixed-base",
                commandProfileRef = ProfileReference("um980-base-config", "UM980 base config"),
                ntripCasterProfileRef = null,
            )

        private fun builtInUm980SettingsSet(
            id: String,
            name: String,
            workflowId: String,
            commandProfileRef: ProfileReference = ProfileReference(
                "um980-binary-multihz",
                "UM980 multi-Hz binary RTK+PPP",
            ),
            ntripCasterProfileRef: ProfileReference?,
        ): RecordingSettingsSet =
            RecordingSettingsSet(
                id = id,
                name = name,
                workflowId = workflowId,
                receiverProfileId = "um980-n4",
                commandProfileRef = commandProfileRef,
                usbBaudProfileRef = ProfileReference("um980-230400", "UM980 230400"),
                ntripCasterProfileRef = ntripCasterProfileRef,
                ntripMountpointProfileRef = null,
                ntripCasterUploadProfileRef = null,
                baseCasterUploadEnabled = false,
                recordingOutputProfileRef = ProfileReference(
                    "default-record-everything",
                    "Default V1 recording outputs",
                ),
                storageProfileRef = ProfileReference("app-private", "App-private external storage"),
                isProtected = true,
            )
    }
}

fun RecordingSettingsSet.effectiveCommandProfileRef(): ProfileReference =
    overrides.commandProfileRef ?: commandProfileRef

fun RecordingSettingsSet.effectiveUsbBaudProfileRef(): ProfileReference =
    overrides.usbBaudProfileRef ?: usbBaudProfileRef

fun RecordingSettingsSet.effectiveNtripCasterProfileRef(): ProfileReference? =
    overrides.ntripCasterProfileRef ?: ntripCasterProfileRef

fun RecordingSettingsSet.effectiveNtripMountpointProfileRef(): ProfileReference? =
    overrides.ntripMountpointProfileRef ?: ntripMountpointProfileRef

fun RecordingSettingsSet.effectiveNtripCasterUploadProfileRef(): ProfileReference? =
    overrides.ntripCasterUploadProfileRef ?: ntripCasterUploadProfileRef

fun RecordingSettingsSet.effectiveBaseCasterUploadEnabled(): Boolean =
    overrides.baseCasterUploadEnabled ?: baseCasterUploadEnabled

fun RecordingSettingsSet.effectiveRecordingOutputProfileRef(): ProfileReference =
    overrides.recordingOutputProfileRef ?: recordingOutputProfileRef

fun RecordingSettingsSet.effectiveStorageProfileRef(): ProfileReference =
    overrides.storageProfileRef ?: storageProfileRef

fun RecordingSettingsSet.reapplied(): RecordingSettingsSet =
    copy(overrides = SettingsSetOverrides())

object SettingsSetJson {
    private const val KEY_COMMAND = "commandProfile"
    private const val KEY_USB_BAUD = "usbBaudProfile"
    private const val KEY_NTRIP_CASTER = "ntripCasterProfile"
    private const val KEY_NTRIP_MOUNTPOINT = "ntripMountpointProfile"
    private const val KEY_NTRIP_CASTER_UPLOAD = "ntripCasterUploadProfile"
    private const val KEY_RTKLIB = "rtklibProfile"
    private const val KEY_SOLUTION_POLICY = "solutionPolicyProfile"
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
        .putNullable("ntripCasterUploadProfile", settingsSet.ntripCasterUploadProfileRef?.toJson())
        .put("baseCasterUploadEnabled", settingsSet.baseCasterUploadEnabled)
        .putNullable("rtklibProfile", settingsSet.rtklibProfileRef?.toJson())
        .putNullable("solutionPolicyProfile", settingsSet.solutionPolicyProfileRef?.toJson())
        .put("recordingOutputProfile", settingsSet.recordingOutputProfileRef.toJson())
        .put("storageProfile", settingsSet.storageProfileRef.toJson())
        .putNullable("basePositionProfile", settingsSet.basePositionProfileRef?.toJson())
        .put("optionPolicies", settingsSet.optionPolicies.toJson())
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
        ntripCasterUploadProfileRef = json.optJSONObject(KEY_NTRIP_CASTER_UPLOAD)?.let(ProfileReference::fromJson),
        baseCasterUploadEnabled = json.optBoolean("baseCasterUploadEnabled", false),
        rtklibProfileRef = json.optJSONObject(KEY_RTKLIB)?.let(ProfileReference::fromJson),
        solutionPolicyProfileRef = json.optJSONObject(KEY_SOLUTION_POLICY)?.let(ProfileReference::fromJson),
        recordingOutputProfileRef = ProfileReference.fromJson(json.getJSONObject(KEY_RECORDING_OUTPUT)),
        storageProfileRef = ProfileReference.fromJson(json.getJSONObject(KEY_STORAGE)),
        basePositionProfileRef = json.optJSONObject(KEY_BASE_POSITION)?.let(ProfileReference::fromJson),
        optionPolicies = json.optJSONObject("optionPolicies")?.let(SettingsSetOptionPolicies::fromJson)
            ?: SettingsSetOptionPolicies.defaults(),
        overrides = SettingsSetOverridesJson.fromJson(json.optJSONObject("overrides") ?: JSONObject()),
        isProtected = json.optBoolean("isProtected", false),
    )
}

private fun SettingsSetOptionPolicies.toJson(): JSONObject {
    val json = JSONObject()
    ActiveSetupOptionKey.entries.forEach { key ->
        val policy = policyFor(key)
        if (policy != SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE) {
            json.put(key.name, policy.name)
        }
    }
    return json
}

private fun SettingsSetOptionPolicies.Companion.fromJson(json: JSONObject): SettingsSetOptionPolicies {
    var policies = SettingsSetOptionPolicies.defaults()
    ActiveSetupOptionKey.entries.forEach { key ->
        val raw = json.optString(key.name, "")
        if (raw.isNotBlank()) {
            val policy = runCatching { SettingsSetOptionPolicy.valueOf(raw) }
                .getOrElse { SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE }
            policies = policies.withPolicy(key, policy)
        }
    }
    return policies
}

private fun SettingsSetOverrides.toJson(): JSONObject {
    val json = JSONObject()
    commandProfileRef?.let { json.put("commandProfileRef", it.toJson()) }
    usbBaudProfileRef?.let { json.put("usbBaudProfileRef", it.toJson()) }
    ntripCasterProfileRef?.let { json.put("ntripCasterProfileRef", it.toJson()) }
    ntripMountpointProfileRef?.let { json.put("ntripMountpointProfileRef", it.toJson()) }
    ntripCasterUploadProfileRef?.let { json.put("ntripCasterUploadProfileRef", it.toJson()) }
    recordingOutputProfileRef?.let { json.put("recordingOutputProfileRef", it.toJson()) }
    storageProfileRef?.let { json.put("storageProfileRef", it.toJson()) }
    baseCasterUploadEnabled?.let { json.put("baseCasterUploadEnabled", it) }
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
    ntripCasterUpload?.let {
        json.put(
            "ntripCasterUpload",
            JSONObject()
                .putNullable("host", it.host)
                .putNullable("port", it.port)
                .putNullable("mountpoint", it.mountpoint)
                .putNullable("username", it.username)
                .putNullable("secretId", it.secretId),
        )
    }
    recordingOutput?.let {
        json.put(
            "recordingOutput",
            JSONObject()
                .putNullable("recordTxToReceiver", it.recordTxToReceiver)
                .putNullable("recordNtripCorrectionInput", it.recordNtripCorrectionInput)
                .putNullable("exportNmea", it.exportNmea)
                .putNullable("pppNmeaGgaQuality", it.pppNmeaGgaQuality)
                .putNullable("exportJsonSolution", it.exportJsonSolution)
                .putNullable("exportGpx", it.exportGpx)
                .putNullable("recordRemoteBaseRaw", it.recordRemoteBaseRaw)
                .putNullable("enableMockLocation", it.enableMockLocation)
                .putNullable("mockLocationRateHz", it.mockLocationRateHz),
        )
    }
    storage?.let {
        json.put(
            "storage",
            JSONObject()
                .putNullable("kind", it.kind)
                .putNullable("treeUri", it.treeUri)
                .put("requiresTreeReselection", it.requiresTreeReselection),
        )
    }
    return json
}

private object SettingsSetOverridesJson {
    fun fromJson(json: JSONObject): SettingsSetOverrides = SettingsSetOverrides(
        commandProfileRef = json.optJSONObject("commandProfileRef")?.let(ProfileReference::fromJson),
        usbBaudProfileRef = json.optJSONObject("usbBaudProfileRef")?.let(ProfileReference::fromJson),
        ntripCasterProfileRef = json.optJSONObject("ntripCasterProfileRef")?.let(ProfileReference::fromJson),
        ntripMountpointProfileRef = json.optJSONObject("ntripMountpointProfileRef")?.let(ProfileReference::fromJson),
        ntripCasterUploadProfileRef = json.optJSONObject("ntripCasterUploadProfileRef")?.let(ProfileReference::fromJson),
        recordingOutputProfileRef = json.optJSONObject("recordingOutputProfileRef")?.let(ProfileReference::fromJson),
        storageProfileRef = json.optJSONObject("storageProfileRef")?.let(ProfileReference::fromJson),
        baseCasterUploadEnabled = json.optBooleanOrNull("baseCasterUploadEnabled"),
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
        ntripCasterUpload = if (!json.has("ntripCasterUpload") && !json.has("ntripCasterUploadProfile")) {
            null
        } else {
            json.optJSONObject("ntripCasterUpload")?.let {
                require(!(it.has("password") && !it.isNull("password"))) {
                    "Settings sets must not contain plaintext NTRIP caster upload passwords."
                }
                NtripCasterUploadOverride(
                    host = it.optNullableString("host"),
                    port = it.optIntOrNull("port"),
                    mountpoint = it.optNullableString("mountpoint"),
                    username = it.optNullableString("username"),
                    secretId = it.optNullableString("secretId"),
                    password = it.optNullableString("password"),
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
                    pppNmeaGgaQuality = it.optIntOrNull("pppNmeaGgaQuality"),
                    exportJsonSolution = it.optBooleanOrNull("exportJsonSolution"),
                    exportGpx = it.optBooleanOrNull("exportGpx"),
                    recordRemoteBaseRaw = it.optBooleanOrNull("recordRemoteBaseRaw"),
                    enableMockLocation = it.optBooleanOrNull("enableMockLocation"),
                    mockLocationRateHz = it.optIntOrNull("mockLocationRateHz"),
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
                    requiresTreeReselection = it.optBoolean("requiresTreeReselection", false),
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
