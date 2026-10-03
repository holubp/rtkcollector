package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.json.JSONObject
import org.rtkcollector.app.base.AcceptedBaseCoordinate
import org.rtkcollector.app.profile.ActiveRecordingConfig
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingPolicyProfile
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.StorageProfile
import org.rtkcollector.app.profile.UsbBaudProfile
import org.rtkcollector.app.profile.ActiveSetupSelections
import org.rtkcollector.app.profile.StartSelectionLease
import org.rtkcollector.app.profile.ActiveSelectionsMemory
import org.rtkcollector.app.profile.SelectionChoice
import org.rtkcollector.app.profile.SettingsSetOptionPolicy

class RecordingSetupBridgeTest {
    @Test
    fun `accepted published live source and mock ASK answers are session consumed but newer equal answers survive`() {
        listOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, ActiveSetupOptionKey.RECORDING_OUTPUT).forEach { key ->
            listOf(false, true).forEach { prepareNewer ->
                val bridge = RecordingSetupAuthority()
                val cache = ActiveSelectionsMemory()
                val owner = Any()
                val set = liveAskSet()
                val initial = liveAskAnswers(set)
                bridge.acceptStart(bridge.stageStart(snapshot(), selectionLease =
                    cache.captureStart(owner, initial, 0, ActiveSetupOptionKey.entries.toSet())))
                val other = set.copy(id = "set-b")
                val otherAnswers = liveAskAnswers(other)
                cache.remember(owner, otherAnswers)
                val candidate = initial.choose(set, key, SelectionChoice.profile("Y"))
                val receipt = acceptLiveAnswer(bridge, key)
                val consumed = cache.publishChoice(owner, candidate, 1, key)
                bridge.consumePublishedSelections(receipt, consumed)
                if (prepareNewer) cache.remember(owner,
                    consumed.selections.choose(set, key, SelectionChoice.profile("Y")))
                bridge.stop("session")
                bridge.stop("session")
                val current = cache.cached(owner, ActiveSetupSelections.fromJson(consumed.selections.toJson()))!!
                assertEquals(if (prepareNewer) "Y" else null, current.transientChoices[key]?.profileId)
                assertEquals("commands", current.rememberedChoices[ActiveSetupOptionKey.RECEIVER_COMMAND]?.profileId)
                assertEquals(otherAnswers, cache.cached(owner, ActiveSetupSelections.fromJson(otherAnswers.toJson())))
            }
        }
    }

    @Test
    fun `publication after terminal cannot resurrect other answers and delayed ownership preserves newer equal answer`() {
        listOf(false, true).forEach { prepareNewer ->
            val bridge = RecordingSetupAuthority()
            val cache = ActiveSelectionsMemory()
            val owner = Any()
            val set = liveAskSet()
            val initial = liveAskAnswers(set)
            bridge.acceptStart(bridge.stageStart(snapshot(), selectionLease =
                cache.captureStart(owner, initial, 0, ActiveSetupOptionKey.entries.toSet())))
            val receipt = acceptLiveAnswer(bridge, ActiveSetupOptionKey.NTRIP_MOUNTPOINT)
            bridge.stop("session")
            val candidate = initial.choose(set, ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SelectionChoice.profile("Y"))
            val consumed = cache.publishChoice(owner, candidate, 1, ActiveSetupOptionKey.NTRIP_MOUNTPOINT)
            assertFalse(ActiveSetupOptionKey.RECORDING_OUTPUT in consumed.selections.transientChoices)
            if (prepareNewer) cache.remember(owner, consumed.selections.choose(set,
                ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SelectionChoice.profile("Y")))
            bridge.consumePublishedSelections(receipt, consumed)
            val current = cache.cached(owner, ActiveSetupSelections.fromJson(consumed.selections.toJson()))!!
            assertEquals(if (prepareNewer) "Y" else null,
                current.transientChoices[ActiveSetupOptionKey.NTRIP_MOUNTPOINT]?.profileId)
        }
    }

    @Test
    fun `rejected patch cannot consume prepared next start ASK answer`() {
        val bridge = RecordingSetupAuthority()
        val cache = ActiveSelectionsMemory()
        val owner = Any()
        val set = liveAskSet()
        val initial = liveAskAnswers(set)
        bridge.acceptStart(bridge.stageStart(snapshot(), selectionLease =
            cache.captureStart(owner, initial, 0, ActiveSetupOptionKey.entries.toSet())))
        val rejected = bridge.acceptSource(bridge.stageSourceUpdate(SetupPatchRequest("rejected", "session", 99, 0),
            "set", source(), caster(), null))!!
        assertFalse(rejected.accepted)
        val prepared = initial.choose(set, ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SelectionChoice.profile("Y"))
        val lease = cache.captureStart(owner, prepared, 1, setOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT))
        bridge.consumePublishedSelections(rejected, lease)
        bridge.stop("session")
        assertEquals("Y", cache.cached(owner, ActiveSetupSelections.fromJson(prepared.toJson()))!!
            .transientChoices[ActiveSetupOptionKey.NTRIP_MOUNTPOINT]?.profileId)
    }

    @Test
    fun `published live Choose once answers remain remembered after session Stop`() {
        listOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, ActiveSetupOptionKey.RECORDING_OUTPUT).forEach { key ->
            val bridge = RecordingSetupAuthority()
            val cache = ActiveSelectionsMemory()
            val owner = Any()
            val defaults = liveAskSet()
            val set = defaults.copy(optionPolicies = defaults.optionPolicies.withPolicy(key,
                SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER))
            val initial = liveAskAnswers(set)
            bridge.acceptStart(bridge.stageStart(snapshot(), selectionLease =
                cache.captureStart(owner, initial, 0, ActiveSetupOptionKey.entries.toSet())))
            val receipt = acceptLiveAnswer(bridge, key)
            val candidate = initial.choose(set, key, SelectionChoice.profile("Y"))
            val consumed = cache.publishChoice(owner, candidate, 1, key)
            bridge.consumePublishedSelections(receipt, consumed)
            bridge.stop("session")
            assertEquals("Y", cache.cached(owner, ActiveSetupSelections.fromJson(consumed.selections.toJson()))!!
                .rememberedChoices[key]?.profileId)
        }
    }

    private fun liveAskSet() = RecordingSettingsSet.builtInRoverNtrip().copy(id = "set", optionPolicies =
        RecordingSettingsSet.builtInRoverNtrip().optionPolicies
            .withPolicy(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SettingsSetOptionPolicy.ASK_EVERY_TIME)
            .withPolicy(ActiveSetupOptionKey.RECORDING_OUTPUT, SettingsSetOptionPolicy.ASK_EVERY_TIME)
            .withPolicy(ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER))

    private fun liveAskAnswers(set: RecordingSettingsSet) = ActiveSetupSelections(set.id)
        .choose(set, ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SelectionChoice.profile("old-source"))
        .choose(set, ActiveSetupOptionKey.RECORDING_OUTPUT, SelectionChoice.profile("out"))
        .choose(set, ActiveSetupOptionKey.RECEIVER_COMMAND, SelectionChoice.profile("commands"))

    private fun acceptLiveAnswer(bridge: RecordingSetupAuthority, key: ActiveSetupOptionKey): SetupBridgeReceipt {
        val request = SetupPatchRequest("live", "session", 0, 0)
        val receipt = if (key == ActiveSetupOptionKey.NTRIP_MOUNTPOINT)
            bridge.acceptSource(bridge.stageSourceUpdate(request, "set", source().copy(id = "Y"), caster(), null))!!
        else bridge.acceptMock(bridge.stageMockUpdate(request, "set", output().copy(id = "Y", enableMockLocation = true)))!!
        assertTrue(receipt.accepted)
        return receipt
    }
    @Test
    fun `service stop completes consumed selection lease once outside bridge monitor`() {
        val bridge = RecordingSetupAuthority()
        var finished = 0
        val lease = StartSelectionLease(ActiveSetupSelections(snapshot().settingsSetId), 1L) {
            assertFalse(Thread.holdsLock(bridge))
            finished++
        }
        val token = bridge.stageStart(snapshot(), selectionLease = lease)
        bridge.acceptStart(token)
        bridge.stop("unrelated-session")
        assertEquals(0, finished)
        bridge.stop("session")
        bridge.stop("session")
        assertEquals(1, finished)
    }

    @Test
    fun `failed validation and dispatch complete only their own staged leases`() {
        val bridge = RecordingSetupAuthority()
        var failedValidation = 0
        val invalidLease = StartSelectionLease(ActiveSetupSelections(snapshot().settingsSetId), 1L) { failedValidation++ }
        assertThrows(IllegalArgumentException::class.java) {
            bridge.stageStart(snapshot().copy(profileIds = emptyMap()), selectionLease = invalidLease)
        }
        assertEquals(1, failedValidation)
        var failedDispatch = 0
        val lease = StartSelectionLease(ActiveSetupSelections(snapshot().settingsSetId), 2L) { failedDispatch++ }
        val token = bridge.stageStart(snapshot(), selectionLease = lease)
        assertThrows(IllegalStateException::class.java) {
            bridge.dispatchStart(token) { error("Foreground dispatch rejected") }
        }
        assertEquals(1, failedDispatch)
        assertNull(bridge.acceptStart(token))
    }

    @Test
    fun `start is one use and exposes only redacted state`() {
        val bridge = RecordingSetupAuthority()
        val token = bridge.stageStart(snapshot())
        assertNull(bridge.current())
        assertNull(bridge.acceptStart("missing"))
        assertNotNull(bridge.acceptStart(token))
        assertNull(bridge.acceptStart(token))
        assertEquals("session", bridge.current()?.sessionId)
        assertEquals(output(), bridge.current()?.recordingOutputProfile)
        assertEquals(emptySet<ActiveSetupOptionKey>(), bridge.current()?.lockedOptions)
        assertEquals(null, bridge.current()?.casterRestrictionId)
        assertFalse(bridge.current()?.mockEnabled ?: true)
        assertEquals(output().mockLocationRateHz, bridge.current()?.mockRateHz)
        assertEquals(output(), bridge.currentOutputProfile())
        assertFalse(bridge.current().toString().contains("password"))
        assertThrows(IllegalStateException::class.java) { bridge.stageStart(snapshot()) }
    }

    @Test
    fun `start rejects output owner mismatch before staging`() {
        val bridge = RecordingSetupAuthority()
        val invalid = snapshot().let { it.copy(recordingOutputProfile = it.recordingOutputProfile.copy(exportGpx = true)) }
        assertThrows(IllegalArgumentException::class.java) { bridge.stageStart(invalid) }
        assertNull(bridge.current())
    }

    @Test
    fun `start rejects profile provenance mismatch`() {
        val bridge = RecordingSetupAuthority()
        val invalid = snapshot().let { it.copy(profileIds = it.profileIds +
            (ActiveSetupOptionKey.NTRIP_MOUNTPOINT to "unrelated-source")) }
        assertThrows(IllegalArgumentException::class.java) { bridge.stageStart(invalid) }
        assertThrows(IllegalArgumentException::class.java) {
            bridge.stageStart(snapshot().copy(profileIds = snapshot().profileIds +
                (ActiveSetupOptionKey.SOLUTION_POLICY to "unrelated-solution")))
        }
    }

    @Test
    fun `fixed base start owns its selected coordinate and clears it on stop`() {
        val bridge = RecordingSetupAuthority()
        val coordinate = AcceptedBaseCoordinate("base", "Base", 49.0, 15.0, 100.0, 60.0, 40.0,
            "ETRS89", null, "PPP", null, null, null, null, null, null, "Accepted position")
        val rover = snapshot()
        val fixed = rover.copy(config = rover.config.copy(
            workflowId = "fixed-base", ntrip = rover.config.ntrip.copy(enabled = false),
            recording = rover.config.recording.copy(recordNtripCorrectionInput = false, recordRemoteBaseRaw = false),
            modeCommands = listOf("MODE BASE 49.0000000000 15.0000000000 60.0000"),
        ), profileIds = rover.profileIds + (ActiveSetupOptionKey.BASE_COORDINATE to "base"))
        assertThrows(IllegalArgumentException::class.java) { bridge.stageStart(fixed) }
        assertThrows(IllegalArgumentException::class.java) {
            bridge.stageStart(fixed, coordinate.copy(id = "other"))
        }
        bridge.acceptStart(bridge.stageStart(fixed, coordinate))
        assertEquals(coordinate, bridge.serviceBasePosition("session"))
        bridge.stop("session")
        assertNull(bridge.serviceBasePosition("session"))
    }

    @Test
    fun `strict correction v2 cannot silently use core v1 fallback`() {
        val bridge = RecordingSetupAuthority()
        val strict = snapshot().let { it.copy(config = it.config.copy(
            ntrip = it.config.ntrip.copy(protocolPolicy = "NTRIP_V2_ONLY"),
        )) }
        assertThrows(IllegalArgumentException::class.java) { bridge.stageStart(strict) }
        bridge.acceptStart(bridge.stageStart(snapshot()))
        assertThrows(IllegalArgumentException::class.java) {
            bridge.stageSourceUpdate(SetupPatchRequest("strict", "session", 0, 1), "set", source(),
                caster().copy(protocolPolicy = "NTRIP_V2_ONLY"), null)
        }
    }

    @Test
    fun `accepted patch can be queried and retried without changing revision`() {
        val bridge = RecordingSetupAuthority()
        bridge.acceptStart(bridge.stageStart(snapshot()))
        val request = SetupPatchRequest("request", "session", 0, 7)
        val first = bridge.acceptSource(bridge.stageSourceUpdate(request, "set", source(), caster(), null))
        assertTrue(first?.accepted == true)
        assertEquals(1L, bridge.current()?.revision)
        assertEquals(first, bridge.result("request"))
        val replay = bridge.acceptSource(bridge.stageSourceUpdate(request, "set", source(), caster(), null))
        assertEquals(first, replay)
        assertEquals(1L, bridge.current()?.revision)
        assertEquals("next-source", first?.profileIds?.get(ActiveSetupOptionKey.NTRIP_MOUNTPOINT))
        assertEquals("set", first?.settingsSetId)
        assertTrue(shouldApplySetupReceipt(0, first))
        assertFalse(shouldApplySetupReceipt(1, replay))
    }

    @Test
    fun `current constraints remain owned by originating recording setup`() {
        val bridge = RecordingSetupAuthority()
        val locked = setOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, ActiveSetupOptionKey.RECORDING_OUTPUT)
        bridge.acceptStart(bridge.stageStart(snapshot().copy(lockedOptions = locked, casterRestrictionId = "caster")))
        val current = requireNotNull(bridge.current())
        assertEquals("set", current.settingsSetId)
        assertEquals(locked, current.lockedOptions)
        assertEquals("caster", current.casterRestrictionId)
        val rejected = bridge.acceptMock(bridge.stageMockUpdate(
            SetupPatchRequest("locked", "session", 0, 1), "set", output().copy(enableMockLocation = true),
        ))
        assertFalse(rejected?.accepted ?: true)
        assertEquals(current, bridge.current())
    }

    @Test
    fun `session provenance retains start and latest accepted identities without credentials`() {
        val bridge = RecordingSetupAuthority()
        bridge.acceptStart(bridge.stageStart(snapshot()))
        val started = requireNotNull(bridge.current())
        bridge.acceptSource(bridge.stageSourceUpdate(SetupPatchRequest("source", "session", 0, 2), "set",
            source(), caster().copy(secretId = "owner-binding"), "private-password"))
        val encoded = withRecordingSetupProvenance("{\"sessionUuid\":\"session\",\"stoppedAt\":\"now\"}",
            started, requireNotNull(bridge.current()))
        val parsed = JSONObject(encoded)
        assertEquals("now", parsed.getString("stoppedAt"))
        val ownership = parsed.getJSONObject("settingsOwnership")
        assertEquals(0L, ownership.getJSONObject("started").getLong("revision"))
        assertEquals(1L, ownership.getJSONObject("latestAccepted").getLong("revision"))
        assertEquals("set", ownership.getJSONObject("latestAccepted").getString("settingsSetId"))
        assertFalse(encoded.contains("private-password"))
        assertFalse(encoded.contains("owner-binding"))
    }

    @Test
    fun `session provenance rejects a metadata session mismatch`() {
        val started = SetupBridgeState("session", 0, "set", emptyMap())
        assertThrows(IllegalArgumentException::class.java) {
            withRecordingSetupProvenance("{\"sessionUuid\":\"another-session\"}", started, started)
        }
    }

    @Test
    fun `failed source preflight leaves snapshot and selection unchanged`() {
        val bridge = RecordingSetupAuthority()
        bridge.acceptStart(bridge.stageStart(snapshot()))
        val request = SetupPatchRequest("preflight", "session", 0, 4)
        val receipt = bridge.acceptSource(bridge.stageSourceUpdate(request, "set", source(), caster(), null)) {
            throw IllegalArgumentException("Selected endpoint cannot be used by this app build.")
        }
        assertFalse(receipt?.accepted ?: true)
        assertEquals(0L, bridge.current()?.revision)
        assertEquals("old-source", bridge.current()?.profileIds?.get(ActiveSetupOptionKey.NTRIP_MOUNTPOINT))
        assertEquals(receipt, bridge.result("preflight"))
    }

    @Test
    fun `live patches reject a different originating settings set`() {
        val bridge = RecordingSetupAuthority()
        bridge.acceptStart(bridge.stageStart(snapshot()))
        assertThrows(IllegalArgumentException::class.java) {
            bridge.stageSourceUpdate(SetupPatchRequest("foreign-source", "session", 0, 1),
                "another-set", source(), caster(), null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            bridge.stageMockUpdate(SetupPatchRequest("foreign-mock", "session", 0, 1),
                "another-set", output())
        }
        assertEquals(0L, bridge.current()?.revision)
    }

    @Test
    fun `stale patch and delayed token cannot alter a restarted session`() {
        val bridge = RecordingSetupAuthority()
        bridge.acceptStart(bridge.stageStart(snapshot()))
        val old = bridge.stageSourceUpdate(SetupPatchRequest("old", "session", 0, 1), "set", source(), caster(), null)
        bridge.stop("session")
        assertNull(bridge.acceptSource(old))
        assertNull(bridge.result("old"))
        bridge.acceptStart(bridge.stageStart(snapshot().copy(sessionId = "new-session")))
        val stale = bridge.acceptSource(bridge.stageSourceUpdate(
            SetupPatchRequest("late", "session", 0, 1), "set", source(), caster(), null,
        ))
        assertFalse(stale?.accepted ?: true)
        assertEquals(0L, bridge.current()?.revision)
    }

    @Test
    fun `idle stop invalidates a staged start`() {
        val bridge = RecordingSetupAuthority()
        val token = bridge.stageStart(snapshot())
        bridge.cancelPending()
        assertNull(bridge.acceptStart(token))
        assertNull(bridge.current())
    }

    @Test
    fun `a stopped session identity cannot be reused in process`() {
        val bridge = RecordingSetupAuthority()
        bridge.acceptStart(bridge.stageStart(snapshot()))
        bridge.stop("session")
        assertThrows(IllegalArgumentException::class.java) { bridge.stageStart(snapshot()) }
    }

    @Test
    fun `staged start freezes mutable command inputs and ignores later profile copies`() {
        val bridge = RecordingSetupAuthority()
        val original = snapshot()
        val commands = original.config.initCommands.toMutableList()
        val staged = original.copy(config = original.config.copy(initCommands = commands))
        val token = bridge.stageStart(staged)
        commands += "LOG UNRELATED"
        val running = requireNotNull(bridge.acceptStart(token))
        assertFalse("LOG UNRELATED" in running.config.initCommands)
        val editedLibraryOutput = original.recordingOutputProfile.copy(exportGpx = true)
        assertFalse(bridge.current()?.recordingOutputProfile == editedLibraryOutput)
    }

    @Test
    fun `failed patch leaves running snapshot unchanged and allows a new request`() {
        val bridge = RecordingSetupAuthority()
        bridge.acceptStart(bridge.stageStart(snapshot()))
        val rejected = bridge.acceptSource(bridge.stageSourceUpdate(
            SetupPatchRequest("bad", "session", 0, 1), "set", source().copy(casterProfileId = "wrong"), caster(), null,
        ))
        assertFalse(rejected?.accepted ?: true)
        assertEquals(0L, bridge.current()?.revision)
        val accepted = bridge.acceptSource(bridge.stageSourceUpdate(
            SetupPatchRequest("retry", "session", 0, 1), "set", source(), caster(), null,
        ))
        assertTrue(accepted?.accepted == true)
    }

    @Test
    fun `mock candidate cannot carry unrelated output edits`() {
        val bridge = RecordingSetupAuthority()
        bridge.acceptStart(bridge.stageStart(snapshot()))
        val request = SetupPatchRequest("mock", "session", 0, 1)
        val invalid = output().copy(exportGpx = true, enableMockLocation = true)
        assertFalse(bridge.acceptMock(bridge.stageMockUpdate(request, "set", invalid))?.accepted ?: true)
        assertFalse(bridge.current()?.profileIds?.get(ActiveSetupOptionKey.RECORDING_OUTPUT) == "derived")
    }

    @Test
    fun `mock acknowledgement exposes only running owner and selected values`() {
        val bridge = RecordingSetupAuthority()
        bridge.acceptStart(bridge.stageStart(snapshot()))
        val derived = output().copy(id = "derived", name = "Derived", enableMockLocation = true,
            mockLocationRateHz = 5)
        val receipt = bridge.acceptMock(bridge.stageMockUpdate(
            SetupPatchRequest("mock-on", "session", 0, 3), "set", derived,
        ))
        assertTrue(receipt?.accepted == true)
        assertEquals(3L, receipt?.selectionRevision)
        assertEquals(derived, bridge.current()?.recordingOutputProfile)
        assertTrue(bridge.current()?.mockEnabled == true)
        assertEquals(5, bridge.current()?.mockRateHz)
    }

    companion object {
        internal fun snapshot(): RunningSetupSnapshot {
            val output = output()
            val config = ActiveRecordingConfig.resolve(
                settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                    commandProfileRef = ProfileReference("commands", "Commands"),
                    usbBaudProfileRef = ProfileReference("baud", "Baud"),
                    ntripCasterProfileRef = null,
                    ntripMountpointProfileRef = ProfileReference("old-source", "Old"),
                    recordingOutputProfileRef = ProfileReference("out", "Out"),
                    storageProfileRef = ProfileReference("storage", "Storage"),
                ),
                commandProfile = CommandProfile("commands", "Commands", receiverFamily = "um980"),
                usbBaudProfile = UsbBaudProfile("baud", "Baud"),
                ntripCasterProfile = caster(),
                ntripMountpointProfile = source().copy(id = "old-source", mountpoint = "OLD"),
                recordingPolicyProfile = output,
                storageProfile = StorageProfile("storage", "Storage"),
                workflowName = "Rover NTRIP",
                workflowUsesNtrip = true,
                passwordLookup = { null },
            )
            return RunningSetupSnapshot("session", 0, "set", config, output,
                mapOf(
                    ActiveSetupOptionKey.RECEIVER_COMMAND to "commands",
                    ActiveSetupOptionKey.USB_BAUD to "baud",
                    ActiveSetupOptionKey.NTRIP_CASTER to "caster",
                    ActiveSetupOptionKey.NTRIP_MOUNTPOINT to "old-source",
                    ActiveSetupOptionKey.RECORDING_OUTPUT to "out",
                    ActiveSetupOptionKey.STORAGE to "storage",
                ), emptySet(), null)
        }

        private fun output() = RecordingPolicyProfile("out", "Out")
        private fun caster() = NtripCasterProfile("caster", "Caster", host = "caster.example")
        private fun source() = NtripMountpointProfile("next-source", "Next", "caster", mountpoint = "NEXT")
    }
}
