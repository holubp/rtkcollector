package org.rtkcollector.app.recording

import kotlin.test.Test
import kotlin.test.assertEquals

class NtripUpdatePolicyTest {
    @Test fun queuedConnectingCannotOverwriteNewerTerminalSnapshot() {
        val queue = java.util.ArrayDeque<Runnable>()
        val delivered = mutableListOf<String>()
        val delivery = LatestNtripStatusDelivery<String>({ queue.add(it) }, delivered::add)
        delivery.offer("CONNECTING")
        delivery.offer("STREAMING")
        delivery.offer("AUTH_ERROR")
        assertEquals(1, queue.size)
        queue.remove().run()
        assertEquals(listOf("AUTH_ERROR"), delivered)
        assertEquals(0, queue.size)
    }

    @Test fun newerStatusDuringDeliveryGetsOneSubsequentTurn() {
        val queue = java.util.ArrayDeque<Runnable>()
        val delivered = mutableListOf<String>()
        lateinit var delivery: LatestNtripStatusDelivery<String>
        delivery = LatestNtripStatusDelivery({ queue.add(it) }) {
            delivered.add(it)
            if (it == "CONNECTING") { delivery.offer("STREAMING"); delivery.offer("TLS_ERROR") }
        }
        delivery.offer("CONNECTING")
        queue.remove().run()
        assertEquals(1, queue.size)
        queue.remove().run()
        assertEquals(listOf("CONNECTING", "TLS_ERROR"), delivered)
        assertEquals(0, queue.size)
    }

    @Test
    fun rejectsLiveUpdateWhenActiveWorkflowDoesNotUseNtrip() {
        val result = NtripUpdatePolicy.validateUpdate(
            activeRecordingRunning = true,
            activeWorkflowUsesNtrip = false,
        )

        assertEquals(false, result.allowed)
        assertEquals(RecordingErrorCategory.NTRIP, result.category)
    }

    @Test
    fun allowsLiveUpdateWhenActiveWorkflowUsesNtrip() {
        val result = NtripUpdatePolicy.validateUpdate(
            activeRecordingRunning = true,
            activeWorkflowUsesNtrip = true,
        )

        assertEquals(true, result.allowed)
        assertEquals(RecordingErrorCategory.NONE, result.category)
    }
}
