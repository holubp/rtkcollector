package org.rtkcollector.app.profile

import org.json.JSONArray
import org.json.JSONObject

class PrivateRecoveryInput(private val original: String) {
    fun contentForPrivateStorage(): String = original
    override fun toString(): String = "PrivateRecoveryInput(redacted)"
}

internal fun isAllowedRecoveryField(field: String): Boolean {
    if (field in setOf("selection", "ntrip", "upload", "source", "command", "usbBaud", "recordingOutput",
            "storage", "workflowApplicationPolicy", "ntripCasterPolicy", "secretId", "treeUri",
            "basePositionProfileRef", "baseCoordinateSelection", "unclassifiedInput", "baseCasterUploadEnabled")) return true
    val parts = field.split('.')
    if (parts.size != 2) return false
    val fields = when (parts[0]) {
        "command" -> setOf("initScript", "shutdownScript")
        "usbBaud" -> setOf("profileBaud", "serialBaud", "usbVid", "usbPid", "usbDeviceName")
        "ntripCaster" -> setOf("host", "port", "username", "secretId")
        "ntripMountpoint" -> setOf("mountpoint", "stationId", "baseLatDeg", "baseLonDeg")
        "ntripCasterUpload" -> setOf("host", "port", "mountpoint", "username", "secretId")
        "recordingOutput" -> setOf("recordTxToReceiver", "recordNtripCorrectionInput", "exportNmea",
            "pppNmeaGgaQuality", "exportJsonSolution", "exportGpx", "recordRemoteBaseRaw", "enableMockLocation", "mockLocationRateHz")
        "storage" -> setOf("kind", "treeUri", "requiresTreeReselection")
        "optionPolicies" -> ActiveSetupOptionKey.entries.map { it.name }.toSet()
        "commandProfileRef", "usbBaudProfileRef", "ntripCasterProfileRef", "ntripMountpointProfileRef",
        "ntripCasterUploadProfileRef", "recordingOutputProfileRef", "storageProfileRef" -> setOf("id", "name")
        else -> emptySet()
    }
    return parts[1] in fields
}

/** Inspect keys without copying unclassified names or values into portable recovery metadata. */
internal fun retainPrivateLegacyInput(backup: SettingsBackupFile, original: JSONObject): SettingsBackupFile {
    val canonical = backup.toJson()
    val unknownOptions = linkedSetOf<ActiveSetupOptionKey>()
    val unknownPolicies = mutableMapOf<String, MutableSet<ActiveSetupOptionKey>>()
    fun optionFor(root: String): ActiveSetupOptionKey = when (root) {
        "commandProfiles" -> ActiveSetupOptionKey.RECEIVER_COMMAND
        "usbBaudProfiles" -> ActiveSetupOptionKey.USB_BAUD
        "ntripCasterProfiles", "ntripMountpointProfiles" -> ActiveSetupOptionKey.NTRIP_MOUNTPOINT
        "ntripCasterUploadProfiles" -> ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD
        "recordingPolicyProfiles" -> ActiveSetupOptionKey.RECORDING_OUTPUT
        "rtklibProfiles" -> ActiveSetupOptionKey.RTKLIB
        "solutionPolicyProfiles" -> ActiveSetupOptionKey.SOLUTION_POLICY
        "storageProfiles" -> ActiveSetupOptionKey.STORAGE
        else -> ActiveSetupOptionKey.WORKFLOW
    }
    fun inspect(value: Any?, reference: Any?, root: String, setId: String? = null) {
        when (value) {
            is JSONObject -> {
                val model = reference as? JSONObject
                val ownerSet = if (root == "settingsSets") value.optString("id", setId.orEmpty()) else setId
                value.keys().asSequence().forEach { key ->
                    if (key == "optionPolicies" && ownerSet != null) {
                        val policies = value.optJSONObject(key)
                        policies?.keys()?.asSequence()?.forEach { optionName ->
                            val option = ActiveSetupOptionKey.entries.firstOrNull { it.name == optionName }
                            val policy = policies.optString(optionName)
                            if (option == null) unknownOptions += ActiveSetupOptionKey.WORKFLOW
                            else if (SettingsSetOptionPolicy.entries.none { it.name == policy }) {
                                unknownPolicies.getOrPut(ownerSet) { linkedSetOf() } += option
                            }
                        }
                    } else if (model?.has(key) == true) {
                        inspect(value.opt(key), model.opt(key), if (root.isEmpty()) key else root, ownerSet)
                    } else if (key != "protected") {
                        unknownOptions += optionFor(if (root.isEmpty()) key else root)
                    }
                }
            }
            is JSONArray -> (0 until value.length()).forEach { index ->
                inspect(value.opt(index), (reference as? JSONArray)?.opt(index), root, setId)
            }
        }
    }
    inspect(original, canonical, "")
    val records = backup.migrationRecovery.toMutableMap()
    backup.settingsSets.forEach { set ->
        val added = unknownOptions.map { MigrationReviewIssue(it, "unclassifiedInput",
            LegacyFieldDisposition.UNCERTAIN, MigrationReviewReason.UNCLASSIFIED_INPUT) } +
            unknownPolicies[set.id].orEmpty().map { MigrationReviewIssue(it, "optionPolicies.${it.name}",
                LegacyFieldDisposition.UNCERTAIN, MigrationReviewReason.POLICY_CONFLICT) }
        if (added.isNotEmpty()) {
            val prior = records[set.id] ?: LegacyMigrationRecovery(set, emptySet())
            records[set.id] = prior.copy(issues = (prior.issues + added).distinct(),
                reviewReasons = prior.reviewReasons + added.mapNotNull { it.reason })
        }
    }
    return backup.copy(migrationRecovery = records, privateRecoveryInput = PrivateRecoveryInput(original.toString()))
}
