package org.rtkcollector.app.profile

data class ActiveSetupValidationMessage(
    val key: ActiveSetupOptionKey,
    val message: String,
)

data class ActiveSetup(
    val settingsSetId: String,
    val settingsSetName: String,
    val options: Map<ActiveSetupOptionKey, EffectiveSetupOption>,
    val rememberedOverrides: Map<ActiveSetupOptionKey, String>,
    val transientChoices: Map<ActiveSetupOptionKey, String>,
) {
    fun option(key: ActiveSetupOptionKey): EffectiveSetupOption =
        options.getValue(key)

    val messages: List<ActiveSetupValidationMessage>
        get() = options.values.mapNotNull { option ->
            option.problem?.let { ActiveSetupValidationMessage(option.key, it) }
        }

    val canStart: Boolean
        get() = messages.isEmpty()
}

object ActiveSetupResolver {
    fun resolve(
        settingsSet: RecordingSettingsSet,
        rememberedOverrides: Map<ActiveSetupOptionKey, String>,
        transientChoices: Map<ActiveSetupOptionKey, String>,
        compatibility: Map<ActiveSetupOptionKey, Boolean>,
    ): ActiveSetup {
        val defaults = settingsSet.defaultOptionValues()
        val options = ActiveSetupOptionKey.entries.associateWith { key ->
            EffectiveSetupOption(
                key = key,
                label = key.label,
                defaultValueId = defaults[key],
                rememberedOverrideValueId = rememberedOverrides[key],
                transientValueId = transientChoices[key],
                policy = settingsSet.optionPolicies.policyFor(key),
                compatible = compatibility[key] ?: true,
            )
        }
        return ActiveSetup(
            settingsSetId = settingsSet.id,
            settingsSetName = settingsSet.name,
            options = options,
            rememberedOverrides = rememberedOverrides,
            transientChoices = transientChoices,
        )
    }

    fun rememberedAfterStop(
        policies: SettingsSetOptionPolicies,
        rememberedOverrides: Map<ActiveSetupOptionKey, String>,
    ): Map<ActiveSetupOptionKey, String> =
        rememberedOverrides.filterKeys { key ->
            policies.policyFor(key) != SettingsSetOptionPolicy.ASK_EVERY_TIME
        }

    private fun RecordingSettingsSet.defaultOptionValues(): Map<ActiveSetupOptionKey, String?> =
        mapOf(
            ActiveSetupOptionKey.WORKFLOW to workflowId,
            ActiveSetupOptionKey.RECEIVER_COMMAND to commandProfileRef.id,
            ActiveSetupOptionKey.USB_BAUD to usbBaudProfileRef.id,
            ActiveSetupOptionKey.NTRIP_CASTER to ntripCasterProfileRef?.id,
            ActiveSetupOptionKey.NTRIP_MOUNTPOINT to ntripMountpointProfileRef?.id,
            ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD to ntripCasterUploadProfileRef?.id,
            ActiveSetupOptionKey.RTKLIB to rtklibProfileRef?.id,
            ActiveSetupOptionKey.SOLUTION_POLICY to solutionPolicyProfileRef?.id,
            ActiveSetupOptionKey.RECORDING_OUTPUT to recordingOutputProfileRef.id,
            ActiveSetupOptionKey.STORAGE to storageProfileRef.id,
            ActiveSetupOptionKey.BASE_COORDINATE to basePositionProfileRef?.id,
        )

    private val ActiveSetupOptionKey.label: String
        get() = when (this) {
            ActiveSetupOptionKey.WORKFLOW -> "Workflow"
            ActiveSetupOptionKey.RECEIVER_COMMAND -> "Receiver/init profile"
            ActiveSetupOptionKey.USB_BAUD -> "USB/baud profile"
            ActiveSetupOptionKey.NTRIP_CASTER -> "NTRIP caster"
            ActiveSetupOptionKey.NTRIP_MOUNTPOINT -> "NTRIP mountpoint"
            ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> "NTRIP caster upload"
            ActiveSetupOptionKey.RTKLIB -> "RTKLIB profile"
            ActiveSetupOptionKey.SOLUTION_POLICY -> "Solution policy"
            ActiveSetupOptionKey.RECORDING_OUTPUT -> "Recording outputs"
            ActiveSetupOptionKey.STORAGE -> "Storage"
            ActiveSetupOptionKey.BASE_COORDINATE -> "Base coordinate"
        }
}
