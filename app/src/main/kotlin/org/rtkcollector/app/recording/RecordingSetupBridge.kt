package org.rtkcollector.app.recording

import java.util.UUID
import org.json.JSONObject
import org.rtkcollector.app.base.AcceptedBaseCoordinate
import org.rtkcollector.app.profile.ActiveSetupOptionKey
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.RecordingPolicyProfile
import org.rtkcollector.app.profile.StartSelectionLease
import org.rtkcollector.app.profile.ActiveSetupSelections

/** Secret-free service state suitable for UI reconciliation after broadcasts or resume. */
data class SetupBridgeState(
    val sessionId: String,
    val revision: Long,
    val settingsSetId: String,
    val profileIds: Map<ActiveSetupOptionKey, String?>,
    val recordingOutputProfile: RecordingPolicyProfile? = null,
    val lockedOptions: Set<ActiveSetupOptionKey> = emptySet(),
    val casterRestrictionId: String? = null,
    val mockEnabled: Boolean = false,
    val mockRateHz: Int = RecordingPolicyProfile.DEFAULT_MOCK_LOCATION_RATE_HZ,
)

/** Acceptance means configuration changed; it does not claim a network connection succeeded. */
data class SetupBridgeReceipt(
    val requestId: String,
    val sessionId: String,
    val accepted: Boolean,
    val revision: Long,
    val selectionRevision: Long,
    val profileIds: Map<ActiveSetupOptionKey, String?>,
    val message: String?,
    val settingsSetId: String? = null,
)

/** Adds only identities and selection constraints to already-redacted session metadata. */
internal fun withRecordingSetupProvenance(
    metadataJson: String,
    started: SetupBridgeState,
    latestAccepted: SetupBridgeState,
): String {
    fun projection(state: SetupBridgeState): JSONObject = JSONObject()
        .put("sessionId", state.sessionId)
        .put("revision", state.revision)
        .put("settingsSetId", state.settingsSetId)
        .put("profileIds", JSONObject().apply {
            state.profileIds.entries.sortedBy { it.key.name }.forEach { (key, id) ->
                put(key.name, id ?: JSONObject.NULL)
            }
        })
        .put("lockedOptions", org.json.JSONArray(state.lockedOptions.map { it.name }.sorted()))
        .put("casterRestrictionId", state.casterRestrictionId ?: JSONObject.NULL)
    require(started.sessionId == latestAccepted.sessionId && started.settingsSetId == latestAccepted.settingsSetId) {
        "Session provenance must belong to one recording setup."
    }
    val metadata = JSONObject(metadataJson)
    require(metadata.optString("sessionUuid") == started.sessionId) {
        "Session provenance must match the session metadata."
    }
    return metadata.put("settingsOwnership", JSONObject()
        .put("started", projection(started))
        .put("latestAccepted", projection(latestAccepted))).toString()
}

internal fun shouldApplySetupReceipt(previousRevision: Long?, receipt: SetupBridgeReceipt?): Boolean =
    previousRevision != null && receipt?.accepted == true && receipt.revision > previousRevision

open class RecordingSetupAuthority {
    private sealed interface Pending {
        class Start(val snapshot: RunningSetupSnapshot, val basePosition: AcceptedBaseCoordinate?,
            val selectionLease: StartSelectionLease?) : Pending
        class Source(val request: SetupPatchRequest, val source: NtripMountpointProfile,
            val caster: NtripCasterProfile, val password: String?) : Pending
        class Mock(val request: SetupPatchRequest, val output: RecordingPolicyProfile) : Pending
    }

    private val running = RunningSetupConfiguration()
    private val staged = LinkedHashMap<String, Pending>()
    private val receipts = LinkedHashMap<String, SetupBridgeReceipt>()
    private val usedSessionIds = LinkedHashSet<String>()
    private var activeBasePosition: AcceptedBaseCoordinate? = null
    private var activeSelectionLease: StartSelectionLease? = null

    fun stageStart(snapshot: RunningSetupSnapshot, basePosition: AcceptedBaseCoordinate? = null,
        selectionLease: StartSelectionLease? = null): String = try {
        synchronized(this) { stageValidatedStart(snapshot, basePosition, selectionLease) }
    } catch (failure: Throwable) {
        selectionLease?.finish()
        throw failure
    }

    private fun stageValidatedStart(snapshot: RunningSetupSnapshot, basePosition: AcceptedBaseCoordinate?,
        selectionLease: StartSelectionLease?): String {
        require(selectionLease == null || selectionLease.selections.settingsSetId == snapshot.settingsSetId) {
            "Start answers belong to another settings set."
        }
        check(running.snapshot() == null) { "A recording configuration is already active." }
        require(snapshot.revision == 0L && snapshot.sessionId !in usedSessionIds) {
            "A new recording requires a fresh session identity and revision zero."
        }
        if (snapshot.config.workflowId == "fixed-base") {
            require(basePosition != null &&
                basePosition.id == snapshot.profileIds[ActiveSetupOptionKey.BASE_COORDINATE]) {
                "A validated selected base coordinate is required for fixed-base recording."
            }
            basePosition.validate()
        } else {
            require(basePosition == null) { "Only fixed-base recording may stage a base coordinate." }
        }
        snapshot.config.validateForStart()
        require(!snapshot.config.ntrip.enabled || snapshot.config.ntrip.protocolPolicy != "NTRIP_V2_ONLY") {
            "Strict NTRIP v2 correction download is unavailable until the client can disable v1 fallback."
        }
        val owner = snapshot.recordingOutputProfile
        owner.validate()
        val output = snapshot.config.recording
        require(snapshot.profileIds[ActiveSetupOptionKey.RECEIVER_COMMAND] == snapshot.config.commandProfileId &&
            snapshot.profileIds[ActiveSetupOptionKey.USB_BAUD] == snapshot.config.usbBaudProfileId &&
            snapshot.profileIds[ActiveSetupOptionKey.STORAGE] == snapshot.config.storage.id &&
            snapshot.profileIds[ActiveSetupOptionKey.RTKLIB] == snapshot.config.rtklib.profileId &&
            snapshot.profileIds[ActiveSetupOptionKey.SOLUTION_POLICY] == snapshot.config.solutionPolicy.profileId &&
            (!snapshot.config.ntrip.enabled ||
                snapshot.profileIds[ActiveSetupOptionKey.NTRIP_CASTER] == snapshot.config.ntrip.casterProfileId &&
                snapshot.profileIds[ActiveSetupOptionKey.NTRIP_MOUNTPOINT] == snapshot.config.ntrip.sourceProfileId)) {
            "Selected profile provenance does not match the validated configuration."
        }
        require(snapshot.profileIds[ActiveSetupOptionKey.RECORDING_OUTPUT] == owner.id &&
            output.recordTxToReceiver == owner.recordTxToReceiver &&
            output.recordNtripCorrectionInput == (snapshot.config.ntrip.enabled && owner.recordNtripCorrectionInput) &&
            output.exportNmea == owner.exportNmea && output.pppNmeaGgaQuality == owner.pppNmeaGgaQuality &&
            output.exportJsonSolution == owner.exportJsonSolution && output.exportGpx == owner.exportGpx &&
            output.recordRemoteBaseRaw == (snapshot.config.ntrip.enabled && owner.recordRemoteBaseRaw) &&
            output.enableMockLocation == owner.enableMockLocation && output.mockLocationRateHz == owner.mockLocationRateHz) {
            "Recording outputs do not match their selected owner profile."
        }
        return stage(Pending.Start(snapshot.copy(
            config = snapshot.config.copy(
                initCommands = snapshot.config.initCommands.toList(),
                baudSwitchCommands = snapshot.config.baudSwitchCommands.toList(),
                modeCommands = snapshot.config.modeCommands.toList(),
                shutdownCommands = snapshot.config.shutdownCommands.toList(),
                rtklib = snapshot.config.rtklib.copy(validationErrors = snapshot.config.rtklib.validationErrors.toList()),
                recording = snapshot.config.recording.copy(
                    expectedSessionArtifacts = snapshot.config.recording.expectedSessionArtifacts.toSet(),
                ),
            ),
            profileIds = snapshot.profileIds.toMap(),
            lockedOptions = snapshot.lockedOptions.toSet(),
        ), basePosition, selectionLease))
    }

    @Synchronized
    fun stageSourceUpdate(
        request: SetupPatchRequest,
        originSettingsSetId: String,
        source: NtripMountpointProfile,
        caster: NtripCasterProfile,
        boundPassword: String?,
    ): String {
        require(running.snapshot()?.settingsSetId == originSettingsSetId) {
            "The update belongs to another settings set or no recording is active."
        }
        require(caster.protocolPolicy != "NTRIP_V2_ONLY") {
            "Strict NTRIP v2 correction download is unavailable until the client can disable v1 fallback."
        }
        return stage(Pending.Source(request, source, caster.copy(
            sourcetableMountpoints = caster.sourcetableMountpoints.toList(),
        ), boundPassword))
    }

    @Synchronized
    fun stageMockUpdate(
        request: SetupPatchRequest,
        originSettingsSetId: String,
        output: RecordingPolicyProfile,
    ): String {
        require(running.snapshot()?.settingsSetId == originSettingsSetId) {
            "The update belongs to another settings set or no recording is active."
        }
        return stage(Pending.Mock(request, output))
    }

    fun acceptStart(token: String?): RunningSetupSnapshot? {
        var pending: Pending.Start? = null
        return try {
            synchronized(this) {
                val start = staged.remove(token ?: return null) as? Pending.Start ?: return null
                pending = start
                running.begin(start.snapshot)
                activeBasePosition = start.basePosition
                activeSelectionLease = start.selectionLease ?: StartSelectionLease(
                    ActiveSetupSelections(start.snapshot.settingsSetId), 0L) {}
                usedSessionIds += start.snapshot.sessionId
                if (usedSessionIds.size > MAX_SESSION_IDS) usedSessionIds.remove(usedSessionIds.first())
                receipts.clear()
                running.snapshot()
            }
        } catch (failure: Throwable) {
            pending?.selectionLease?.finish()
            throw failure
        }
    }

    fun dispatchStart(token: String?, dispatch: () -> Unit) {
        try { dispatch() } catch (failure: Throwable) {
            cancelStart(token)
            throw failure
        }
    }

    fun consumePublishedSelections(receipt: SetupBridgeReceipt, consumed: StartSelectionLease) {
        if (!receipt.accepted || receipt.settingsSetId != consumed.selections.settingsSetId) return
        val sessionLease = synchronized(this) {
            activeSelectionLease.takeIf {
                running.snapshot()?.let { it.sessionId == receipt.sessionId && it.settingsSetId == receipt.settingsSetId } == true
            }
        }
        // If Stop raced publication or adoption, finish immediately without taking bridge locks.
        if (sessionLease == null) consumed.finish() else sessionLease.adopt(consumed)
    }

    fun cancelStart(token: String?) {
        val pending = synchronized(this) { staged.remove(token ?: return) as? Pending.Start }
        pending?.selectionLease?.finish()
    }

    @Synchronized
    fun acceptSource(
        token: String?,
        validateCandidate: (RunningSetupSnapshot) -> Unit = {},
    ): SetupBridgeReceipt? {
        val pending = staged.remove(token ?: return null) as? Pending.Source ?: return null
        val current = running.snapshot()
            ?: return record(pending.request, SetupPatchResult.Rejected(
                pending.request.requestId, "No recording configuration is active."))
        if (receipts.containsKey(pending.request.requestId)) return receipts[pending.request.requestId]
        val candidate = RunningSetupConfiguration().apply { begin(current) }.updateSource(
            pending.request, pending.source, pending.caster,
        ) { secretId -> pending.password.takeIf { secretId == pending.caster.secretId } }
        if (candidate is SetupPatchResult.Rejected) return record(pending.request, candidate)
        try {
            validateCandidate((candidate as SetupPatchResult.Accepted).snapshot)
        } catch (failure: IllegalArgumentException) {
            return record(pending.request, SetupPatchResult.Rejected(
                pending.request.requestId, failure.message ?: "Selected endpoint cannot be used."))
        }
        val result = running.updateSource(pending.request, pending.source, pending.caster) { secretId ->
            pending.password.takeIf { secretId == pending.caster.secretId }
        }
        return record(pending.request, result)
    }

    @Synchronized
    fun acceptMock(token: String?): SetupBridgeReceipt? {
        val pending = staged.remove(token ?: return null) as? Pending.Mock ?: return null
        return record(pending.request, running.updateMock(pending.request, pending.output))
    }

    @Synchronized
    fun current(): SetupBridgeState? = running.snapshot()?.let {
        SetupBridgeState(it.sessionId, it.revision, it.settingsSetId, it.profileIds.toMap(),
            it.recordingOutputProfile, it.lockedOptions.toSet(), it.casterRestrictionId,
            it.config.recording.enableMockLocation, it.config.recording.mockLocationRateHz)
    }

    @Synchronized
    fun currentOutputProfile(): RecordingPolicyProfile? = running.snapshot()?.recordingOutputProfile

    @Synchronized
    internal fun serviceSnapshot(sessionId: String): RunningSetupSnapshot? =
        running.snapshot()?.takeIf { it.sessionId == sessionId }

    @Synchronized
    internal fun serviceBasePosition(sessionId: String): AcceptedBaseCoordinate? =
        activeBasePosition?.takeIf { running.snapshot()?.sessionId == sessionId }

    @Synchronized
    fun result(requestId: String): SetupBridgeReceipt? = receipts[requestId]
        ?.takeIf { running.snapshot()?.sessionId == it.sessionId }

    fun stop(sessionId: String) {
        val lease = synchronized(this) {
            if (running.snapshot()?.sessionId != sessionId) return
            running.stop(sessionId)
            activeBasePosition = null
            staged.entries.removeAll { it.value !is Pending.Start }
            receipts.clear()
            activeSelectionLease.also { activeSelectionLease = null }
        }
        lease?.finish()
    }

    fun cancelPending() {
        val leases = synchronized(this) {
            staged.values.filterIsInstance<Pending.Start>().mapNotNull { it.selectionLease }.also { staged.clear() }
        }
        leases.forEach(StartSelectionLease::finish)
    }

    private fun record(request: SetupPatchRequest, result: SetupPatchResult): SetupBridgeReceipt {
        val snapshot = running.snapshot()
        val receipt = when (result) {
            is SetupPatchResult.Accepted -> SetupBridgeReceipt(result.requestId, result.snapshot.sessionId,
                true, result.snapshot.revision, result.selectionRevision, result.snapshot.profileIds.toMap(), null,
                result.snapshot.settingsSetId)
            is SetupPatchResult.Rejected -> SetupBridgeReceipt(result.requestId, request.sessionId,
                false, snapshot?.revision ?: -1L, request.selectionRevision,
                snapshot?.profileIds?.toMap().orEmpty(), result.reason, snapshot?.settingsSetId)
        }
        receipts[request.requestId] = receipt
        if (receipts.size > MAX_RECEIPTS) receipts.remove(receipts.keys.first())
        return receipt
    }

    private fun stage(pending: Pending): String {
        check(staged.size < MAX_STAGED) { "Too many pending configuration requests." }
        val token = UUID.randomUUID().toString()
        staged[token] = pending
        return token
    }

    private companion object {
        const val MAX_STAGED = 16
        const val MAX_RECEIPTS = 32
        const val MAX_SESSION_IDS = 64
    }
}

/** The Activity and non-exported service share the app's default process. */
object RecordingSetupBridge : RecordingSetupAuthority()
