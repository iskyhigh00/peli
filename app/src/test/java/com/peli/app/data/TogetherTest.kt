package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherTest {
    private fun partner(id: Int, watchlist: Boolean = false, seen: Boolean = false, disliked: Boolean = false) =
        entry(id, owner = OWNER_PARTNER, watchlist = watchlist, seen = seen, disliked = disliked)

    @Test fun matchesExcludeSeenAndDislikedAndOrderMutualFirst() {
        val mine = listOf(
            entry(1, watchlist = true),
            entry(2, watchlist = true),
            entry(3, watchlist = true),
            entry(4, watchlist = true),
        )
        val hers = listOf(
            partner(1, watchlist = true),
            partner(2, seen = true),
            partner(3, disliked = true),
            partner(5, watchlist = true),
        )
        val result = computeMatches(mine, hers)

        assertEquals(listOf(1, 4, 5), result.map { it.entry.movieId })
        assertTrue(result[0].both)
        assertTrue(result[1].iWant && !result[1].partnerWants)
        assertTrue(result[2].partnerWants && !result[2].iWant)
    }

    @Test fun movieSeenByMeIsNotACandidate() {
        val result = computeMatches(listOf(entry(1, seen = true, liked = true)), listOf(partner(1, watchlist = true)))
        assertTrue(result.isEmpty())
    }

    @Test fun myDislikeRemovesItEvenIfPartnerWantsIt() {
        val result = computeMatches(listOf(entry(1, disliked = true)), listOf(partner(1, watchlist = true)))
        assertTrue(result.isEmpty())
    }

    @Test fun nobodyWantingMeansNoMatch() {
        assertTrue(computeMatches(listOf(entry(1)), listOf(partner(1))).isEmpty())
        assertTrue(computeMatches(emptyList(), emptyList()).isEmpty())
    }

    @Test fun singleSidedMatchesAreSortedByTitle() {
        val mine = listOf(entry(1, title = "Zeta", watchlist = true), entry(2, title = "Alfa", watchlist = true))
        val result = computeMatches(mine, emptyList())
        assertEquals(listOf("Alfa", "Zeta"), result.map { it.entry.title })
        assertFalse(result.any { it.both })
    }
}
