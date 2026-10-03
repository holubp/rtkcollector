package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class LiveSetupRetryPolicyTest {
    @Test
    fun `known rejection retries with fresh request and current owner revision`() {
        val decision = planLiveSetupRetry(
            original = LiveSetupRequest("old", "session", 3, 7),
            settingsSetId = "set",
            running = LiveSetupOwner("session", "set", 5),
            receipt = LiveSetupReceipt("old", "session", "set", accepted = false),
            freshRequestId = "new",
            selectionRevision = 11,
        )

        assertEquals(
            LiveSetupRetryDecision.Dispatch(LiveSetupRequest("new", "session", 5, 11)),
            decision,
        )
    }

    @Test
    fun `accepted receipt resolves lost acknowledgement without dispatch`() {
        val receipt = LiveSetupReceipt("old", "session", "set", accepted = true)

        val decision = planLiveSetupRetry(
            original = LiveSetupRequest("old", "session", 3, 7),
            settingsSetId = "set",
            running = LiveSetupOwner("session", "set", 4),
            receipt = receipt,
            freshRequestId = "must-not-be-used",
            selectionRevision = 11,
        )

        assertEquals(LiveSetupRetryDecision.Acknowledged(receipt), decision)
    }

    @Test
    fun `missing receipt preserves request identity for safe idempotent retry`() {
        val original = LiveSetupRequest("same", "session", 3, 7)

        val decision = planLiveSetupRetry(
            original = original,
            settingsSetId = "set",
            running = LiveSetupOwner("session", "set", 4),
            receipt = null,
            freshRequestId = "unused",
            selectionRevision = 11,
        )

        assertEquals(LiveSetupRetryDecision.Dispatch(original), decision)
    }

    @Test
    fun `retry cannot cross a recording or settings set`() {
        val decision = planLiveSetupRetry(
            original = LiveSetupRequest("old", "session", 3, 7),
            settingsSetId = "set",
            running = LiveSetupOwner("next-session", "set", 0),
            receipt = LiveSetupReceipt("old", "session", "set", accepted = false),
            freshRequestId = "new",
            selectionRevision = 11,
        )

        assertIs<LiveSetupRetryDecision.Blocked>(decision)
    }

    @Test
    fun `complete patched owner and dependency content must still match running before publish`() {
        val running = LivePatchOwner("source-a", "mountpoint-content", "caster-a", "caster-content")

        assertEquals(true, mayPublishLivePatchSelection(running, running, 7, 7))
        assertEquals(false, mayPublishLivePatchSelection(
            LivePatchOwner("source-b", "edited-mountpoint", "caster-a", "caster-content"), running, 7, 7,
        ))
        assertEquals(false, mayPublishLivePatchSelection(
            LivePatchOwner("source-a", "mountpoint-content", "caster-a", "edited-caster-content"), running, 7, 7,
        ))
        assertEquals(false, mayPublishLivePatchSelection(
            LivePatchOwner("output-a", "output-content", null, null),
            LivePatchOwner("output-a", "output-edited-content", null, null), 7, 7,
        ))
    }

    @Test
    fun `newer next start generation blocks publication after retry`() {
        val owner = LivePatchOwner("output-a", "output-content", null, null)

        assertEquals(false, mayPublishLivePatchSelection(owner, owner, 7, 11))
    }
}
