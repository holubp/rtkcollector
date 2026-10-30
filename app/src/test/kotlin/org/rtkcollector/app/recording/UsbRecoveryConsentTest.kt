package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UsbRecoveryConsentTest {
    @Test fun `consent is requested only once per observed attachment including after denial`() {
        val consent = UsbRecoveryConsent()
        consent.begin(1)
        consent.observe(20)
        val first = requireNotNull(consent.request())
        assertNull(consent.request())
        assertTrue(consent.complete(first, false))
        assertTrue(consent.denied)
        assertNull(consent.request())
        consent.observe(null)
        consent.observe(20)
        val next = requireNotNull(consent.request())
        assertNotEquals(first, next)
        assertFalse(consent.complete(first, true))
        assertTrue(consent.complete(next, true))
        assertFalse(consent.denied)
    }

    @Test fun `Stop new session and changed device reject late permission results`() {
        val consent = UsbRecoveryConsent()
        consent.begin(1); consent.observe(20)
        val first = requireNotNull(consent.request())
        consent.stop()
        assertFalse(consent.isCurrent(first))
        assertNull(consent.request())
        consent.begin(2); consent.observe(20)
        val second = requireNotNull(consent.request())
        assertFalse(consent.complete(first, true))
        consent.observe(30)
        assertFalse(consent.complete(second, true))
        val third = requireNotNull(consent.request())
        assertTrue(consent.isCurrent(third))
        assertFalse(consent.complete(third.copy(deviceId = 20), true))
    }

    @Test fun `repeated polling does not invalidate pending consent and completion is one shot`() {
        val consent = UsbRecoveryConsent()
        consent.begin(9); consent.observe(20)
        val ticket = requireNotNull(consent.request())
        repeat(10) { consent.observe(20); assertTrue(consent.isCurrent(ticket)); assertNull(consent.request()) }
        assertTrue(consent.complete(ticket, true))
        assertFalse(consent.complete(ticket, true))
        assertNull(consent.request())
    }

    @Test fun `access granted externally retires pending callback without admitting another dialog`() {
        val consent = UsbRecoveryConsent()
        consent.begin(1); consent.observe(20)
        val ticket = requireNotNull(consent.request())
        consent.accessAvailable()
        assertFalse(consent.complete(ticket, false))
        assertFalse(consent.denied)
        assertNull(consent.request())
    }
}
