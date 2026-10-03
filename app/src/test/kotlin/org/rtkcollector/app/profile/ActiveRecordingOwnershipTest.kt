package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rtkcollector.core.correction.NtripTransportMode
import org.rtkcollector.core.solution.SolutionSourcePolicy

class ActiveRecordingOwnershipTest {
    @Test
    fun `shared readiness rejects undocumented baud transitions but permits matching baud`() {
        listOf("generic-nmea-rtcm", "custom-receiver", "um980-custom").forEach { family ->
            val plain = set.copy(workflowId = "plain-rover", receiverProfileId = family)
            val owned = graph().copy(commandProfiles = listOf(command.copy(receiverFamily = family, runtimeScript = "")),
                usbBaudProfiles = listOf(baud.copy(profileBaud = 230400, serialBaud = 460800)))
            val invalid = ActiveSetupResolver.resolve(plain, ActiveSetupSelections(plain.id), profileGraph = owned)
            assertFalse(invalid.canStart)
            assertTrue(invalid.messages.any { it.message.contains("baud") })
            assertTrue(ActiveSetupResolver.resolve(plain, ActiveSetupSelections(plain.id), profileGraph =
                owned.copy(usbBaudProfiles = listOf(baud.copy(profileBaud = 230400, serialBaud = 230400)))).canStart)
        }
    }
    private val command = CommandProfile("commands", "Commands", runtimeScript = "MODE ROVER")
    private val baud = UsbBaudProfile("baud", "Baud")
    private val output = RecordingPolicyProfile("output", "Output")
    private val storage = StorageProfile("storage", "Storage")
    private val caster = NtripCasterProfile("caster", "Account", host = "caster.example", username = "account", secretId = "committed-binding",
        transportMode = NtripTransportMode.PLAINTEXT)
    private val source = NtripMountpointProfile("source", "Source", casterProfileId = "caster", mountpoint = "MOUNT")
    private val set = RecordingSettingsSet.builtInRoverNtrip().copy(
        commandProfileRef = ProfileReference(command.id, command.name),
        usbBaudProfileRef = ProfileReference(baud.id, baud.name),
        ntripCasterProfileRef = null,
        ntripMountpointProfileRef = ProfileReference(source.id, source.name),
        recordingOutputProfileRef = ProfileReference(output.id, output.name),
        storageProfileRef = ProfileReference(storage.id, storage.name),
    )

    @Test
    fun `correction factory uses explicit binding despite canonical password collision`() {
        val lookups = mutableListOf<String>()
        val correction = ActiveNtripConfig.fromProfiles(caster, source) { id ->
            lookups += id
            when (id) { "committed-binding" -> "new"; ntripCasterSecretId(caster.id) -> "old"; else -> null }
        }
        assertEquals(listOf("committed-binding"), lookups)
        assertEquals("new", correction.password)
        assertEquals("committed-binding", correction.secretRef)
        assertEquals(caster.transportMode, correction.transportMode)
        assertEquals(caster.username, correction.username)
        val missing = ActiveNtripConfig.fromProfiles(caster, source) { id -> if (id == ntripCasterSecretId(caster.id)) "old" else null }
        assertNull(missing.password)
    }

    @Test
    fun `correction factory rejects source caster mismatch before any secret lookup`() {
        assertThrows(IllegalArgumentException::class.java) {
            ActiveNtripConfig.fromProfiles(caster.copy(id = "same-host-other-account"), source) { error("must not lookup") }
        }
    }

    @Test
    fun `correction protocol policy is retained and invalid policy rejects before lookup`() {
        listOf("NTRIP_V1_ONLY", "NTRIP_V2_ONLY", "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY").forEach { policy ->
            val owned = caster.copy(protocolPolicy = policy)
            val correction = ActiveNtripConfig.fromProfiles(owned, source) { null }
            assertEquals(policy, correction.protocolPolicy)
            val config = resolve(ntripCasterProfile = owned)
            assertEquals(policy, config.ntrip.protocolPolicy)
            config.validateForStart()
        }
        val invalid = caster.copy(protocolPolicy = "UNSUPPORTED_PRIVATE_VALUE")
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ActiveNtripConfig.fromProfiles(invalid, source) { error("must not lookup") }
        }
        assertFalse(failure.message.orEmpty().contains("UNSUPPORTED_PRIVATE_VALUE"))
        assertFalse(ActiveSetupResolver.resolve(set, ActiveSetupSelections(set.id), profileGraph = graph().copy(ntripCasterProfiles = listOf(invalid))).canStart)
        assertThrows(IllegalArgumentException::class.java) {
            val valid = resolve()
            valid.copy(ntrip = valid.ntrip.copy(protocolPolicy = "UNKNOWN")).validateForStart()
        }
    }

    @Test
    fun `source metadata round trips into correction snapshot without GGA rewriting`() {
        val owned = source.copy(stationId = "station-42", baseLatDeg = 49.0, baseLonDeg = 15.0, ggaUploadPolicy = "LEGACY_UNSUPPORTED")
        val restored = NtripMountpointProfile.fromJson(owned.toJson())
        val correction = ActiveNtripConfig.fromProfiles(caster, restored) { null }
        assertEquals("station-42", correction.stationId)
        assertEquals(49.0, correction.baseLatDeg)
        assertEquals(15.0, correction.baseLonDeg)
        assertEquals("LEGACY_UNSUPPORTED", correction.ggaUploadPolicy)
    }

    @Test
    fun `source identity edits reset coordinate lineage only when identity changes`() {
        val owned = source.copy(stationId = "42", baseLatDeg = 49.0, baseLonDeg = 15.0)
        assertEquals(owned, owned.withSourceIdentity(owned.casterProfileId, owned.mountpoint))
        listOf(owned.withSourceIdentity("other", owned.mountpoint), owned.withSourceIdentity(owned.casterProfileId, "OTHER")).forEach {
            assertNull(it.stationId)
            assertNull(it.baseLatDeg)
            assertNull(it.baseLonDeg)
        }
        listOf(owned.copy(baseLatDeg = Double.NaN), owned.copy(baseLatDeg = 91.0), owned.copy(baseLonDeg = -181.0)).forEach {
            assertThrows(IllegalArgumentException::class.java, it::validate)
        }
    }

    @Test
    fun `receiver compatibility never accepts a generic prefix as another family`() {
        assertFalse(ProfileCompatibility.commandProfile("ublox-m8t", command.copy(receiverFamily = "ublox-m8p")).activatable)
        assertFalse(ProfileCompatibility.commandProfile("ublox-m8t", command.copy(receiverFamily = "ublox")).activatable)
        assertTrue(ProfileCompatibility.commandProfile("um980-n4", command.copy(receiverFamily = "um980")).activatable)
    }

    @Test
    fun `runtime rejects legacy fields and reference overlays without lookup`() {
        listOf(
            SettingsSetOverrides(command = CommandProfileOverride(initScript = "different")),
            SettingsSetOverrides(ntripCaster = NtripCasterOverride(secretId = "other-secret")),
            SettingsSetOverrides(recordingOutput = RecordingOutputOverride(enableMockLocation = true)),
            SettingsSetOverrides(ntripMountpointProfileRef = ProfileReference("other", "Other")),
        ).forEach { legacy ->
            val failure = assertThrows(IllegalArgumentException::class.java) {
                resolve(set.copy(overrides = legacy), passwordLookup = { error("must not lookup") })
            }
            assertTrue(failure.message.orEmpty().contains("migrate"))
        }
    }

    @Test
    fun `locked overlays remain rejected after legacy helper and shared preflight`() {
        val legacy = set.copy(optionPolicies = set.optionPolicies.withPolicy(ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.LOCKED),
            overrides = SettingsSetOverrides(command = CommandProfileOverride(initScript = "different")))
        val adapted = legacy.effectiveForActiveSetup()
        val setup = ActiveSetupResolver.resolve(adapted, ActiveSetupSelections(adapted.id), profileGraph = graph())
        assertFalse(setup.canStart)
        assertTrue(setup.messages.any { it.message.contains("migrate") })
        val failure = assertThrows(IllegalArgumentException::class.java) { resolve(adapted) }
        assertTrue(failure.message.orEmpty().contains("migrate"))
    }

    @Test
    fun `runtime rejects wrong command identity and receiver family before credentials`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolve(commandProfile = command.copy(id = "same-name-other-id"), passwordLookup = { error("must not lookup") })
        }
        val failure = assertThrows(IllegalArgumentException::class.java) {
            resolve(commandProfile = command.copy(receiverFamily = "ublox-m8t"), passwordLookup = { error("must not lookup") })
        }
        assertTrue(failure.message.orEmpty().contains("not um980-n4"))
    }

    @Test
    fun `inactive optional profiles cannot block raw rover recording`() {
        val plain = set.copy(workflowId = "plain-rover", rtklibProfileRef = ProfileReference("missing", "Missing"),
            ntripCasterUploadProfileRef = ProfileReference("missing-upload", "Missing upload"))
        val config = resolve(plain, ntripCasterProfile = caster.copy(id = "", port = -1),
            ntripMountpointProfile = source.copy(id = ""), rtklibProfile = RtklibProfile("", "Invalid"),
            uploadProfile = NtripCasterUploadProfile("", "Invalid", port = -1), passwordLookup = { error("must not lookup") })
        config.validateForStart()
        assertFalse(config.ntrip.enabled)
        assertFalse(config.rtklib.enabled)
        assertFalse(config.casterUpload.enabled)
    }

    @Test
    fun `screen rtklib only is rejected while disabled mock rtklib policy stays dormant`() {
        val policy = SolutionPolicyProfile("solution", "Solution", screenPolicy = SolutionSourcePolicy.AUTO_BEST,
            mockPolicy = SolutionSourcePolicy.RTKLIB_ONLY)
        val selected = set.copy(workflowId = "plain-rover", solutionPolicyProfileRef = ProfileReference(policy.id, policy.name))
        val config = resolve(selected, solution = policy)
        config.validateForStart()
        assertEquals(SolutionSourcePolicy.AUTO_BEST, config.solutionPolicy.screenPolicy)
        assertEquals(SolutionSourcePolicy.RTKLIB_ONLY, config.solutionPolicy.mockPolicy)
        assertThrows(IllegalArgumentException::class.java) { resolve(selected, solution = policy.copy(screenPolicy = SolutionSourcePolicy.RTKLIB_ONLY)) }
        assertThrows(IllegalArgumentException::class.java) { resolve(selected, solution = policy, recordingOutput = output.copy(enableMockLocation = true)) }
    }

    @Test
    fun `enabled upload with missing profile is rejected rather than changed to off`() {
        val base = set.copy(workflowId = "base-calibration", baseCasterUploadEnabled = true,
            ntripCasterUploadProfileRef = ProfileReference("absent-upload", "Absent upload"))
        assertThrows(IllegalArgumentException::class.java) { resolve(base) }
    }

    @Test
    fun `temporary coordinate determination does not ask for accepted coordinate`() {
        val temporary = set.copy(workflowId = "base-calibration", optionPolicies = set.optionPolicies
            .withPolicy(ActiveSetupOptionKey.BASE_COORDINATE, SettingsSetOptionPolicy.ASK_EVERY_TIME))
        val setup = ActiveSetupResolver.resolve(temporary, ActiveSetupSelections(temporary.id))
        assertFalse(setup.option(ActiveSetupOptionKey.BASE_COORDINATE).applicable)
        resolve(temporary).validateForStart()
    }

    @Test
    fun `shared graph rejects invalid rtklib input route before credentials`() {
        val rtklib = RtklibProfile("rtk", "RTK", enabled = true)
        val selected = set.copy(workflowId = "rover-rtklib", rtklibProfileRef = ProfileReference(rtklib.id, rtklib.name))
        val graph = graph().copy(rtklibProfiles = listOf(rtklib))
        val setup = ActiveSetupResolver.resolve(selected, ActiveSetupSelections(selected.id), profileGraph = graph)
        assertFalse(setup.canStart)
        assertTrue(setup.messages.any { it.key == ActiveSetupOptionKey.RTKLIB && it.message.contains("OBSVMB") })
        assertThrows(IllegalArgumentException::class.java) {
            ActiveRecordingConfig.resolve(selected, ActiveSetupSelections(selected.id), graph, "RTK", { error("must not lookup") })
        }
    }

    @Test
    fun `duplicate source IDs never resolve by list order or same name`() {
        val graph = graph().copy(ntripMountpointProfiles = listOf(source, source.copy(mountpoint = "OTHER")))
        val setup = ActiveSetupResolver.resolve(set, ActiveSetupSelections(set.id), profileGraph = graph)
        assertFalse(setup.canStart)
        assertTrue(setup.messages.any { it.message.contains("ambiguous") })
    }

    @Test
    fun `selected base coordinate mismatch is rejected without rewriting commands`() {
        val base = modelTestBaseCoordinate()
        val fixed = set.copy(workflowId = "fixed-base", basePositionProfileRef = ProfileReference(base.id, base.name))
        val fixedCommand = command.copy(runtimeScript = base.toUm980FixedBaseModeCommand())
        val graph = graph().copy(commandProfiles = listOf(fixedCommand), baseCoordinates = listOf(base))
        val config = ActiveRecordingConfig.resolve(fixed, ActiveSetupSelections(fixed.id), graph, "Fixed", { null })
        assertEquals(listOf(base.toUm980FixedBaseModeCommand()), config.modeCommands)
        val mismatched = graph.copy(baseCoordinates = listOf(base.copy(latDeg = base.latDeg + 0.1)))
        val setup = ActiveSetupResolver.resolve(fixed, ActiveSetupSelections(fixed.id), profileGraph = mismatched)
        assertFalse(setup.canStart)
        assertTrue(setup.messages.any { it.key == ActiveSetupOptionKey.BASE_COORDINATE })
    }

    @Test
    fun `upload binding does not fall back to colliding canonical credentials`() {
        val base = modelTestBaseCoordinate()
        val upload = NtripCasterUploadProfile("upload", "Upload", host = "upload.example", mountpoint = "BASE", secretId = "upload-owner")
        val fixed = set.copy(workflowId = "fixed-base", basePositionProfileRef = ProfileReference(base.id, base.name),
            baseCasterUploadEnabled = true, ntripCasterUploadProfileRef = ProfileReference(upload.id, upload.name))
        val graph = graph().copy(commandProfiles = listOf(command.copy(runtimeScript = base.toUm980FixedBaseModeCommand() + "\nRTCM1006 COM1 10\nRTCM1074 COM1 1")),
            baseCoordinates = listOf(base), ntripCasterUploadProfiles = listOf(upload))
        val lookups = mutableListOf<String>()
        val config = ActiveRecordingConfig.resolve(fixed, ActiveSetupSelections(fixed.id), graph, "Fixed", { id ->
            lookups += id
            if (id == ntripCasterUploadSecretId(upload.id)) "old" else null
        })
        assertEquals(listOf("upload-owner"), lookups)
        assertNull(config.casterUpload.password)
        assertEquals("upload-owner", config.casterUpload.secretRef)
    }

    @Test
    fun `shared upload preflight rejects monitoring only commands and HTTP mountpoint`() {
        val base = modelTestBaseCoordinate()
        val upload = NtripCasterUploadProfile("upload", "Upload", host = "upload.example", mountpoint = "BASE")
        val fixed = set.copy(workflowId = "fixed-base", basePositionProfileRef = ProfileReference(base.id, base.name),
            baseCasterUploadEnabled = true, ntripCasterUploadProfileRef = ProfileReference(upload.id, upload.name))
        val graph = graph().copy(commandProfiles = listOf(command.copy(runtimeScript = base.toUm980FixedBaseModeCommand() + "\nBESTNAVB COM1 1")),
            baseCoordinates = listOf(base), ntripCasterUploadProfiles = listOf(upload))
        val setup = ActiveSetupResolver.resolve(fixed, ActiveSetupSelections(fixed.id), profileGraph = graph)
        assertFalse(setup.canStart)
        assertTrue(setup.messages.any { it.key == ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD && it.message.contains("base RTCM output") })
        val validCommands = graph.copy(commandProfiles = listOf(command.copy(runtimeScript = base.toUm980FixedBaseModeCommand() + "\nRTCM1006 COM1 10\nRTCM1074 COM1 1")))
        val malformed = validCommands.copy(ntripCasterUploadProfiles = listOf(upload.copy(mountpoint = "BASE HTTP/1.1")))
        assertFalse(ActiveSetupResolver.resolve(fixed, ActiveSetupSelections(fixed.id), profileGraph = malformed).canStart)
    }

    private fun graph() = ActiveSetupProfileGraph(commandProfiles = listOf(command), usbBaudProfiles = listOf(baud),
        ntripCasterProfiles = listOf(caster), ntripMountpointProfiles = listOf(source),
        recordingOutputProfiles = listOf(output), storageProfiles = listOf(storage))

    @Test
    fun `snapshot caster provenance comes from selected source not settings suggestion`() {
        val selected = set.copy(ntripCasterProfileRef = ProfileReference("other-caster", "Same account name"))
        val setup = ActiveSetupResolver.resolve(selected, ActiveSetupSelections(selected.id), profileGraph = graph())
        assertTrue(setup.canStart)
        assertEquals(caster.id, setup.snapshot().profileId(ActiveSetupOptionKey.NTRIP_CASTER))
        assertEquals(caster.id, setup.displayProjection(selected, graph()::referenceFor).references[ActiveSetupOptionKey.NTRIP_CASTER]?.id)
    }

    @Test
    fun `next start observes same ID owner and dependency edits without changing defaults`() {
        val initial = ActiveRecordingConfig.resolve(set, ActiveSetupSelections(set.id), graph(), "Rover", { null })
        val edited = graph().copy(ntripCasterProfiles = listOf(caster.copy(host = "new.example")),
            recordingOutputProfiles = listOf(output.copy(enableMockLocation = true)))
        val next = ActiveRecordingConfig.resolve(set, ActiveSetupSelections(set.id), edited, "Rover", { null })
        assertEquals(initial.commandProfileId, next.commandProfileId)
        assertEquals("new.example", next.ntrip.host)
        assertTrue(next.recording.enableMockLocation)
        assertEquals(source.id, set.ntripMountpointProfileRef?.id)
        val invalidDependency = edited.copy(ntripMountpointProfiles = listOf(source.copy(casterProfileId = "missing")))
        assertFalse(ActiveSetupResolver.resolve(set, ActiveSetupSelections(set.id), profileGraph = invalidDependency).canStart)
        val changedPolicy = set.copy(optionPolicies = set.optionPolicies.withPolicy(ActiveSetupOptionKey.NTRIP_MOUNTPOINT,
            SettingsSetOptionPolicy.ASK_EVERY_TIME))
        assertFalse(ActiveSetupResolver.resolve(changedPolicy, ActiveSetupSelections(set.id), profileGraph = edited).canStart)
    }

    private fun resolve(
        settings: RecordingSettingsSet = set,
        commandProfile: CommandProfile = command,
        ntripCasterProfile: NtripCasterProfile? = caster,
        ntripMountpointProfile: NtripMountpointProfile? = source,
        rtklibProfile: RtklibProfile? = null,
        uploadProfile: NtripCasterUploadProfile? = null,
        solution: SolutionPolicyProfile? = null,
        recordingOutput: RecordingPolicyProfile = output,
        passwordLookup: (String) -> String? = { null },
    ): ActiveRecordingConfig = ActiveRecordingConfig.resolve(
        settingsSet = settings, commandProfile = commandProfile, usbBaudProfile = baud,
        ntripCasterProfile = ntripCasterProfile, ntripMountpointProfile = ntripMountpointProfile,
        ntripCasterUploadProfile = uploadProfile, rtklibProfile = rtklibProfile, solutionPolicyProfile = solution,
        recordingPolicyProfile = recordingOutput, storageProfile = storage, workflowName = settings.workflowId,
        workflowUsesNtrip = settings.workflowId in setOf("rover-ntrip", "rover-rtklib", "rover-ntrip-rtklib", "base-calibration"),
        passwordLookup = passwordLookup,
    )
}
