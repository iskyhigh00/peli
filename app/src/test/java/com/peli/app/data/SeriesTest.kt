package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesTest {
    private fun s(
        watchlist: Boolean = false, seen: Boolean = false, liked: Boolean = false, disliked: Boolean = false, myRating: Int = 0,
    ) = SeriesEntry(1, "Dark", "", null, "2017", watchlist = watchlist, seen = seen, liked = liked, disliked = disliked, myRating = myRating)

    @Test fun tmdbSeriesUsesNameAndFirstAirDate() {
        val e = TmdbTv(id = 5, name = "Dark", original_name = " Dark ", first_air_date = "2017-12-01", genre_ids = listOf(18, 9648)).toSeries()
        assertEquals("Dark", e.title)
        assertEquals("2017", e.year)
        assertEquals("2017-12-01", e.firstAirDate)
        assertEquals("18,9648", e.genres)
        assertEquals("Dark", e.originalTitle)
    }

    @Test fun missingFieldsDoNotBreakTheConversion() {
        val e = TmdbTv(id = 7, first_air_date = "").toSeries()
        assertEquals("", e.title)
        assertEquals("", e.year)
        assertNull(e.firstAirDate)
        assertNull(e.genres)
    }

    @Test fun wantingASeriesAlreadySeenChangesNothing() {
        val before = s(seen = true)
        assertEquals(before, before.toggleWatch())
    }

    @Test fun likingImpliesSeenAndLeavesTheWatchlist() {
        val e = s(watchlist = true, disliked = true).toggleLiked()
        assertTrue(e.liked && e.seen)
        assertFalse(e.watchlist || e.disliked)
    }

    @Test fun markingSeenLeavesTheWatchlistAndUnmarkingClearsLikeAndRating() {
        assertFalse(s(watchlist = true).toggleSeen().watchlist)
        val e = s(seen = true, liked = true, myRating = 9).toggleSeen()
        assertFalse(e.seen || e.liked)
        assertEquals(0, e.myRating)
    }

    @Test fun ratingImpliesSeenAndIsClamped() {
        val e = s(watchlist = true).withRating(15)
        assertEquals(10, e.myRating)
        assertTrue(e.seen)
        assertFalse(e.watchlist)
        assertEquals(0, e.withRating(0).myRating)
    }

    @Test fun dislikeLeavesTheWatchlist() {
        val e = s(watchlist = true).toggleDisliked()
        assertTrue(e.disliked)
        assertFalse(e.watchlist)
    }

    @Test fun anUnmarkedSeriesIsEmptyAndHasNoLabel() {
        assertTrue(s().isEmpty)
        assertNull(s().stateLabel())
        assertEquals("Quiero verla", s(watchlist = true).stateLabel())
    }

    @Test fun filtersPickOnlyMatchingMarks() {
        assertTrue(SeriesFilter.All.accepts(null))
        assertFalse(SeriesFilter.Want.accepts(null))
        assertTrue(SeriesFilter.Want.accepts(s(watchlist = true)))
        assertTrue(SeriesFilter.Seen.accepts(s(seen = true)))
        assertFalse(SeriesFilter.Liked.accepts(s(seen = true)))
    }
}
