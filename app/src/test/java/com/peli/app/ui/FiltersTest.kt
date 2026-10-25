package com.peli.app.ui

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {
    @Test fun searchFiltersLookAtMyMark() {
        val want = entry(watchlist = true)
        assertTrue(SearchFilter.All.accepts(null))
        assertTrue(SearchFilter.Unmarked.accepts(null))
        assertFalse(SearchFilter.Want.accepts(null))
        assertTrue(SearchFilter.Want.accepts(want))
        assertFalse(SearchFilter.Unmarked.accepts(want))
        assertTrue(SearchFilter.Liked.accepts(entry(liked = true, seen = true)))
        assertTrue(SearchFilter.Seen.accepts(entry(seen = true)))
        assertTrue(SearchFilter.Disliked.accepts(entry(disliked = true)))
    }

    @Test fun mineFilters() {
        assertTrue(MineFilter.All.accepts(entry()))
        assertTrue(MineFilter.Want.accepts(entry(watchlist = true)))
        assertFalse(MineFilter.Want.accepts(entry(liked = true, seen = true)))
        assertTrue(MineFilter.Liked.accepts(entry(liked = true, seen = true)))
        assertTrue(MineFilter.Seen.accepts(entry(seen = true)))
        assertTrue(MineFilter.Disliked.accepts(entry(disliked = true)))
    }

    private val a = entry(1, title = "beta", year = "2001", rating = 5.0, updatedAt = 10)
    private val b = entry(2, title = "Alfa", year = "1999", rating = 9.0, updatedAt = 30)
    private val c = entry(3, title = "gamma", year = "2010", rating = null, updatedAt = 20)

    @Test fun ordersByTitleIgnoringCase() {
        assertEquals(listOf("Alfa", "beta", "gamma"), listOf(a, b, c).orderedBy(MineSort.Title).map { it.title })
    }

    @Test fun ordersByMostRecentlyMarked() {
        assertEquals(listOf(2, 3, 1), listOf(a, b, c).orderedBy(MineSort.Recent).map { it.movieId })
    }

    @Test fun ordersByYearNewestFirst() {
        assertEquals(listOf(3, 1, 2), listOf(a, b, c).orderedBy(MineSort.Year).map { it.movieId })
    }

    @Test fun ordersByRatingWithUnratedLast() {
        assertEquals(listOf(2, 1, 3), listOf(a, b, c).orderedBy(MineSort.Rating).map { it.movieId })
    }

    @Test fun queueDropsEverythingAlreadyMarked() {
        val queue = listOf(entry(1), entry(2), entry(3), entry(4))
        val marked = listOf(entry(2, watchlist = true), entry(3, liked = true, seen = true), entry(4, disliked = true))
        assertEquals(listOf(1), queue.withoutMarked(marked).map { it.movieId })
    }

    @Test fun queueKeepsEverythingWhenNothingIsMarked() {
        val queue = listOf(entry(1), entry(2))
        assertEquals(listOf(1, 2), queue.withoutMarked(emptyList()).map { it.movieId })
        // Una marca vacía no cuenta como marcada.
        assertEquals(listOf(1, 2), queue.withoutMarked(listOf(entry(1))).map { it.movieId })
    }

    @Test fun ties_areBrokenByTitleSoOrderIsStable() {
        val x = entry(1, title = "B", updatedAt = 5)
        val y = entry(2, title = "A", updatedAt = 5)
        assertEquals(listOf("A", "B"), listOf(x, y).orderedBy(MineSort.Recent).map { it.title })
    }
}
