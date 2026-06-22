package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardFormattersTest {
    @Test
    fun `distance formatter uses mm cm m and km`() {
        assertEquals("8 mm", formatDistance(0.008))
        assertEquals("3 cm", formatDistance(0.03))
        assertEquals("12.4 m", formatDistance(12.4))
        assertEquals("42.8 km", formatDistance(42_800.0))
    }

    @Test
    fun `distance formatter handles null and signed values`() {
        assertEquals("n/a", formatDistance(null))
        assertEquals("-8 mm", formatDistance(-0.008))
        assertEquals("-1.5 m", formatDistance(-1.5))
    }

    @Test
    fun `dashboard distance formatter can force meters`() {
        assertEquals("0.008 m", formatDashboardDistance("8 mm", DashboardDistanceUnitPreference.STATIC_METERS))
        assertEquals("0.030 m", formatDashboardDistance("3 cm", DashboardDistanceUnitPreference.STATIC_METERS))
        assertEquals("12.400 m", formatDashboardDistance("12.4 m", DashboardDistanceUnitPreference.STATIC_METERS))
        assertEquals("42800.000 m", formatDashboardDistance("42.8 km", DashboardDistanceUnitPreference.STATIC_METERS))
    }

    @Test
    fun `dashboard distance formatter keeps dynamic units by default`() {
        assertEquals("8 mm", formatDashboardDistance("0.008 m", DashboardDistanceUnitPreference.DYNAMIC))
        assertEquals("3 cm", formatDashboardDistance("0.030 m", DashboardDistanceUnitPreference.DYNAMIC))
        assertEquals("12.4 m", formatDashboardDistance("12.4 m", DashboardDistanceUnitPreference.DYNAMIC))
        assertEquals("n/a", formatDashboardDistance("n/a", DashboardDistanceUnitPreference.DYNAMIC))
    }

    @Test
    fun `dashboard distance pair formatter applies same unit preference to both values`() {
        assertEquals(
            "0.008 m / 0.030 m",
            formatDashboardDistancePair("8 mm / 3 cm", DashboardDistanceUnitPreference.STATIC_METERS),
        )
        assertEquals(
            "8 mm / 3 cm",
            formatDashboardDistancePair("0.008 m / 0.030 m", DashboardDistanceUnitPreference.DYNAMIC),
        )
    }

    @Test
    fun `bytes formatter uses compact units`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 kB", formatBytes(1536))
        assertEquals("2.1 MB", formatBytes(2_100_000))
        assertEquals("3.5 GB", formatBytes(3_500_000_000))
    }

    @Test
    fun `bytes formatter handles null`() {
        assertEquals("n/a", formatBytes(null))
    }

    @Test
    fun `rate formatter uses compact units per second`() {
        assertEquals("512 B/s", formatRate(512.4))
        assertEquals("1.5 kB/s", formatRate(1536.0))
        assertEquals("2.1 MB/s", formatRate(2_100_000.0))
    }

    @Test
    fun `rate formatter handles null`() {
        assertEquals("n/a", formatRate(null))
    }

    @Test
    fun `gga fix quality is interpreted`() {
        assertEquals("Invalid", interpretGgaFixQuality(0))
        assertEquals("Single", interpretGgaFixQuality(1))
        assertEquals("DGPS", interpretGgaFixQuality(2))
        assertEquals("RTK fix", interpretGgaFixQuality(4))
        assertEquals("RTK float", interpretGgaFixQuality(5))
        assertEquals("Estimated", interpretGgaFixQuality(6))
        assertEquals("Base/manual", interpretGgaFixQuality(7))
        assertEquals("PPP", interpretGgaFixQuality(9))
        assertEquals("Unknown GGA 17", interpretGgaFixQuality(17))
        assertEquals("n/a", interpretGgaFixQuality(null))
    }

    @Test
    fun `utc display keeps fixed millisecond precision`() {
        assertEquals("2026-06-17T12:34:56.000Z", displayUtcTime("2026-06-17T12:34:56Z"))
        assertEquals("2026-06-17T12:34:56.050Z", displayUtcTime("2026-06-17T12:34:56.05Z"))
        assertEquals("123456.000", displayUtcTime("123456"))
        assertEquals("123456.050", displayUtcTime("123456.05"))
        assertEquals("n/a", displayUtcTime(""))
    }

    @Test
    fun `epoch millis utc display uses fixed millisecond precision`() {
        assertEquals("2026-06-17T12:34:56.000Z", displayEpochMillisUtc(1781699696000L))
        assertEquals("2026-06-17T12:34:56.050Z", displayEpochMillisUtc(1781699696050L))
        assertEquals("n/a", displayEpochMillisUtc(null))
        assertEquals("n/a", displayEpochMillisUtc(0L))
    }

    @Test
    fun `receiver frequency default follows selected receiver family`() {
        assertEquals(DefaultUm980ReceiverFrequency, receiverFrequencyForFamily("um980-n4"))
        assertEquals(DefaultUm980ReceiverFrequency, receiverFrequencyForFamily("Unicore N4"))
        assertEquals(DefaultUbloxReceiverFrequency, receiverFrequencyForFamily("ublox-m8t"))
        assertEquals(DefaultUm980ReceiverFrequency, receiverFrequencyForFamily(null))
    }
}
