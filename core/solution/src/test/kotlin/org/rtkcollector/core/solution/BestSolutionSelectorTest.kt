package org.rtkcollector.core.solution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BestSolutionSelectorTest {
    @Test
    fun `selects higher quality fresh candidate`() {
        val now = 10_000L
        val single = candidate("single", FixClass.SINGLE, updatedAtMillis = now - 500)
        val rtkFloat = candidate("float", FixClass.RTK_FLOAT, updatedAtMillis = now - 900)

        val selected = BestSolutionSelector.select(listOf(single, rtkFloat), nowMillis = now)

        assertEquals("float", selected?.sourceId)
        assertEquals(FixClass.RTK_FLOAT, selected?.fixClass)
    }

    @Test
    fun `drops stale candidates`() {
        val now = 10_000L
        val stale = candidate("old", FixClass.RTK_FIXED, updatedAtMillis = now - 6_000)

        val selected = BestSolutionSelector.select(listOf(stale), nowMillis = now)

        assertNull(selected)
    }

    @Test
    fun `uses better accuracy when quality is tied`() {
        val now = 10_000L
        val coarse = candidate("coarse", FixClass.DGPS, updatedAtMillis = now - 100, horizontalAccuracyM = 2.0)
        val precise = candidate("precise", FixClass.DGPS, updatedAtMillis = now - 300, horizontalAccuracyM = 0.4)

        val selected = BestSolutionSelector.select(listOf(coarse, precise), nowMillis = now)

        assertEquals("precise", selected?.sourceId)
    }

    @Test
    fun `ppp converging does not outrank dgps`() {
        val now = 10_000L
        val dgps = candidate("dgps", FixClass.DGPS, updatedAtMillis = now - 500)
        val pppConverging = candidate("ppp", FixClass.PPP_CONVERGING, updatedAtMillis = now - 100)

        val selected = BestSolutionSelector.select(listOf(dgps, pppConverging), nowMillis = now)

        assertEquals("dgps", selected?.sourceId)
    }

    @Test
    fun `ignores candidate dated in the future`() {
        val now = 10_000L
        val future = candidate("future", FixClass.RTK_FIXED, updatedAtMillis = now + 1_000)

        val selected = BestSolutionSelector.select(listOf(future), nowMillis = now)

        assertNull(selected)
    }

    @Test
    fun `prefers more recent candidate when rank and accuracy tie`() {
        val now = 10_000L
        val older = candidate(
            "older",
            FixClass.SINGLE,
            updatedAtMillis = now - 1_500,
            horizontalAccuracyM = 1.0,
        )
        val newer = candidate(
            "newer",
            FixClass.SINGLE,
            updatedAtMillis = now - 500,
            horizontalAccuracyM = 1.0,
        )

        val selected = BestSolutionSelector.select(listOf(older, newer), nowMillis = now)

        assertEquals("newer", selected?.sourceId)
    }

    @Test
    fun `respects custom maxAgeMillis`() {
        val now = 10_000L
        val recent = candidate("recent", FixClass.RTK_FIXED, updatedAtMillis = now - 3_000)

        val selectedDefault = BestSolutionSelector.select(listOf(recent), nowMillis = now)
        val selectedTight = BestSolutionSelector.select(listOf(recent), nowMillis = now, maxAgeMillis = 1_000)

        assertEquals("recent", selectedDefault?.sourceId)
        assertNull(selectedTight)
    }

    @Test
    fun `sbas and dgps share rank so accuracy breaks the tie`() {
        val now = 10_000L
        val sbas = candidate(
            "sbas",
            FixClass.SBAS,
            updatedAtMillis = now - 100,
            horizontalAccuracyM = 2.0,
        )
        val dgps = candidate(
            "dgps",
            FixClass.DGPS,
            updatedAtMillis = now - 100,
            horizontalAccuracyM = 0.4,
        )

        val selected = BestSolutionSelector.select(listOf(sbas, dgps), nowMillis = now)

        assertEquals("dgps", selected?.sourceId)
    }

    private fun candidate(
        id: String,
        fixClass: FixClass,
        updatedAtMillis: Long,
        horizontalAccuracyM: Double? = null,
    ): SolutionCandidate =
        SolutionCandidate(
            sourceId = id,
            receiverFamily = "test",
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = fixClass,
            updatedAtMillis = updatedAtMillis,
            latDeg = 50.0,
            lonDeg = 14.0,
            ellipsoidalHeightM = 300.0,
            mslAltitudeM = 255.0,
            horizontalAccuracyM = horizontalAccuracyM,
            verticalAccuracyM = null,
            satellitesUsed = 12,
            satellitesInView = 18,
        )
}
