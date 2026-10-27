package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureTest {
    private val day = 86_400_000L
    private val t0 = 1_000_000_000_000L

    private fun delivered(round: Long = 10, at: Long = t0) = Exposure(1, round, at)
    private fun visible(round: Long = 10, at: Long = t0, vRound: Long = 10, vAt: Long = t0) =
        Exposure(1, round, at, visibleRound = vRound, visibleAt = vAt)
    private fun skipped(skips: Int, sRound: Long = 10, sAt: Long = t0) =
        Exposure(1, 10, t0, visibleRound = 10, visibleAt = t0, skipRound = sRound, skipAt = sAt, skips = skips)

    @Test fun neverSeenBeforeIsAlwaysEligible() {
        assertTrue(ExposureRules.eligible(null, 1, t0))
    }

    @Test fun hardFloorBlocksRecentDeliveries() {
        assertFalse(ExposureRules.eligible(delivered(round = 10), round = 14, now = t0 + 365 * day))
    }

    @Test fun deliveredButNeverVisibleNeeds60RoundsAnd14Days() {
        assertFalse(ExposureRules.eligible(delivered(), round = 69, now = t0 + 20 * day))
        assertFalse(ExposureRules.eligible(delivered(), round = 70, now = t0 + 13 * day))
        assertTrue(ExposureRules.eligible(delivered(), round = 70, now = t0 + 14 * day))
    }

    @Test fun visibleNeeds100RoundsAnd30DaysCountedFromTheVisibleEvent() {
        val v = visible()
        assertFalse(ExposureRules.eligible(v, round = 109, now = t0 + 40 * day))
        assertFalse(ExposureRules.eligible(v, round = 110, now = t0 + 29 * day))
        assertTrue(ExposureRules.eligible(v, round = 110, now = t0 + 30 * day))
    }

    @Test fun aLateVisibleEventDoesNotShortenAnyPeriod() {
        // entregada en la ronda 10 (hace 60 días) pero recién vista en la 69: manda el evento más tardío
        val late = visible(round = 10, at = t0, vRound = 69, vAt = t0 + 60 * day)
        assertFalse(ExposureRules.eligible(late, round = 120, now = t0 + 70 * day))   // 51 rondas desde que se vio
        assertTrue(ExposureRules.eligible(late, round = 169, now = t0 + 91 * day))
    }

    @Test fun skippedWaitsLongerEachTimeFromTheSkipEvent() {
        assertEquals(1, skipped(1).skips)
        assertFalse(ExposureRules.eligible(skipped(1), round = 159, now = t0 + 100 * day))
        assertFalse(ExposureRules.eligible(skipped(1), round = 160, now = t0 + 59 * day))
        assertTrue(ExposureRules.eligible(skipped(1), round = 160, now = t0 + 60 * day))
        assertFalse(ExposureRules.eligible(skipped(2), round = 210, now = t0 + 179 * day))
        assertTrue(ExposureRules.eligible(skipped(2), round = 210, now = t0 + 180 * day))
    }

    @Test fun thereIsNoAutomaticRelaxation() {
        // 14 días desde la entrega: a los 10 días sigue bloqueada sin importar cuántas rondas pasen
        assertFalse(ExposureRules.eligible(delivered(), round = 5_000, now = t0 + 10 * day))
    }

    @Test fun tiersPutNeverDeliveredFirstAndSkippedLast() {
        assertEquals(0, ExposureRules.tier(null))
        assertEquals(1, ExposureRules.tier(delivered()))
        assertEquals(1, ExposureRules.tier(visible()))
        assertEquals(2, ExposureRules.tier(skipped(1)))
    }

    @Test fun encodeDecodeRoundTripAndIgnoresGarbage() {
        val items = listOf(delivered(), Exposure(2, 5, t0 + 5, 6, t0 + 9, 7, t0 + 11, 2))
        val decoded = ExposureRules.decode(ExposureRules.encode(items) + ";basura;1,2,3")
        assertEquals(items.associateBy { it.id }, decoded)
        assertTrue(ExposureRules.decode("").isEmpty())
    }

    @Test fun readsTheOldFiveFieldFormat() {
        val d = ExposureRules.decode("3,12,500,1,1;4,9,100,0,0")
        assertEquals(Exposure(3, 12, 500, 12, 500, 12, 500, 1), d.getValue(3))
        assertEquals(Exposure(4, 9, 100), d.getValue(4))
    }

    @Test fun trimNeverForgetsWhatIsStillProtected() {
        // 4.000 omitidas protegidas + una entrega nueva: no se olvida ninguna (límite blando)
        val items = LinkedHashMap<Int, Exposure>()
        (1..4000).forEach { items[it] = Exposure(it, 50, t0, 50, t0, 50, t0, 1) }
        items[9999] = Exposure(9999, 60, t0 + day)
        val kept = ExposureRules.trim(items, round = 61, now = t0 + 2 * day, capacity = 4000)
        assertEquals(4001, kept.size)
        assertTrue(9999 in kept)
    }

    @Test fun trimOnlyDropsFullyExpiredRecordsOldestFirst() {
        val items = LinkedHashMap<Int, Exposure>()
        items[1] = Exposure(1, 1, t0)                    // vencida hace mucho
        items[2] = Exposure(2, 2, t0 + day)              // vencida
        items[3] = Exposure(3, 500, t0 + 400 * day)      // reciente: protegida
        val kept = ExposureRules.trim(items, round = 520, now = t0 + 401 * day, capacity = 2)
        assertEquals(setOf(2, 3), kept.keys)
    }

    @Test fun migrationKeepsOrderAndBlocksTheMostRecentLongest() {
        val migrated = ExposureRules.migrate((1..80).toList(), round = 20, now = t0, perRound = 40)
        assertEquals(80, migrated.size)
        assertEquals(20L, migrated.getValue(80).deliveredRound)
        assertEquals(19L, migrated.getValue(1).deliveredRound)
        assertFalse(ExposureRules.eligible(migrated.getValue(80), round = 21, now = t0 + day))
    }

    // ---- Reparto por niveles ----

    @Test fun neverDeliveredAreUsedBeforeAnyRepeat() {
        val candidates = (1..10).map { entry(it) }
        val snapshot = mapOf(
            1 to Exposure(1, 1, t0),                                     // vencida (entregada sin respuesta)
            2 to Exposure(2, 1, t0, 1, t0, 1, t0, 1),                    // omitida vencida
            3 to Exposure(3, 198, t0),                                   // reciente: bloqueada
        )
        val tiers = Delivery.tiers(candidates, snapshot, round = 200, now = t0 + 400 * day)
        assertEquals(setOf(4, 5, 6, 7, 8, 9, 10), tiers[0].map { it.movieId }.toSet())
        assertEquals(listOf(1), tiers[1].map { it.movieId })
        assertEquals(listOf(2), tiers[2].map { it.movieId })
        assertTrue(tiers.flatten().none { it.movieId == 3 })
    }

    @Test fun chooseOnlyTouchesTheNextTierWhenThePreviousOneIsNotEnough() {
        val t0list = (1..5).map { entry(it) }
        val t1list = (6..9).map { entry(it) }
        val t2list = listOf(entry(10))
        val pick: (List<Entry>, Int, List<Entry>) -> List<Entry> = { list, need, _ -> list.take(need) }
        assertEquals(listOf(1, 2, 3), Delivery.choose(listOf(t0list, t1list, t2list), 3, pick).map { it.movieId })
        assertEquals((1..7).toList(), Delivery.choose(listOf(t0list, t1list, t2list), 7, pick).map { it.movieId })
        assertEquals((1..10).toList(), Delivery.choose(listOf(t0list, t1list, t2list), 40, pick).map { it.movieId })
    }
}
