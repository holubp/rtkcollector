package org.rtkcollector.app.ui.profiles

data class LiveSetupRequest(
    val requestId: String,
    val sessionId: String,
    val expectedRevision: Long,
    val selectionRevision: Long,
)

data class LiveSetupOwner(
    val sessionId: String,
    val settingsSetId: String,
    val revision: Long,
)

data class LiveSetupReceipt(
    val requestId: String,
    val sessionId: String,
    val settingsSetId: String,
    val accepted: Boolean,
)

sealed interface LiveSetupRetryDecision {
    data class Dispatch(val request: LiveSetupRequest) : LiveSetupRetryDecision
    data class Acknowledged(val receipt: LiveSetupReceipt) : LiveSetupRetryDecision
    data class Blocked(val reason: String) : LiveSetupRetryDecision
}

fun planLiveSetupRetry(
    original: LiveSetupRequest,
    settingsSetId: String,
    running: LiveSetupOwner?,
    receipt: LiveSetupReceipt?,
    freshRequestId: String,
    selectionRevision: Long,
): LiveSetupRetryDecision {
    val owner = running ?: return LiveSetupRetryDecision.Blocked("Recording configuration is unavailable; refresh before retrying.")
    if (owner.sessionId != original.sessionId || owner.settingsSetId != settingsSetId) {
        return LiveSetupRetryDecision.Blocked("The recording or settings set changed; discard this update.")
    }
    if (receipt == null) return LiveSetupRetryDecision.Dispatch(original)
    if (receipt.requestId != original.requestId || receipt.sessionId != owner.sessionId ||
        receipt.settingsSetId != owner.settingsSetId) {
        return LiveSetupRetryDecision.Blocked("The acknowledgement belongs to another recording update.")
    }
    if (receipt.accepted) return LiveSetupRetryDecision.Acknowledged(receipt)
    if (freshRequestId.isBlank() || freshRequestId == original.requestId) {
        return LiveSetupRetryDecision.Blocked("A fresh update identity could not be created.")
    }
    return LiveSetupRetryDecision.Dispatch(LiveSetupRequest(
        requestId = freshRequestId,
        sessionId = owner.sessionId,
        expectedRevision = owner.revision,
        selectionRevision = selectionRevision,
    ))
}

data class LivePatchOwner(
    val profileId: String,
    val profileContent: String,
    val dependencyId: String?,
    val dependencyContent: String?,
)

fun mayPublishLivePatchSelection(
    patchedOwner: LivePatchOwner,
    currentOwner: LivePatchOwner?,
    originalSelectionRevision: Long,
    currentSelectionRevision: Long,
): Boolean = currentOwner != null && patchedOwner == currentOwner &&
    originalSelectionRevision == currentSelectionRevision
