package org.rtkcollector.app.recording

import org.rtkcollector.app.profile.ActiveNtripConfig
import org.rtkcollector.app.profile.ActiveRecordingConfig
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.RecordingPolicyProfile
import org.rtkcollector.core.solution.SolutionSourcePolicy

data class RunningSetupSnapshot(
    val sessionId: String,
    val revision: Long,
    val settingsSetId: String,
    val config: ActiveRecordingConfig,
    val recordingOutputProfile: RecordingPolicyProfile,
    val profileIds: Map<ActiveSetupOptionKey, String?>,
    val lockedOptions: Set<ActiveSetupOptionKey>,
    val casterRestrictionId: String?,
) {
    init {
        require(sessionId.isNotBlank() && settingsSetId.isNotBlank()) { "Session and settings set identities are required." }
        require(revision >= 0) { "Configuration revision cannot be negative." }
    }

    override fun toString(): String =
        "RunningSetupSnapshot(sessionId=$sessionId, revision=$revision, settingsSetId=$settingsSetId)"
}

data class SetupPatchRequest(
    val requestId: String,
    val sessionId: String,
    val expectedRevision: Long,
    val selectionRevision: Long,
)

sealed interface SetupPatchResult {
    val requestId: String

    data class Accepted(
        val snapshot: RunningSetupSnapshot,
        override val requestId: String,
        val selectionRevision: Long,
    ) : SetupPatchResult {
        fun mayPublishSelection(settingsSetId: String, currentRevision: Long): Boolean =
            snapshot.settingsSetId == settingsSetId && selectionRevision == currentRevision
    }

    data class Rejected(override val requestId: String, val reason: String) : SetupPatchResult
}

/** Service-owned acceptance state. It does not connect sockets or modify capture. */
class RunningSetupConfiguration {
    private var active: RunningSetupSnapshot? = null
    private val acceptedRequests = LinkedHashMap<String, SetupPatchResult.Accepted>()

    @Synchronized
    fun begin(snapshot: RunningSetupSnapshot) {
        check(active == null) { "Another recording configuration is still active." }
        snapshot.config.validateForStart()
        snapshot.recordingOutputProfile.validate()
        active = snapshot.copy(profileIds = snapshot.profileIds.toMap(), lockedOptions = snapshot.lockedOptions.toSet())
        acceptedRequests.clear()
    }

    @Synchronized
    fun snapshot(): RunningSetupSnapshot? = active

    @Synchronized
    fun stop(sessionId: String) {
        if (active?.sessionId != sessionId) return
        active = null
        acceptedRequests.clear()
    }

    @Synchronized
    fun acknowledged(requestId: String, sessionId: String): SetupPatchResult.Accepted? =
        acceptedRequests[requestId]?.takeIf { active?.sessionId == sessionId }

    @Synchronized
    fun updateSource(
        request: SetupPatchRequest,
        source: NtripMountpointProfile,
        caster: NtripCasterProfile,
        passwordLookup: (String) -> String?,
    ): SetupPatchResult = applyPatch(request, ActiveSetupOptionKey.NTRIP_MOUNTPOINT) { snapshot ->
        require(snapshot.config.ntrip.enabled) { "This recording workflow does not use correction download." }
        require(snapshot.casterRestrictionId == null || snapshot.casterRestrictionId == caster.id) {
            "Selected source does not satisfy this recording's caster restriction."
        }
        val correction = ActiveNtripConfig.fromProfiles(caster, source, passwordLookup)
        snapshot.copy(
            config = snapshot.config.copy(ntrip = correction),
            profileIds = snapshot.profileIds + mapOf(
                ActiveSetupOptionKey.NTRIP_MOUNTPOINT to source.id,
                ActiveSetupOptionKey.NTRIP_CASTER to caster.id,
            ),
        )
    }

    @Synchronized
    fun updateMock(request: SetupPatchRequest, output: RecordingPolicyProfile): SetupPatchResult =
        applyPatch(request, ActiveSetupOptionKey.RECORDING_OUTPUT) { snapshot ->
            output.validate()
            val before = snapshot.config.recording
            val owner = snapshot.recordingOutputProfile
            require(output.copy(
                id = owner.id, name = owner.name, isProtected = owner.isProtected,
                enableMockLocation = owner.enableMockLocation, mockLocationRateHz = owner.mockLocationRateHz,
            ) == owner) {
                "Live mock changes cannot alter other recording outputs."
            }
            require(!output.enableMockLocation || snapshot.config.rtklib.enabled ||
                snapshot.config.solutionPolicy.mockPolicy != SolutionSourcePolicy.RTKLIB_ONLY) {
                "RTKLIB-only mock output requires an active RTKLIB workflow."
            }
            snapshot.copy(
                config = snapshot.config.copy(recording = before.copy(
                    enableMockLocation = output.enableMockLocation,
                    mockLocationRateHz = output.mockLocationRateHz,
                )),
                recordingOutputProfile = output,
                profileIds = snapshot.profileIds + (ActiveSetupOptionKey.RECORDING_OUTPUT to output.id),
            )
        }

    private fun applyPatch(
        request: SetupPatchRequest,
        key: ActiveSetupOptionKey,
        change: (RunningSetupSnapshot) -> RunningSetupSnapshot,
    ): SetupPatchResult {
        val snapshot = active ?: return reject(request, "No recording configuration is active.")
        if (request.requestId.isBlank() || request.sessionId != snapshot.sessionId) {
            return reject(request, "The update belongs to another recording.")
        }
        acceptedRequests[request.requestId]?.let { return it }
        if (request.expectedRevision != snapshot.revision) {
            return reject(request, "Recording configuration changed; refresh and retry.")
        }
        if (key in snapshot.lockedOptions) return reject(request, "This selection is fixed for the recording.")
        return try {
            val updated = change(snapshot)
            updated.config.validateForStart()
            val next = updated.copy(revision = Math.addExact(snapshot.revision, 1L))
            active = next
            SetupPatchResult.Accepted(next, request.requestId, request.selectionRevision).also {
                acceptedRequests[request.requestId] = it
                if (acceptedRequests.size > MAX_RECEIPTS) acceptedRequests.remove(acceptedRequests.keys.first())
            }
        } catch (failure: IllegalArgumentException) {
            reject(request, failure.message ?: "Recording configuration update is invalid.")
        } catch (_: RuntimeException) {
            reject(request, "Recording configuration update could not be applied.")
        }
    }

    private fun reject(request: SetupPatchRequest, message: String): SetupPatchResult.Rejected =
        SetupPatchResult.Rejected(request.requestId, message)

    companion object {
        private const val MAX_RECEIPTS = 32
    }
}
