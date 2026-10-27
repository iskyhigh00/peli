package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureTest {
    private val day = 86_400_000L
    private val t0 = 1_000_000_000_000L

    private fun x(round: Long = 10, at: Long = t0, visible: Boolean = false, skips: Int = 0) = Exposure(1, round, at, visible, skips)

    @Test fun neverSeenBeforeIsAlwaysEligible() {
        assertTrue(ExposureRules.eligible(null, 1, t0))
    }

    @Test fun hardFloorBlocksEvenWithMaximumRelaxation() {
        assertFalse(ExposureRules.eligible(x(round = 10), round = 14, now = t0 + 365 * day, relax = 0.1))
        assertTrue(ExposureRules.eligible(x(round = 10), round = 16, now = t0 + 365 * day, relax = 0.1))
    }

    @Test fun deliveredButNeverVisibleNeeds60RoundsAnd14Days() {
        assertFalse(ExposureRules.eligible(x(), round = 69, now = t0 + 20 * day))
        assertFalse(ExposureRules.eligible(x(), round = 70, now = t0 + 13 * day))
        assertTrue(ExposureRules.eligible(x(), round = 70, now = t0 + 14 * day))
    }

    @Test fun visibleNeeds100RoundsAnd30Days() {
        val v = x(visible = true)
        assertFalse(ExposureRules.eligible(v, round = 109, now = t0 + 40 * day))
        assertFalse(ExposureRules.eligible(v, round = 110, now = t0 + 29 * day))
        assertTrue(ExposureRules.eligible(v, round = 110, now = t0 + 30 * day))
    }

    @Test fun skippedWaitsLongerEachTime() {
        assertEquals(150 to 60, ExposureRules.waits(x(skips = 1)))
        assertEquals(200 to 180, ExposureRules.waits(x(skips = 2)))
        assertFalse(ExposureRules.eligible(x(skips = 1), round = 159, now = t0 + 100 * day))
        assertTrue(ExposureRules.eligible(x(skips = 1), round = 160, now = t0 + 60 * day))
        assertFalse(ExposureRules.eligible(x(skips = 2), round = 210, now = t0 + 179 * day))
    }

    @Test fun relaxationShortensWaitsButNotTheFloor() {
        val e = x()
        assertFalse(ExposureRules.eligible(e, round = 40, now = t0 + 10 * day, relax = 1.0))
        assertTrue(ExposureRules.eligible(e, round = 40, now = t0 + 10 * day, relax = 0.5))
        assertEquals(listOf(1.0, 0.5, 0.25, 0.1), ExposureRules.RELAX_LEVELS)
    }

    @Test fun encodeDecodeRoundTripAndIgnoresGarbage() {
        val items = listOf(x(), Exposure(2, 5, t0 + 5, true, 2))
        val decoded = ExposureRules.decode(ExposureRules.encode(items) + ";basura;1,2,3")
        assertEquals(items.associateBy { it.id }, decoded)
        assertTrue(ExposureRules.decode("").isEmpty())
    }

    @Test fun trimKeepsSkippedAndNewest() {
        val items = (1..10).associateWith { Exposure(it, 1, t0 + it, false, if (it == 1) 1 else 0) }
        val kept = ExposureRules.trim(items, capacity = 3)
        assertEquals(setOf(1, 10, 9), kept.keys)
    }

    @Test fun migrationKeepsOrderAndBlocksTheMostRecentLongest() {
        val migrated = ExposureRules.migrate((1..80).toList(), round = 20, now = t0, perRound = 40)
        assertEquals(80, migrated.size)
        assertEquals(20L, migrated.getValue(80).round)
        assertEquals(19L, migrated.getValue(1).round)
        assertFalse(ExposureRules.eligible(migrated.getValue(80), round = 21, now = t0 + day))
    }
}
