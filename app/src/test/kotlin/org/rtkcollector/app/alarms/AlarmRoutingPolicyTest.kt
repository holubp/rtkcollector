package org.rtkcollector.app.alarms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlarmRoutingPolicyTest {
    private val speaker = AlarmRouteDevice(1, AlarmRouteKind.SPEAKER)
    private val bluetooth = AlarmRouteDevice(2, AlarmRouteKind.BLUETOOTH)
    private val wired = AlarmRouteDevice(3, AlarmRouteKind.OTHER)

    @Test fun `disabled preserves system routing rather than forcing speaker`() {
        val decision = AlarmRoutingPolicy().decide(false, listOf(bluetooth, speaker, wired), 3, 0, 1)
        assertNull(decision.deviceId)
        assertFalse(decision.fallback)
        assertFalse(decision.failed)
        assertNull(decision.warning)
        assertEquals("System audio", decision.label)
    }
    @Test fun `Bluetooth is selected deterministically and current usable choice retained`() {
        val policy = AlarmRoutingPolicy()
        val voice = AlarmRouteDevice(4, AlarmRouteKind.BLUETOOTH, priority = 1)
        val other = AlarmRouteDevice(5, AlarmRouteKind.BLUETOOTH)
        assertEquals(2, policy.decide(true, listOf(voice, other, bluetooth, speaker), null, 0, 1).deviceId)
        val confirmed = policy.decide(true, listOf(other, speaker, bluetooth), 2, 1, 2)
        assertEquals(2, confirmed.deviceId)
        assertTrue(confirmed.confirmed)
        assertEquals("Bluetooth audio", confirmed.label)
    }
    @Test fun `no Bluetooth is a speaker fallback and not an error`() {
        val decision = AlarmRoutingPolicy().decide(true, listOf(wired, speaker), 1, 0, 1)
        assertEquals(1, decision.deviceId)
        assertTrue(decision.fallback)
        assertTrue(decision.confirmed)
        assertNull(decision.warning)
        assertFalse(decision.failed)
    }
    @Test fun `disconnect immediately selects speaker and reconnect permits Bluetooth again`() {
        val policy = AlarmRoutingPolicy()
        assertTrue(policy.decide(true, listOf(bluetooth, speaker), 2, 0, 1).confirmed)
        assertEquals(1, policy.decide(true, listOf(speaker), 2, 1, 2).deviceId)
        assertEquals(2, policy.decide(true, listOf(speaker, bluetooth), 1, 2, 3).deviceId)
    }
    @Test fun `rejected Bluetooth falls back immediately without a retry storm`() {
        val policy = AlarmRoutingPolicy()
        policy.decide(true, listOf(bluetooth, speaker), null, 0, 1)
        policy.reject(2)
        val fallback = policy.decide(true, listOf(bluetooth, speaker), 1, 1, 1)
        assertEquals(1, fallback.deviceId)
        assertTrue(fallback.confirmed)
        assertNotNull(fallback.warning)
        assertEquals(1, policy.decide(true, listOf(bluetooth, speaker), 1, 10_000, 1).deviceId)
        assertEquals(2, policy.decide(true, listOf(bluetooth, speaker), 1, 10_001, 2).deviceId)
        policy.reject(2)
        policy.reset()
        assertEquals(2, policy.decide(true, listOf(bluetooth, speaker), 1, 10_002, 2).deviceId)
    }
    @Test fun `preferred device is not confirmed actual output and times out after 500ms`() {
        val policy = AlarmRoutingPolicy()
        val pending = policy.decide(true, listOf(bluetooth, speaker), 1, 0, 1)
        assertFalse(pending.confirmed)
        assertEquals("Switching to Bluetooth audio", pending.label)
        assertEquals(2, policy.decide(true, listOf(bluetooth, speaker), 1, 499, 1).deviceId)
        val fallback = policy.decide(true, listOf(bluetooth, speaker), 1, 500, 1)
        assertEquals(1, fallback.deviceId)
        assertTrue(fallback.confirmed)
    }
    @Test fun `preparation time does not consume actual route confirmation window`() {
        val policy = AlarmRoutingPolicy()
        policy.decide(true, listOf(bluetooth, speaker), null, 0, 1, playing = false)
        assertEquals(2, policy.decide(true, listOf(bluetooth, speaker), null, 10_000, 1, playing = false).deviceId)
        policy.decide(true, listOf(bluetooth, speaker), null, 10_001, 1)
        assertEquals(2, policy.decide(true, listOf(bluetooth, speaker), null, 10_500, 1).deviceId)
        assertEquals(1, policy.decide(true, listOf(bluetooth, speaker), null, 10_501, 1).deviceId)
    }
    @Test fun `missing or rejected phone speaker is an explicit failure`() {
        assertTrue(AlarmRoutingPolicy().decide(true, listOf(wired), 3, 0, 1).failed)
        val policy = AlarmRoutingPolicy()
        policy.decide(true, listOf(speaker), null, 0, 1)
        policy.reject(1)
        val result = policy.decide(true, listOf(speaker), null, 1, 1)
        assertTrue(result.failed)
        assertFalse(result.confirmed)
    }
}
