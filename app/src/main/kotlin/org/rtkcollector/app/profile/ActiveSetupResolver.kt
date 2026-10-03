package org.rtkcollector.app.profile

import org.rtkcollector.app.base.AcceptedBaseCoordinate
import org.rtkcollector.app.base.FixedBaseCommandValidator
import org.rtkcollector.core.solution.SolutionSourcePolicy
import org.rtkcollector.core.correction.Um980RtcmBaseOutputSanity
import org.rtkcollector.core.correction.normalizeSourceUploadMountpoint

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
    val uploadSelection: UploadSelection? = null,
    val defaultUploadSelection: UploadSelection? = null,
    val validationMessages: List<ActiveSetupValidationMessage> = emptyList(),
    val resolvedProfiles: ResolvedActiveSetupProfiles? = null,
) {
    fun option(key: ActiveSetupOptionKey): EffectiveSetupOption =
        options.getValue(key)

    val messages: List<ActiveSetupValidationMessage>
        get() = options.values.mapNotNull { option ->
            option.problem?.let { ActiveSetupValidationMessage(option.key, it) }
        } + validationMessages

    val canStart: Boolean
        get() = messages.isEmpty()

    val isModified: Boolean
        get() = options.values.any { it.key != ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD && it.isOverridden } ||
            (option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD).applicable && uploadSelection != null &&
                uploadSelection != defaultUploadSelection)

    fun snapshot(): ActiveSetupSnapshot = ActiveSetupSnapshot(
        settingsSetId = settingsSetId,
        profileIds = options.mapValues { (_, option) -> option.effectiveValueId.takeIf { option.dependencyActive } } +
            (ActiveSetupOptionKey.NTRIP_CASTER to resolvedProfiles?.caster?.id),
        uploadEnabled = option(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD).applicable && uploadSelection?.enabled == true,
    )

    fun displayProjection(
        settingsSet: RecordingSettingsSet,
        referenceLookup: (ActiveSetupOptionKey, String) -> ProfileReference?,
    ): ActiveSetupDisplayProjection {
        require(settingsSet.id == settingsSetId) { "Active setup belongs to another settings set." }
        val snapshot = snapshot()
        val references = options.mapValues { (key, option) ->
            option.effectiveValueId?.let { referenceLookup(key, it) }
        } + (ActiveSetupOptionKey.NTRIP_CASTER to snapshot.profileId(ActiveSetupOptionKey.NTRIP_CASTER)
            ?.let { referenceLookup(ActiveSetupOptionKey.NTRIP_CASTER, it) })
        return ActiveSetupDisplayProjection(snapshot, references, options, messages)
    }

    fun projectSettingsSet(
        settingsSet: RecordingSettingsSet,
        referenceLookup: (ActiveSetupOptionKey, String) -> ProfileReference?,
    ): RecordingSettingsSet {
        require(settingsSet.id == settingsSetId) { "Active setup belongs to another settings set." }
        require(canStart) { messages.joinToString(" ") { it.message } }
        fun reference(key: ActiveSetupOptionKey, original: ProfileReference?): ProfileReference? {
            if (!option(key).dependencyActive) return original
            val selectedId = option(key).effectiveValueId ?: return null
            return original?.takeIf { it.id == selectedId }
                ?: requireNotNull(referenceLookup(key, selectedId)) {
                    "Selected $key profile $selectedId is missing."
                }.also {
                    require(it.id == selectedId) { "Profile lookup returned another identity for $key." }
                    it.validate()
                }
        }
        fun required(key: ActiveSetupOptionKey, original: ProfileReference): ProfileReference =
            requireNotNull(reference(key, original)) { "$key requires a profile." }

        return settingsSet.copy(
            workflowId = requireNotNull(option(ActiveSetupOptionKey.WORKFLOW).effectiveValueId) {
                "Workflow must be selected."
            },
            commandProfileRef = required(ActiveSetupOptionKey.RECEIVER_COMMAND, settingsSet.commandProfileRef),
            usbBaudProfileRef = required(ActiveSetupOptionKey.USB_BAUD, settingsSet.usbBaudProfileRef),
            ntripCasterProfileRef = null,
            ntripCasterRestrictionRef = settingsSet.correctionCasterRestrictionRef(),
            ntripCasterPolicyNeedsReview = settingsSet.correctionCasterPolicyProblem() != null,
            ntripMountpointProfileRef = reference(ActiveSetupOptionKey.NTRIP_MOUNTPOINT,
                settingsSet.ntripMountpointProfileRef),
            ntripCasterUploadProfileRef = reference(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD,
                settingsSet.ntripCasterUploadProfileRef),
            baseCasterUploadEnabled = snapshot().uploadEnabled,
            rtklibProfileRef = reference(ActiveSetupOptionKey.RTKLIB, settingsSet.rtklibProfileRef),
            solutionPolicyProfileRef = reference(ActiveSetupOptionKey.SOLUTION_POLICY,
                settingsSet.solutionPolicyProfileRef),
            recordingOutputProfileRef = required(ActiveSetupOptionKey.RECORDING_OUTPUT,
                settingsSet.recordingOutputProfileRef),
            storageProfileRef = required(ActiveSetupOptionKey.STORAGE, settingsSet.storageProfileRef),
            basePositionProfileRef = reference(ActiveSetupOptionKey.BASE_COORDINATE,
                settingsSet.basePositionProfileRef),
            overrides = SettingsSetOverrides(),
        )
    }
}

data class ActiveSetupDisplayProjection(
    val snapshot: ActiveSetupSnapshot,
    val references: Map<ActiveSetupOptionKey, ProfileReference?>,
    val options: Map<ActiveSetupOptionKey, EffectiveSetupOption>,
    val messages: List<ActiveSetupValidationMessage>,
)

data class ActiveSetupSnapshot(
    val settingsSetId: String,
    val profileIds: Map<ActiveSetupOptionKey, String?>,
    val uploadEnabled: Boolean,
) {
    fun profileId(key: ActiveSetupOptionKey): String? = profileIds[key]
}

data class ResolvedActiveSetupProfiles(
    val command: CommandProfile?,
    val usbBaud: UsbBaudProfile?,
    val caster: NtripCasterProfile?,
    val source: NtripMountpointProfile?,
    val upload: NtripCasterUploadProfile?,
    val rtklib: RtklibProfile?,
    val solution: SolutionPolicyProfile?,
    val output: RecordingPolicyProfile?,
    val storage: StorageProfile?,
    val baseCoordinate: AcceptedBaseCoordinate?,
)

data class ActiveSetupProfileGraph(
    val commandProfiles: List<CommandProfile> = emptyList(),
    val usbBaudProfiles: List<UsbBaudProfile> = emptyList(),
    val ntripCasterProfiles: List<NtripCasterProfile> = emptyList(),
    val ntripMountpointProfiles: List<NtripMountpointProfile> = emptyList(),
    val ntripCasterUploadProfiles: List<NtripCasterUploadProfile> = emptyList(),
    val rtklibProfiles: List<RtklibProfile> = emptyList(),
    val solutionPolicyProfiles: List<SolutionPolicyProfile> = emptyList(),
    val recordingOutputProfiles: List<RecordingPolicyProfile> = emptyList(),
    val storageProfiles: List<StorageProfile> = emptyList(),
    val baseCoordinates: List<AcceptedBaseCoordinate> = emptyList(),
) {
    fun referenceFor(key: ActiveSetupOptionKey, id: String): ProfileReference? {
        val entries = when (key) {
            ActiveSetupOptionKey.RECEIVER_COMMAND -> commandProfiles.map { it.id to it.name }
            ActiveSetupOptionKey.USB_BAUD -> usbBaudProfiles.map { it.id to it.name }
            ActiveSetupOptionKey.NTRIP_CASTER -> ntripCasterProfiles.map { it.id to it.name }
            ActiveSetupOptionKey.NTRIP_MOUNTPOINT -> ntripMountpointProfiles.map { it.id to it.name }
            ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> ntripCasterUploadProfiles.map { it.id to it.name }
            ActiveSetupOptionKey.RTKLIB -> rtklibProfiles.map { it.id to it.name }
            ActiveSetupOptionKey.SOLUTION_POLICY -> solutionPolicyProfiles.map { it.id to it.name }
            ActiveSetupOptionKey.RECORDING_OUTPUT -> recordingOutputProfiles.map { it.id to it.name }
            ActiveSetupOptionKey.STORAGE -> storageProfiles.map { it.id to it.name }
            ActiveSetupOptionKey.BASE_COORDINATE -> baseCoordinates.map { it.id to it.name }
            ActiveSetupOptionKey.WORKFLOW -> emptyList()
        }
        return entries.filter { it.first == id }.singleOrNull()?.let { ProfileReference(it.first, it.second) }
    }

    internal fun resolve(set: RecordingSettingsSet, setup: ActiveSetup): ActiveSetup {
        val problems = mutableListOf<ActiveSetupValidationMessage>()
        fun problem(key: ActiveSetupOptionKey, message: String) { problems += ActiveSetupValidationMessage(key, message) }
        if (set.overrides.hasChanges) {
            problem(ActiveSetupOptionKey.WORKFLOW,
                "Settings set contains legacy overlays; migrate them to owning profiles before starting.")
        }
        fun checked(key: ActiveSetupOptionKey, validation: () -> Unit) {
            try { validation() } catch (error: IllegalArgumentException) {
                problem(key, error.message ?: "Selected $key profile is invalid.")
            }
        }
        fun <T> lookup(key: ActiveSetupOptionKey, values: List<T>, id: (T) -> String): T? {
            if (!setup.option(key).dependencyActive) return null
            val selected = setup.option(key).effectiveValueId ?: return null
            val matches = values.filter { id(it) == selected }
            if (matches.size != 1) {
                problem(key, "Selected $key profile $selected is ${if (matches.isEmpty()) "missing" else "ambiguous"}.")
                return null
            }
            return matches.single()
        }
        val command = lookup(ActiveSetupOptionKey.RECEIVER_COMMAND, commandProfiles, CommandProfile::id)
        val baud = lookup(ActiveSetupOptionKey.USB_BAUD, usbBaudProfiles, UsbBaudProfile::id)
        val source = lookup(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, ntripMountpointProfiles, NtripMountpointProfile::id)
        val upload = lookup(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD, ntripCasterUploadProfiles, NtripCasterUploadProfile::id)
        val rtklib = lookup(ActiveSetupOptionKey.RTKLIB, rtklibProfiles, RtklibProfile::id)
        val solution = lookup(ActiveSetupOptionKey.SOLUTION_POLICY, solutionPolicyProfiles, SolutionPolicyProfile::id)
        val output = lookup(ActiveSetupOptionKey.RECORDING_OUTPUT, recordingOutputProfiles, RecordingPolicyProfile::id)
        val storage = lookup(ActiveSetupOptionKey.STORAGE, storageProfiles, StorageProfile::id)
        val base = lookup(ActiveSetupOptionKey.BASE_COORDINATE, baseCoordinates, AcceptedBaseCoordinate::id)
        val caster = source?.let { selected ->
            val matches = ntripCasterProfiles.filter { it.id == selected.casterProfileId }
            if (matches.size != 1) {
                problem(ActiveSetupOptionKey.NTRIP_MOUNTPOINT,
                    "Source caster profile ${selected.casterProfileId} is ${if (matches.isEmpty()) "missing" else "ambiguous"}.")
                null
            } else matches.single()
        }
        val workflowId = setup.option(ActiveSetupOptionKey.WORKFLOW).effectiveValueId
        if (workflowId != null && workflowId !in setOf("plain-rover", "rover-ntrip", "rover-rtklib", "rover-ntrip-rtklib", "base-calibration", "fixed-base")) {
            problem(ActiveSetupOptionKey.WORKFLOW, "Selected workflow is not supported.")
        }
        command?.let {
            checked(ActiveSetupOptionKey.RECEIVER_COMMAND, it::validate)
            val family = ProfileCompatibility.commandProfile(set.receiverProfileId, it)
            if (!family.activatable) problem(ActiveSetupOptionKey.RECEIVER_COMMAND, family.reason.orEmpty())
            checked(ActiveSetupOptionKey.RECEIVER_COMMAND) {
                val commands = (it.initScript + "\n" + it.runtimeScript).lineSequence().map(String::trim).toList()
                validateWorkflowModeCommandsForStart(workflowId, commands)
                validateUm980OutputFrequenciesForStart(it.receiverFamily, commands)
            }
        }
        baud?.let { checked(ActiveSetupOptionKey.USB_BAUD, it::validate) }
        if (command != null && baud != null) checked(ActiveSetupOptionKey.USB_BAUD) {
            receiverBaudTransitionCommands(command.receiverFamily, baud.profileBaud, baud.serialBaud)
        }
        output?.let { checked(ActiveSetupOptionKey.RECORDING_OUTPUT, it::validate) }
        storage?.let {
            checked(ActiveSetupOptionKey.STORAGE, it::validate)
            if (it.requiresTreeReselection) problem(ActiveSetupOptionKey.STORAGE, "Select the Android recording folder again before starting.")
        }
        source?.let { checked(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, it::validate) }
        if (setup.option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).applicable) {
            set.correctionCasterPolicyProblem()?.let { problem(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, it) }
            val restriction = set.correctionCasterRestrictionRef()?.id
            if (source != null && restriction != null && source.casterProfileId != restriction) {
                problem(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, "Selected source does not satisfy caster restriction $restriction.")
            }
        }
        caster?.let { checked(ActiveSetupOptionKey.NTRIP_MOUNTPOINT) {
            it.validate()
            validateCorrectionProtocolPolicy(it.protocolPolicy)
            require(it.host.isNotBlank()) { "NTRIP host is required." }
            require(source.mountpoint.isNotBlank()) { "NTRIP mountpoint is required." }
            it.toCore(false)
        } }
        upload?.let { checked(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD) {
            it.validate()
            require(it.host.isNotBlank()) { "NTRIP caster upload host is required." }
            require(it.mountpoint.isNotBlank()) { "NTRIP caster upload mountpoint is required." }
            normalizeSourceUploadMountpoint(it.mountpoint)
            it.toCore(false)
        } }
        if (upload != null && command != null) {
            val commands = (command.initScript + "\n" + command.runtimeScript).lineSequence().toList()
            val sanity = Um980RtcmBaseOutputSanity.validateCommands(commands)
            if (!sanity.canUpload) problem(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD,
                "NTRIP caster upload requires base RTCM output: ${sanity.errors.joinToString(" ")}")
        }
        rtklib?.let { checked(ActiveSetupOptionKey.RTKLIB) {
            it.validate()
            require(it.enabled) { "Selected workflow requires an enabled RTKLIB profile." }
        } }
        if (rtklib?.enabled == true && command != null) {
            val validation = RtklibStartValidator.validate(
                enabled = true, receiverProfileId = set.receiverProfileId,
                commands = (command.initScript + "\n" + command.runtimeScript).lineSequence()
                    .map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.toList(),
                ntripEnabled = setup.option(ActiveSetupOptionKey.NTRIP_MOUNTPOINT).applicable,
                ntripConfigured = caster != null && caster.host.isNotBlank() && source.mountpoint.isNotBlank(),
                outputNmea = rtklib.outputNmea, outputPos = rtklib.outputPos,
            )
            validation.errors.forEach { problem(ActiveSetupOptionKey.RTKLIB, it) }
        }
        solution?.let { selected ->
            checked(ActiveSetupOptionKey.SOLUTION_POLICY, selected::validate)
            if (rtklib?.enabled != true) {
                if (selected.screenPolicy == SolutionSourcePolicy.RTKLIB_ONLY) {
                    problem(ActiveSetupOptionKey.SOLUTION_POLICY, "Screen RTKLIB_ONLY requires an active RTKLIB workflow.")
                }
                if (output?.enableMockLocation == true && selected.mockPolicy == SolutionSourcePolicy.RTKLIB_ONLY) {
                    problem(ActiveSetupOptionKey.SOLUTION_POLICY, "Mock RTKLIB_ONLY requires an active RTKLIB workflow.")
                }
            }
        }
        base?.let { checked(ActiveSetupOptionKey.BASE_COORDINATE, it::validate) }
        if (workflowId == "fixed-base" && command != null && base != null) {
            checked(ActiveSetupOptionKey.BASE_COORDINATE) {
                FixedBaseCommandValidator.validateSelectedCoordinateMatchesProfile(command, base)
            }
        }
        return setup.copy(validationMessages = setup.validationMessages + problems, resolvedProfiles = ResolvedActiveSetupProfiles(
            command, baud, caster, source, upload, rtklib, solution, output, storage, base,
        ))
    }
}

object ActiveSetupResolver {
    fun resolve(
        settingsSet: RecordingSettingsSet,
        selections: ActiveSetupSelections,
        currentWorkflowId: String? = null,
        compatibility: Map<ActiveSetupOptionKey, Boolean> = emptyMap(),
        profileGraph: ActiveSetupProfileGraph? = null,
    ): ActiveSetup {
        require(settingsSet.id == selections.settingsSetId) { "Selection belongs to another settings set." }
        val defaults = settingsSet.defaultOptionValues().toMutableMap()
        if (settingsSet.workflowApplicationPolicy == WorkflowApplicationPolicy.LEAVE_INTACT) {
            defaults[ActiveSetupOptionKey.WORKFLOW] = selections.workflowBaselineId ?: currentWorkflowId
        }
        val workflowChoice = choiceFor(settingsSet, selections, ActiveSetupOptionKey.WORKFLOW)
        val workflowId = if (workflowChoice != null) workflowChoice.profileId else when (settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW)) {
            SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER, SettingsSetOptionPolicy.ASK_EVERY_TIME -> null
            else -> defaults[ActiveSetupOptionKey.WORKFLOW]
        }
        val usesNtrip = workflowId in setOf("rover-ntrip", "rover-rtklib", "rover-ntrip-rtklib", "base-calibration")
        val usesRtklib = workflowId in setOf("rover-rtklib", "rover-ntrip-rtklib")
        val usesBase = workflowId in setOf("fixed-base", "base-calibration")
        val uploadPolicy = settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD)
        val defaultUpload = UploadSelection(settingsSet.baseCasterUploadEnabled,
            settingsSet.ntripCasterUploadProfileRef?.id?.let(SelectionChoice::profile) ?: SelectionChoice.none())
        val upload = when (uploadPolicy) {
            SettingsSetOptionPolicy.LOCKED -> defaultUpload
            SettingsSetOptionPolicy.ASK_EVERY_TIME -> selections.transientUploadSelection
            SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER -> selections.uploadSelection
            SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE -> selections.uploadSelection ?: defaultUpload
        }
        val options = ActiveSetupOptionKey.entries.associateWith { key ->
            val applicable = when (key) {
                ActiveSetupOptionKey.NTRIP_CASTER -> false
                ActiveSetupOptionKey.NTRIP_MOUNTPOINT -> usesNtrip
                ActiveSetupOptionKey.RTKLIB -> usesRtklib
                ActiveSetupOptionKey.BASE_COORDINATE -> workflowId == "fixed-base" || usesBase && upload?.enabled == true
                ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> usesBase
                else -> true
            }
            EffectiveSetupOption(
                key = key,
                label = key.label,
                defaultValueId = defaults[key],
                rememberedOverrideValueId = null,
                transientValueId = null,
                policy = settingsSet.optionPolicies.policyFor(key),
                compatible = (compatibility[key] ?: true) &&
                    !(key == ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD && upload?.enabled == true &&
                        upload.profile.kind != SelectionChoiceKind.PROFILE),
                selectedChoice = when (key) {
                    ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> upload?.profile
                    else -> choiceFor(settingsSet, selections, key)
                },
                applicable = applicable,
                dependencyActive = applicable && (key != ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD || upload?.enabled == true),
                required = key in setOf(ActiveSetupOptionKey.WORKFLOW, ActiveSetupOptionKey.RECEIVER_COMMAND,
                    ActiveSetupOptionKey.USB_BAUD, ActiveSetupOptionKey.NTRIP_MOUNTPOINT, ActiveSetupOptionKey.RTKLIB,
                    ActiveSetupOptionKey.RECORDING_OUTPUT, ActiveSetupOptionKey.STORAGE, ActiveSetupOptionKey.BASE_COORDINATE,
                    ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD),
                selectionPresent = if (key == ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD) upload != null else
                    choiceFor(settingsSet, selections, key)?.kind?.let { it != SelectionChoiceKind.UNCHOSEN } ?: false,
                provenance = when {
                    settingsSet.isOptionLocked(key) -> SelectionProvenance.LOCKED
                    key == ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD -> when {
                        upload == null -> SelectionProvenance.UNCHOSEN
                        uploadPolicy == SettingsSetOptionPolicy.ASK_EVERY_TIME -> SelectionProvenance.TRANSIENT
                        selections.uploadSelection != null -> if (uploadPolicy == SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER) SelectionProvenance.REMEMBERED else SelectionProvenance.ACTIVE
                        else -> SelectionProvenance.DEFAULT
                    }
                    key in selections.transientChoices && settingsSet.optionPolicies.policyFor(key) == SettingsSetOptionPolicy.ASK_EVERY_TIME -> SelectionProvenance.TRANSIENT
                    key in selections.activeChoices && settingsSet.optionPolicies.policyFor(key) == SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE -> SelectionProvenance.ACTIVE
                    key in selections.rememberedChoices && settingsSet.optionPolicies.policyFor(key) == SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER -> SelectionProvenance.REMEMBERED
                    settingsSet.optionPolicies.policyFor(key) in setOf(SettingsSetOptionPolicy.ASK_EVERY_TIME, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER) -> SelectionProvenance.UNCHOSEN
                    key == ActiveSetupOptionKey.WORKFLOW && settingsSet.workflowApplicationPolicy == WorkflowApplicationPolicy.LEAVE_INTACT -> SelectionProvenance.WORKFLOW_BASELINE
                    else -> SelectionProvenance.DEFAULT
                },
            )
        }
        val setup = ActiveSetup(
            settingsSet.id, settingsSet.name, options,
            selections.rememberedChoices.mapNotNull { (key, value) -> value.profileId?.let { key to it } }.toMap(),
            selections.transientChoices.mapNotNull { (key, value) -> value.profileId?.let { key to it } }.toMap(),
            upload,
            defaultUpload,
            validationMessages = listOfNotNull(settingsSet.workflowActivationPolicyProblem()?.let {
                ActiveSetupValidationMessage(ActiveSetupOptionKey.WORKFLOW, it)
            }),
        )
        return profileGraph?.resolve(settingsSet, setup) ?: setup
    }

    private fun choiceFor(
        set: RecordingSettingsSet,
        selections: ActiveSetupSelections,
        key: ActiveSetupOptionKey,
    ): SelectionChoice? = when (set.optionPolicies.policyFor(key)) {
        SettingsSetOptionPolicy.LOCKED -> null
        SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE -> selections.activeChoices[key]
        SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER -> selections.rememberedChoices[key]
        SettingsSetOptionPolicy.ASK_EVERY_TIME -> selections.transientChoices[key]
    }

    fun resolve(
        settingsSet: RecordingSettingsSet,
        rememberedOverrides: Map<ActiveSetupOptionKey, String>,
        transientChoices: Map<ActiveSetupOptionKey, String>,
        compatibility: Map<ActiveSetupOptionKey, Boolean>,
    ): ActiveSetup {
        fun Map<ActiveSetupOptionKey, String>.choices() = filterValues(String::isNotBlank)
            .mapValues { SelectionChoice.profile(it.value) }
        return resolve(settingsSet, ActiveSetupSelections(
            settingsSetId = settingsSet.id,
            rememberedChoices = rememberedOverrides.choices(),
            activeChoices = rememberedOverrides.choices(),
            transientChoices = transientChoices.choices(),
        ), compatibility = compatibility)
    }

    fun rememberedAfterStop(
        policies: SettingsSetOptionPolicies,
        rememberedOverrides: Map<ActiveSetupOptionKey, String>,
    ): Map<ActiveSetupOptionKey, String> =
        rememberedOverrides.filterKeys { key ->
            policies.policyFor(key) != SettingsSetOptionPolicy.ASK_EVERY_TIME
        }

    internal fun RecordingSettingsSet.defaultOptionValues(): Map<ActiveSetupOptionKey, String?> =
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
