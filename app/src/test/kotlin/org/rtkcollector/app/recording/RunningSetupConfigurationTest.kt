package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.ActiveRecordingConfig
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.RecordingPolicyProfile
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.StorageProfile
import org.rtkcollector.app.profile.UsbBaudProfile
import org.rtkcollector.core.solution.SolutionSourcePolicy

class RunningSetupConfigurationTest {
    @Test
    fun `stale session cannot change the new recording`() {
        val authority = authority()
        val before = authority.snapshot()

        val result = authority.updateSource(request(sessionId = "old-session"), source(), caster()) { null }

        assertTrue(result is SetupPatchResult.Rejected)
        assertEquals(before, authority.snapshot())
    }

    @Test
    fun `source changes only the running correction snapshot`() {
        val authority = authority()
        val before = requireNotNull(authority.snapshot())

        val result = authority.updateSource(request(), source(), caster()) { null }

        assertTrue(result is SetupPatchResult.Accepted)
        val after = requireNotNull(authority.snapshot())
        assertEquals("NEXT", after.config.ntrip.mountpoint)
        assertEquals(before.config.copy(ntrip = after.config.ntrip), after.config)
        assertEquals(1L, after.revision)
        assertEquals("next-source", after.profileIds[ActiveSetupOptionKey.NTRIP_MOUNTPOINT])
    }

    @Test
    fun `replaying accepted request is idempotent and acknowledgement is not network success`() {
        val authority = authority()
        val first = authority.updateSource(request(), source(), caster()) { null }
        val replay = authority.updateSource(request(), source(), caster()) { error("Replay must not reread credentials") }

        assertTrue(first is SetupPatchResult.Accepted)
        assertEquals(first, replay)
        assertEquals(1L, authority.snapshot()?.revision)
    }

    @Test
    fun `stale revision rejects while retaining accepted configuration`() {
        val authority = authority()
        authority.updateSource(request(), source(), caster()) { null }
        val before = authority.snapshot()

        val result = authority.updateSource(request(id = "second"), source(), caster()) { null }

        assertTrue(result is SetupPatchResult.Rejected)
        assertEquals(before, authority.snapshot())
    }

    @Test
    fun `locked source and conflicting restriction never accept a change`() {
        for (authority in listOf(
            authority(locked = setOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT)),
            authority(restriction = "another-caster"),
        )) {
            val before = authority.snapshot()
            val result = authority.updateSource(request(), source(), caster()) { null }
            assertTrue(result is SetupPatchResult.Rejected)
            assertEquals(before, authority.snapshot())
        }
    }

    @Test
    fun `invalid source is rejected without altering accepted state`() {
        val authority = authority()
        val before = authority.snapshot()

        val result = authority.updateSource(request(), source().copy(casterProfileId = "wrong"), caster()) { null }

        assertTrue(result is SetupPatchResult.Rejected)
        assertEquals("Selected source belongs to another caster profile.", (result as SetupPatchResult.Rejected).reason)
        assertEquals(before, authority.snapshot())
    }

    @Test
    fun `unreadable source secret rejects without exposing exception contents`() {
        val authority = authority()
        val before = authority.snapshot()

        val result = authority.updateSource(request(), source(), caster().copy(secretId = "owner-secret")) {
            throw IllegalStateException("sensitive-value-must-not-appear")
        }

        assertTrue(result is SetupPatchResult.Rejected)
        assertFalse(result.toString().contains("sensitive-value-must-not-appear"))
        assertEquals(before, authority.snapshot())
    }

    @Test
    fun `secret lookup illegal argument is sanitized without changing running snapshot`() {
        val authority = authority()
        val before = authority.snapshot()
        val sentinel = "private-credential-sentinel"
        val result = authority.updateSource(request(), source(), caster().copy(secretId = "owner-secret")) {
            throw IllegalArgumentException(sentinel)
        }
        assertTrue(result is SetupPatchResult.Rejected)
        assertEquals("Selected profile credentials could not be read.", (result as SetupPatchResult.Rejected).reason)
        assertFalse(result.toString().contains(sentinel))
        assertEquals(before, authority.snapshot())
    }

    @Test
    fun `mock patch preserves running output flags and rejects unrelated changes`() {
        val authority = authority()
        val before = requireNotNull(authority.snapshot())
        val output = RecordingPolicyProfile("derived-output", "Mock on", enableMockLocation = true,
            mockLocationRateHz = 5)

        val rejected = authority.updateMock(request(), output.copy(exportGpx = true))
        assertTrue(rejected is SetupPatchResult.Rejected)
        assertEquals(before, authority.snapshot())
        val accepted = authority.updateMock(request(id = "valid"), output)
        assertTrue(accepted is SetupPatchResult.Accepted)
        val after = requireNotNull(authority.snapshot())
        assertTrue(after.config.recording.enableMockLocation)
        assertEquals(5, after.config.recording.mockLocationRateHz)
        assertEquals(before.config.recording.copy(enableMockLocation = true, mockLocationRateHz = 5), after.config.recording)
        assertEquals(before.config.copy(recording = after.config.recording), after.config)
    }

    @Test
    fun `stop discards session authority and rejects delayed patches`() {
        val authority = authority()
        authority.stop("session")
        assertEquals(null, authority.snapshot())
        assertTrue(authority.updateMock(request(), RecordingPolicyProfile("out", "Out")) is SetupPatchResult.Rejected)
    }

    @Test
    fun `late stop cannot discard a newer session snapshot`() {
        val authority = authority()
        val before = authority.snapshot()
        authority.stop("old-session")
        assertEquals(before, authority.snapshot())
    }

    @Test
    fun `mock enable validates formerly dormant explicit engine policy`() {
        val authority = authority()
        val snapshot = requireNotNull(authority.snapshot())
        authority.stop(snapshot.sessionId)
        authority.begin(snapshot.copy(config = snapshot.config.copy(
            solutionPolicy = snapshot.config.solutionPolicy.copy(mockPolicy = SolutionSourcePolicy.RTKLIB_ONLY),
        )))

        val result = authority.updateMock(request(), RecordingPolicyProfile("mock", "Mock",
            enableMockLocation = true, mockLocationRateHz = 5))

        assertTrue(result is SetupPatchResult.Rejected)
        assertFalse(requireNotNull(authority.snapshot()).config.recording.enableMockLocation)
    }

    @Test
    fun `acknowledgement cannot overwrite a newer next start choice`() {
        val accepted = SetupPatchResult.Accepted(snapshot = requireNotNull(authority().snapshot()),
            requestId = "request", selectionRevision = 7)

        assertTrue(accepted.mayPublishSelection("set", 7))
        assertFalse(accepted.mayPublishSelection("set", 8))
        assertFalse(accepted.mayPublishSelection("other-set", 7))
    }

    @Test
    fun `mock derivation cannot change dormant source recording settings`() {
        val authority = authority()
        val snapshot = requireNotNull(authority.snapshot())
        authority.stop(snapshot.sessionId)
        authority.begin(snapshot.copy(config = snapshot.config.copy(
            ntrip = snapshot.config.ntrip.copy(enabled = false),
            recording = snapshot.config.recording.copy(recordNtripCorrectionInput = false),
        )))

        val result = authority.updateMock(request(), snapshot.recordingOutputProfile.copy(
            id = "derived", recordNtripCorrectionInput = false, enableMockLocation = true,
        ))

        assertTrue(result is SetupPatchResult.Rejected)
        assertTrue(requireNotNull(authority.snapshot()).recordingOutputProfile.recordNtripCorrectionInput)
    }

    private fun authority(
        locked: Set<ActiveSetupOptionKey> = emptySet(),
        restriction: String? = null,
    ): RunningSetupConfiguration = RunningSetupConfiguration().apply {
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
            recordingPolicyProfile = RecordingPolicyProfile("out", "Out"),
            storageProfile = StorageProfile("storage", "Storage"),
            workflowName = "Rover NTRIP",
            workflowUsesNtrip = true,
            passwordLookup = { null },
        )
        begin(RunningSetupSnapshot("session", 0, "set", config, RecordingPolicyProfile("out", "Out"),
            mapOf(ActiveSetupOptionKey.NTRIP_MOUNTPOINT to "old-source",
                ActiveSetupOptionKey.RECORDING_OUTPUT to "out"), locked, restriction))
    }

    private fun request(id: String = "request", sessionId: String = "session") =
        SetupPatchRequest(id, sessionId, expectedRevision = 0, selectionRevision = 7)

    private fun caster() = NtripCasterProfile("caster", "Caster", host = "caster.example")
    private fun source() = NtripMountpointProfile("next-source", "Next", "caster", mountpoint = "NEXT")
}
