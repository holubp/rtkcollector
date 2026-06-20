package org.rtkcollector.app.profile

enum class ActiveSetupOptionKey {
    WORKFLOW,
    RECEIVER_COMMAND,
    USB_BAUD,
    NTRIP_CASTER,
    NTRIP_MOUNTPOINT,
    NTRIP_CASTER_UPLOAD,
    RTKLIB,
    SOLUTION_POLICY,
    RECORDING_OUTPUT,
    STORAGE,
    BASE_COORDINATE,
}

enum class SettingsSetOptionPolicy {
    DEFAULT_OVERRIDABLE,
    LOCKED,
    CHOOSE_ONCE_REMEMBER,
    ASK_EVERY_TIME,
}

data class SettingsSetOptionPolicies(
    val values: Map<ActiveSetupOptionKey, SettingsSetOptionPolicy> = defaultsMap(),
) {
    fun policyFor(key: ActiveSetupOptionKey): SettingsSetOptionPolicy =
        values[key] ?: SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE

    fun withPolicy(
        key: ActiveSetupOptionKey,
        policy: SettingsSetOptionPolicy,
    ): SettingsSetOptionPolicies =
        copy(values = values + (key to policy))

    companion object {
        fun defaults(): SettingsSetOptionPolicies = SettingsSetOptionPolicies()

        fun defaultsMap(): Map<ActiveSetupOptionKey, SettingsSetOptionPolicy> =
            ActiveSetupOptionKey.entries.associateWith {
                SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE
            }
    }
}

data class EffectiveSetupOption(
    val key: ActiveSetupOptionKey,
    val label: String,
    val defaultValueId: String?,
    val rememberedOverrideValueId: String?,
    val transientValueId: String?,
    val policy: SettingsSetOptionPolicy,
    val compatible: Boolean,
) {
    val effectiveValueId: String?
        get() = when (policy) {
            SettingsSetOptionPolicy.LOCKED -> defaultValueId
            SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE -> rememberedOverrideValueId ?: transientValueId ?: defaultValueId
            SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER -> rememberedOverrideValueId ?: transientValueId ?: defaultValueId
            SettingsSetOptionPolicy.ASK_EVERY_TIME -> transientValueId
        }?.takeIf(String::isNotBlank)

    val requiresUserSelection: Boolean
        get() = effectiveValueId == null &&
            (policy == SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER ||
                policy == SettingsSetOptionPolicy.ASK_EVERY_TIME)

    val isOverridden: Boolean
        get() = policy != SettingsSetOptionPolicy.LOCKED &&
            effectiveValueId != null &&
            effectiveValueId != defaultValueId

    val canStart: Boolean
        get() = compatible && !requiresUserSelection

    val problem: String?
        get() = when {
            !compatible -> "$label is not compatible with the active setup."
            requiresUserSelection -> "$label must be selected for this recording."
            else -> null
        }
}
